package sp.phone.view.webview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.alibaba.android.arouter.launcher.ARouter;

import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.List;

import org.json.JSONObject;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.ArticleListActivity;
import gov.anzong.androidnga.activity.TopicListActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.gallery.ImageGalleryPolicy;
import gov.anzong.androidnga.gallery.ImageZoomActivity;
import sp.phone.util.StringUtils;
import sp.phone.linuxdo.LinuxDoAvatarProxy;
import sp.phone.linuxdo.LinuxDoHttpSession;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoMediaProxy;
import sp.phone.linuxdo.LinuxDoTransportPolicy;
import sp.phone.linuxdo.LinuxDoWebSession;

public class WebViewClientEx extends WebViewClient {

    private static final String PLACEHOLDER_AVATAR =
            "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'>"
                    + "<circle cx='16' cy='16' r='16' fill='#d7d7d7'/></svg>";
    private static final String PLACEHOLDER_INLINE_IMAGE =
            "<svg xmlns='http://www.w3.org/2000/svg' width='1' height='1' viewBox='0 0 1 1'/>";
    private static final int MAX_INLINE_MEDIA_BYTES = 8 * 1024 * 1024;

    private List<String> mImgUrlList;
    private boolean mLinuxDoMediaTransport;
    private boolean mEagerNetworkImages;
    private Runnable mPageFinishedListener;


    private static final String NGA_USER_PROFILE_END = "&";

    private static final String NGA_READ = "/read.php?";

    private static final String NGA_THREAD = "/thread.php?";

    private static final String NGA_USER_PROFILE = "/nuke.php?func=ucp&username=";

    private static String[] sReadPrefix;

    private static String[] sThreadPrefix;

    private static String[] NGA_USER_PROFILE_START;

    static {
        String[] domains = ContextUtils.getContext().getResources().getStringArray(gov.anzong.androidnga.common.R.array.nga_domain_no_http);
        sThreadPrefix = new String[domains.length];
        sReadPrefix = new String[domains.length];
        NGA_USER_PROFILE_START = new String[domains.length];
        for (int i = 0; i < domains.length; i++) {
            sThreadPrefix[i] = domains[i] + NGA_THREAD;
            sReadPrefix[i] = domains[i] + NGA_READ;
            NGA_USER_PROFILE_START[i] = domains[i] + NGA_USER_PROFILE;
        }
    }

    public WebViewClientEx(Context context) {
        super();
    }

    public WebViewClientEx() {
        super();
    }

    public void setImgUrls(List<String> list) {
        mImgUrlList = list;
    }

    public void setLinuxDoMediaTransport(boolean enabled) {
        mLinuxDoMediaTransport = enabled;
    }

    public void setEagerNetworkImages(boolean eager) {
        mEagerNetworkImages = eager;
    }

    /**
     * Receives the first document-complete callback for a detached article body.
     * The listener is intentionally optional so the existing NGA WebView paths
     * keep their original behavior.
     */
    public void setPageFinishedListener(Runnable listener) {
        mPageFinishedListener = listener;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(
            WebView view, WebResourceRequest request) {
        return interceptLinuxDoAvatar(view, request == null || request.getUrl() == null
                ? null : request.getUrl().toString());
    }

    @Override
    @SuppressWarnings("deprecation")
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return interceptLinuxDoAvatar(view, url);
    }

    private WebResourceResponse interceptLinuxDoAvatar(WebView view, String requestUrl) {
        if (!mLinuxDoMediaTransport) return null;
        String sourceUrl = LinuxDoAvatarProxy.unwrap(requestUrl);
        // Body images deliberately keep WebView's proven direct-loading path. The asynchronous
        // placeholder/replacement transport can miss its replacement while images are blocked,
        // which makes image-only posts look like empty content.
        if (sourceUrl == null) return null;
        boolean avatarRequest = true;
        final String resolvedSourceUrl = sourceUrl;
        LinuxDoHttpSession session = LinuxDoHttpSession.getInstance();
        byte[] bytes = session.getCachedAvatar(resolvedSourceUrl);
        if (bytes == null || bytes.length == 0) {
            // WebView resource threads must never wait on DNS, TLS, or DoH. Return a tiny
            // placeholder immediately and replace it from the async source-isolated cache.
            fetchLinuxDoMedia(view, requestUrl, resolvedSourceUrl, avatarRequest, 0);
            String placeholder = avatarRequest ? PLACEHOLDER_AVATAR : PLACEHOLDER_INLINE_IMAGE;
            return new WebResourceResponse(
                    "image/svg+xml", "utf-8",
                    new ByteArrayInputStream(placeholder.getBytes(StandardCharsets.UTF_8)));
        }
        return new WebResourceResponse(
                detectImageMime(bytes), null, new ByteArrayInputStream(bytes));
    }

    private void fetchLinuxDoMedia(
            WebView view, String requestUrl, String sourceUrl, boolean avatarRequest,
            int attempt) {
        LinuxDoHttpSession.getInstance().fetchMedia(
                sourceUrl, new LinuxDoHttpSession.ByteCallback() {
                    @Override
                    public void onSuccess(byte[] loaded) {
                        if (loaded == null || loaded.length == 0
                                || loaded.length > MAX_INLINE_MEDIA_BYTES || view == null) return;
                        if (avatarRequest) {
                            // Avatars are deliberately tiny, so their established data-URI path
                            // cannot hit the large body-image transaction failure below.
                            String dataUri = "data:" + detectImageMime(loaded) + ";base64,"
                                    + Base64.encodeToString(loaded, Base64.NO_WRAP);
                            replaceLinuxDoImage(view, requestUrl, sourceUrl, dataUri);
                            return;
                        }
                        // Never send a full body image through evaluateJavascript: a multi-MiB
                        // base64 string can exceed WebView/Binder limits and leave an image-only
                        // floor permanently blank. This URL is served from the populated cache.
                        replaceLinuxDoImage(
                                view, requestUrl, sourceUrl, LinuxDoMediaProxy.wrap(sourceUrl));
                    }

                    @Override
                    public void onFailure(LinuxDoWebSession.Failure failure) {
                        if (!avatarRequest && attempt == 0 && view != null) {
                            // One bounded retry covers transient media failures without a loop.
                            view.postDelayed(() -> fetchLinuxDoMedia(
                                    view, requestUrl, sourceUrl, false, 1), 400L);
                        }
                    }
                });
    }

    private static void replaceLinuxDoImage(
            WebView view, String requestUrl, String sourceUrl, String replacementUrl) {
        if (replacementUrl == null || replacementUrl.isEmpty()) return;
        String key = requestUrl == null ? "" : requestUrl;
        String javascript = "(function(){var key=" + JSONObject.quote(key)
                + ",source=" + JSONObject.quote(sourceUrl)
                + ",replacement=" + JSONObject.quote(replacementUrl)
                + ",xs=document.getElementsByTagName('img');"
                + "for(var i=0;i<xs.length;i++){var x=xs[i],src=x.src||'',cur=x.currentSrc||'',"
                + "raw=x.getAttribute('src')||'',lazy=x.getAttribute('data-src')||'',"
                + "original=x.getAttribute('data-original-src')||'',set=x.getAttribute('srcset')||'';"
                + "if(x.getAttribute('data-nga-avatar-key')===key||src===key||src===source||"
                + "cur===key||cur===source||raw===key||raw===source||lazy===key||lazy===source||"
                + "original===key||original===source||(key&&set.indexOf(key)>=0)||"
                + "set.indexOf(source)>=0){"
                + "x.removeAttribute('srcset');x.removeAttribute('sizes');"
                + "x.removeAttribute('data-src');x.src=replacement;}}})();";
        view.post(() -> view.evaluateJavascript(javascript, null));
    }

    private static boolean isLinuxDoInlineMedia(String requestUrl) {
        if (requestUrl == null || requestUrl.trim().isEmpty()) return false;
        try {
            Uri uri = Uri.parse(requestUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && LinuxDoTransportPolicy.isAllowedMediaHost(uri.getHost());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String detectImageMime(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I'
                && bytes[2] == 'F') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't'
                && bytes[6] == 'y' && bytes[7] == 'p'
                && bytes[8] == 'a' && bytes[9] == 'v'
                && bytes[10] == 'i' && bytes[11] == 'f') return "image/avif";
        if (bytes.length >= 4 && bytes[0] == '<'
                && (bytes[1] == 's' || bytes[1] == '?' || bytes[1] == '!')) {
            return "image/svg+xml";
        }
        return "image/*";
    }

    private boolean overrideProfileUrlLoading(Context context, String url) {
        for (String profileStart : NGA_USER_PROFILE_START)
            if (url.contains(profileStart)) {
                String data = StringUtils.getStringBetween(url, 0,
                        profileStart, NGA_USER_PROFILE_END).result;
                try {
                    data = URLDecoder.decode(data, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                if (!StringUtils.isEmpty(data)) {
                    ARouter.getInstance()
                            .build(ARouterConstants.ACTIVITY_PROFILE)
                            .withString("mode", "username")
                            .withString("username", data)
                            .navigation(context);
                }
                return true;
            }
        return false;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Context context = view.getContext();

        if (openImageGallery(context, url)) return true;

        // Keep cross-topic LinuxDo links inside the fast native reader. Returning true here is
        // important: letting WebView continue as well as starting an Activity caused the visible
        // "opens then jumps back" double navigation.
        if (LinuxDoNavigation.openNativeTopicLink(context, url)) return true;

        if (!url.startsWith("http") && !url.startsWith("market")) {
            url = "http://" + url;
        }

        for (String read : sReadPrefix) {
            if (url.startsWith(read, "http://".length())
                    || url.startsWith(read, "https://".length())) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(url));
                intent.putExtra("fromreplyactivity", 1);
                intent.setClass(context, ArticleListActivity.class);
                context.startActivity(intent);
                return true;
            }
        }

        for (String thread : sThreadPrefix) {
            if (url.startsWith(thread, "http://".length())
                    || url.startsWith(thread, "https://".length())) {
                Intent intent = new Intent();
                intent.setData(Uri.parse(url));
                intent.setClass(context, TopicListActivity.class);
                context.startActivity(intent);
                return true;
            }
        }

        if (!overrideProfileUrlLoading(context, url)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    /** API 24+ dispatches this overload for main-frame image links. */
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request == null || request.getUrl() == null
                ? null : request.getUrl().toString();
        if (openImageGallery(view.getContext(), url)) return true;
        return shouldOverrideUrlLoading(view, url == null ? "" : url);
    }

    private boolean openImageGallery(Context context, String clickedUrl) {
        if (context == null || clickedUrl == null || clickedUrl.trim().isEmpty()) return false;
        // A URL without a conventional suffix can still be an image when the parser supplied it
        // in the row gallery. This covers CDN query strings and extensionless image endpoints.
        int currentIndex = ImageGalleryPolicy.findIndex(mImgUrlList, clickedUrl);
        if (currentIndex < 0 && !ImageGalleryPolicy.isImageUrl(clickedUrl)) return false;

        String[] urls = ImageGalleryPolicy.copyUrls(mImgUrlList, clickedUrl);
        currentIndex = ImageGalleryPolicy.findIndex(java.util.Arrays.asList(urls), clickedUrl);
        if (currentIndex < 0) currentIndex = Math.max(0, urls.length - 1);

        Intent intent = new Intent(context, ImageZoomActivity.class);
        intent.putExtra(ImageZoomActivity.KEY_GALLERY_URLS, urls);
        // Pass the resolved list entry as well as the index. The index is authoritative and does
        // not depend on URL spelling after WebView has decoded/normalized the clicked href.
        intent.putExtra(ImageZoomActivity.KEY_GALLERY_CUR_URL, urls[currentIndex]);
        intent.putExtra(ImageZoomActivity.KEY_GALLERY_INDEX, currentIndex);
        context.startActivity(intent);
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (mEagerNetworkImages) view.getSettings().setBlockNetworkImage(false);
        super.onPageFinished(view, url);
        if (mPageFinishedListener != null) {
            // onPageFinished only means that Chromium finished parsing the document.  A
            // detached WebView can still have an empty first compositor frame at this point,
            // which used to let the LinuxDo loading layer disappear before the floor body was
            // actually paintable.  Wait for the visual state and one UI frame so the readiness
            // signal tracks visible text rather than the network callback.
            final Runnable listener = mPageFinishedListener;
            view.postVisualStateCallback(SystemClock.uptimeMillis(),
                    new WebView.VisualStateCallback() {
                        @Override
                        public void onComplete(long requestId) {
                            view.postOnAnimation(listener);
                        }
                    });
        }
    }
}
