package sp.phone.mvp.model.web;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;

import gov.anzong.androidnga.NgaClientApp;
import gov.anzong.androidnga.base.util.ContextUtils;

/**
 * Serialized, short-lived WebView transport used only after strict native JSON parsing fails.
 * The WebView cookie jar is used in place and no cookie or page body crosses a logging boundary.
 */
public final class NgaWebArticleFallbackSession {

    public interface Callback {
        void onSuccess(String snapshot);
        void onFailure(Failure failure);
    }

    public interface RequestHandle {
        void cancel();
    }

    public enum Failure {
        HTTP_OR_PROTOCOL,
        EXTRACTOR_UNAVAILABLE,
        RESPONSE_TOO_LARGE,
        TIMEOUT
    }

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_RESPONSE_CHARS = 8 * 1024 * 1024;
    private static final int MAX_EXTRACTOR_BYTES = 128 * 1024;
    private static final long REQUEST_TIMEOUT_MS = 25_000L;
    private static final long POLL_INTERVAL_MS = 80L;
    private static final String EXTRACTOR_ASSET = "nga_web_fallback_extract.js";
    private static final String PAGE_READY_SCRIPT =
            "(function(){"
                    + "if(document.readyState!=='complete')return false;"
                    + "if(!document.querySelector('[id^=postcontent]'))return false;"
                    + "var s=document.querySelectorAll('script');"
                    + "var d=false,p=false;"
                    + "for(var i=0;i<s.length;i++){var t=s[i].textContent||'';"
                    + "if(t.indexOf('commonui.postArg.setDefault(')>=0)d=true;"
                    + "if(t.indexOf('commonui.postArg.proc(')>=0)p=true;"
                    + "if(d&&p)return true;}return false;}())";

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Request> mQueue = new ArrayDeque<>();
    private Request mActiveRequest;
    private WebView mWebView;
    private int mGeneration;
    private long mDeadline;
    private String mExtractorScript;
    private final Runnable mTimeoutRunnable = () -> {
        int generation = mGeneration;
        if (isCurrent(generation) && SystemClock.uptimeMillis() >= mDeadline) {
            finishFailure(generation, Failure.TIMEOUT);
        }
    };

    public static NgaWebArticleFallbackSession getInstance() {
        Context application = ContextUtils.getApplication();
        if (!(application instanceof NgaClientApp)) {
            throw new IllegalStateException("NGA application is not initialized");
        }
        return ((NgaClientApp) application).getNgaWebArticleFallbackSession();
    }

    public RequestHandle load(String url, Callback callback) {
        if (callback == null) return () -> { };
        Request request = new Request(url, callback);
        runOnMain(() -> enqueue(request));
        return () -> {
            request.cancelled = true;
            runOnMain(() -> cancelOnMain(request));
        };
    }

    private void enqueue(Request request) {
        if (request.cancelled) return;
        if (!NgaWebArticleFallbackPolicy.isAllowedReadUrl(request.url)) {
            safelyFail(request, Failure.HTTP_OR_PROTOCOL);
            return;
        }
        mQueue.addLast(request);
        pump();
    }

    private void cancelOnMain(Request request) {
        if (request == mActiveRequest) {
            abandonActive();
            pump();
        } else {
            mQueue.remove(request);
        }
    }

    private void pump() {
        if (mActiveRequest != null) return;
        while (!mQueue.isEmpty() && mQueue.peekFirst().cancelled) mQueue.removeFirst();
        if (mQueue.isEmpty()) return;
        mActiveRequest = mQueue.removeFirst();
        int generation = ++mGeneration;
        mDeadline = SystemClock.uptimeMillis() + REQUEST_TIMEOUT_MS;
        try {
            createWebView(generation);
            mMainHandler.postDelayed(mTimeoutRunnable, REQUEST_TIMEOUT_MS);
            mWebView.loadUrl(mActiveRequest.url);
        } catch (RuntimeException error) {
            finishFailure(generation, Failure.EXTRACTOR_UNAVAILABLE);
        }
    }

    private void createWebView(int generation) {
        Context application = ContextUtils.getApplication();
        MutableContextWrapper wrapper = new MutableContextWrapper(application);
        mWebView = new WebView(wrapper);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadsImagesAutomatically(false);
        settings.setBlockNetworkImage(true);
        settings.setSafeBrowsingEnabled(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(
                        request.getUrl().toString())) {
                    return false;
                }
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(url)) return false;
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(url)) {
                    finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!isCurrent(generation)) return;
                if (!NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(url)) {
                    finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                    return;
                }
                if (NgaWebArticleFallbackPolicy.isAllowedReadUrl(url)) {
                    pollPageReady(generation);
                }
            }

            @Override
            public void onReceivedError(
                    WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                    finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                finishFailure(generation, Failure.EXTRACTOR_UNAVAILABLE);
                return true;
            }
        });
    }

    private void pollPageReady(int generation) {
        if (!isCurrent(generation)) return;
        if (isTimedOut()) {
            finishFailure(generation, Failure.TIMEOUT);
            return;
        }
        mWebView.evaluateJavascript(PAGE_READY_SCRIPT, value -> {
            if (!isCurrent(generation)) return;
            if ("true".equals(value)) injectExtractor(generation);
            else mMainHandler.postDelayed(() -> pollPageReady(generation), POLL_INTERVAL_MS);
        });
    }

    private void injectExtractor(int generation) {
        if (!isCurrent(generation)) return;
        String extractor;
        try {
            extractor = getExtractorScript();
        } catch (IOException error) {
            finishFailure(generation, Failure.EXTRACTOR_UNAVAILABLE);
            return;
        }
        mWebView.evaluateJavascript(extractor, ignored -> pollSnapshotState(generation));
    }

    private void pollSnapshotState(int generation) {
        if (!isCurrent(generation)) return;
        if (isTimedOut()) {
            finishFailure(generation, Failure.TIMEOUT);
            return;
        }
        mWebView.evaluateJavascript(
                "window.__ngaJwArticleSnapshot&&window.__ngaJwArticleSnapshot.state", value -> {
                    if (!isCurrent(generation)) return;
                    if ("\"done\"".equals(value)) readSnapshotLength(generation);
                    else if ("\"error\"".equals(value) || "null".equals(value)) {
                        finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
                    } else {
                        mMainHandler.postDelayed(
                                () -> pollSnapshotState(generation), POLL_INTERVAL_MS);
                    }
                });
    }

    private void readSnapshotLength(int generation) {
        mWebView.evaluateJavascript("window.__ngaJwArticleSnapshot.text.length", value -> {
            if (!isCurrent(generation)) return;
            try {
                int length = Integer.parseInt(value);
                if (length <= 0 || length > MAX_RESPONSE_CHARS) {
                    finishFailure(generation, Failure.RESPONSE_TOO_LARGE);
                    return;
                }
                readChunk(generation, length, 0, new StringBuilder(length));
            } catch (RuntimeException error) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            }
        });
    }

    private void readChunk(
            int generation, int total, int offset, StringBuilder snapshot) {
        if (!isCurrent(generation)) return;
        if (isTimedOut()) {
            finishFailure(generation, Failure.TIMEOUT);
            return;
        }
        if (offset >= total) {
            finishSuccess(generation, snapshot.toString());
            return;
        }
        int end = Math.min(total, offset + CHUNK_SIZE);
        String expression = "window.__ngaJwArticleSnapshot.text.substring("
                + offset + "," + end + ")";
        mWebView.evaluateJavascript(expression, encoded -> {
            if (!isCurrent(generation)) return;
            try {
                snapshot.append(new JSONArray("[" + encoded + "]").getString(0));
                readChunk(generation, total, end, snapshot);
            } catch (Exception error) {
                finishFailure(generation, Failure.HTTP_OR_PROTOCOL);
            }
        });
    }

    private String getExtractorScript() throws IOException {
        if (mExtractorScript != null) return mExtractorScript;
        try (InputStream input = ContextUtils.getApplication().getAssets().open(EXTRACTOR_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_EXTRACTOR_BYTES) throw new IOException("Extractor is too large");
                output.write(buffer, 0, read);
            }
            mExtractorScript = output.toString(StandardCharsets.UTF_8.name());
            return mExtractorScript;
        }
    }

    private boolean isTimedOut() {
        return SystemClock.uptimeMillis() >= mDeadline;
    }

    private boolean isCurrent(int generation) {
        return mActiveRequest != null && mWebView != null && generation == mGeneration;
    }

    private void finishSuccess(int generation, String snapshot) {
        if (!isCurrent(generation)) return;
        Request request = takeActive();
        if (request != null && !request.cancelled) {
            try {
                request.callback.onSuccess(snapshot);
            } catch (RuntimeException ignored) {
                // Client exceptions must not strand the serialized transport.
            }
        }
        pump();
    }

    private void finishFailure(int generation, Failure failure) {
        if (!isCurrent(generation)) return;
        Request request = takeActive();
        if (request != null && !request.cancelled) safelyFail(request, failure);
        pump();
    }

    private void safelyFail(Request request, Failure failure) {
        if (request.cancelled) return;
        try {
            request.callback.onFailure(failure);
        } catch (RuntimeException ignored) {
            // Client exceptions must not strand the serialized transport.
        }
    }

    private Request takeActive() {
        Request request = mActiveRequest;
        mActiveRequest = null;
        mMainHandler.removeCallbacks(mTimeoutRunnable);
        mGeneration++;
        destroyWebView();
        return request;
    }

    private void abandonActive() {
        if (mActiveRequest == null) return;
        mActiveRequest = null;
        mMainHandler.removeCallbacks(mTimeoutRunnable);
        mGeneration++;
        destroyWebView();
    }

    private void destroyWebView() {
        if (mWebView == null) return;
        mWebView.stopLoading();
        mWebView.setWebViewClient(null);
        mWebView.loadUrl("about:blank");
        mWebView.clearHistory();
        mWebView.removeAllViews();
        mWebView.destroy();
        mWebView = null;
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else mMainHandler.post(runnable);
    }

    private static final class Request {
        final String url;
        final Callback callback;
        volatile boolean cancelled;

        Request(String url, Callback callback) {
            this.url = url;
            this.callback = callback;
        }
    }

    public NgaWebArticleFallbackSession() {
    }
}
