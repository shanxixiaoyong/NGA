package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.util.LruCache;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import gov.anzong.androidnga.base.util.ContextUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okio.Buffer;
import okio.BufferedSource;

/** Lazy native transport whose resolver and browser session are isolated to linux.do. */
public final class LinuxDoHttpSession {

    private static final LinuxDoHttpSession INSTANCE = new LinuxDoHttpSession();
    private static final long MAX_RESPONSE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final LruCache<String, byte[]> AVATAR_CACHE =
            new LruCache<String, byte[]>(4 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, byte[] value) {
                    return value == null ? 0 : value.length;
                }
            };

    public interface ByteCallback {
        void onSuccess(byte[] bytes);

        void onFailure(LinuxDoWebSession.Failure failure);
    }

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Object mClientLock = new Object();
    private OkHttpClient mClient;
    private String mClientDohUrl;
    private LinuxDoCloseableDns mPlatformDns;
    private String mCsrfToken;

    public static LinuxDoHttpSession getInstance() {
        return INSTANCE;
    }

    public void fetch(String path, LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> enqueueOnMain(path, callback));
    }

    /** Downloads a Linux DO avatar through the same isolated DNS transport as topic JSON. */
    public void fetchAvatar(String url, ByteCallback callback) {
        if (callback == null) return;
        if (url == null || url.trim().isEmpty()) {
            mMainHandler.post(() -> callback.onFailure(
                    LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL));
            return;
        }
        byte[] cached;
        synchronized (AVATAR_CACHE) {
            cached = AVATAR_CACHE.get(url);
        }
        if (cached != null) {
            byte[] result = cached;
            mMainHandler.post(() -> callback.onSuccess(result));
            return;
        }
        mMainHandler.post(() -> enqueueAvatar(url, callback));
    }

    /** Used only by WebView's background resource interceptor for inline Boost avatars. */
    public byte[] fetchAvatarBlocking(String url) {
        if (Looper.myLooper() == Looper.getMainLooper()) return null;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();
        fetchAvatar(url, new ByteCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                result.set(bytes);
                latch.countDown();
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                latch.countDown();
            }
        });
        try {
            latch.await(21, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    /** Sends one user-triggered mutation. It is never retried automatically. */
    public void post(
            String path,
            Map<String, String> fields,
            LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> preparePost(path, fields, callback));
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
            mCsrfToken = null;
        }
    }

    public void invalidateCsrfToken() {
        mMainHandler.post(() -> mCsrfToken = null);
    }

    private void preparePost(
            String path,
            Map<String, String> fields,
            LinuxDoWebSession.Callback callback) {
        if (!LinuxDoTransportPolicy.isAllowedMutationPath(path)
                || fields == null || fields.isEmpty()) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            return;
        }
        if (mCsrfToken != null && !mCsrfToken.isEmpty()) {
            enqueuePost(path, fields, mCsrfToken, callback);
            return;
        }
        fetch("/session/csrf.json", new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                try {
                    String token = new JSONObject(json).optString("csrf", "").trim();
                    if (token.isEmpty()) {
                        callback.onFailure(LinuxDoWebSession.Failure.SESSION_UNAVAILABLE);
                        return;
                    }
                    mCsrfToken = token;
                    enqueuePost(path, fields, token, callback);
                } catch (Exception error) {
                    callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                }
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onFailure(failure);
            }
        });
    }

    private void enqueuePost(
            String path,
            Map<String, String> fields,
            String csrfToken,
            LinuxDoWebSession.Callback callback) {
        try {
            String cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
            if (cookie == null || cookie.trim().isEmpty()) {
                callback.onFailure(LinuxDoWebSession.Failure.SESSION_UNAVAILABLE);
                return;
            }
            String userAgent = WebSettings.getDefaultUserAgent(ContextUtils.getApplication());
            FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (field.getKey() != null && field.getValue() != null) {
                    formBuilder.add(field.getKey(), field.getValue());
                }
            }
            FormBody form = formBuilder.build();
            HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)) {
                Buffer encoded = new Buffer();
                form.writeTo(encoded);
                LinuxDoCronetSession.getInstance().post(
                        path, cookie, userAgent, csrfToken,
                        encoded.readByteArray(), guardedMutationCallback(callback));
                return;
            }
            Request request = new Request.Builder()
                    .url(LinuxDoConstants.ORIGIN + path)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .header("Cookie", cookie)
                    .header("X-CSRF-Token", csrfToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", LinuxDoConstants.ORIGIN)
                    .header("Referer", LinuxDoConstants.ORIGIN + "/")
                    .post(form)
                    .build();
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
                                LinuxDoTransportPolicy.classifyMutation(response.code(), body);
                        if (kind == LinuxDoTransportPolicy.ResponseKind.JSON) {
                            persistResponseCookies(response.headers("Set-Cookie"));
                            mMainHandler.post(() -> callback.onSuccess(body));
                        } else {
                            LinuxDoWebSession.Failure failure =
                                    kind == LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED
                                            ? LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                                            : LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
                            if (failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED) {
                                mCsrfToken = null;
                                LinuxDoSessionState.setReady(false);
                            }
                            postFailure(callback, failure);
                        }
                    } catch (Exception error) {
                        postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                    }
                }
            });
        } catch (Exception | LinkageError error) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private LinuxDoWebSession.Callback guardedMutationCallback(
            LinuxDoWebSession.Callback callback) {
        return new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                callback.onSuccess(json);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                if (failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED) {
                    mCsrfToken = null;
                }
                callback.onFailure(failure);
            }
        };
    }

    private void enqueueOnMain(String path, LinuxDoWebSession.Callback callback) {
        try {
            enqueueSafely(path, callback);
        } catch (RuntimeException | LinkageError error) {
            // Resolver/WebView-provider/library setup must never terminate the UI process.
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private void enqueueAvatar(String url, ByteCallback callback) {
        try {
            HttpUrl target = HttpUrl.parse(url);
            if (target == null || !target.isHttps()
                    || !LinuxDoTransportPolicy.isAllowedAvatarHost(target.host())
                    || !target.username().isEmpty() || !target.password().isEmpty()) {
                callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                return;
            }
            String userAgent = WebSettings.getDefaultUserAgent(ContextUtils.getApplication());
            String cookie = "";
            if ("linux.do".equalsIgnoreCase(target.host())) {
                cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
                if (cookie == null) cookie = "";
            }
            HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)) {
                LinuxDoCronetSession.getInstance().fetchBinary(
                        target.toString(), target.host(), cookie, userAgent,
                        cachingAvatarCallback(url, callback));
                return;
            }
            Request.Builder requestBuilder = new Request.Builder()
                    .url(target)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("User-Agent", userAgent)
                    .header("Referer", LinuxDoConstants.ORIGIN + "/")
                    .get();
            if (!cookie.trim().isEmpty()) {
                requestBuilder.header("Cookie", cookie);
            }
            client().newCall(requestBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException error) {
                    postAvatarFailure(callback);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response closeable = response) {
                        String contentType = response.header("Content-Type", "");
                        if (!response.isSuccessful()
                                || (!contentType.isEmpty()
                                && !contentType.toLowerCase(java.util.Locale.ROOT)
                                .startsWith("image/"))) {
                            postAvatarFailure(callback);
                            return;
                        }
                        byte[] bytes = readBytesBounded(response.body(), MAX_AVATAR_BYTES);
                        if (bytes.length == 0) {
                            postAvatarFailure(callback);
                            return;
                        }
                        synchronized (AVATAR_CACHE) {
                            AVATAR_CACHE.put(url, bytes);
                        }
                        mMainHandler.post(() -> callback.onSuccess(bytes));
                    } catch (Exception error) {
                        postAvatarFailure(callback);
                    }
                }
            });
        } catch (Exception | LinkageError error) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private ByteCallback cachingAvatarCallback(String url, ByteCallback callback) {
        return new ByteCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                synchronized (AVATAR_CACHE) {
                    AVATAR_CACHE.put(url, bytes);
                }
                callback.onSuccess(bytes);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onFailure(failure);
            }
        };
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

    private static byte[] readBytesBounded(ResponseBody body, long maxBytes)
            throws IOException {
        if (body == null) return new byte[0];
        if (body.contentLength() > maxBytes) throw new ResponseTooLargeException();
        BufferedSource source = body.source();
        Buffer buffer = new Buffer();
        long total = 0L;
        while (true) {
            long read = source.read(buffer, 8192L);
            if (read == -1L) break;
            total += read;
            if (total > maxBytes) throw new ResponseTooLargeException();
        }
        return buffer.readByteArray();
    }

    private void postFailure(
            LinuxDoWebSession.Callback callback, LinuxDoWebSession.Failure failure) {
        mMainHandler.post(() -> callback.onFailure(failure));
    }

    private void postAvatarFailure(ByteCallback callback) {
        mMainHandler.post(() -> callback.onFailure(
                LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL));
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
