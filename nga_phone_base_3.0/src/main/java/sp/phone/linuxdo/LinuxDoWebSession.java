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

    public interface Callback {
        void onSuccess(String json);
        void onFailure(Failure failure);
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
            if (!isExactOrigin(current)) {
                mWebView.loadUrl(LinuxDoConstants.ORIGIN + "/latest");
            }
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
            if (mWebView != null) {
                mWebView.loadUrl(LinuxDoConstants.ORIGIN + "/login");
            }
        });
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
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri destination = request.getUrl();
                if (isExactOrigin(destination)) return false;
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
                PageListener listener = mPageListener;
                if (listener != null) listener.onPageFinished();
            }
        });
    }

    private static boolean isExactOrigin(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && "linux.do".equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1 && TextUtils.isEmpty(uri.getUserInfo());
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
