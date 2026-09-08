package sp.phone.linuxdo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import gov.anzong.androidnga.activity.LinuxDoSessionActivity;
import gov.anzong.androidnga.activity.TopicListActivity;
import gov.anzong.androidnga.activity.ArticleListActivity;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.param.ParamKey;
import sp.phone.param.TopicListParam;

public final class LinuxDoNavigation {
    public static final String EXTRA_LOGIN_ONLY = "linuxdo_login_only";
    public static final String EXTRA_DOH_URL = "linuxdo_login_doh_url";
    public static final String EXTRA_SESSION_HANDOFF = "linuxdo_session_handoff";
    public static final String EXTRA_RESET_SESSION = "linuxdo_reset_session";
    public static final String EXTRA_BROWSER_URL = "linuxdo_browser_url";
    private LinuxDoNavigation() {
    }

    public static void openVerification(Context context) {
        Intent intent = sessionIntent(context, false, false);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openLogin(Context context) {
        openLogin(context, false);
    }

    public static void openLogin(Context context, boolean resetSession) {
        Intent intent = sessionIntent(context, true, false);
        intent.putExtra(EXTRA_RESET_SESSION, resetSession);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /** Opens a topic in the DNS-isolated real WebView instead of the native JSON renderer. */
    public static void openTopicInBrowser(Context context, int topicId) {
        if (topicId <= 0) return;
        openUrlInBrowser(context, LinuxDoConstants.ORIGIN + "/t/" + topicId);
    }

    /**
     * Routes a linux.do topic link from an article body back into the native reader.  Discourse
     * accepts both /t/{id}/{postNumber} and /t/{slug}/{id}/{postNumber}; the post number is
     * one-based while our saved/read floor is zero-based.
     */
    public static boolean openNativeTopicLink(Context context, String target) {
        if (context == null || target == null) return false;
        try {
            Uri uri = Uri.parse(target.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !LinuxDoConstants.HOST.equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null) return false;
            java.util.List<String> parts = uri.getPathSegments();
            if (parts == null || parts.size() < 2 || !"t".equals(parts.get(0))) return false;
            int topicIndex;
            int topicId;
            try {
                topicId = Integer.parseInt(parts.get(1));
                topicIndex = 1;
            } catch (NumberFormatException ignored) {
                if (parts.size() < 3) return false;
                topicId = Integer.parseInt(parts.get(2));
                topicIndex = 2;
            }
            if (topicId <= 0) return false;
            int postNumber = 1;
            if (parts.size() > topicIndex + 1) {
                try {
                    postNumber = Math.max(1, Integer.parseInt(parts.get(topicIndex + 1)));
                } catch (NumberFormatException ignored) {
                    postNumber = 1;
                }
            }
            ArticleListParam param = new ArticleListParam();
            param.source = ContentSource.LINUX_DO;
            param.tid = topicId;
            param.targetFloor = postNumber - 1;
            param.page = Math.max(1, ((postNumber - 1) / 20) + 1);
            Intent intent = new Intent(context, ArticleListActivity.class);
            intent.putExtra(ParamKey.KEY_PARAM, param);
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Opens an exact-origin LINUX DO URL in the same scoped DoH browser surface. */
    public static void openUrlInBrowser(Context context, String target) {
        if (context == null || target == null || target.trim().isEmpty()) return;
        Uri parsed = Uri.parse(target.startsWith("/")
                ? LinuxDoConstants.ORIGIN + target : target.trim());
        if (!"https".equalsIgnoreCase(parsed.getScheme())
                || !"linux.do".equalsIgnoreCase(parsed.getHost())
                || parsed.getPort() != -1 || parsed.getUserInfo() != null) return;
        Intent intent = new Intent(context, LinuxDoSessionActivity.class);
        intent.putExtra(EXTRA_DOH_URL, LinuxDoDohConfig.currentUrl());
        intent.putExtra(EXTRA_BROWSER_URL, parsed.toString());
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static Intent sessionIntent(Context context, boolean loginOnly, boolean openList) {
        Intent intent = new Intent(context, LinuxDoSessionActivity.class);
        intent.putExtra(EXTRA_LOGIN_ONLY, loginOnly);
        intent.putExtra(EXTRA_DOH_URL, LinuxDoDohConfig.currentUrl());
        intent.putExtra(EXTRA_SESSION_HANDOFF, new LinuxDoSessionHandoff(context, openList));
        return intent;
    }

    public static void openNativeList(Context context) {
        openNativeList(context, LinuxDoRepository.Feed.LATEST);
    }

    /** Opens one of the native LINUX DO global streams in the ordinary topic list. */
    public static void openNativeList(Context context, LinuxDoRepository.Feed feed) {
        TopicListParam param = new TopicListParam();
        param.source = ContentSource.LINUX_DO;
        param.fid = LinuxDoConstants.BOARD_FID;
        LinuxDoRepository.Feed safeFeed = feed == null
                ? LinuxDoRepository.Feed.LATEST : feed;
        param.linuxDoFeed = safeFeed.code();
        // The aggregate board is the LinuxDo home surface itself; “最新” is its
        // default ordering, not a second top-level board/tab name.
        param.title = LinuxDoConstants.BOARD_NAME;
        Intent intent = new Intent(context, TopicListActivity.class);
        intent.putExtra(ParamKey.KEY_PARAM, param);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /** Opens the native category/subcategory and stream picker. */
    public static void openCategoryDirectory(Context context) {
        LinuxDoHubActivity.open(context, LinuxDoHubActivity.MODE_CATEGORIES, null);
    }

    public static void openCategoryDirectory(
            Context context, int parentId, String name, String slug) {
        LinuxDoHubActivity.openCategoryDirectory(context, parentId, name, slug);
    }

    /** Opens a Discourse category in the same native topic-list surface. */
    public static void openCategory(Context context, int categoryId, String name, String slug) {
        openCategory(context, categoryId, name, slug, LinuxDoRepository.Feed.LATEST);
    }

    /** Opens a category using one of the native Discourse streams. */
    public static void openCategory(
            Context context, int categoryId, String name, String slug,
            LinuxDoRepository.Feed feed) {
        if (categoryId <= 0) return;
        TopicListParam param = new TopicListParam();
        param.source = ContentSource.LINUX_DO;
        param.fid = categoryId;
        param.linuxDoCategoryId = categoryId;
        param.linuxDoCategorySlug = slug;
        param.linuxDoFeed = (feed == null ? LinuxDoRepository.Feed.LATEST : feed).code();
        param.title = name;
        Intent intent = new Intent(context, TopicListActivity.class);
        intent.putExtra(ParamKey.KEY_PARAM, param);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /**
     * Opens the board/category that owns an article.  Keeping this routing in one
     * place prevents an article opened from history, search, or a mixed feed from
     * accidentally falling back to the NGA list when its source is LinuxDo.
     */
    public static void openTopicBoard(
            Context context, int source, int boardId, String name, String slug) {
        if (context == null) return;
        if (source == ContentSource.LINUX_DO) {
            if (boardId > 0) {
                openCategory(context, boardId, name, slug);
            } else {
                openNativeList(context);
            }
            return;
        }
        if (boardId <= 0) return;
        TopicListParam param = new TopicListParam();
        param.fid = boardId;
        param.title = name;
        param.source = ContentSource.NGA;
        Intent intent = new Intent(context, TopicListActivity.class);
        intent.putExtra(ParamKey.KEY_PARAM, param);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openTopic(
            Context context, int topicId, String title, int postNumber) {
        if (topicId <= 0) return;
        ArticleListParam param = new ArticleListParam();
        param.source = ContentSource.LINUX_DO;
        param.tid = topicId;
        param.title = title;
        param.page = Math.max(1, (Math.max(1, postNumber) - 1) / 20 + 1);
        param.targetFloor = Math.max(0, postNumber - 1);
        Intent intent = new Intent(context, ArticleListActivity.class);
        intent.putExtra(ParamKey.KEY_PARAM, param);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openSearch(Context context) {
        LinuxDoHubActivity.open(context, LinuxDoHubActivity.MODE_SEARCH, null);
    }

    public static void openSettings(Context context) {
        Intent intent = new Intent(context, LauncherSubActivity.class);
        intent.putExtra("fragment", sp.phone.ui.fragment.LinuxDoSettingsFragment.class.getName());
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /** Opens the local LinuxDo title/tag filter directly from the board overflow. */
    public static void openFilterSettings(Context context) {
        Intent intent = new Intent(context, LauncherSubActivity.class);
        intent.putExtra("fragment", sp.phone.ui.fragment.LinuxDoFilterFragment.class.getName());
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /** Edits the LinuxDo-only DoH endpoint without opening a nested preference screen. */
    public static void editDoh(Context context) {
        if (context == null) return;
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(LinuxDoDohConfig.currentUrl());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context)
                .setTitle("LINUX DO 专用 DNS over HTTPS")
                .setMessage("仅 LinuxDo 请求使用；请输入完整的 HTTPS 地址")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String url = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (!LinuxDoDohConfig.isValid(url)) {
                        ToastUtils.error("请输入完整的 HTTPS DoH 地址");
                        return;
                    }
                    PreferenceUtils.putData(PreferenceKey.KEY_LINUX_DO_DOH_URL, url);
                    LinuxDoHttpSession.getInstance().invalidateClient();
                    ToastUtils.show("LINUX DO DNS 已更新");
                })
                .show();
    }

    public static void openNotifications(Context context) {
        LinuxDoHubActivity.open(context, LinuxDoHubActivity.MODE_NOTIFICATIONS, null);
    }

    public static void openProfile(Context context, String username) {
        LinuxDoHubActivity.open(context, LinuxDoHubActivity.MODE_PROFILE, username);
    }

    public static void openTrustLevels(Context context) {
        LinuxDoHubActivity.open(context, LinuxDoHubActivity.MODE_TRUST, null);
    }

    /**
     * Bounded fallback for notification targets that do not have a native
     * screen yet. Only exact-origin HTTPS links are handed to the browser.
     */
    public static void openSafeWeb(Context context, String target) {
        if (context == null || target == null || target.trim().isEmpty()) return;
        Uri parsed = Uri.parse(target.startsWith("/")
                ? LinuxDoConstants.ORIGIN + target : target);
        if (!"https".equalsIgnoreCase(parsed.getScheme())
                || !"linux.do".equalsIgnoreCase(parsed.getHost())) return;
        openUrlInBrowser(context, parsed.toString());
    }
}
