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
import java.util.List;
import java.util.Map;

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

    private static final int PAGE_SIZE = 20;
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
        row.setContent(sanitizeCooked(post.getString("cooked")));
        row.setFormattedHtmlData(wrapCooked(row.getContent()));
        row.setScore(LinuxDoPostPayloadParser.resolveLikeCount(post));
        row.setMemberGroup("信任等级 " + post.getIntValue("trust_level"));
        row.setPostCount("-");
        String avatar = post.getString("avatar_template");
        if (!TextUtils.isEmpty(avatar)) {
            avatar = avatar.replace("{size}", "96");
            if (avatar.startsWith("/")) avatar = LinuxDoConstants.ORIGIN + avatar;
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
        clean = clean.replace("href=\"/", "href=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("src=\"/", "src=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("href='/", "href='" + LinuxDoConstants.ORIGIN + "/")
                .replace("src='/", "src='" + LinuxDoConstants.ORIGIN + "/");
        return clean;
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
