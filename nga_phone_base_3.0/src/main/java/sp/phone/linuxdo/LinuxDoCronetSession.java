package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
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
    private static final long REQUEST_TIMEOUT_MS = 20_000L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "linuxdo-cronet");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<RequestState> mRequests = new HashSet<>();

    private CronetEngine mEngine;
    private boolean mEngineStale;

    static LinuxDoCronetSession getInstance() {
        return INSTANCE;
    }

    void fetch(
            String path,
            String cookie,
            String userAgent,
            LinuxDoWebSession.Callback callback) {
        mExecutor.execute(() -> start(path, cookie, userAgent, callback));
    }

    void invalidate() {
        mExecutor.execute(() -> {
            mEngineStale = true;
            for (RequestState state : new ArrayList<>(mRequests)) state.cancelSilently();
            closeStaleEngineIfIdle();
        });
    }

    private void start(
            String path,
            String cookie,
            String userAgent,
            LinuxDoWebSession.Callback callback) {
        try {
            CronetEngine engine = engine(userAgent);
            RequestState state = new RequestState(callback);
            UrlRequest.Builder requestBuilder = engine.newUrlRequestBuilder(
                            LinuxDoConstants.ORIGIN + path, state, mExecutor)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", userAgent);
            if (cookie != null && !cookie.trim().isEmpty()) {
                requestBuilder.addHeader("Cookie", cookie);
            }
            UrlRequest request = requestBuilder.build();
            state.attach(request);
            mRequests.add(state);
            request.start();
            mMainHandler.postDelayed(
                    () -> mExecutor.execute(state::timeout), REQUEST_TIMEOUT_MS);
        } catch (Throwable error) {
            mEngineStale = true;
            closeStaleEngineIfIdle();
            postFailure(callback, LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
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

    private void finish(RequestState state) {
        mRequests.remove(state);
        closeStaleEngineIfIdle();
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

    private void postFailure(
            LinuxDoWebSession.Callback callback, LinuxDoWebSession.Failure failure) {
        mMainHandler.post(() -> callback.onFailure(failure));
    }

    private final class RequestState extends UrlRequest.Callback {
        private final LinuxDoWebSession.Callback mCallback;
        private final ByteArrayOutputStream mBody = new ByteArrayOutputStream();
        private UrlRequest mRequest;
        private UrlResponseInfo mInfo;
        private boolean mDone;
        private LinuxDoWebSession.Failure mCanceledFailure;

        RequestState(LinuxDoWebSession.Callback callback) {
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
            LinuxDoTransportPolicy.ResponseKind kind = LinuxDoTransportPolicy.classify(
                    info.getHttpStatusCode(), body);
            if (kind == LinuxDoTransportPolicy.ResponseKind.JSON) {
                persistResponseCookies(info.getAllHeaders());
                mMainHandler.post(() -> mCallback.onSuccess(body));
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
