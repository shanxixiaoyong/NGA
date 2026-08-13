package sp.phone.view.webview;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import com.alibaba.android.arouter.launcher.ARouter;

import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.ArticleListActivity;
import gov.anzong.androidnga.activity.TopicListActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.gallery.ImageZoomActivity;
import sp.phone.util.StringUtils;
import sp.phone.linuxdo.LinuxDoAvatarProxy;
import sp.phone.linuxdo.LinuxDoHttpSession;

public class WebViewClientEx extends WebViewClient {

    private List<String> mImgUrlList;
    private boolean mLinuxDoMediaTransport;


    private static final String NGA_USER_PROFILE_END = "&";

    private static final String[] SUFFIX_IMAGE = {
            ".gif", ".jpg", ".png", ".jpeg", ".bmp", ".webp"
    };

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

    @Override
    public WebResourceResponse shouldInterceptRequest(
            WebView view, WebResourceRequest request) {
        return interceptLinuxDoAvatar(request == null || request.getUrl() == null
                ? null : request.getUrl().toString());
    }

    @Override
    @SuppressWarnings("deprecation")
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return interceptLinuxDoAvatar(url);
    }

    private WebResourceResponse interceptLinuxDoAvatar(String requestUrl) {
        if (!mLinuxDoMediaTransport) return null;
        String sourceUrl = LinuxDoAvatarProxy.unwrap(requestUrl);
        if (sourceUrl == null) return null;
        byte[] bytes = LinuxDoHttpSession.getInstance().fetchAvatarBlocking(sourceUrl);
        if (bytes == null || bytes.length == 0) return null;
        return new WebResourceResponse(
                detectImageMime(bytes), null, new ByteArrayInputStream(bytes));
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

        for (String suffix : SUFFIX_IMAGE) {
            if (url.endsWith(suffix)) {
                Intent intent = new Intent();
                if (mImgUrlList == null) {
                    mImgUrlList = new ArrayList<>();
                    mImgUrlList.add(url);
                } else if (mImgUrlList.isEmpty()) {
                    mImgUrlList.add(url);
                }
                String[] urls = new String[mImgUrlList.size()];
                mImgUrlList.toArray(urls);
                intent.putExtra(ImageZoomActivity.KEY_GALLERY_URLS, urls);
                intent.putExtra(ImageZoomActivity.KEY_GALLERY_CUR_URL, url);
                intent.setClass(context, ImageZoomActivity.class);
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

    @Override
    public void onPageFinished(WebView view, String url) {
        view.getSettings().setBlockNetworkImage(false);
        super.onPageFinished(view, url);
    }
}
