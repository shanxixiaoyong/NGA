package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.http.OnHttpCallBack;
import io.reactivex.schedulers.Schedulers;
import sp.phone.common.PhoneConfiguration;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.theme.ThemeManager;

/** Discourse JSON boundary. UI/model callers never parse linux.do payloads directly. */
public final class LinuxDoRepository {

    public interface MutationCallback {
        void onSuccess();
        void onError(String message);
    }

    private static final int PAGE_SIZE = 20;
    private static final Pattern IMAGE_SRC = Pattern.compile(
            "(?i)<img\\b[^>]*\\bsrc=['\"]([^'\"]+)['\"]");
    private static final LinuxDoRepository INSTANCE = new LinuxDoRepository();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, String> mCategories = new HashMap<>();
    private final LinkedHashMap<Integer, TopicSnapshot> mTopicCache =
            new LinkedHashMap<Integer, TopicSnapshot>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, TopicSnapshot> eldest) {
                    return size() > 8;
                }
            };
    private final Map<Integer, List<ArticleWaiter>> mTopicInFlight = new HashMap<>();

    private LinuxDoRepository() {
    }

    public static LinuxDoRepository getInstance() {
        return INSTANCE;
    }

    public void createReply(
            int topicId,
            Integer replyToPostNumber,
            String raw,
            MutationCallback callback) {
        if (topicId <= 0 || TextUtils.isEmpty(raw) || callback == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("topic_id", String.valueOf(topicId));
        fields.put("raw", raw.trim());
        if (replyToPostNumber != null && replyToPostNumber > 0) {
            fields.put("reply_to_post_number", String.valueOf(replyToPostNumber));
        }
        postMutation("/posts.json", fields, topicId, callback);
    }

    public void createBoost(
            int topicId,
            int postId,
            String raw,
            MutationCallback callback) {
        if (topicId <= 0 || postId <= 0 || TextUtils.isEmpty(raw) || callback == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("raw", raw.trim());
        postMutation("/discourse-boosts/posts/" + postId + "/boosts",
                fields, topicId, callback);
    }

    public void likePost(int topicId, int postId, MutationCallback callback) {
        if (topicId <= 0 || postId <= 0 || callback == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", String.valueOf(postId));
        fields.put("post_action_type_id", "2");
        postMutation("/post_actions", fields, topicId, callback);
    }

    public void invalidateTopic(int topicId) {
        synchronized (mTopicCache) {
            mTopicCache.remove(topicId);
        }
    }

    private void postMutation(
            String path,
            Map<String, String> fields,
            int topicId,
            MutationCallback callback) {
        LinuxDoHttpSession.getInstance().post(path, fields, new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                invalidateTopic(topicId);
                callback.onSuccess();
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                        || failure == LinuxDoWebSession.Failure.SESSION_UNAVAILABLE
                        ? "LINUX DO 登录已失效，请重新登录"
                        : "操作失败，请稍后重试");
            }
        });
    }

    public void loadTopics(int appPage, OnHttpCallBack<TopicListInfo> callback) {
        if (mCategories.isEmpty()) {
            fetch("/categories.json", new SessionCallback() {
                @Override
                public void onSuccess(String json) {
                    parseOffMain(() -> {
                                parseCategories(json);
                                return Boolean.TRUE;
                            }, ignored -> loadTopicPage(appPage, callback), callback);
                }

                @Override
                public void onFailure(LinuxDoWebSession.Failure failure) {
                    callback.onError(messageFor(failure));
                }
            });
        } else {
            loadTopicPage(appPage, callback);
        }
    }

    private void loadTopicPage(int appPage, OnHttpCallBack<TopicListInfo> callback) {
        int page = Math.max(0, appPage - 1);
        fetch("/latest.json?page=" + page, new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                parseOffMain(() -> parseTopics(json), callback::onSuccess, callback);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void loadArticle(int topicId, int appPage, OnHttpCallBack<ThreadData> callback) {
        TopicSnapshot cached;
        synchronized (mTopicCache) {
            cached = mTopicCache.get(topicId);
        }
        if (cached != null) {
            loadArticlePage(cached, appPage, callback);
            return;
        }
        synchronized (mTopicInFlight) {
            List<ArticleWaiter> waiters = mTopicInFlight.get(topicId);
            if (waiters != null) {
                waiters.add(new ArticleWaiter(appPage, callback));
                return;
            }
            waiters = new ArrayList<>();
            waiters.add(new ArticleWaiter(appPage, callback));
            mTopicInFlight.put(topicId, waiters);
        }
        fetch("/t/" + topicId + ".json", new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                parseOffMain(() -> parseTopicSnapshot(json), snapshot -> {
                    synchronized (mTopicCache) {
                        mTopicCache.put(topicId, snapshot);
                    }
                    List<ArticleWaiter> waiters;
                    synchronized (mTopicInFlight) {
                        waiters = mTopicInFlight.remove(topicId);
                    }
                    if (waiters != null) {
                        for (ArticleWaiter waiter : waiters) {
                            loadArticlePage(snapshot, waiter.page, waiter.callback);
                        }
                    }
                }, new OnHttpCallBack<Object>() {
                    @Override
                    public void onError(String text) {
                        failTopicWaiters(topicId, text);
                    }
                });
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                failTopicWaiters(topicId, messageFor(failure));
            }
        });
    }

    public void loadUserLocation(String username, OnHttpCallBack<String> callback) {
        if (TextUtils.isEmpty(username)) {
            callback.onSuccess(null);
            return;
        }
        final String encoded;
        try {
            encoded = URLEncoder.encode(username, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            callback.onError("LINUX DO 用户名无法编码");
            return;
        }
        fetch("/u/" + encoded + ".json", new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                parseOffMain(() -> {
                    JSONObject user = JSON.parseObject(json).getJSONObject("user");
                    return user == null ? null : trimToNull(user.getString("location"));
                }, callback::onSuccess, callback);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    private void loadArticlePage(
            TopicSnapshot snapshot, int appPage, OnHttpCallBack<ThreadData> callback) {
        int start = Math.max(0, appPage - 1) * PAGE_SIZE;
        if (start >= snapshot.stream.size()) {
            callback.onError("该页不存在");
            return;
        }
        int end = Math.min(snapshot.stream.size(), start + PAGE_SIZE);
        List<Integer> ids = snapshot.stream.subList(start, end);
        List<Integer> missing = new ArrayList<>();
        synchronized (snapshot) {
            for (Integer id : ids) if (!snapshot.posts.containsKey(id)) missing.add(id);
        }
        if (missing.isEmpty()) {
            parseOffMain(() -> buildThreadData(snapshot, ids), callback::onSuccess, callback);
            return;
        }
        StringBuilder path = new StringBuilder("/t/").append(snapshot.topicId)
                .append("/posts.json?");
        for (int index = 0; index < missing.size(); index++) {
            if (index > 0) path.append('&');
            path.append("post_ids%5B%5D=").append(missing.get(index));
        }
        fetch(path.toString(), new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                parseOffMain(() -> {
                    JSONObject root = JSON.parseObject(json);
                    JSONObject stream = root.getJSONObject("post_stream");
                    JSONArray posts = stream == null
                            ? root.getJSONArray("posts") : stream.getJSONArray("posts");
                    if (posts == null) throw new IllegalArgumentException("Missing posts");
                    synchronized (snapshot) {
                        for (int i = 0; i < posts.size(); i++) {
                            JSONObject post = posts.getJSONObject(i);
                            snapshot.posts.put(post.getIntValue("id"), post);
                        }
                    }
                    return buildThreadData(snapshot, ids);
                }, callback::onSuccess, callback);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    private void parseCategories(String json) {
        Map<Integer, String> categories = LinuxDoTopicPayloadParser.parseCategories(json);
        synchronized (mCategories) {
            mCategories.clear();
            mCategories.putAll(categories);
        }
    }

    private TopicListInfo parseTopics(String json) {
        Map<Integer, String> categories;
        synchronized (mCategories) {
            categories = new HashMap<>(mCategories);
        }
        List<LinuxDoTopicPayloadParser.TopicRecord> topics =
                LinuxDoTopicPayloadParser.parseTopics(json, categories);
        TopicListInfo result = new TopicListInfo();
        result.setName(LinuxDoConstants.BOARD_NAME);
        result.curTime = (int) (System.currentTimeMillis() / 1000L);
        for (LinuxDoTopicPayloadParser.TopicRecord topic : topics) {
            ThreadPageInfo row = new ThreadPageInfo();
            row.setTid(topic.id);
            row.setFid(topic.categoryId);
            row.setBoard(topic.categoryName == null ? LinuxDoConstants.BOARD_NAME : topic.categoryName);
            row.setTags(topic.tags);
            row.setSubject(topic.title);
            row.setReplies(topic.replyCount);
            row.setPostDate(topic.createdAt);
            row.setLastPost(topic.lastPostedAt);
            // This is the topic-list page, not the article page. New topics always open at
            // the first post; the reader's source-scoped progress policy performs restores.
            row.setPage(1);
            row.setAuthorId(topic.authorId);
            row.setAuthor(topic.author);
            row.setLastPoster(topic.lastPoster);
            result.addThreadPage(row);
        }
        return result;
    }

    private TopicSnapshot parseTopicSnapshot(String json) {
        JSONObject root = JSON.parseObject(json);
        int topicId = root.getIntValue("id");
        JSONObject postStream = root.getJSONObject("post_stream");
        JSONArray streamJson = postStream == null ? null : postStream.getJSONArray("stream");
        if (topicId <= 0 || streamJson == null) throw new IllegalArgumentException("Missing stream");
        TopicSnapshot snapshot = new TopicSnapshot();
        snapshot.topicId = topicId;
        snapshot.title = root.getString("title");
        snapshot.categoryId = root.getIntValue("category_id");
        parseTopicBadgeNames(root, snapshot);
        for (int index = 0; index < streamJson.size(); index++) {
            snapshot.stream.add(streamJson.getIntValue(index));
        }
        JSONArray posts = postStream.getJSONArray("posts");
        if (posts != null) {
            for (int index = 0; index < posts.size(); index++) {
                JSONObject post = posts.getJSONObject(index);
                snapshot.posts.put(post.getIntValue("id"), post);
            }
        }
        return snapshot;
    }

    private ThreadData buildThreadData(TopicSnapshot snapshot, List<Integer> ids) {
        List<ThreadRowInfo> rows = new ArrayList<>();
        synchronized (snapshot) {
            for (Integer id : ids) {
                JSONObject post = snapshot.posts.get(id);
                if (post == null) throw new IllegalArgumentException("Missing requested post");
                rows.add(mapPost(snapshot, post));
            }
        }
        ThreadPageInfo thread = new ThreadPageInfo();
        thread.setTid(snapshot.topicId);
        thread.setFid(snapshot.categoryId);
        thread.setBoard(categoryName(snapshot.categoryId));
        thread.setSubject(snapshot.title);
        thread.setReplies(Math.max(0, snapshot.stream.size() - 1));
        ThreadData data = new ThreadData();
        data.setThreadInfo(thread);
        data.setRowList(rows);
        data.set__ROWS(snapshot.stream.size());
        data.setRowNum(rows.size());
        return data;
    }

    private ThreadRowInfo mapPost(TopicSnapshot snapshot, JSONObject post) {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setTid(snapshot.topicId);
        row.fid = snapshot.categoryId;
        row.setPid(post.getIntValue("id"));
        row.setLou(Math.max(0, post.getIntValue("post_number") - 1));
        row.setAuthorid(post.getIntValue("user_id"));
        row.setAuthor(firstNonBlank(post.getString("username"), post.getString("name")));
        row.setSubject(row.getLou() == 0 ? snapshot.title : null);
        row.setPostdate(formatIso(post.getString("created_at")));
        String cooked = sanitizeCooked(post.getString("cooked"));
        cooked += renderBoosts(post.getJSONArray("boosts"));
        row.setContent(cooked);
        row.setFormattedHtmlData(wrapCooked(row.getContent()));
        collectImageUrls(row, cooked);
        row.setScore(LinuxDoPostPayloadParser.resolveLikeCount(post));
        row.setMemberGroup(buildLinuxDoIdentity(snapshot, post));
        row.setPostCount("-");
        String avatar = post.getString("avatar_template");
        if (!TextUtils.isEmpty(avatar)) {
            avatar = avatar.replace("{size}", "96");
            if (avatar.startsWith("//")) avatar = "https:" + avatar;
            else if (avatar.startsWith("/")) avatar = LinuxDoConstants.ORIGIN + avatar;
            row.setJs_escap_avatar(avatar);
        }
        return row;
    }

    private String categoryName(int id) {
        synchronized (mCategories) {
            String name = mCategories.get(id);
            return TextUtils.isEmpty(name) ? LinuxDoConstants.BOARD_NAME : name;
        }
    }

    private static String formatIso(String iso) {
        try {
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault()).format(Instant.parse(iso));
        } catch (Exception ignored) {
            return iso == null ? "" : iso;
        }
    }

    private static String sanitizeCooked(String cooked) {
        if (cooked == null) return "";
        String clean = cooked
                .replaceAll("(?is)<(script|form|iframe|object|embed)[^>]*>.*?</\\1>", "")
                .replaceAll("(?is)<(script|form|iframe|object|embed)[^>]*/?>", "")
                .replaceAll("(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1", "")
                .replaceAll("(?i)(href|src)\\s*=\\s*(['\"])javascript:[^'\"]*\\2", "$1=$2#$2");
        clean = clean
                .replaceAll("(?is)<div\\b[^>]*class=['\"][^'\"]*\\bmeta\\b[^'\"]*['\"][^>]*>.*?</div>", "")
                .replaceAll("(?is)<span\\b[^>]*class=['\"][^'\"]*(?:filename|informations|image-source-link)[^'\"]*['\"][^>]*>.*?</span>", "")
                .replaceAll("(?i)<img\\b(?![^>]*\\bloading=)", "<img loading=\"eager\" decoding=\"async\"");
        clean = clean.replace("href=\"//", "href=\"https://")
                .replace("src=\"//", "src=\"https://")
                .replace("href='//", "href='https://")
                .replace("src='//", "src='https://")
                .replace("href=\"/", "href=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("src=\"/", "src=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("href='/", "href='" + LinuxDoConstants.ORIGIN + "/")
                .replace("src='/", "src='" + LinuxDoConstants.ORIGIN + "/");
        return clean;
    }

    private static String renderBoosts(JSONArray boosts) {
        if (boosts == null || boosts.isEmpty()) return "";
        StringBuilder html = new StringBuilder("<div class='linuxdo-boosts'>");
        for (int index = 0; index < boosts.size() && index < 80; index++) {
            JSONObject boost = boosts.getJSONObject(index);
            if (boost == null) continue;
            JSONObject user = boost.getJSONObject("user");
            String avatar = user == null
                    ? boost.getString("avatar_template") : user.getString("avatar_template");
            if (!TextUtils.isEmpty(avatar)) {
                avatar = avatar.replace("{size}", "48");
                if (avatar.startsWith("//")) avatar = "https:" + avatar;
                else if (avatar.startsWith("/")) avatar = LinuxDoConstants.ORIGIN + avatar;
            }
            String content = sanitizeCooked(boost.getString("cooked"));
            if (TextUtils.isEmpty(content)) continue;
            html.append("<div class='linuxdo-boost'>");
            if (!TextUtils.isEmpty(avatar)) {
                html.append("<img class='linuxdo-boost-avatar' src='")
                        .append(escapeAttribute(LinuxDoAvatarProxy.wrap(avatar))).append("'>");
            }
            html.append(content).append("</div>");
        }
        return html.append("</div>").toString();
    }

    private static void collectImageUrls(ThreadRowInfo row, String html) {
        Matcher matcher = IMAGE_SRC.matcher(html == null ? "" : html);
        while (matcher.find() && row.getImageUrls().size() < 200) {
            row.addImageUrl(matcher.group(1));
        }
    }

    private static String buildLinuxDoIdentity(TopicSnapshot snapshot, JSONObject post) {
        Set<String> details = new LinkedHashSet<>();
        Object trust = post.get("trust_level");
        if (trust != null) details.add("信任等级 " + post.getIntValue("trust_level"));
        addNonBlank(details, post.getString("user_title"));
        addNonBlank(details, post.getString("primary_group_name"));
        JSONArray granted = post.getJSONArray("badges_granted");
        if (granted != null) {
            for (int index = 0; index < granted.size() && details.size() < 6; index++) {
                JSONObject badge = granted.getJSONObject(index);
                if (badge != null) addNonBlank(details, badge.getString("name"));
            }
        }
        List<String> topicBadges = snapshot.badgesByUser.get(post.getIntValue("user_id"));
        if (topicBadges != null) {
            for (String badge : topicBadges) {
                if (details.size() >= 6) break;
                addNonBlank(details, badge);
            }
        }
        return details.isEmpty() ? "LINUX DO" : TextUtils.join(" · ", details);
    }

    private static void parseTopicBadgeNames(JSONObject root, TopicSnapshot snapshot) {
        JSONObject container = root.getJSONObject("user_badges");
        if (container == null) return;
        Map<Integer, String> badgeNames = new HashMap<>();
        Object rawBadges = container.get("badges");
        if (rawBadges instanceof JSONObject) {
            for (Map.Entry<String, Object> entry : ((JSONObject) rawBadges).entrySet()) {
                if (!(entry.getValue() instanceof JSONObject)) continue;
                try {
                    badgeNames.put(Integer.parseInt(entry.getKey()),
                            ((JSONObject) entry.getValue()).getString("name"));
                } catch (NumberFormatException ignored) { }
            }
        } else if (rawBadges instanceof JSONArray) {
            JSONArray badges = (JSONArray) rawBadges;
            for (int index = 0; index < badges.size(); index++) {
                JSONObject badge = badges.getJSONObject(index);
                if (badge != null) badgeNames.put(
                        badge.getIntValue("id"), badge.getString("name"));
            }
        }
        Object rawUsers = container.get("users");
        if (rawUsers instanceof JSONObject) {
            for (Map.Entry<String, Object> entry : ((JSONObject) rawUsers).entrySet()) {
                try {
                    addUserBadges(snapshot, Integer.parseInt(entry.getKey()),
                            entry.getValue(), badgeNames);
                } catch (NumberFormatException ignored) { }
            }
        } else if (rawUsers instanceof JSONArray) {
            JSONArray users = (JSONArray) rawUsers;
            for (int index = 0; index < users.size(); index++) {
                JSONObject user = users.getJSONObject(index);
                if (user != null) addUserBadges(snapshot, user.getIntValue("id"), user, badgeNames);
            }
        }
    }

    private static void addUserBadges(
            TopicSnapshot snapshot, int userId, Object rawUser, Map<Integer, String> names) {
        if (!(rawUser instanceof JSONObject) || userId <= 0) return;
        JSONArray ids = ((JSONObject) rawUser).getJSONArray("badge_ids");
        if (ids == null) return;
        List<String> badges = new ArrayList<>();
        for (int index = 0; index < ids.size() && badges.size() < 5; index++) {
            String name = names.get(ids.getIntValue(index));
            if (!TextUtils.isEmpty(name)) badges.add(name);
        }
        if (!badges.isEmpty()) snapshot.badgesByUser.put(userId, badges);
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (!TextUtils.isEmpty(value)) values.add(value.trim());
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeText(value).replace("'", "&#39;").replace("\"", "&quot;");
    }

    private static String wrapCooked(String cooked) {
        int textSize = PhoneConfiguration.getInstance().getTopicContentSize();
        return LinuxDoPostHtml.wrap(
                cooked, textSize, ThemeManager.getInstance().isNightMode());
    }

    private void failTopicWaiters(int topicId, String message) {
        List<ArticleWaiter> waiters;
        synchronized (mTopicInFlight) {
            waiters = mTopicInFlight.remove(topicId);
        }
        if (waiters != null) for (ArticleWaiter waiter : waiters) waiter.callback.onError(message);
    }

    private static void fetch(String path, SessionCallback callback) {
        // Native reads go directly through the isolated LINUX DO transport. This avoids both
        // the system DNS used by WebView and a visible login page for anonymous read access.
        try {
            LinuxDoHttpSession.getInstance().fetch(path, callback);
        } catch (RuntimeException | LinkageError error) {
            callback.onFailure(LinuxDoWebSession.Failure.HTTP_OR_PROTOCOL);
        }
    }

    private <T> void parseOffMain(
            Parser<T> parser, Success<T> success, OnHttpCallBack<?> callback) {
        Schedulers.computation().scheduleDirect(() -> {
            try {
                T value = parser.parse();
                mMainHandler.post(() -> success.accept(value));
            } catch (Throwable error) {
                mMainHandler.post(() -> callback.onError("LINUX DO 数据格式暂时无法解析"));
            }
        });
    }

    private static String messageFor(LinuxDoWebSession.Failure failure) {
        return failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                || failure == LinuxDoWebSession.Failure.SESSION_UNAVAILABLE
                ? "LINUX DO 访问被验证拦截，请稍后重试或检查网络"
                : "LINUX DO 加载失败，请稍后重试";
    }

    private static String firstNonBlank(String first, String second) {
        return TextUtils.isEmpty(first) ? (second == null ? "" : second) : first;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private abstract static class SessionCallback implements LinuxDoWebSession.Callback {
    }

    private interface Parser<T> {
        T parse() throws Exception;
    }

    private interface Success<T> {
        void accept(T value);
    }

    private static final class TopicSnapshot {
        int topicId;
        int categoryId;
        String title;
        final List<Integer> stream = new ArrayList<>();
        final Map<Integer, JSONObject> posts = new HashMap<>();
        final Map<Integer, List<String>> badgesByUser = new HashMap<>();
    }

    private static final class ArticleWaiter {
        final int page;
        final OnHttpCallBack<ThreadData> callback;

        ArticleWaiter(int page, OnHttpCallBack<ThreadData> callback) {
            this.page = page;
            this.callback = callback;
        }
    }
}
