package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import gov.anzong.androidnga.base.util.ContextUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okio.Buffer;
import okio.BufferedSource;

/** Lazy native GET transport whose resolver and browser session are isolated to linux.do. */
public final class LinuxDoHttpSession {

    private static final LinuxDoHttpSession INSTANCE = new LinuxDoHttpSession();
    private static final long MAX_RESPONSE_BYTES = 8L * 1024L * 1024L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Object mClientLock = new Object();
    private OkHttpClient mClient;
    private String mClientDohUrl;
    private LinuxDoCloseableDns mPlatformDns;

    public static LinuxDoHttpSession getInstance() {
        return INSTANCE;
    }

    public void fetch(String path, LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> enqueueOnMain(path, callback));
    }

    public void invalidateClient() {
        LinuxDoCronetSession.getInstance().invalidate();
        synchronized (mClientLock) {
            if (mClient != null) {
                mClient.dispatcher().cancelAll();
                mClient.connectionPool().evictAll();
            }
            closePlatformDns();
            mClient = null;
            mClientDohUrl = null;
        }
    }

    private void enqueueOnMain(String path, LinuxDoWebSession.Callback callback) {
        try {
            enqueueSafely(path, callback);
        } catch (RuntimeException | LinkageError error) {
            // Resolver/WebView-provider/library setup must never terminate the UI process.
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private void enqueueSafely(String path, LinuxDoWebSession.Callback callback) {
        if (!LinuxDoTransportPolicy.isAllowedPath(path)) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            return;
        }
        String cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
        if (cookie == null) cookie = "";
        String userAgent = WebSettings.getDefaultUserAgent(ContextUtils.getApplication());
        HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)) {
            LinuxDoCronetSession.getInstance().fetch(
                    path, cookie, userAgent, callback);
            return;
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(LinuxDoConstants.ORIGIN + path)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get();
        if (!cookie.trim().isEmpty()) requestBuilder.header("Cookie", cookie);
        Request request = requestBuilder.build();
        client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    String body = readBounded(response.body());
                    LinuxDoTransportPolicy.ResponseKind kind =
                            LinuxDoTransportPolicy.classify(response.code(), body);
                    if (kind == LinuxDoTransportPolicy.ResponseKind.JSON) {
                        persistResponseCookies(response.headers("Set-Cookie"));
                        mMainHandler.post(() -> callback.onSuccess(body));
                    } else {
                        LinuxDoWebSession.Failure failure =
                                kind == LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED
                                ? LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                                : LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
                        if (failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED) {
                            LinuxDoSessionState.setReady(false);
                        }
                        postFailure(callback, failure);
                    }
                } catch (ResponseTooLargeException error) {
                    postFailure(callback, LinuxDoWebSession.Failure.RESPONSE_TOO_LARGE);
                } catch (Exception error) {
                    postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                }
            }
        });
    }

    private OkHttpClient client() {
        String dohUrl = LinuxDoDohConfig.currentUrl();
        synchronized (mClientLock) {
            if (mClient != null && dohUrl.equals(mClientDohUrl)) return mClient;
            OkHttpClient bootstrap = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
            HttpUrl resolverUrl = HttpUrl.get(dohUrl);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)) {
                closePlatformDns();
                mPlatformDns = new LinuxDoHttpEngineDns(ContextUtils.getApplication());
                mClient = bootstrap.newBuilder()
                        .dns(mPlatformDns)
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .connectionPool(new ConnectionPool(2, 2, TimeUnit.MINUTES))
                        .build();
                mClientDohUrl = dohUrl;
                return mClient;
            }
            DnsOverHttps.Builder dnsBuilder = new DnsOverHttps.Builder()
                    .client(bootstrap)
                    .url(resolverUrl)
                    .includeIPv6(true);
            List<InetAddress> bootstrapAddresses =
                    LinuxDoDohConfig.bootstrapAddresses(resolverUrl);
            if (!bootstrapAddresses.isEmpty()) {
                dnsBuilder.bootstrapDnsHosts(bootstrapAddresses);
            }
            DnsOverHttps dns = dnsBuilder.build();
            mClient = bootstrap.newBuilder()
                    .dns(dns)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(2, 2, TimeUnit.MINUTES))
                    .build();
            mClientDohUrl = dohUrl;
            return mClient;
        }
    }

    private void closePlatformDns() {
        if (mPlatformDns == null) return;
        try {
            mPlatformDns.close();
        } catch (RuntimeException ignored) {
            // A resolver teardown must not prevent replacing the isolated client.
        }
        mPlatformDns = null;
    }

    private static String readBounded(ResponseBody body) throws IOException {
        if (body == null) return "";
        if (body.contentLength() > MAX_RESPONSE_BYTES) throw new ResponseTooLargeException();
        BufferedSource source = body.source();
        Buffer buffer = new Buffer();
        long total = 0L;
        while (true) {
            long read = source.read(buffer, 8192L);
            if (read == -1L) break;
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new ResponseTooLargeException();
        }
        return buffer.readString(StandardCharsets.UTF_8);
    }

    private void postFailure(
            LinuxDoWebSession.Callback callback, LinuxDoWebSession.Failure failure) {
        mMainHandler.post(() -> callback.onFailure(failure));
    }

    private void persistResponseCookies(List<String> cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        mMainHandler.post(() -> {
            CookieManager manager = CookieManager.getInstance();
            for (String cookie : cookies) manager.setCookie(LinuxDoConstants.ORIGIN, cookie);
            manager.flush();
        });
    }

    private static final class ResponseTooLargeException extends IOException {
    }

    private LinuxDoHttpSession() {
    }
}
