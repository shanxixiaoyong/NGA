package sp.phone.linuxdo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.MutableContextWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;

import gov.anzong.androidnga.NgaClientApp;
import gov.anzong.androidnga.base.util.ContextUtils;

/** Lazy, serialized, same-origin read transport for linux.do's browser session. */
public final class LinuxDoWebSession {

    private static final String TAG = "LinuxDoLoginWeb";

    public interface Callback {
        void onSuccess(String json);
        void onFailure(Failure failure);
    }

    /**
     * Marker for callers whose success path immediately hands the payload to a background
     * parser. Native transports may deliver successful responses on their worker thread and
     * avoid a main-thread round trip; failures remain serialized on the main thread.
     */
    public interface BackgroundSuccessCallback extends Callback {
    }

    /** A small response that gates the first readable article frame. */
    public interface CriticalSuccessCallback extends BackgroundSuccessCallback {
    }

    public enum Failure {
        VERIFICATION_REQUIRED,
        SESSION_UNAVAILABLE,
        HTTP_OR_PROTOCOL,
        RESPONSE_TOO_LARGE,
        TIMEOUT
    }

    public interface PageListener {
        void onPageFinished();

        /**
         * Reports the finished main-frame URL when a caller needs to distinguish the login
         * document from a post-login redirect. The default implementation keeps the original
         * callback contract for list/browser owners that only care that a page is ready.
         */
        default void onPageFinished(String url) {
            onPageFinished();
        }

        /**
         * Reports a main-frame navigation error without exposing response bodies or cookies.
         * Login uses this signal to leave the WebView TCP fallback when the current network
         * only permits the native Cronet/QUIC path.
         */
        default void onPageError(String url, int errorCode, String description) {
        }
    }

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_RESPONSE_CHARS = 8 * 1024 * 1024;
    private static final long IDLE_DESTROY_DELAY_MS = 15_000L;
    private static final long REQUEST_TIMEOUT_MS = 20_000L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Request> mQueue = new ArrayDeque<>();
    private WebView mWebView;
    private MutableContextWrapper mContextWrapper;
    private Request mActiveRequest;
    private PageListener mPageListener;
    private boolean mKeepNavigationInWebView;
    private int mOwners;
    private long mRequestDeadline;
    private int mRequestGeneration;

    private final Runnable mDestroyRunnable = this::destroyIfIdle;

    public LinuxDoWebSession() {
    }

    public static LinuxDoWebSession getInstance() {
        Context context = ContextUtils.getApplication();
        if (!(context instanceof NgaClientApp)) {
            throw new IllegalStateException("NGA application is not initialized");
        }
        return ((NgaClientApp) context).getLinuxDoWebSession();
    }

    public void acquire() {
        runOnMain(() -> {
            mOwners++;
            mMainHandler.removeCallbacks(mDestroyRunnable);
        });
    }

    public void release() {
        runOnMain(() -> {
            mOwners = Math.max(0, mOwners - 1);
            if (mOwners == 0) {
                mMainHandler.removeCallbacks(mDestroyRunnable);
                mMainHandler.postDelayed(mDestroyRunnable, IDLE_DESTROY_DELAY_MS);
            }
        });
    }

    public void attach(Activity activity, ViewGroup container, PageListener pageListener) {
        runOnMain(() -> {
            mMainHandler.removeCallbacks(mDestroyRunnable);
            if (mWebView == null) createWebView(activity);
            mContextWrapper.setBaseContext(activity);
            ViewGroup parent = (ViewGroup) mWebView.getParent();
            if (parent != null) parent.removeView(mWebView);
            container.addView(mWebView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPageListener = pageListener;
            Uri current = Uri.parse(mWebView.getUrl() == null ? "" : mWebView.getUrl());
            // A login-only owner calls showLoginPage() explicitly. Do not start /latest in
            // parallel: it can replace the login document while its challenge is running.
            if (pageListener != null && !isExactOrigin(current)) {
                mWebView.loadUrl(LinuxDoConstants.ORIGIN + "/latest");
            }
        });
    }

    /**
     * Attaches a newly-created WebView after a scoped proxy override has become active.
     * Chromium may retain resolver/socket state on an existing instance, so explicit browser
     * mode must not reuse the login/native-fetch WebView that existed before the DoH proxy.
     */
    public void attachFresh(Activity activity, ViewGroup container, PageListener pageListener) {
        runOnMain(() -> {
            destroy();
            mMainHandler.removeCallbacks(mDestroyRunnable);
            createWebView(activity);
            mContextWrapper.setBaseContext(activity);
            container.removeAllViews();
            container.addView(mWebView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPageListener = pageListener;
        });
    }

    public void detachToApplication(Context context) {
        runOnMain(() -> {
            if (mWebView == null) return;
            ViewGroup parent = (ViewGroup) mWebView.getParent();
            if (parent != null) parent.removeView(mWebView);
            mContextWrapper.setBaseContext(context.getApplicationContext());
            mPageListener = null;
        });
    }

    public void showLoginPage() {
        runOnMain(() -> {
            if (mWebView == null) return;
            // Keep the login document, hCaptcha/Turnstile challenge and /session submission
            // inside this exact WebView. Fetching HTML with OkHttp and attaching it through
            // loadDataWithBaseURL splits the browser's challenge/cookie context and can leave
            // a completed challenge spinning forever during login.
            mWebView.loadUrl(LinuxDoConstants.ORIGIN + "/login");
        });
    }

    /** Flushes WebView cookies at a trusted session boundary. */
    public void flushCookies(Runnable completion) {
        runOnMain(() -> {
            try {
                android.webkit.CookieManager.getInstance().flush();
            } catch (RuntimeException ignored) {
                // Cookie flushing is best effort; the platform store remains authoritative.
            }
            if (completion != null) mMainHandler.post(completion);
        });
    }

    public void setPageListener(PageListener listener) {
        runOnMain(() -> mPageListener = listener);
    }

    /**
     * Keeps the authentication document and any OAuth/challenge redirects in this WebView.
     * The normal topic browser leaves this disabled so ordinary foreign links can still use
     * the platform browser when explicitly opened.
     */
    public void setKeepNavigationInWebView(boolean keep) {
        runOnMain(() -> mKeepNavigationInWebView = keep);
    }

    /** Exact-origin browser navigation used by the explicit browser-mode screen. */
    public void showPage(String url) {
        runOnMain(() -> {
            if (mWebView == null) return;
            Uri destination = Uri.parse(url == null ? "" : url);
            if (isExactOrigin(destination)) mWebView.loadUrl(destination.toString());
        });
    }

    /** Removes only linux.do cookies, keeping NGA and unrelated WebView sessions intact. */
    public void clearExactOriginCookies(Runnable completion) {
        runOnMain(() -> {
            android.webkit.CookieManager manager = android.webkit.CookieManager.getInstance();
            String header = manager.getCookie(LinuxDoConstants.ORIGIN);
            if (TextUtils.isEmpty(header)) {
                if (completion != null) completion.run();
                return;
            }
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (String part : header.split(";")) {
                int separator = part.indexOf('=');
                if (separator > 0) names.add(part.substring(0, separator).trim());
            }
            java.util.ArrayList<String> safeNames = new java.util.ArrayList<>();
            for (String name : names) {
                if (name.matches("[A-Za-z0-9_!#$%&'*+.^`|~-]{1,128}")) safeNames.add(name);
            }
            clearCookieAt(manager, safeNames, 0, completion);
        });
    }

    private static void clearCookieAt(
            android.webkit.CookieManager manager,
            java.util.List<String> names,
            int index,
            Runnable completion) {
        if (index >= names.size()) {
            manager.flush();
            if (completion != null) completion.run();
            return;
        }
        manager.setCookie(LinuxDoConstants.ORIGIN,
                names.get(index) + "=; Max-Age=0; Path=/; Secure",
                ignored -> clearCookieAt(manager, names, index + 1, completion));
    }

    public void destroyNow() {
        runOnMain(this::destroy);
    }

    public void fetch(String path, Callback callback) {
        if (callback == null) return;
        runOnMain(() -> {
            if (!LinuxDoTransportPolicy.isAllowedPath(path)) {
                callback.onFailure(Failure.HTTP_OR_PROTOCOL);
                return;
            }
            if (mWebView == null || !isExactOrigin(Uri.parse(mWebView.getUrl() == null
                    ? "" : mWebView.getUrl()))) {
                callback.onFailure(Failure.SESSION_UNAVAILABLE);
                return;
            }
            mQueue.add(new Request(path, callback));
            pump();
        });
    }

    private void createWebView(Activity activity) {
        mContextWrapper = new MutableContextWrapper(activity);
        mWebView = new WebView(mContextWrapper);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Cloudflare Turnstile uses a cross-origin iframe and an internal
        // about:blank/srcdoc document while the login form is bootstrapped.
        // Keep the normal WebView cookie jar available to that challenge; native
        // JSON requests still send cookies only to the exact linux.do origin.
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(mWebView, true);
        }
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri destination = request.getUrl();
                if (mKeepNavigationInWebView) {
                    // Keep HTTP(S) redirects and challenge-internal documents in this WebView;
                    // explicitly block custom schemes instead of allowing Android to resolve
                    // them through another browser or external application.
                    return !isWebNavigation(destination);
                }
                if (isExactOrigin(destination)
                        || LinuxDoTransportPolicy.isAllowedChallengeHost(
                        destination.getScheme(), destination.getHost(), destination.getPort(),
                        destination.getUserInfo())
                        || LinuxDoTransportPolicy.isAllowedChallengeDocument(
                        destination.toString())) {
                    // Keep the Cloudflare managed challenge iframe/navigation in this
                    // WebView. Other cross-origin navigations are still externalized below.
                    return false;
                }
                try {
                    Context context = mContextWrapper == null
                            ? view.getContext() : mContextWrapper.getBaseContext();
                    Intent intent = new Intent(Intent.ACTION_VIEW, destination);
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception ignored) {
                    // The foreign URL remains outside this session even without a handler.
                }
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri destination = Uri.parse(url == null ? "" : url);
                if (mKeepNavigationInWebView) {
                    return !isWebNavigation(destination);
                }
                if (isExactOrigin(destination)
                        || LinuxDoTransportPolicy.isAllowedChallengeHost(
                        destination.getScheme(), destination.getHost(), destination.getPort(),
                        destination.getUserInfo())
                        || LinuxDoTransportPolicy.isAllowedChallengeDocument(
                        destination.toString())) {
                    return false;
                }
                try {
                    Context context = mContextWrapper == null
                            ? view.getContext() : mContextWrapper.getBaseContext();
                    Intent intent = new Intent(Intent.ACTION_VIEW, destination);
                    if (!(context instanceof Activity)) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(intent);
                } catch (Exception ignored) {
                    // The foreign URL remains outside this session even without a handler.
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i(TAG, "page finished " + url);
                PageListener listener = mPageListener;
                if (listener != null) listener.onPageFinished(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    Log.e(TAG, "main frame error " + request.getUrl() + " code="
                            + error.getErrorCode() + " " + error.getDescription());
                    PageListener listener = mPageListener;
                    if (listener != null) {
                        listener.onPageError(request.getUrl().toString(), error.getErrorCode(),
                                error.getDescription() == null
                                        ? "" : error.getDescription().toString());
                    }
                }
                super.onReceivedError(view, request, error);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description,
                    String failingUrl) {
                Log.e(TAG, "legacy frame error " + failingUrl + " code=" + errorCode
                        + " " + description);
                PageListener listener = mPageListener;
                if (listener != null && isExactOrigin(Uri.parse(failingUrl == null ? "" : failingUrl))) {
                    listener.onPageError(failingUrl, errorCode, description == null ? "" : description);
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                    android.net.http.SslError error) {
                Log.e(TAG, "SSL error " + (error == null ? "unknown" : error.toString()));
                super.onReceivedSslError(view, handler, error);
            }
        });
    }

    private static boolean isExactOrigin(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "linux.do".equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1 && TextUtils.isEmpty(uri.getUserInfo());
    }

    private static boolean isWebNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)
                || LinuxDoTransportPolicy.isAllowedChallengeDocument(uri.toString());
    }

    private void pump() {
        if (mActiveRequest != null || mWebView == null || mQueue.isEmpty()) return;
        mActiveRequest = mQueue.removeFirst();
        mRequestDeadline = SystemClock.uptimeMillis() + REQUEST_TIMEOUT_MS;
        int generation = ++mRequestGeneration;
        String script = "window.__ngaJwFetch={state:'loading',text:'',status:0};"
                + "fetch(" + JSONObject.quote(mActiveRequest.path)
                + ",{method:'GET',credentials:'include',redirect:'follow'})"
                + ".then(function(r){window.__ngaJwFetch.status=r.status;return r.text();})"
                + ".then(function(t){window.__ngaJwFetch.text=t;window.__ngaJwFetch.state='done';})"
                + ".catch(function(){window.__ngaJwFetch.state='error';});void(0);";
        mWebView.evaluateJavascript(script, ignored -> pollState(generation));
    }

    private void pollState(int generation) {
        if (!isCurrent(generation)) return;
        if (SystemClock.uptimeMillis() >= mRequestDeadline) {
            finishFailure(generation, Failure.TIMEOUT);
            return;
        }
        mWebView.evaluateJavascript("window.__ngaJwFetch&&window.__ngaJwFetch.state", value -> {
            if (!isCurrent(generation)) return;
            if ("\"done\"".equals(value)) {
                readResponseLength(generation);
            } else if ("\"error\"".equals(value) || "null".equals(value)) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            } else {
                mMainHandler.postDelayed(() -> pollState(generation), 80L);
            }
        });
    }

    private void readResponseLength(int generation) {
        mWebView.evaluateJavascript("window.__ngaJwFetch.text.length", value -> {
            if (!isCurrent(generation)) return;
            try {
                int length = Integer.parseInt(value);
                if (length < 0 || length > MAX_RESPONSE_CHARS) {
                    finishFailure(generation, Failure.RESPONSE_TOO_LARGE);
                    return;
                }
                readChunk(generation, length, 0, new StringBuilder(length));
            } catch (Exception error) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            }
        });
    }

    private void readChunk(int generation, int total, int offset, StringBuilder result) {
        if (!isCurrent(generation)) return;
        if (SystemClock.uptimeMillis() >= mRequestDeadline) {
            finishFailure(generation, Failure.TIMEOUT);
            return;
        }
        if (offset >= total) {
            classifyAndFinish(generation, result.toString());
            return;
        }
        int end = Math.min(total, offset + CHUNK_SIZE);
        String expression = "window.__ngaJwFetch.text.substring(" + offset + "," + end + ")";
        mWebView.evaluateJavascript(expression, encoded -> {
            if (!isCurrent(generation)) return;
            try {
                String chunk = new JSONArray("[" + encoded + "]").getString(0);
                result.append(chunk);
                readChunk(generation, total, end, result);
            } catch (Exception error) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            }
        });
    }

    private void classifyAndFinish(int generation, String body) {
        mWebView.evaluateJavascript("window.__ngaJwFetch.status", statusText -> {
            if (!isCurrent(generation)) return;
            try {
                int status = Integer.parseInt(statusText);
                LinuxDoTransportPolicy.ResponseKind kind =
                        LinuxDoTransportPolicy.classify(status, body);
                if (kind != LinuxDoTransportPolicy.ResponseKind.JSON) {
                    finishFailure(generation,
                            kind == LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED
                            ? Failure.VERIFICATION_REQUIRED : Failure.HTTP_OR_PROTOCOL);
                    return;
                }
                Request request = takeActive();
                if (request != null) request.callback.onSuccess(body);
                pump();
            } catch (Exception error) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            }
        });
    }

    private boolean isCurrent(int generation) {
        return mWebView != null && mActiveRequest != null && generation == mRequestGeneration;
    }

    private void finishFailure(int generation, Failure failure) {
        if (!isCurrent(generation)) return;
        Request request = takeActive();
        if (request != null) request.callback.onFailure(failure);
        pump();
    }

    private Request takeActive() {
        Request request = mActiveRequest;
        mActiveRequest = null;
        return request;
    }

    private void destroyIfIdle() {
        if (mOwners == 0) destroy();
    }

    private void destroy() {
        mMainHandler.removeCallbacksAndMessages(null);
        mRequestGeneration++;
        if (mActiveRequest != null) mActiveRequest.callback.onFailure(Failure.SESSION_UNAVAILABLE);
        while (!mQueue.isEmpty()) mQueue.removeFirst().callback.onFailure(Failure.SESSION_UNAVAILABLE);
        mActiveRequest = null;
        if (mWebView != null) {
            ViewGroup parent = (ViewGroup) mWebView.getParent();
            if (parent != null) parent.removeView(mWebView);
            mWebView.stopLoading();
            mWebView.destroy();
        }
        mWebView = null;
        mContextWrapper = null;
        mPageListener = null;
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else mMainHandler.post(runnable);
    }

    private static final class Request {
        final String path;
        final Callback callback;

        Request(String path, Callback callback) {
            this.path = path;
            this.callback = callback;
        }
    }
}
