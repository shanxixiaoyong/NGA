package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.content.Context;
import android.net.Uri;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetProvider;
import org.chromium.net.CronetException;
import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;
import org.chromium.net.UploadDataProviders;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import gov.anzong.androidnga.base.util.ContextUtils;

/** LINUX DO-only QUIC transport for networks that reset ordinary TLS after DoH. */
final class LinuxDoCronetSession {

    private static final LinuxDoCronetSession INSTANCE = new LinuxDoCronetSession();
    private static final long MAX_RESPONSE_BYTES = 8L * 1024L * 1024L;
    private static final long MAX_MEDIA_BYTES = 8L * 1024L * 1024L;
    private static final long REQUEST_TIMEOUT_MS = 20_000L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mRequestExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "linuxdo-cronet-json");
        thread.setDaemon(true);
        return thread;
    });
    // Media engines and their resolver work must never sit in front of a tapped article JSON
    // request. Keep them serialized on their own lane so avatar bursts remain bounded without
    // delaying the document that controls time-to-first-content.
    private final ExecutorService mBinaryExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "linuxdo-cronet-media");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<RequestState> mRequests = new HashSet<>();
    private final Set<BinaryRequestState> mBinaryRequests = new HashSet<>();
    private final Map<String, CronetEngine> mBinaryEngines = new HashMap<>();

    private CronetEngine mEngine;
    private boolean mEngineStale;
    private static volatile Boolean sProviderAvailable;

    static LinuxDoCronetSession getInstance() {
        return INSTANCE;
    }

    /**
     * Android can expose the Cronet API while disabling every provider (notably
     * emulator/system images without Play Services). In that case callers must
     * use the OkHttp + isolated DoH transport instead of failing every request.
     */
    static boolean isAvailable(Context context) {
        if (context == null) return false;
        Boolean known = sProviderAvailable;
        if (known != null) return known;
        boolean available = false;
        try {
            for (CronetProvider provider : CronetProvider.getAllProviders(
                    context.getApplicationContext())) {
                if (provider != null && provider.isEnabled()) {
                    available = true;
                    break;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Treat provider discovery failures as unavailable; OkHttp remains safe.
        }
        sProviderAvailable = available;
        return available;
    }

    void fetch(
            String path,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            LinuxDoWebSession.Callback callback) {
        mRequestExecutor.execute(() -> start(
                path, cookie, apiKey, clientId, userAgent,
                null, null, null, true, callback));
    }

    /** Fetches the first-party login document without requiring a JSON response shape. */
    void fetchHtml(
            String path,
            String cookie,
            String userAgent,
            LinuxDoWebSession.Callback callback) {
        mRequestExecutor.execute(() -> start(
                path, cookie, "", "", userAgent,
                null, null, null, false, callback));
    }

    void post(
            String path,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            String csrfToken,
            byte[] body,
            LinuxDoWebSession.Callback callback) {
        mRequestExecutor.execute(() -> start(
                path, cookie, apiKey, clientId, userAgent, "POST", csrfToken,
                body == null ? new byte[0] : body, true, callback));
    }

    void put(
            String path,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            String csrfToken,
            byte[] body,
            LinuxDoWebSession.Callback callback) {
        mRequestExecutor.execute(() -> start(
                path, cookie, apiKey, clientId, userAgent, "PUT", csrfToken,
                body == null ? new byte[0] : body, true, callback));
    }

    void delete(
            String path,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            String csrfToken,
            byte[] body,
            LinuxDoWebSession.Callback callback) {
        mRequestExecutor.execute(() -> start(
                path, cookie, apiKey, clientId, userAgent, "DELETE", csrfToken,
                body == null ? new byte[0] : body, true, callback));
    }

    void fetchBinary(
            String url,
            String host,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            LinuxDoHttpSession.ByteCallback callback) {
        mBinaryExecutor.execute(() -> startBinary(
                url, host, cookie, apiKey, clientId, userAgent, callback));
    }

    /** Builds the source-isolated QUIC engine ahead of the first foreground read. */
    void warmup(String userAgent) {
        mRequestExecutor.execute(() -> {
            try {
                engine(userAgent);
            } catch (Throwable ignored) {
                // Warmup is best effort; the real request reports transport failures.
            }
        });
    }

    void invalidate() {
        mRequestExecutor.execute(() -> {
            mEngineStale = true;
            for (RequestState state : new ArrayList<>(mRequests)) state.cancelSilently();
            closeStaleEngineIfIdle();
        });
        mBinaryExecutor.execute(() -> {
            for (BinaryRequestState state : new ArrayList<>(mBinaryRequests)) {
                state.cancelSilently();
            }
            closeBinaryEngines();
        });
    }

    private void startBinary(
            String url,
            String host,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            LinuxDoHttpSession.ByteCallback callback) {
        try {
            CronetEngine engine = binaryEngine(host, userAgent);
            BinaryRequestState state = new BinaryRequestState(callback);
            UrlRequest.Builder requestBuilder = engine.newUrlRequestBuilder(
                            url, state, mBinaryExecutor)
                    .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .addHeader("User-Agent", userAgent)
                    .addHeader("Referer", LinuxDoConstants.ORIGIN + "/");
            if (cookie != null && !cookie.trim().isEmpty()) {
                requestBuilder.addHeader("Cookie", cookie);
            }
            addUserApiHeaders(requestBuilder, apiKey, clientId);
            UrlRequest request = requestBuilder.build();
            state.attach(request);
            mBinaryRequests.add(state);
            request.start();
            mMainHandler.postDelayed(
                    () -> mBinaryExecutor.execute(state::timeout), REQUEST_TIMEOUT_MS);
        } catch (Throwable error) {
            postBinaryFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private void start(
            String path,
            String cookie,
            String apiKey,
            String clientId,
            String userAgent,
            String method,
            String csrfToken,
            byte[] uploadBody,
            boolean validateJson,
            LinuxDoWebSession.Callback callback) {
        try {
            CronetEngine engine = engine(userAgent);
            boolean mutation = uploadBody != null;
            RequestState state = new RequestState(callback, mutation, validateJson);
            UrlRequest.Builder requestBuilder = engine.newUrlRequestBuilder(
                            LinuxDoConstants.ORIGIN + path, state, mRequestExecutor)
                    // The login document is HTML. Sending the JSON media type here can make
                    // Discourse/Cloudflare return a challenge or an empty negotiation result
                    // instead of the page that the WebView is meant to render.
                    .addHeader("Accept", validateJson
                            ? "application/json"
                            : "text/html,application/xhtml+xml")
                    .addHeader("User-Agent", userAgent);
            if (callback instanceof LinuxDoWebSession.CriticalSuccessCallback) {
                requestBuilder.setPriority(UrlRequest.Builder.REQUEST_PRIORITY_HIGHEST);
            }
            if (cookie != null && !cookie.trim().isEmpty()) {
                requestBuilder.addHeader("Cookie", cookie);
            }
            addUserApiHeaders(requestBuilder, apiKey, clientId);
            if (mutation) {
                requestBuilder.setHttpMethod(method)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .addHeader("Origin", LinuxDoConstants.ORIGIN)
                        .addHeader("Referer", LinuxDoConstants.ORIGIN + "/")
                        .setUploadDataProvider(
                                UploadDataProviders.create(uploadBody), mRequestExecutor);
                if (csrfToken != null && !csrfToken.isEmpty()) {
                    requestBuilder.addHeader("X-CSRF-Token", csrfToken);
                }
            }
            UrlRequest request = requestBuilder.build();
            state.attach(request);
            mRequests.add(state);
            request.start();
            mMainHandler.postDelayed(
                    () -> mRequestExecutor.execute(state::timeout), REQUEST_TIMEOUT_MS);
        } catch (Throwable error) {
            mEngineStale = true;
            closeStaleEngineIfIdle();
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private static void addUserApiHeaders(
            UrlRequest.Builder builder, String apiKey, String clientId) {
        if (apiKey == null || apiKey.trim().isEmpty()
                || clientId == null || clientId.trim().isEmpty()) return;
        builder.addHeader("User-Api-Key", apiKey)
                .addHeader("User-Api-Client-Id", clientId);
    }

    private CronetEngine engine(String userAgent) throws Exception {
        if (mEngine != null && !mEngineStale) return mEngine;
        if (!mRequests.isEmpty()) throw new IllegalStateException("resolver replacement in flight");
        closeEngine();
        LinuxDoHttpEngineDns resolver = new LinuxDoHttpEngineDns(
                ContextUtils.getApplication());
        List<InetAddress> addresses;
        try {
            addresses = resolver.lookup(LinuxDoConstants.HOST);
        } finally {
            resolver.close();
        }
        if (addresses.isEmpty()) throw new IllegalStateException("LINUX DO address unavailable");
        String address = addresses.get(0).getHostAddress();
        String options = "{\"HostResolverRules\":{\"host_resolver_rules\":\"MAP "
                + LinuxDoConstants.HOST + ' ' + address + "\"}}";
        mEngine = new ExperimentalCronetEngine.Builder(ContextUtils.getApplication())
                .setUserAgent(userAgent)
                .enableQuic(true)
                .addQuicHint(LinuxDoConstants.HOST, 443, 443)
                .setExperimentalOptions(options)
                .build();
        mEngineStale = false;
        return mEngine;
    }

    private CronetEngine binaryEngine(String host, String userAgent) throws Exception {
        CronetEngine existing = mBinaryEngines.get(host);
        if (existing != null) return existing;
        if (mBinaryEngines.size() >= 3) {
            throw new IllegalStateException("avatar host budget exhausted");
        }
        LinuxDoHttpEngineDns resolver = new LinuxDoHttpEngineDns(
                ContextUtils.getApplication());
        List<InetAddress> addresses;
        try {
            addresses = resolver.lookup(host);
        } finally {
            resolver.close();
        }
        if (addresses.isEmpty()) throw new IllegalStateException("avatar address unavailable");
        String address = addresses.get(0).getHostAddress();
        String options = "{\"HostResolverRules\":{\"host_resolver_rules\":\"MAP "
                + host + ' ' + address + "\"}}";
        CronetEngine engine = new ExperimentalCronetEngine.Builder(
                ContextUtils.getApplication())
                .setUserAgent(userAgent)
                .enableQuic(true)
                .addQuicHint(host, 443, 443)
                .setExperimentalOptions(options)
                .build();
        mBinaryEngines.put(host, engine);
        return engine;
    }

    private void finish(RequestState state) {
        mRequests.remove(state);
        closeStaleEngineIfIdle();
    }

    private void finish(BinaryRequestState state) {
        mBinaryRequests.remove(state);
    }

    private void closeStaleEngineIfIdle() {
        if (mEngineStale && mRequests.isEmpty()) closeEngine();
    }

    private void closeEngine() {
        if (mEngine == null) return;
        try {
            mEngine.shutdown();
        } catch (RuntimeException ignored) {
            // The next request can still construct a fresh isolated engine.
        }
        mEngine = null;
    }

    private void closeBinaryEngines() {
        for (CronetEngine engine : mBinaryEngines.values()) {
            try {
                engine.shutdown();
            } catch (RuntimeException ignored) {
                // Resolver replacement will lazily create a fresh media engine.
            }
        }
        mBinaryEngines.clear();
    }

    private void postFailure(
            LinuxDoWebSession.Callback callback, LinuxDoWebSession.Failure failure) {
        mMainHandler.post(() -> callback.onFailure(failure));
    }

    private void postBinaryFailure(
            LinuxDoHttpSession.ByteCallback callback,
            LinuxDoWebSession.Failure failure) {
        mMainHandler.post(() -> callback.onFailure(failure));
    }

    private final class BinaryRequestState extends UrlRequest.Callback {
        private final LinuxDoHttpSession.ByteCallback mCallback;
        private final ByteArrayOutputStream mBody = new ByteArrayOutputStream();
        private UrlRequest mRequest;
        private boolean mDone;
        private LinuxDoWebSession.Failure mCanceledFailure;

        BinaryRequestState(LinuxDoHttpSession.ByteCallback callback) {
            mCallback = callback;
        }

        void attach(UrlRequest request) {
            mRequest = request;
        }

        void timeout() {
            if (mDone) return;
            mCanceledFailure = LinuxDoWebSession.Failure.TIMEOUT;
            mRequest.cancel();
        }

        void cancelSilently() {
            if (mDone) return;
            mDone = true;
            mRequest.cancel();
            finish(this);
        }

        @Override
        public void onRedirectReceived(
                UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            mCanceledFailure = LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
            request.cancel();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            int status = info.getHttpStatusCode();
            if (status < 200 || status >= 300 || !hasImageContentType(info)) {
                mCanceledFailure = LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
                request.cancel();
                return;
            }
            request.read(ByteBuffer.allocateDirect(32 * 1024));
        }

        @Override
        public void onReadCompleted(
                UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
            buffer.flip();
            int count = buffer.remaining();
            if ((long) mBody.size() + count > MAX_MEDIA_BYTES) {
                mCanceledFailure = LinuxDoWebSession.Failure.RESPONSE_TOO_LARGE;
                request.cancel();
                return;
            }
            byte[] bytes = new byte[count];
            buffer.get(bytes);
            mBody.write(bytes, 0, bytes.length);
            buffer.clear();
            request.read(buffer);
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            if (mDone) return;
            mDone = true;
            byte[] bytes = mBody.toByteArray();
            if (bytes.length == 0) {
                postBinaryFailure(mCallback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            } else {
                mMainHandler.post(() -> mCallback.onSuccess(bytes));
            }
            finish(this);
        }

        @Override
        public void onFailed(
                UrlRequest request, UrlResponseInfo info, CronetException error) {
            if (mDone) return;
            mDone = true;
            postBinaryFailure(mCallback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            finish(this);
        }

        @Override
        public void onCanceled(UrlRequest request, UrlResponseInfo info) {
            if (mDone) return;
            mDone = true;
            postBinaryFailure(mCallback, mCanceledFailure == null
                    ? LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL : mCanceledFailure);
            finish(this);
        }
    }

    private static boolean hasImageContentType(UrlResponseInfo info) {
        Map<String, List<String>> headers = info.getAllHeaders();
        if (headers == null || headers.isEmpty()) return true;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"content-type".equalsIgnoreCase(entry.getKey())) continue;
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) return true;
            String value = values.get(0);
            return value != null && value.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("image/");
        }
        return true;
    }

    private final class RequestState extends UrlRequest.Callback {
        private final LinuxDoWebSession.Callback mCallback;
        private final boolean mMutation;
        private final boolean mValidateJson;
        private final ByteArrayOutputStream mBody = new ByteArrayOutputStream();
        private UrlRequest mRequest;
        private UrlResponseInfo mInfo;
        private boolean mDone;
        private LinuxDoWebSession.Failure mCanceledFailure;

        RequestState(
                LinuxDoWebSession.Callback callback,
                boolean mutation,
                boolean validateJson) {
            mCallback = callback;
            mMutation = mutation;
            mValidateJson = validateJson;
        }

        void attach(UrlRequest request) {
            mRequest = request;
        }

        void timeout() {
            if (mDone) return;
            mCanceledFailure = LinuxDoWebSession.Failure.TIMEOUT;
            mRequest.cancel();
        }

        void cancelSilently() {
            if (mDone) return;
            mDone = true;
            mRequest.cancel();
            finish(this);
        }

        @Override
        public void onRedirectReceived(
                UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
            if (!mValidateJson) {
                Uri destination = Uri.parse(newLocationUrl == null ? "" : newLocationUrl);
                // Cronet normally supplies an absolute Location, but accepting a root-relative
                // same-origin redirect keeps the login bootstrap compatible with Discourse
                // installations that emit `Location: /login`.
                if (destination.getScheme() == null && newLocationUrl != null
                        && newLocationUrl.startsWith("/")) {
                    destination = Uri.parse(LinuxDoConstants.ORIGIN + newLocationUrl);
                }
                if ("https".equalsIgnoreCase(destination.getScheme())
                        && LinuxDoConstants.HOST.equalsIgnoreCase(destination.getHost())
                        && destination.getPort() == -1
                        && destination.getUserInfo() == null) {
                    request.followRedirect();
                    return;
                }
                mCanceledFailure = LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
                request.cancel();
                return;
            }
            mInfo = info;
            mCanceledFailure = LinuxDoWebSession.Failure.VERIFICATION_REQUIRED;
            request.cancel();
        }

        @Override
        public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
            mInfo = info;
            request.read(ByteBuffer.allocateDirect(64 * 1024));
        }

        @Override
        public void onReadCompleted(
                UrlRequest request, UrlResponseInfo info, ByteBuffer buffer) {
            buffer.flip();
            int count = buffer.remaining();
            if ((long) mBody.size() + count > MAX_RESPONSE_BYTES) {
                mCanceledFailure = LinuxDoWebSession.Failure.RESPONSE_TOO_LARGE;
                request.cancel();
                return;
            }
            byte[] bytes = new byte[count];
            buffer.get(bytes);
            mBody.write(bytes, 0, bytes.length);
            buffer.clear();
            request.read(buffer);
        }

        @Override
        public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
            if (mDone) return;
            mDone = true;
            String body = new String(mBody.toByteArray(), StandardCharsets.UTF_8);
            if (!mValidateJson) {
                if (body.isEmpty()) {
                    postFailure(mCallback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
                } else {
                    persistResponseCookies(info.getAllHeaders());
                    deliverSuccess(mCallback, body);
                }
                finish(this);
                return;
            }
            LinuxDoTransportPolicy.ResponseKind kind = mMutation
                    ? LinuxDoTransportPolicy.classifyMutation(
                            info.getHttpStatusCode(), body, info.getAllHeaders())
                    : LinuxDoTransportPolicy.classify(
                            info.getHttpStatusCode(), body, info.getAllHeaders());
            if (kind == LinuxDoTransportPolicy.ResponseKind.JSON) {
                persistResponseCookies(info.getAllHeaders());
                deliverSuccess(mCallback, body);
            } else {
                LinuxDoWebSession.Failure failure =
                        kind == LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED
                                ? LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                                : LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL;
                if (failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED) {
                    LinuxDoSessionState.setReady(false);
                }
                postFailure(mCallback, failure);
            }
            finish(this);
        }

        @Override
        public void onFailed(
                UrlRequest request, UrlResponseInfo info, CronetException error) {
            if (mDone) return;
            mDone = true;
            mEngineStale = true;
            postFailure(mCallback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
            finish(this);
        }

        @Override
        public void onCanceled(UrlRequest request, UrlResponseInfo info) {
            if (mDone) return;
            mDone = true;
            LinuxDoWebSession.Failure failure = mCanceledFailure == null
                    ? LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL : mCanceledFailure;
            if (failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED) {
                LinuxDoSessionState.setReady(false);
            }
            postFailure(mCallback, failure);
            finish(this);
        }
    }

    private void deliverSuccess(LinuxDoWebSession.Callback callback, String body) {
        if (callback instanceof LinuxDoWebSession.BackgroundSuccessCallback) {
            callback.onSuccess(body);
        } else {
            mMainHandler.post(() -> callback.onSuccess(body));
        }
    }

    private void persistResponseCookies(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return;
        List<String> cookies = null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if ("set-cookie".equalsIgnoreCase(entry.getKey())) {
                cookies = entry.getValue();
                break;
            }
        }
        if (cookies == null || cookies.isEmpty()) return;
        List<String> values = new ArrayList<>(cookies);
        mMainHandler.post(() -> {
            CookieManager manager = CookieManager.getInstance();
            for (String cookie : values) manager.setCookie(LinuxDoConstants.ORIGIN, cookie);
            manager.flush();
        });
    }

    private LinuxDoCronetSession() {
    }
}
