package sp.phone.linuxdo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONObject;

/** One browser/cookie/TLS context for the complete official login and managed challenge. */
public final class LinuxDoAuthBrowser {
    public interface Listener {
        void onPage();
        void onError(String message);
        void onProgress(int value);
    }
    public interface Result { void receive(String kind, String username); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WebView view;
    private final FrameLayout container;
    private boolean closed;
    private boolean failed;
    private int probeId;

    @SuppressLint("SetJavaScriptEnabled")
    public LinuxDoAuthBrowser(Activity activity, FrameLayout parent, Listener listener) {
        container = parent;
        view = new WebView(activity);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView webView, int progress) {
                if (!closed) listener.onProgress(progress);
            }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView webView, String url, android.graphics.Bitmap icon) {
                failed = false;
                ++probeId;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest request) {
                return !allowedNavigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView webView, String url) {
                return !allowedNavigation(Uri.parse(url));
            }
            @Override public void onPageFinished(WebView webView, String url) {
                if (!closed && !failed) listener.onPage();
            }
            @Override public void onReceivedError(WebView webView, WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame() && !closed) {
                    failed = true;
                    listener.onError("网页连接失败（" + error.getErrorCode()
                            + "）。可重试或检查专用 DNS；不会清除登录状态。");
                }
            }
            @Override public void onReceivedSslError(WebView webView,
                    android.webkit.SslErrorHandler ssl, android.net.http.SslError error) {
                ssl.cancel();
                failed = true;
                if (!closed) listener.onError("网站证书校验失败，已停止连接以保护账号。请检查设备时间与网络。");
            }
            @Override public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) {
                failed = true;
                if (!closed) {
                    close();
                    listener.onError("网页组件已退出，请点重试重新打开。");
                }
                return true;
            }
        });
        container.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }

    private static boolean allowedNavigation(Uri uri) {
        return uri != null && ("https".equalsIgnoreCase(uri.getScheme())
                || "about:blank".equals(uri.toString()) || "about:srcdoc".equals(uri.toString()));
    }

    public void load(String url) {
        if (closed || !LinuxDoAuthFlow.isFirstParty(url)) return;
        failed = false;
        ++probeId;
        view.loadUrl(url);
    }

    public void fill(String identifier, String password, android.webkit.ValueCallback<Boolean> callback) {
        fill(identifier, password, true, callback);
    }

    public void fill(String identifier, String password, boolean replace,
            android.webkit.ValueCallback<Boolean> callback) {
        if (closed || failed || !LinuxDoAuthFlow.isFirstParty(view.getUrl())) {
            callback.onReceiveValue(false);
            return;
        }
        view.evaluateJavascript(LinuxDoAuthScripts.fill(identifier, password, replace),
                result -> { if (!closed) callback.onReceiveValue("true".equals(result)); });
    }

    public void probe(boolean verification, Result callback) {
        if (closed || failed || !LinuxDoAuthFlow.isFirstParty(view.getUrl())) {
            callback.receive("network", "");
            return;
        }
        int id = ++probeId;
        String key = "__ngaSessionProbe" + id;
        view.evaluateJavascript(LinuxDoAuthScripts.probe(verification, key), ignored -> {
            if (!closed && id == probeId) poll(id, key, 0, callback);
        });
    }

    private void poll(int id, String key, int attempt, Result callback) {
        if (closed || id != probeId) return;
        if (!LinuxDoAuthFlow.isFirstParty(view.getUrl())) { callback.receive("network", ""); return; }
        view.evaluateJavascript("window[" + JSONObject.quote(key) + "]||null", result -> {
            if (closed || id != probeId) return;
            if (result == null || "null".equals(result)) {
                if (attempt < 18) handler.postDelayed(() -> poll(id, key, attempt + 1, callback), 500);
                else callback.receive("network", "");
                return;
            }
            try {
                JSONObject value = new JSONObject(result);
                callback.receive(value.optString("kind", "network"), value.optString("username", ""));
            } catch (Exception ignored) { callback.receive("network", ""); }
        });
    }

    public void close() {
        if (closed) return;
        closed = true;
        ++probeId;
        handler.removeCallbacksAndMessages(null);
        container.removeView(view);
        try { view.stopLoading(); } catch (RuntimeException ignored) { /* Renderer may be gone. */ }
        view.setWebChromeClient(null);
        view.setWebViewClient(new WebViewClient());
        view.destroy();
    }
}
