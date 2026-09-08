package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.util.LruCache;
import android.webkit.CookieManager;
import android.webkit.WebSettings;

import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.util.NLog;
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
    private static final long MAX_MEDIA_BYTES = 8L * 1024L * 1024L;
    // Keep idle LinuxDo TLS connections available across nearby topic/page requests.
    // The Android 14+ Cronet path owns its longer-lived engine separately; this value
    // covers the OkHttp/DoH fallback path without changing the resolver's DNS policy.
    private static final long CONNECTION_KEEP_ALIVE_MINUTES = 3L;
    private static final long WARMUP_REUSE_MS = 3L * 60L * 1000L;
    private static final LruCache<String, byte[]> AVATAR_CACHE =
            new LruCache<String, byte[]>(8 * 1024 * 1024) {
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
    private final Object mAvatarFlightLock = new Object();
    private final Map<String, List<ByteCallback>> mAvatarInFlight = new HashMap<>();
    private OkHttpClient mClient;
    private String mClientDohUrl;
    private LinuxDoCloseableDns mPlatformDns;
    private String mCsrfToken;
    private String mUserAgent;
    private String mWarmupDohUrl;
    private long mLastWarmupAt;

    public static LinuxDoHttpSession getInstance() {
        return INSTANCE;
    }

    public void fetch(String path, LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        LinuxDoWebSession.Callback delivery = "/session/csrf.json".equals(path)
                ? cachingCsrfCallback(callback) : callback;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            enqueueOnMain(path, delivery);
        } else {
            mMainHandler.post(() -> enqueueOnMain(path, delivery));
        }
    }

    /** Retains a prefetched login CSRF value so the Verify tap can reuse the warm connection. */
    private LinuxDoWebSession.Callback cachingCsrfCallback(
            LinuxDoWebSession.Callback callback) {
        return new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                try {
                    String token = new JSONObject(json == null ? "{}" : json)
                            .optString("csrf", "").trim();
                    if (!token.isEmpty()) mCsrfToken = token;
                } catch (Exception ignored) {
                    // The original consumer still owns response validation and reporting.
                }
                callback.onSuccess(json);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onFailure(failure);
            }
        };
    }

    /**
     * Fetches the first-party login document through the configured LINUX DO resolver.
     *
     * <p>The visible login page is still owned by the existing WebView so cookies and challenge
     * state remain in the same session. This method only supplies its initial HTML when the
     * device's ordinary resolver cannot reach linux.do; JSON reads continue to use {@link #fetch}.
     */
    public void fetchLoginPage(LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> enqueueLoginPage(callback));
    }

    /** Reads the authenticated trust-level progress card through the same isolated DoH. */
    public void fetchTrustProgress(LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> enqueueTrustProgress(callback));
    }

    private void enqueueTrustProgress(LinuxDoWebSession.Callback callback) {
        final String origin = "https://connect.linux.do";
        try {
            CookieManager manager = CookieManager.getInstance();
            String cookie = manager.getCookie(origin);
            if (cookie == null || cookie.trim().isEmpty()) {
                cookie = manager.getCookie(LinuxDoConstants.ORIGIN);
            }
            if (cookie == null) cookie = "";
            Request.Builder builder = new Request.Builder()
                    .url(origin + "/")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", userAgent())
                    .header("Referer", LinuxDoConstants.ORIGIN + "/")
                    .get();
            if (!cookie.trim().isEmpty()) builder.header("Cookie", cookie);
            client().newCall(builder.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException error) {
                    postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                }

                @Override public void onResponse(Call call, Response response) {
                    try (Response closeable = response) {
                        String body = readBounded(response.body());
                        if (!response.isSuccessful() || body.trim().isEmpty()
                                || !body.contains("class=\"card")
                                && !body.contains("class='card")) {
                            postFailure(callback,
                                    response.code() == 401 || response.code() == 403
                                            || response.isRedirect()
                                            ? LinuxDoWebSession.Failure.SESSION_UNAVAILABLE
                                            : LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                            return;
                        }
                        List<String> cookies = response.headers("Set-Cookie");
                        if (cookies != null && !cookies.isEmpty()) {
                            mMainHandler.post(() -> {
                                CookieManager cookieManager = CookieManager.getInstance();
                                for (String value : cookies) cookieManager.setCookie(origin, value);
                                cookieManager.flush();
                            });
                        }
                        mMainHandler.post(() -> callback.onSuccess(body));
                    } catch (Exception error) {
                        postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                    }
                }
            });
        } catch (Exception | LinkageError error) {
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    /** Downloads a Linux DO avatar through the same isolated DNS transport as topic JSON. */
    public void fetchAvatar(String url, ByteCallback callback) {
        fetchMedia(url, callback);
    }

    /** Downloads a small LINUX DO inline asset (emoji, avatar, or reaction image). */
    public void fetchMedia(String url, ByteCallback callback) {
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
        boolean startRequest = false;
        synchronized (mAvatarFlightLock) {
            List<ByteCallback> waiters = mAvatarInFlight.get(url);
            if (waiters == null) {
                waiters = new ArrayList<>();
                mAvatarInFlight.put(url, waiters);
                startRequest = true;
            }
            waiters.add(callback);
        }
        if (!startRequest) return;
        final long requestStart = SystemClock.elapsedRealtime();
        mMainHandler.post(() -> enqueueAvatar(
                url, fanOutAvatarCallback(url, requestStart)));
    }

    /** Returns a transport-owned immutable-by-convention avatar cache entry, if present. */
    public byte[] getCachedAvatar(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        synchronized (AVATAR_CACHE) {
            return AVATAR_CACHE.get(url);
        }
    }

    /** Starts a deduplicated avatar request without retaining a UI callback. */
    public void prefetchAvatar(String url) {
        fetchAvatar(url, new ByteCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                // The transport cache is populated before this callback.
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                // A prefetch is best effort and must not surface a toast.
            }
        });
    }

    /** Best-effort source-isolated warmup for an inline post image. */
    public void prefetchMedia(String url) {
        fetchMedia(url, new ByteCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                // The shared media cache is populated before this callback.
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                // Inline media is optional and must not affect article readiness.
            }
        });
    }

    /** Warm the isolated resolver/client before the first article body is requested. */
    public void warmup() {
        Runnable action = () -> {
            try {
                String dohUrl = LinuxDoDohConfig.currentUrl();
                long now = SystemClock.elapsedRealtime();
                if (dohUrl.equals(mWarmupDohUrl)
                        && now - mLastWarmupAt < WARMUP_REUSE_MS) return;
                HttpUrl resolverUrl = HttpUrl.get(dohUrl);
                String userAgent = userAgent();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)
                        && LinuxDoCronetSession.isAvailable(
                        ContextUtils.getApplication())) {
                    LinuxDoCronetSession.getInstance().warmup(userAgent);
                } else {
                    // Building the DoH-backed OkHttp client does not perform a request.
                    client();
                }
                mWarmupDohUrl = dohUrl;
                mLastWarmupAt = now;
            } catch (RuntimeException | LinkageError ignored) {
                // The real request still owns error reporting; warmup is optional.
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else mMainHandler.post(action);
    }

    /** Sends one user-triggered mutation. It is never retried automatically. */
    public void post(
            String path,
            Map<String, String> fields,
            LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> prepareMutation(
                "POST", path, fields, null, java.util.Collections.emptyList(), callback));
    }

    /** Sends one user-triggered PUT. It is never retried automatically. */
    public void put(
            String path,
            Map<String, String> fields,
            LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> prepareMutation(
                "PUT", path, fields, null, java.util.Collections.emptyList(), callback));
    }

    /** PUT variant for Rails-style repeated form values such as poll options[]. */
    public void putRepeated(
            String path,
            Map<String, String> fields,
            String repeatedKey,
            List<String> repeatedValues,
            LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        List<String> values = repeatedValues == null
                ? java.util.Collections.emptyList() : new ArrayList<>(repeatedValues);
        mMainHandler.post(() -> prepareMutation(
                "PUT", path, fields, repeatedKey, values, callback));
    }

    /** Sends one user-triggered DELETE. It is never retried automatically. */
    public void delete(
            String path,
            Map<String, String> fields,
            LinuxDoWebSession.Callback callback) {
        if (callback == null) return;
        mMainHandler.post(() -> prepareMutation(
                "DELETE", path, fields, null, java.util.Collections.emptyList(), callback));
    }

    public void invalidateClient() {
        OkHttpClient oldClient;
        LinuxDoCloseableDns oldPlatformDns;
        synchronized (mClientLock) {
            oldClient = mClient;
            oldPlatformDns = mPlatformDns;
            mClient = null;
            mPlatformDns = null;
            mClientDohUrl = null;
            mCsrfToken = null;
            mWarmupDohUrl = null;
            mLastWarmupAt = 0L;
        }
        Runnable cleanup = () -> {
            LinuxDoCronetSession.getInstance().invalidate();
            if (oldClient != null) {
                try {
                    oldClient.dispatcher().cancelAll();
                    // Closing an OkHttp TLS socket can perform a network write on Android.
                    // Keep this off the main thread; login completion must never crash the UI.
                    oldClient.connectionPool().evictAll();
                } catch (RuntimeException ignored) {
                    // Client teardown is best effort. A request may already be completing.
                }
            }
            if (oldPlatformDns != null) {
                try {
                    oldPlatformDns.close();
                } catch (RuntimeException ignored) {
                    // Resolver teardown is best effort while a DoH lookup is winding down.
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ThreadUtils.postOnSubThread(cleanup);
        } else {
            cleanup.run();
        }
    }

    public void invalidateCsrfToken() {
        mMainHandler.post(() -> mCsrfToken = null);
    }

    private void prepareMutation(
            String method,
            String path,
            Map<String, String> fields,
            String repeatedKey,
            List<String> repeatedValues,
            LinuxDoWebSession.Callback callback) {
        boolean hasRepeatedValues = repeatedKey != null && repeatedValues != null
                && !repeatedValues.isEmpty();
        if (!LinuxDoTransportPolicy.isAllowedMutationPath(path)
                || (fields == null && !isBodylessMutation(method))
                || (fields != null && fields.isEmpty() && !hasRepeatedValues
                && !isBodylessMutation(method))) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            return;
        }
        final Map<String, String> safeFields = fields == null
                ? java.util.Collections.emptyMap() : fields;
        final String safeRepeatedKey = hasRepeatedValues ? repeatedKey : null;
        final List<String> safeRepeatedValues = hasRepeatedValues
                ? new ArrayList<>(repeatedValues) : java.util.Collections.emptyList();
        if (LinuxDoUserApiAuth.hasCredential()) {
            enqueueMutation(method, path, safeFields, safeRepeatedKey,
                    safeRepeatedValues, "", callback);
            return;
        }
        if (mCsrfToken != null && !mCsrfToken.isEmpty()) {
            enqueueMutation(method, path, safeFields, safeRepeatedKey,
                    safeRepeatedValues, mCsrfToken, callback);
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
                    enqueueMutation(method, path, safeFields, safeRepeatedKey,
                            safeRepeatedValues, token, callback);
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

    private void enqueueMutation(
            String method,
            String path,
            Map<String, String> fields,
            String repeatedKey,
            List<String> repeatedValues,
            String csrfToken,
            LinuxDoWebSession.Callback callback) {
        try {
            String cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
            if (cookie == null) cookie = "";
            String apiKey = LinuxDoUserApiAuth.apiKey();
            String clientId = apiKey.isEmpty() ? "" : LinuxDoUserApiAuth.clientId();
            if (cookie.trim().isEmpty() && apiKey.isEmpty()) {
                callback.onFailure(LinuxDoWebSession.Failure.SESSION_UNAVAILABLE);
                return;
            }
            String userAgent = userAgent();
            FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (field.getKey() != null && field.getValue() != null) {
                    formBuilder.add(field.getKey(), field.getValue());
                }
            }
            if (repeatedKey != null && repeatedValues != null) {
                for (String value : repeatedValues) {
                    if (value != null) formBuilder.add(repeatedKey, value);
                }
            }
            FormBody form = formBuilder.build();
            HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)
                    && LinuxDoCronetSession.isAvailable(ContextUtils.getApplication())) {
                Buffer encoded = new Buffer();
                form.writeTo(encoded);
                byte[] body = encoded.readByteArray();
                if ("PUT".equals(method)) {
                    LinuxDoCronetSession.getInstance().put(
                            path, cookie, apiKey, clientId, userAgent, csrfToken, body,
                            guardedMutationCallback(callback));
                } else if ("DELETE".equals(method)) {
                    LinuxDoCronetSession.getInstance().delete(
                            path, cookie, apiKey, clientId, userAgent, csrfToken, body,
                            guardedMutationCallback(callback));
                } else {
                    LinuxDoCronetSession.getInstance().post(
                            path, cookie, apiKey, clientId, userAgent, csrfToken, body,
                            guardedMutationCallback(callback));
                }
                return;
            }
            Request.Builder requestBuilder = new Request.Builder()
                    .url(LinuxDoConstants.ORIGIN + path)
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", LinuxDoConstants.ORIGIN)
                    .header("Referer", LinuxDoConstants.ORIGIN + "/")
                    .method(method, form);
            if (!cookie.trim().isEmpty()) requestBuilder.header("Cookie", cookie);
            if (!csrfToken.isEmpty()) requestBuilder.header("X-CSRF-Token", csrfToken);
            addUserApiHeaders(requestBuilder, apiKey, clientId);
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
                                LinuxDoTransportPolicy.classifyMutation(
                                        response.code(), body, response.headers().toMultimap());
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

    private static boolean isBodylessMutation(String method) {
        return "PUT".equals(method) || "DELETE".equals(method);
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

    private void enqueueLoginPage(LinuxDoWebSession.Callback callback) {
        if (!LinuxDoTransportPolicy.isAllowedLoginPath("/login")) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            return;
        }
        try {
            String cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
            if (cookie == null) cookie = "";
            String userAgent = userAgent();
            HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)
                    && LinuxDoCronetSession.isAvailable(ContextUtils.getApplication())) {
                LinuxDoCronetSession.getInstance().fetchHtml(
                        "/login", cookie, userAgent, callback);
                return;
            }
            Request.Builder requestBuilder = new Request.Builder()
                    .url(LinuxDoConstants.ORIGIN + "/login")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", userAgent)
                    .get();
            if (!cookie.trim().isEmpty()) requestBuilder.header("Cookie", cookie);
            // Keep redirects disabled at this boundary. The WebView owns navigation and can
            // follow only its validated first-party origin after the HTML is attached.
            client().newCall(requestBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException error) {
                    postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response closeable = response) {
                        String body = readBounded(response.body());
                        if (body.isEmpty()) {
                            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                            return;
                        }
                        persistResponseCookies(response.headers("Set-Cookie"));
                        mMainHandler.post(() -> callback.onSuccess(body));
                    } catch (ResponseTooLargeException error) {
                        postFailure(callback, LinuxDoWebSession.Failure.RESPONSE_TOO_LARGE);
                    } catch (Exception error) {
                        postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                    }
                }
            });
        } catch (Exception | LinkageError error) {
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private void enqueueAvatar(String url, ByteCallback callback) {
        try {
            HttpUrl target = HttpUrl.parse(url);
            if (target == null || !target.isHttps()
                    || !LinuxDoTransportPolicy.isAllowedMediaHost(target.host())
                    || !target.username().isEmpty() || !target.password().isEmpty()) {
                callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                return;
            }
            String userAgent = userAgent();
            String cookie = "";
            String apiKey = "";
            String clientId = "";
            if ("linux.do".equalsIgnoreCase(target.host())) {
                cookie = CookieManager.getInstance().getCookie(LinuxDoConstants.ORIGIN);
                if (cookie == null) cookie = "";
                apiKey = LinuxDoUserApiAuth.apiKey();
                if (!apiKey.isEmpty()) clientId = LinuxDoUserApiAuth.clientId();
            }
            HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)
                    && LinuxDoCronetSession.isAvailable(ContextUtils.getApplication())) {
                LinuxDoCronetSession.getInstance().fetchBinary(
                        target.toString(), target.host(), cookie, apiKey, clientId, userAgent,
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
            addUserApiHeaders(requestBuilder, apiKey, clientId);
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
                        byte[] bytes = readBytesBounded(response.body(), MAX_MEDIA_BYTES);
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

    private ByteCallback fanOutAvatarCallback(String url, long requestStart) {
        return new ByteCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                NLog.d("LinuxDoPerf", "avatar_http_ms="
                        + (SystemClock.elapsedRealtime() - requestStart));
                List<ByteCallback> waiters;
                synchronized (mAvatarFlightLock) {
                    waiters = mAvatarInFlight.remove(url);
                }
                if (waiters == null) return;
                for (ByteCallback waiter : waiters) waiter.onSuccess(bytes);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                NLog.d("LinuxDoPerf", "avatar_failed_ms="
                        + (SystemClock.elapsedRealtime() - requestStart));
                List<ByteCallback> waiters;
                synchronized (mAvatarFlightLock) {
                    waiters = mAvatarInFlight.remove(url);
                }
                if (waiters == null) return;
                for (ByteCallback waiter : waiters) waiter.onFailure(failure);
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
        String apiKey = LinuxDoUserApiAuth.apiKey();
        String clientId = apiKey.isEmpty() ? "" : LinuxDoUserApiAuth.clientId();
        String userAgent = userAgent();
        HttpUrl resolverUrl = HttpUrl.get(LinuxDoDohConfig.currentUrl());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && LinuxDoDohConfig.usesCloudflareDefault(resolverUrl)
                && LinuxDoCronetSession.isAvailable(ContextUtils.getApplication())) {
            LinuxDoCronetSession.getInstance().fetch(
                    path, cookie, apiKey, clientId, userAgent, callback);
            return;
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(LinuxDoConstants.ORIGIN + path)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .get();
        if (!cookie.trim().isEmpty()) requestBuilder.header("Cookie", cookie);
        addUserApiHeaders(requestBuilder, apiKey, clientId);
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
                            LinuxDoTransportPolicy.classify(
                                    response.code(), body, response.headers().toMultimap());
                    if (kind == LinuxDoTransportPolicy.ResponseKind.JSON) {
                        persistResponseCookies(response.headers("Set-Cookie"));
                        deliverSuccess(callback, body);
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

    private static void addUserApiHeaders(
            Request.Builder builder, String apiKey, String clientId) {
        if (apiKey == null || apiKey.trim().isEmpty()
                || clientId == null || clientId.trim().isEmpty()) return;
        builder.header("User-Api-Key", apiKey)
                .header("User-Api-Client-Id", clientId);
    }

    /** Browser CONNECT tunnels borrow the reader's resolver; they never own/close it. */
    List<InetAddress> resolveForBrowser(String hostname) throws java.net.UnknownHostException {
        return client().dns().lookup(hostname);
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
                        .connectionPool(new ConnectionPool(
                                2, CONNECTION_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
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
                    .connectionPool(new ConnectionPool(
                            2, CONNECTION_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
                    .build();
            mClientDohUrl = dohUrl;
            return mClient;
        }
    }

    private void deliverSuccess(LinuxDoWebSession.Callback callback, String body) {
        if (callback instanceof LinuxDoWebSession.BackgroundSuccessCallback) {
            callback.onSuccess(body);
        } else {
            mMainHandler.post(() -> callback.onSuccess(body));
        }
    }

    /** WebView's default UA is process-stable and may initialize provider state on first read. */
    private String userAgent() {
        if (mUserAgent == null) {
            mUserAgent = WebSettings.getDefaultUserAgent(ContextUtils.getApplication());
        }
        return mUserAgent;
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
            CookieManager cookieManager = CookieManager.getInstance();
            for (String cookie : cookies) {
                cookieManager.setCookie(LinuxDoConstants.ORIGIN, cookie);
            }
            cookieManager.flush();
        });
    }

    private static final class ResponseTooLargeException extends IOException {
    }

    private LinuxDoHttpSession() {
    }
}
