package com.justwen.androidnga.base.network.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.justwen.androidnga.base.network.retrofit.RetrofitService;
import com.justwen.androidnga.base.network.retrofit.converter.JsonStringConvertFactory;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

public class NgaRequestBoundaryTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void contextIsImmutableAndRedactsSessionMaterial() {
        NgaRequestContext context = context("account-A", 7, "uid=11; cid=secret");

        assertEquals("account-A", context.getAccountId());
        assertEquals(7, context.getSessionGeneration());
        assertEquals("uid=11; cid=secret", context.getCookieHeader());
        assertFalse(context.toString().contains("account-A"));
        assertFalse(context.toString().contains("secret"));
        assertTrue(context.toString().contains(FoundationAccessPolicy.READ_TOPIC_LIST));
    }

    @Test
    public void reviewedOperationIdsAreExactlyTheThreeFoundationReads() {
        FoundationAccessPolicy policy = FoundationAccessPolicy.enabledForReviewedReads();

        assertEquals(new HashSet<>(Arrays.asList(
                        FoundationAccessPolicy.READ_BOARD_LIST,
                        FoundationAccessPolicy.READ_TOPIC_LIST,
                        FoundationAccessPolicy.READ_ARTICLE_LIST)),
                policy.reviewedReadOperations());
    }

    @Test
    public void requestKeepsCapturedAccountWhenAnotherSnapshotExistsBeforeExecution() throws Exception {
        NgaRequestContext accountA = context("account-A", 2, "uid=1; cid=A");
        Request queuedForA = new Request.Builder()
                .url("https://bbs.ngacn.cc/thread.php")
                .tag(NgaRequestContext.class, accountA)
                .build();

        NgaRequestContext accountB = context("account-B", 3, "uid=2; cid=B");
        Request secured = NgaRequestInterceptors.secureExchange(
                queuedForA, "NgaJustWorks/Test");

        assertSame(accountA, secured.tag(NgaRequestContext.class));
        assertEquals("uid=1; cid=A", secured.header("Cookie"));
        assertEquals("uid=2; cid=B", accountB.getCookieHeader());
    }

    @Test
    public void missingMutationAndUnknownContextMakeZeroRequests() throws Exception {
        assertDenied(new Request.Builder().url(server.url("/missing")).build(),
                FoundationAccessDeniedException.Reason.MISSING_CONTEXT);
        assertDenied(taggedRequest("/mutation", new NgaRequestContext(
                        FoundationAccessPolicy.READ_TOPIC_LIST,
                        NgaRequestContext.Intent.MUTATION,
                        "account-A", 1, "cid=A")),
                FoundationAccessDeniedException.Reason.MUTATION_DENIED);
        assertDenied(taggedRequest("/unknown", new NgaRequestContext(
                        "unreviewed.read",
                        NgaRequestContext.Intent.READ,
                        "account-A", 1, "cid=A")),
                FoundationAccessDeniedException.Reason.UNKNOWN_OPERATION);

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void defaultOffMakesZeroRequestsEvenForReviewedRead() throws Exception {
        OkHttpClient client = client(FoundationAccessPolicy.disabled());
        try {
            client.newCall(taggedRequest("/disabled", context("account-A", 1, "cid=A")))
                    .execute();
            fail("expected access denial");
        } catch (FoundationAccessDeniedException expected) {
            assertEquals(FoundationAccessDeniedException.Reason.READ_ACCESS_DISABLED,
                    expected.getReason());
        }
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void legacyRetrofitMethodWithoutContextMakesZeroRequests() throws Exception {
        RetrofitService service = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(JsonStringConvertFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(client(FoundationAccessPolicy.disabled()))
                .build()
                .create(RetrofitService.class);

        try {
            service.get(server.url("/legacy").toString()).blockingFirst();
            fail("expected access denial");
        } catch (RuntimeException expected) {
            assertTrue(hasCause(expected, FoundationAccessDeniedException.class));
        }
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void externalHostAndRedirectExchangesStripCredentials() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "/next"));
        server.enqueue(new MockResponse().setBody("ok"));
        Request request = taggedRequest("/start", context("account-A", 1, "cid=A"))
                .newBuilder()
                .header("Cookie", "fake")
                .header("Authorization", "fake-auth")
                .build();

        try (Response response = client(FoundationAccessPolicy.enabledForReviewedReads())
                .newCall(request).execute()) {
            assertEquals(200, response.code());
            assertTrue(response.priorResponse() != null);
        }

        RecordedRequest first = server.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest redirected = server.takeRequest(1, TimeUnit.SECONDS);
        assertNull(first.getHeader("Cookie"));
        assertNull(first.getHeader("Authorization"));
        assertNull(redirected.getHeader("Cookie"));
        assertNull(redirected.getHeader("Authorization"));
    }

    @Test
    public void credentialTargetRequiresExactHttpsDefaultPortAndNoUserInfo() {
        assertTrue(NgaRequestInterceptors.isCredentialTarget(
                okhttp3.HttpUrl.parse("https://bbs.ngacn.cc/thread.php")));
        assertFalse(NgaRequestInterceptors.isCredentialTarget(
                okhttp3.HttpUrl.parse("http://bbs.ngacn.cc/thread.php")));
        assertFalse(NgaRequestInterceptors.isCredentialTarget(
                okhttp3.HttpUrl.parse("https://bbs.ngacn.cc:8443/thread.php")));
        assertFalse(NgaRequestInterceptors.isCredentialTarget(
                okhttp3.HttpUrl.parse("https://bbs.ngacn.cc.example/thread.php")));
        assertFalse(NgaRequestInterceptors.isCredentialTarget(
                okhttp3.HttpUrl.parse("https://user@bbs.ngacn.cc/thread.php")));
    }

    private void assertDenied(Request request, FoundationAccessDeniedException.Reason reason)
            throws Exception {
        try {
            client(FoundationAccessPolicy.enabledForReviewedReads())
                    .newCall(request).execute();
            fail("expected access denial");
        } catch (FoundationAccessDeniedException expected) {
            assertEquals(reason, expected.getReason());
        }
    }

    private OkHttpClient client(FoundationAccessPolicy policy) {
        return new OkHttpClient.Builder()
                .addInterceptor(NgaRequestInterceptors.foundationGate(policy))
                .addNetworkInterceptor(NgaRequestInterceptors.credentialBoundary(
                        "NgaJustWorks/Test"))
                .build();
    }

    private Request taggedRequest(String path, NgaRequestContext context) {
        return new Request.Builder()
                .url(server.url(path))
                .tag(NgaRequestContext.class, context)
                .build();
    }

    private NgaRequestContext context(String accountId, long generation, String cookie) {
        return new NgaRequestContext(
                FoundationAccessPolicy.READ_TOPIC_LIST,
                NgaRequestContext.Intent.READ,
                accountId,
                generation,
                cookie);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
