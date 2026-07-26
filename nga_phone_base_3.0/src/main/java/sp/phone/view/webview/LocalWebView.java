package sp.phone.view.webview;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;

import androidx.annotation.Nullable;

import gov.anzong.androidnga.common.util.LogUtils;
import gov.anzong.androidnga.common.view.WebViewEx;
import sp.phone.common.PhoneConfiguration;

/**
 * @author Justwen
 */
public class LocalWebView extends WebViewEx implements DownloadListener {

    private WebViewClientEx mWebViewClientEx;

    private String mContentData;

    public LocalWebView(Context context) {
        this(context, null);
    }

    public LocalWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
        setVerticalScrollBarEnabled(false);
    }

    private void initialize() {
        setDownloadListener(this);
        try {
            setLocalMode();
        } catch (Exception e) {
            // 某些机型的WebView不支持以上方法的调用
        }
    }

    private void downloadByBrowser(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(uri);
        getContext().startActivity(intent);
    }

    public void setLocalMode() {
        mWebViewClientEx = new WebViewClientEx();
        setWebViewClient(mWebViewClientEx);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(PhoneConfiguration.getInstance().getWebViewTextZoom());
        settings.setBlockNetworkImage(true);

        setFocusableInTouchMode(false);
        setFocusable(false);
        setLongClickable(false);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public WebViewClientEx getWebViewClientEx() {
        return mWebViewClientEx;
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        downloadByBrowser(url);
    }

    @Override
    public void loadDataWithBaseURL(@Nullable String baseUrl, String data, @Nullable String mimeType, @Nullable String encoding, @Nullable String historyUrl) {
        if (data.equals(mContentData)) {
            LogUtils.d("Data is not changed, ignore this update");
            return;
        }
        mContentData = data;
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }
}
