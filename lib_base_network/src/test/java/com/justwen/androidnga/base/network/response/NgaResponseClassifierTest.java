package com.justwen.androidnga.base.network.response;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.Test;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;

public class NgaResponseClassifierTest {

    private final NgaResponseClassifier classifier = new NgaResponseClassifier();

    @Test
    public void rawResponsePreservesBoundedBytesHeadersFinalUrlAndRedirectCount() throws Exception {
        byte[] bytes = "payload".getBytes("UTF-8");
        RawNgaResponse raw = RawNgaResponse.from(response(
                200, bytes, "application/json; charset=UTF-8", "7", 2));

        assertEquals(200, raw.getStatusCode());
        assertArrayEquals(bytes, raw.getRawBytes());
        assertEquals("7", raw.firstHeader("X-Test"));
        assertEquals("https://bbs.ngacn.cc/final?tid=secret", raw.getFinalUrl());
        assertEquals(2, raw.getRedirectCount());
        assertFalse(raw.toString().contains("secret"));
        assertFalse(raw.toString().contains("payload"));
    }

    @Test
    public void oversizedResponseFailsInsteadOfReturningPartialPayload() throws Exception {
        try {
            RawNgaResponse.from(response(
                    200, new byte[] {1, 2, 3, 4}, "application/octet-stream", null, 0), 3);
            fail("expected bounded read failure");
        } catch (ResponseTooLargeException expected) {
            assertEquals(3, expected.getMaximumBytes());
            assertEquals(ClassifiedNgaResponse.Type.RESPONSE_TOO_LARGE,
                    classifier.classifyFailure(expected).getType());
        }
    }

    @Test
    public void classifiesUtf8GbkAndSanitizedPayloads() throws Exception {
        assertPayload("{\"ok\":true}", "application/json; charset=UTF-8", "{\"ok\":true}");

        String chinese = "\u4e2d\u6587";
        assertPayload(chinese, "text/plain; charset=GB18030", chinese);

        RawNgaResponse guarded = RawNgaResponse.from(response(
                200,
                "for(;;);{\"ok\":true}".getBytes("UTF-8"),
                "application/json; charset=UTF-8", null, 0));
        assertEquals("{\"ok\":true}", classifier.classify(guarded).getPayload());
    }

    @Test
    public void classifiesAuthChallengeRateLimitSiteMessageDecodeAndNetwork() throws Exception {
        assertType(403, "denied", "text/plain; charset=UTF-8",
                ClassifiedNgaResponse.Type.AUTH_REQUIRED);
        assertType(403, "captcha required", "text/html; charset=UTF-8",
                ClassifiedNgaResponse.Type.CHALLENGE);

        RawNgaResponse limited = RawNgaResponse.from(response(
                429, "slow down".getBytes("UTF-8"), "text/plain", "30", 0));
        ClassifiedNgaResponse rateLimit = classifier.classify(limited);
        assertEquals(ClassifiedNgaResponse.Type.RATE_LIMIT, rateLimit.getType());
        assertEquals("30", rateLimit.getRetryAfter());

        assertType(200, "<html><title>Notice</title></html>", "text/html; charset=UTF-8",
                ClassifiedNgaResponse.Type.SITE_MESSAGE);
        assertType(200, "payload", "text/plain; charset=not-a-real-charset",
                ClassifiedNgaResponse.Type.DECODE_ERROR);
        assertEquals(ClassifiedNgaResponse.Type.NETWORK_ERROR,
                classifier.classifyFailure(new IOException("timeout")).getType());
    }

    private void assertPayload(String source, String contentType, String expected) throws Exception {
        Charset charset = Charset.forName(contentType.substring(contentType.indexOf("charset=") + 8));
        RawNgaResponse raw = RawNgaResponse.from(response(
                200, source.getBytes(charset), contentType, null, 0));
        ClassifiedNgaResponse classified = classifier.classify(raw);
        assertEquals(ClassifiedNgaResponse.Type.PAYLOAD, classified.getType());
        assertEquals(expected, classified.getPayload());
    }

    private void assertType(
            int status,
            String body,
            String contentType,
            ClassifiedNgaResponse.Type expected
    ) throws Exception {
        RawNgaResponse raw = RawNgaResponse.from(response(
                status, body.getBytes("UTF-8"), contentType, null, 0));
        assertEquals(expected, classifier.classify(raw).getType());
    }

    private retrofit2.Response<ResponseBody> response(
            int code,
            byte[] bytes,
            String contentType,
            String testHeader,
            int redirectCount
    ) {
        Request request = new Request.Builder()
                .url("https://bbs.ngacn.cc/final?tid=secret")
                .build();
        okhttp3.Response prior = null;
        for (int i = 0; i < redirectCount; i++) {
            prior = new okhttp3.Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Redirect")
                    .priorResponse(prior)
                    .build();
        }
        okhttp3.Response.Builder rawBuilder = new okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test")
                .priorResponse(prior);
        if (contentType != null) {
            rawBuilder.header("Content-Type", contentType);
        }
        if (testHeader != null) {
            rawBuilder.header(code == 429 ? "Retry-After" : "X-Test", testHeader);
        }
        okhttp3.Response raw = rawBuilder.build();
        ResponseBody body = ResponseBody.create(
                contentType == null ? null : MediaType.parse(contentType), bytes);
        if (code >= 200 && code < 300) {
            return retrofit2.Response.success(body, raw);
        }
        return retrofit2.Response.error(body, raw);
    }
}
