package sp.phone.linuxdo;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Html;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.net.URLEncoder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.base.util.ContextUtils;
import io.reactivex.schedulers.Schedulers;
import sp.phone.common.PhoneConfiguration;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.PostReaction;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.theme.ThemeManager;

/** Discourse JSON boundary. UI/model callers never parse linux.do payloads directly. */
public final class LinuxDoRepository {

    /** Receives a readable first floor while the remainder of page one is mapped off-main. */
    public interface ProgressiveArticleCallback extends OnHttpCallBack<ThreadData> {
        void onFirstFloor(ThreadData preview);
    }

    public enum SearchOrder {
        RELEVANCE,
        TOPIC_CREATED,
        POST_CREATED
    }

    /** Result family requested by the native LINUX DO search surface. */
    public enum SearchScope {
        ALL,
        TOPICS,
        POSTS,
        USERS,
        CATEGORIES
    }

    /** Native Discourse streams exposed by the compact board-discovery screen. */
    public enum Feed {
        LATEST(0, "最新"),
        NEW(1, "新帖"),
        UNREAD(2, "未读"),
        TOP(3, "热门");

        private final int code;
        private final String title;

        Feed(int code, String title) {
            this.code = code;
            this.title = title;
        }

        public int code() {
            return code;
        }

        public String title() {
            return title;
        }

        public static Feed fromCode(int code) {
            for (Feed feed : values()) if (feed.code == code) return feed;
            return LATEST;
        }
    }

    public interface MutationCallback {
        void onSuccess();
        void onError(String message);
    }

    private static final int PAGE_SIZE = 20;
    private static final int MAX_API_PAGE = 10_000;
    private static final int MAX_API_OFFSET = 10_000;
    private static final int RENDERED_PAGE_CACHE_SIZE = 2;
    private static final int MAX_CACHED_HTML_CHARS = 1_500_000;
    private static final String PERF_TAG = "LinuxDoPerf";
    private static final String CATEGORY_CACHE_PREFS = "linuxdo_native_cache";
    private static final String CATEGORY_CACHE_KEY = "site_json_v1";
    private static final long CATEGORY_REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final Pattern IMAGE_SRC = Pattern.compile(
            "(?i)<img\\b[^>]*\\bsrc=['\"]([^'\"]+)['\"]");
    private static final Pattern DANGEROUS_CONTAINER = Pattern.compile(
            "(?is)<(script|form|iframe|object|embed)[^>]*>.*?</\\1>");
    private static final Pattern DANGEROUS_TAG = Pattern.compile(
            "(?is)<(script|form|iframe|object|embed)[^>]*/?>");
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile(
            "(?i)(href|src)\\s*=\\s*(['\"])javascript:[^'\"]*\\2");
    private static final Pattern META_BLOCK = Pattern.compile(
            "(?is)<div\\b[^>]*class=['\"][^'\"]*\\bmeta\\b[^'\"]*['\"][^>]*>.*?</div>");
    private static final Pattern IMAGE_META = Pattern.compile(
            "(?is)<span\\b[^>]*class=['\"][^'\"]*(?:filename|informations|image-source-link)[^'\"]*['\"][^>]*>.*?</span>");
    private static final Pattern IMAGE_WITHOUT_LOADING = Pattern.compile(
            "(?i)<img\\b(?![^>]*\\bloading=)");
    private static final Pattern VIDEO_LINK = Pattern.compile(
            "(?is)<a\\b[^>]*\\bhref=['\"](https://[^'\"]+\\.(?:mp4|webm|mov)(?:\\?[^'\"]*)?)['\"][^>]*>.*?</a>");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final LinuxDoRepository INSTANCE = new LinuxDoRepository();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> mCategories =
            new HashMap<>();
    private final LinkedHashMap<Integer, TopicSnapshot> mTopicCache =
            new LinkedHashMap<Integer, TopicSnapshot>(8, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, TopicSnapshot> eldest) {
                    return size() > 8;
                }
            };
    private final Map<Integer, List<ArticleWaiter>> mTopicInFlight = new HashMap<>();
    private final LinkedHashMap<Integer, ThreadData> mFirstFloorCache =
            new LinkedHashMap<Integer, ThreadData>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, ThreadData> eldest) {
                    return size() > 16;
                }
            };
    private long mLastCategoryRefreshAt;

    private LinuxDoRepository() {
    }

    public static LinuxDoRepository getInstance() {
        return INSTANCE;
    }

    /** Exact site palette; shortened topic projections must never remove the custom pair. */
    public List<String> getEnabledReactions() {
        return LinuxDoReactionAssets.ids();
    }

    public void createReply(
            int topicId,
            Integer replyToPostNumber,
            String raw,
            MutationCallback callback) {
        if (callback == null) return;
        String content = raw == null ? "" : raw.trim();
        if (topicId <= 0 || content.isEmpty()) {
            callback.onError("回复内容不能为空");
            return;
        }
        if (content.codePointCount(0, content.length()) < 20) {
            callback.onError("正式回复至少需要20字");
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("topic_id", String.valueOf(topicId));
        fields.put("raw", content);
        if (replyToPostNumber != null && replyToPostNumber > 0) {
            fields.put("reply_to_post_number", String.valueOf(replyToPostNumber));
        }
        postMutation("/posts.json", fields, topicId, callback);
    }

    /** Creates or updates the current account's native Discourse poll vote. */
    public void votePoll(
            int topicId,
            int postId,
            String pollName,
            List<String> optionIds,
            MutationCallback callback) {
        if (callback == null) return;
        if (topicId <= 0 || postId <= 0 || TextUtils.isEmpty(pollName)
                || optionIds == null || optionIds.isEmpty()) {
            callback.onError("请选择投票选项");
            return;
        }
        List<String> safeOptions = new ArrayList<>();
        for (String optionId : optionIds) {
            String value = optionId == null ? "" : optionId.trim();
            if (!value.isEmpty() && value.length() <= 128 && safeOptions.size() < 40) {
                safeOptions.add(value);
            }
        }
        if (safeOptions.isEmpty()) {
            callback.onError("请选择投票选项");
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("post_id", String.valueOf(postId));
        fields.put("poll_name", pollName.trim());
        LinuxDoHttpSession.getInstance().putRepeated(
                "/polls/vote", fields, "options[]", safeOptions,
                new LinuxDoWebSession.Callback() {
                    @Override
                    public void onSuccess(String json) {
                        invalidateTopic(topicId);
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(LinuxDoWebSession.Failure failure) {
                        callback.onError(messageFor(failure));
                    }
                });
    }

    public void createBoost(
            int topicId,
            int postId,
            String raw,
            MutationCallback callback) {
        if (topicId <= 0 || postId <= 0 || TextUtils.isEmpty(raw) || callback == null) return;
        String content = raw.trim();
        if (content.codePointCount(0, content.length()) > 16) {
            callback.onError("Boost 最多16个字");
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("raw", content);
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

    /** Removes the core Discourse like from one post without touching custom reactions. */
    public void unlikePost(int topicId, int postId, MutationCallback callback) {
        if (topicId <= 0 || postId <= 0 || callback == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("post_action_type_id", "2");
        deleteMutation("/post_actions/" + postId + ".json", fields, topicId, callback);
    }

    /** Toggles one of Discourse's configured custom emoji reactions for a post. */
    public void toggleReaction(
            int topicId, int postId, String reaction, MutationCallback callback) {
        if (topicId <= 0 || postId <= 0 || callback == null
                || !LinuxDoPostPayloadParser.isSafeReactionId(reaction)) {
            if (callback != null) callback.onError("该表情暂不可用");
            return;
        }
        String encoded = reaction.trim();
        LinuxDoHttpSession.getInstance().put(
                "/discourse-reactions/posts/" + postId
                        + "/custom-reactions/" + encoded + "/toggle.json",
                java.util.Collections.emptyMap(),
                new LinuxDoWebSession.Callback() {
                    @Override
                    public void onSuccess(String json) {
                        invalidateTopic(topicId);
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(LinuxDoWebSession.Failure failure) {
                        callback.onError(messageFor(failure));
                    }
                });
    }

    public void invalidateTopic(int topicId) {
        synchronized (mTopicCache) {
            mTopicCache.remove(topicId);
        }
        synchronized (mFirstFloorCache) {
            mFirstFloorCache.remove(topicId);
        }
    }

    /** Drops only a stale article snapshot when a list row reports newer reply metadata. */
    public void invalidateTopicIfBehind(int topicId, int expectedPostCount) {
        if (topicId <= 0 || expectedPostCount <= 0) return;
        boolean stale = false;
        synchronized (mTopicCache) {
            TopicSnapshot snapshot = mTopicCache.get(topicId);
            if (snapshot != null && snapshot.stream.size() < expectedPostCount) {
                mTopicCache.remove(topicId);
                stale = true;
            }
        }
        if (stale) {
            synchronized (mFirstFloorCache) {
                mFirstFloorCache.remove(topicId);
            }
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

    private void deleteMutation(
            String path,
            Map<String, String> fields,
            int topicId,
            MutationCallback callback) {
        LinuxDoHttpSession.getInstance().delete(path, fields, new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                invalidateTopic(topicId);
                callback.onSuccess();
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void loadTopics(int appPage, OnHttpCallBack<TopicListInfo> callback) {
        loadTopics(appPage, null, 0, Feed.LATEST, callback);
    }

    /** Loads the global feed or one native LINUX DO category without WebView navigation. */
    public void loadTopics(
            int appPage,
            String categorySlug,
            int categoryId,
            OnHttpCallBack<TopicListInfo> callback) {
        loadTopics(appPage, categorySlug, categoryId, Feed.LATEST, callback);
    }

    /** Loads a global stream or one native LINUX DO category. */
    public void loadTopics(
            int appPage,
            String categorySlug,
            int categoryId,
            Feed feed,
            OnHttpCallBack<TopicListInfo> callback) {
        Feed safeFeed = feed == null ? Feed.LATEST : feed;
        LinuxDoHttpSession.getInstance().warmup();
        synchronized (mCategories) {
            if (!mCategories.isEmpty()) {
                loadTopicPage(resolvedTopicListPath(
                        appPage, categorySlug, categoryId, safeFeed), callback);
                refreshCategoriesInBackground();
                return;
            }
        }
        String cachedCategories = readCachedCategories();
        if (!TextUtils.isEmpty(cachedCategories)) {
            parseOffMain(() -> {
                        parseCategories(cachedCategories);
                        return Boolean.TRUE;
                    }, ignored -> {
                        // Resolve the canonical slug only after the cached category table has
                        // actually been parsed.  Building /c/{id}.json before this point makes
                        // Discourse redirect to /c/{slug}/{id}.json; the transport correctly
                        // rejects that redirect and the UI used to mistake it for a login gate.
                        loadTopicPage(resolvedTopicListPath(
                                appPage, categorySlug, categoryId, safeFeed), callback);
                        refreshCategoriesInBackground();
                    }, new OnHttpCallBack<Object>() {
                        @Override public void onError(String text) {
                            // A stale/corrupt cache is optional; fall back to the same
                            // non-blocking cold path instead of failing the board.
                            loadTopicsWithoutCategoryCache(topicListPath(
                                    appPage, categorySlug, categoryId, safeFeed), callback);
                        }
                    });
        } else {
            // The category table is only needed for labels/Lv markers. Do not make the
            // first topic list wait for the much larger /site.json response. The two
            // requests are independent and the state object emits an enriched update
            // when the category response arrives.
            if (categoryId <= 0) {
                loadLatestTopicsWithoutCategoryCache(appPage, safeFeed, callback);
            } else {
                // A category URL needs its canonical slug.  On a true cold start obtain the
                // bounded site metadata first; this is a one-time correctness cost and is then
                // served from the persistent category cache.
                loadColdCategoryTopic(appPage, categorySlug, categoryId, safeFeed, callback);
            }
        }
    }

    private String resolvedTopicListPath(
            int appPage, String requestedSlug, int categoryId, Feed feed) {
        String slug = requestedSlug;
        if (TextUtils.isEmpty(slug) && categoryId > 0) {
            synchronized (mCategories) {
                LinuxDoTopicPayloadParser.CategoryRecord record = mCategories.get(categoryId);
                if (record != null) slug = record.slug;
            }
        }
        return topicListPath(appPage, slug, categoryId, feed);
    }

    private void loadColdCategoryTopic(
            int appPage,
            String categorySlug,
            int categoryId,
            Feed feed,
            OnHttpCallBack<TopicListInfo> callback) {
        fetch("/site.json", new SessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> {
                            parseCategories(json);
                            persistCategories(json);
                            return Boolean.TRUE;
                        }, ignored -> loadTopicPage(resolvedTopicListPath(
                                appPage, categorySlug, categoryId, feed), callback),
                        new OnHttpCallBack<Object>() {
                            @Override public void onError(String text) {
                                loadTopicsWithoutCategoryCache(topicListPath(
                                        appPage, categorySlug, categoryId, feed), callback);
                            }
                        });
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                loadTopicsWithoutCategoryCache(topicListPath(
                        appPage, categorySlug, categoryId, feed), callback);
            }
        });
    }

    /** Cold global-feed path kept explicit so its request remains easy to audit. */
    private void loadLatestTopicsWithoutCategoryCache(
            int appPage, Feed feed, OnHttpCallBack<TopicListInfo> callback) {
        TopicListLoadState state = new TopicListLoadState(callback);
        fetch(globalFeedPath(appPage, feed),
                new SessionCallback() {
                    @Override public void onSuccess(String json) {
                        state.onLatestJson(json);
                    }

                    @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                        state.onLatestFailure(messageFor(failure));
                    }
                });
        fetch("/site.json", new SessionCallback() {
            @Override public void onSuccess(String json) {
                state.onCategoriesJson(json);
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                state.onCategoriesFailure();
            }
        });
    }

    private void loadTopicsWithoutCategoryCache(
            String topicPath, OnHttpCallBack<TopicListInfo> callback) {
        TopicListLoadState state = new TopicListLoadState(callback);
        fetch(topicPath,
                new SessionCallback() {
                    @Override
                    public void onSuccess(String json) {
                        state.onLatestJson(json);
                    }

                    @Override
                    public void onFailure(LinuxDoWebSession.Failure failure) {
                        state.onLatestFailure(messageFor(failure));
                    }
                });
        fetch("/site.json", new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                state.onCategoriesJson(json);
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                // Topic rows remain useful without the optional category enrichment.
                state.onCategoriesFailure();
            }
        });
    }

    private void refreshCategoriesInBackground() {
        long now = SystemClock.elapsedRealtime();
        synchronized (mCategories) {
            if (now - mLastCategoryRefreshAt < CATEGORY_REFRESH_INTERVAL_MS) return;
            mLastCategoryRefreshAt = now;
        }
        fetch("/site.json", new SessionCallback() {
            @Override
            public void onSuccess(String json) {
                parseOffMain(() -> {
                            parseCategories(json);
                            persistCategories(json);
                            return Boolean.TRUE;
                        }, ignored -> { }, new OnHttpCallBack<Object>() {
                            @Override public void onError(String text) { }
                        });
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                // Keep the last good category cache; this request is best effort.
            }
        });
    }

    private String readCachedCategories() {
        try {
            return ContextUtils.getContext()
                    .getSharedPreferences(CATEGORY_CACHE_PREFS, android.content.Context.MODE_PRIVATE)
                    .getString(CATEGORY_CACHE_KEY, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void persistCategories(String json) {
        if (TextUtils.isEmpty(json)) return;
        try {
            ContextUtils.getContext()
                    .getSharedPreferences(CATEGORY_CACHE_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(CATEGORY_CACHE_KEY, json)
                    .apply();
        } catch (RuntimeException ignored) {
            // A cache failure must never delay or fail the native topic list.
        }
    }

    private final class TopicListLoadState {
        private final OnHttpCallBack<TopicListInfo> callback;
        private String latestJson;
        private boolean categoriesReady;
        private boolean latestDelivered;
        private boolean enrichedDelivered;
        private boolean latestFailed;

        TopicListLoadState(OnHttpCallBack<TopicListInfo> callback) {
            this.callback = callback;
        }

        void onLatestJson(String json) {
            latestJson = json;
            parseOffMain(() -> parseTopics(json), parsed -> {
                if (latestFailed) return;
                latestDelivered = true;
                callback.onSuccess(parsed);
                if (categoriesReady) emitEnrichedList();
            }, new OnHttpCallBack<Object>() {
                @Override public void onError(String text) {
                    onLatestFailure(text);
                }
            });
        }

        void onCategoriesJson(String json) {
            parseOffMain(() -> {
                        parseCategories(json);
                        persistCategories(json);
                        return Boolean.TRUE;
                    }, ignored -> {
                        categoriesReady = true;
                        if (latestDelivered) emitEnrichedList();
                    }, new OnHttpCallBack<Object>() {
                        @Override public void onError(String text) {
                            onCategoriesFailure();
                        }
                    });
        }

        void onCategoriesFailure() {
            // The latest list has no dependency on category metadata.
        }

        void onLatestFailure(String message) {
            if (latestFailed) return;
            latestFailed = true;
            callback.onError(message);
        }

        private void emitEnrichedList() {
            if (enrichedDelivered || latestJson == null || latestFailed) return;
            enrichedDelivered = true;
            String json = latestJson;
            parseOffMain(() -> {
                        TopicListInfo result = parseTopics(json);
                        result.setMetadataOnly(true);
                        return result;
                    }, callback::onSuccess,
                    new OnHttpCallBack<Object>() {
                        @Override public void onError(String text) { }
                    });
        }
    }

    private void loadTopicPage(String topicPath, OnHttpCallBack<TopicListInfo> callback) {
        fetch(topicPath, new SessionCallback() {
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

    /**
     * Loads the native category/subcategory directory used by board discovery.
     * A previously decoded site cache is published immediately when available;
     * the canonical categories endpoint then refreshes it in the background so
     * opening the picker never waits on a cold metadata request.
     */
    public void loadCategoryDirectory(
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.CategoryRow>> callback) {
        if (callback == null) return;
        String cached = readCachedCategories();
        if (!TextUtils.isEmpty(cached)) {
            parseOffMain(() -> LinuxDoFeaturePayloadParser.parseCategoryDirectory(cached),
                    callback::onSuccess, new OnHttpCallBack<Object>() {
                        @Override public void onError(String ignored) { }
                    });
        }
        // /categories.json may expose only top-level rows on some Discourse builds.
        // /site.json is the canonical flat directory and retains parent_category_id,
        // which the native two-stage board picker needs for Lv/private sub-boards.
        fetch("/site.json", new SessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> {
                            List<LinuxDoFeaturePayloadParser.CategoryRow> rows =
                                    LinuxDoFeaturePayloadParser.parseCategoryDirectory(json);
                            parseCategories(json);
                            persistCategories(json);
                            return rows;
                        }, callback::onSuccess, new OnHttpCallBack<Object>() {
                            @Override public void onError(String text) {
                                if (TextUtils.isEmpty(cached)) callback.onError(text);
                            }
                        });
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                if (TextUtils.isEmpty(cached)) callback.onError(messageFor(failure));
            }
        });
    }

    private static String topicListPath(
            int appPage, String categorySlug, int categoryId, Feed feed) {
        int page = Math.max(0, Math.min(MAX_API_PAGE, appPage - 1));
        if (categoryId <= 0) return globalFeedPath(page + 1, feed);
        String slug = safeCategoryPath(categorySlug);
        if (TextUtils.isEmpty(slug)) {
            return "/c/" + categoryId + ".json?page=" + page;
        }
        return "/c/" + slug + "/" + categoryId + ".json?page=" + page;
    }

    private static String safeCategoryPath(String value) {
        if (TextUtils.isEmpty(value)) return "";
        StringBuilder result = new StringBuilder();
        for (String rawSegment : value.split("/")) {
            String segment = safePathSegment(rawSegment);
            if (TextUtils.isEmpty(segment)) continue;
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        return result.toString();
    }

    private static String globalFeedPath(int appPage, Feed feed) {
        int page = Math.max(0, Math.min(MAX_API_PAGE, appPage - 1));
        Feed safeFeed = feed == null ? Feed.LATEST : feed;
        switch (safeFeed) {
            case NEW:
                return "/new.json?page=" + page;
            case UNREAD:
                return "/unread.json?page=" + page;
            case TOP:
                return "/top.json?period=weekly&page=" + page;
            case LATEST:
            default:
                return "/latest.json?page=" + page;
        }
    }

    private static String safePathSegment(String value) {
        if (TextUtils.isEmpty(value)) return "";
        String trimmed = value.trim();
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '.' || ch == '_' || ch == '-'
                    || ch == '~' || ch == '%') {
                out.append(ch);
            }
        }
        return out.toString();
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
        LinuxDoHttpSession.getInstance().warmup();
        final ArticleWaiter newWaiter = new ArticleWaiter(appPage, callback);
        boolean startsRequest = false;
        ThreadData earlyPreview = null;
        synchronized (mTopicInFlight) {
            List<ArticleWaiter> waiters = mTopicInFlight.get(topicId);
            if (waiters != null) {
                waiters.add(newWaiter);
            } else {
                waiters = new ArrayList<>();
                waiters.add(newWaiter);
                mTopicInFlight.put(topicId, waiters);
                startsRequest = true;
            }
        }
        if (!startsRequest) {
            synchronized (mFirstFloorCache) {
                earlyPreview = mFirstFloorCache.get(topicId);
            }
        }
        if (earlyPreview != null) deliverFirstFloor(newWaiter, earlyPreview);
        if (!startsRequest) return;
        final long requestStart = SystemClock.elapsedRealtime();
        if (appPage <= 1) fetchFirstFloor(topicId, requestStart);
        // The page-qualified topic endpoint contains the full stream metadata and the requested
        // page's cooked posts in one response. Restore used to request page one first and then
        // serially fetch the target post ids, making every previously-read topic one RTT slower.
        String topicPath = appPage <= 1
                ? "/t/" + topicId + ".json"
                : "/t/" + topicId + "/" + appPage + ".json";
        fetchTopicSnapshot(topicId, topicPath, requestStart, appPage > 1);
    }

    private void fetchTopicSnapshot(
            int topicId, String topicPath, long requestStart, boolean allowCanonicalFallback) {
        fetch(topicPath, new ArticleSessionCallback() {
            @Override
            public void onSuccess(String json) {
                final long parseStart = SystemClock.elapsedRealtime();
                NLog.d(PERF_TAG, "article_http_ms="
                        + (parseStart - requestStart));
                parseOffMain(() -> parseTopicSnapshotAndWarmFirstFloor(json), snapshot -> {
                    NLog.d(PERF_TAG, "article_parse_and_first_floor_ms="
                            + (SystemClock.elapsedRealtime() - parseStart));
                    synchronized (mTopicCache) {
                        mTopicCache.put(topicId, snapshot);
                    }
                    if (snapshot.firstFloorPreview != null) {
                        publishFirstFloor(topicId, snapshot.firstFloorPreview);
                    }
                    List<ArticleWaiter> waiters;
                    synchronized (mTopicInFlight) {
                        waiters = mTopicInFlight.remove(topicId);
                    }
                    if (waiters != null) {
                        for (ArticleWaiter waiter : waiters) {
                            loadArticlePage(snapshot, waiter.page, waiter.callback,
                                    !waiter.previewDelivered);
                        }
                    }
                }, new OnHttpCallBack<Object>() {
                    @Override
                    public void onError(String text) {
                        if (allowCanonicalFallback) {
                            fetchTopicSnapshot(topicId, "/t/" + topicId + ".json",
                                    requestStart, false);
                        } else {
                            failTopicWaiters(topicId, text);
                        }
                    }
                });
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                if (allowCanonicalFallback) {
                    fetchTopicSnapshot(topicId, "/t/" + topicId + ".json",
                            requestStart, false);
                } else {
                    failTopicWaiters(topicId, messageFor(failure));
                }
            }
        });
    }

    /** Loads one replied-to floor without navigating away from the current reading position. */
    public void loadReplyTarget(
            int topicId,
            int postId,
            OnHttpCallBack<ThreadRowInfo.ReplyInfo> callback) {
        if (callback == null) return;
        if (topicId <= 0 || postId <= 0) {
            callback.onError("无法读取被回复楼层");
            return;
        }
        JSONObject cachedPost = null;
        TopicSnapshot snapshot;
        synchronized (mTopicCache) {
            snapshot = mTopicCache.get(topicId);
        }
        if (snapshot != null) {
            synchronized (snapshot) {
                cachedPost = snapshot.posts.get(postId);
            }
        }
        if (cachedPost != null) {
            callback.onSuccess(replyInfoFromPost(cachedPost));
            return;
        }
        String path = "/t/" + topicId
                + "/posts.json?post_ids%5B%5D=" + postId;
        fetch(path, new ArticleSessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> {
                    JSONArray posts = postArrayFromPayload(json);
                    if (posts == null || posts.isEmpty()) {
                        throw new IllegalArgumentException("Missing replied-to post");
                    }
                    JSONObject fetchedPost = posts.getJSONObject(0);
                    if (snapshot != null && fetchedPost != null) {
                        synchronized (snapshot) {
                            snapshot.posts.put(fetchedPost.getIntValue("id"), fetchedPost);
                        }
                    }
                    return replyInfoFromPost(fetchedPost);
                }, callback::onSuccess, callback);
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    /** Loads every direct reply and returns them in floor order for one-shot inline expansion. */
    public void loadDirectReplies(
            int topicId,
            int postId,
            OnHttpCallBack<List<ThreadRowInfo.ReplyInfo>> callback) {
        if (callback == null) return;
        if (topicId <= 0 || postId <= 0) {
            callback.onError("无法读取本楼回复");
            return;
        }
        fetch("/posts/" + postId + "/reply-history.json", new ArticleSessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> {
                    JSONArray posts = postArrayFromPayload(json);
                    List<ThreadRowInfo.ReplyInfo> result = new ArrayList<>();
                    TopicSnapshot snapshot;
                    synchronized (mTopicCache) {
                        snapshot = mTopicCache.get(topicId);
                    }
                    if (posts != null) {
                        for (int index = 0; index < posts.size(); index++) {
                            JSONObject post = posts.getJSONObject(index);
                            if (post == null) continue;
                            result.add(replyInfoFromPost(post));
                            if (snapshot != null) {
                                synchronized (snapshot) {
                                    snapshot.posts.put(post.getIntValue("id"), post);
                                }
                            }
                        }
                    }
                    result.sort((left, right) -> Integer.compare(
                            left.getFloor(), right.getFloor()));
                    return result;
                }, callback::onSuccess, callback);
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    private void fetchFirstFloor(int topicId, long requestStart) {
        // Discourse's filter_post_number is exclusive: asking for posts before floor 2
        // returns only floor 1. A topic without floor 2 yields an empty fast response and the
        // ordinary full-topic request below remains the transparent fallback.
        fetch("/t/" + topicId + "/posts.json?post_number=2",
                new CriticalArticleSessionCallback() {
                    @Override
                    public void onSuccess(String json) {
                        final long parseStart = SystemClock.elapsedRealtime();
                        NLog.d(PERF_TAG, "first_floor_http_ms="
                                + (parseStart - requestStart));
                        parseOffMain(() -> parseFirstFloorPreview(topicId, json), preview -> {
                            NLog.d(PERF_TAG, "first_floor_parse_ms="
                                    + (SystemClock.elapsedRealtime() - parseStart));
                            publishFirstFloor(topicId, preview);
                        }, new OnHttpCallBack<Object>() {
                            @Override public void onError(String ignored) {
                                // The full topic request remains the transparent fallback.
                            }
                        });
                    }

                    @Override
                    public void onFailure(LinuxDoWebSession.Failure failure) {
                        // The full topic request remains the transparent fallback.
                    }
                });
    }

    private void publishFirstFloor(int topicId, ThreadData preview) {
        if (preview == null) return;
        synchronized (mFirstFloorCache) {
            mFirstFloorCache.put(topicId, preview);
        }
        List<ArticleWaiter> recipients = new ArrayList<>();
        synchronized (mTopicInFlight) {
            List<ArticleWaiter> waiters = mTopicInFlight.get(topicId);
            if (waiters != null) {
                for (ArticleWaiter waiter : waiters) {
                    if (waiter.page <= 1 && !waiter.previewDelivered
                            && waiter.callback instanceof ProgressiveArticleCallback) {
                        waiter.previewDelivered = true;
                        recipients.add(waiter);
                    }
                }
            }
        }
        for (ArticleWaiter waiter : recipients) {
            ((ProgressiveArticleCallback) waiter.callback).onFirstFloor(preview);
        }
    }

    private void deliverFirstFloor(ArticleWaiter waiter, ThreadData preview) {
        if (waiter == null || preview == null || waiter.page > 1
                || waiter.previewDelivered
                || !(waiter.callback instanceof ProgressiveArticleCallback)) return;
        waiter.previewDelivered = true;
        Runnable delivery = () -> ((ProgressiveArticleCallback) waiter.callback)
                .onFirstFloor(preview);
        if (Looper.myLooper() == Looper.getMainLooper()) delivery.run();
        else mMainHandler.post(delivery);
    }

    /**
     * Starts the exact page-one request at tap time so Activity/Fragment/WebView creation can
     * overlap network and parsing. The regular load joins the same in-flight request and cache.
     */
    public void prefetchArticle(int topicId) {
        prefetchArticle(topicId, 1);
    }

    /** Starts the exact restore page at tap time instead of always warming page one. */
    public void prefetchArticle(int topicId, int appPage) {
        if (topicId <= 0) return;
        loadArticle(topicId, Math.max(1, appPage), new OnHttpCallBack<ThreadData>() {
            @Override public void onSuccess(ThreadData data) { }
            @Override public void onError(String text) { }
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

    public void search(
            String query,
            SearchOrder order,
            int appPage,
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.SearchRow>> callback) {
        search(query, order, SearchScope.ALL, appPage, callback);
    }

    public void search(
            String query,
            SearchOrder order,
            SearchScope scope,
            int appPage,
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.SearchRow>> callback) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            callback.onSuccess(new ArrayList<>());
            return;
        }
        SearchOrder safeOrder = order == null ? SearchOrder.RELEVANCE : order;
        SearchScope safeScope = scope == null ? SearchScope.ALL : scope;
        if (safeScope == SearchScope.CATEGORIES) {
            final String categoryQuery = normalized;
            final int requestedPage = Math.max(1, Math.min(MAX_API_PAGE, appPage));
            fetch("/site.json", new SessionCallback() {
                @Override public void onSuccess(String json) {
                    parseOffMain(() -> {
                                List<LinuxDoFeaturePayloadParser.SearchRow> all =
                                        LinuxDoFeaturePayloadParser.parseCategorySearch(
                                                json, categoryQuery);
                                int start = (requestedPage - 1) * PAGE_SIZE;
                                if (start >= all.size()) return new ArrayList<>();
                                int end = Math.min(all.size(), start + PAGE_SIZE);
                                return new ArrayList<>(all.subList(start, end));
                            }, callback::onSuccess, callback);
                }
                @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                    callback.onError(messageFor(failure));
                }
            });
            return;
        }
        if (safeOrder == SearchOrder.TOPIC_CREATED) normalized += " order:latest_topic";
        else if (safeOrder == SearchOrder.POST_CREATED) normalized += " order:latest";
        final String encoded;
        try {
            encoded = URLEncoder.encode(normalized, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            callback.onError("搜索词无法编码");
            return;
        }
        fetch("/search.json?q=" + encoded + "&page="
                        + Math.max(1, Math.min(MAX_API_PAGE, appPage)),
                new SessionCallback() {
                    @Override public void onSuccess(String json) {
                        parseOffMain(() -> LinuxDoFeaturePayloadParser.parseSearch(
                                        json, safeOrder, safeScope),
                                callback::onSuccess, callback);
                    }
                    @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                        callback.onError(messageFor(failure));
                    }
                });
    }

    public void loadNotifications(
            int offset,
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.NotificationRow>> callback) {
        int safeOffset = Math.max(0, Math.min(MAX_API_OFFSET, offset));
        fetch("/notifications.json?offset=" + safeOffset, new SessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseNotifications(json),
                        callback::onSuccess, callback);
            }
            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void loadUnreadNotificationTotal(OnHttpCallBack<Integer> callback) {
        fetch("/notifications/totals.json", new SessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseUnreadTotal(json),
                        callback::onSuccess, callback);
            }
            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void loadTrustProgress(
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.TrustRequirement>> callback) {
        LinuxDoHttpSession.getInstance().fetchTrustProgress(new LinuxDoWebSession.Callback() {
            @Override public void onSuccess(String html) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseTrustProgress(html),
                        callback::onSuccess, callback);
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void markNotificationRead(long id, MutationCallback callback) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (id > 0) fields.put("id", String.valueOf(id));
        else fields.put("all", "true");
        LinuxDoHttpSession.getInstance().put(
                "/notifications/mark-read.json", fields, new LinuxDoWebSession.Callback() {
                    @Override public void onSuccess(String json) { callback.onSuccess(); }
                    @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                        callback.onError(messageFor(failure));
                    }
                });
    }

    public void loadCurrentUsername(OnHttpCallBack<String> callback) {
        fetch("/session/current.json", new SessionCallback() {
            @Override public void onSuccess(String json) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseCurrentUsername(json),
                        callback::onSuccess, callback);
            }
            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    public void loadProfile(
            String username,
            OnHttpCallBack<LinuxDoFeaturePayloadParser.Profile> callback) {
        final String encoded;
        try {
            encoded = URLEncoder.encode(username == null ? "" : username.trim(), "UTF-8")
                    .replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            callback.onError("用户名无法编码");
            return;
        }
        if (encoded.isEmpty()) {
            callback.onError("请先登录 LINUX DO");
            return;
        }
        fetch("/u/" + encoded + ".json", new SessionCallback() {
            @Override public void onSuccess(String userJson) {
                fetch("/u/" + encoded + "/summary.json", new SessionCallback() {
                    @Override public void onSuccess(String summaryJson) {
                        loadProfileBadges(encoded, userJson, summaryJson, callback);
                    }
                    @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                        loadProfileBadges(encoded, userJson, null, callback);
                    }
                });
            }
            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                callback.onError(messageFor(failure));
            }
        });
    }

    private void loadProfileBadges(
            String encoded,
            String userJson,
            String summaryJson,
            OnHttpCallBack<LinuxDoFeaturePayloadParser.Profile> callback) {
        fetch("/user-badges/" + encoded + ".json", new SessionCallback() {
            @Override public void onSuccess(String badgesJson) {
                loadProfileActivity(encoded, userJson, summaryJson, badgesJson, callback);
            }
            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                loadProfileActivity(encoded, userJson, summaryJson, null, callback);
            }
        });
    }

    private void loadProfileActivity(
            String encoded,
            String userJson,
            String summaryJson,
            String badgesJson,
            OnHttpCallBack<LinuxDoFeaturePayloadParser.Profile> callback) {
        // Activity is optional on private profiles. A successful base profile
        // must still render when this endpoint is unavailable.
        fetch("/user_actions.json?username=" + encoded
                + "&offset=0&filter=4%2C5", new SessionCallback() {
            @Override public void onSuccess(String activityJson) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseProfile(
                                userJson, summaryJson, badgesJson, activityJson),
                        callback::onSuccess, callback);
            }

            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                parseOffMain(() -> LinuxDoFeaturePayloadParser.parseProfile(
                                userJson, summaryJson, badgesJson),
                        callback::onSuccess, callback);
            }
        });
    }

    /**
     * Loads one bounded page of a user's recent topics/replies.  The activity
     * endpoint is deliberately exposed as a typed operation instead of a
     * generic path so profile pagination cannot escape the source allowlist.
     */
    public void loadProfileActivity(
            String username,
            int offset,
            OnHttpCallBack<List<LinuxDoFeaturePayloadParser.ProfileRow>> callback) {
        if (callback == null) return;
        final String encoded;
        try {
            encoded = URLEncoder.encode(username == null ? "" : username.trim(), "UTF-8")
                    .replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            callback.onError("用户名无法编码");
            return;
        }
        if (encoded.isEmpty()) {
            callback.onError("请先登录 LINUX DO");
            return;
        }
        int safeOffset = Math.max(0, Math.min(MAX_API_OFFSET, offset));
        fetch("/user_actions.json?username=" + encoded
                        + "&offset=" + safeOffset + "&filter=4%2C5",
                new SessionCallback() {
                    @Override public void onSuccess(String json) {
                        parseOffMain(() -> LinuxDoFeaturePayloadParser.parseProfileActivity(json),
                                callback::onSuccess, callback);
                    }

                    @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                        callback.onError(messageFor(failure));
                    }
                });
    }

    private void loadArticlePage(
            TopicSnapshot snapshot, int appPage, OnHttpCallBack<ThreadData> callback) {
        loadArticlePage(snapshot, appPage, callback, true);
    }

    private void loadArticlePage(
            TopicSnapshot snapshot, int appPage, OnHttpCallBack<ThreadData> callback,
            boolean allowPreview) {
        int page = Math.max(1, appPage);
        int start = (page - 1) * PAGE_SIZE;
        if (start >= snapshot.stream.size()) {
            callback.onError("该页不存在");
            return;
        }
        ThreadData cachedPage;
        synchronized (snapshot) {
            cachedPage = snapshot.renderedPages.get(page);
            if (allowPreview && cachedPage == null && page == 1
                    && snapshot.firstFloorPreview != null
                    && callback instanceof ProgressiveArticleCallback) {
                ((ProgressiveArticleCallback) callback).onFirstFloor(
                        snapshot.firstFloorPreview);
            }
            if (cachedPage == null) {
                List<OnHttpCallBack<ThreadData>> waiters = snapshot.pageWaiters.get(page);
                if (waiters != null) {
                    waiters.add(callback);
                    return;
                }
                waiters = new ArrayList<>();
                waiters.add(callback);
                snapshot.pageWaiters.put(page, waiters);
            }
        }
        if (cachedPage != null) {
            NLog.d(PERF_TAG, "article_page_cache_hit page=" + page);
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback.onSuccess(cachedPage);
            } else {
                mMainHandler.post(() -> callback.onSuccess(cachedPage));
            }
            return;
        }
        int end = Math.min(snapshot.stream.size(), start + PAGE_SIZE);
        List<Integer> ids = new ArrayList<>(snapshot.stream.subList(start, end));
        List<Integer> missing = new ArrayList<>();
        synchronized (snapshot) {
            for (Integer id : ids) if (!snapshot.posts.containsKey(id)) missing.add(id);
        }
        if (missing.isEmpty()) {
            final long buildStart = SystemClock.elapsedRealtime();
            parseOffMain(() -> {
                ThreadData data = buildThreadData(snapshot, ids);
                cacheRenderedPage(snapshot, page, data);
                return data;
            }, data -> {
                NLog.d(PERF_TAG, "article_page_build_ms="
                        + (SystemClock.elapsedRealtime() - buildStart)
                        + " page=" + page);
                completeArticlePage(snapshot, page, data, null);
            }, pageErrorCallback(snapshot, page));
            return;
        }
        StringBuilder path = new StringBuilder("/t/").append(snapshot.topicId)
                .append("/posts.json?");
        for (int index = 0; index < missing.size(); index++) {
            if (index > 0) path.append('&');
            path.append("post_ids%5B%5D=").append(missing.get(index));
        }
        final long requestStart = SystemClock.elapsedRealtime();
        fetch(path.toString(), new ArticleSessionCallback() {
            @Override
            public void onSuccess(String json) {
                final long buildStart = SystemClock.elapsedRealtime();
                NLog.d(PERF_TAG, "article_page_http_ms="
                        + (buildStart - requestStart) + " page=" + page);
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
                    ThreadData data = buildThreadData(snapshot, ids);
                    cacheRenderedPage(snapshot, page, data);
                    return data;
                }, data -> {
                    NLog.d(PERF_TAG, "article_page_http_build_ms="
                            + (SystemClock.elapsedRealtime() - buildStart)
                            + " page=" + page);
                    completeArticlePage(snapshot, page, data, null);
                }, pageErrorCallback(snapshot, page));
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                completeArticlePage(snapshot, page, null, messageFor(failure));
            }
        });
    }

    private OnHttpCallBack<Object> pageErrorCallback(TopicSnapshot snapshot, int page) {
        return new OnHttpCallBack<Object>() {
            @Override
            public void onError(String text) {
                completeArticlePage(snapshot, page, null, text);
            }
        };
    }

    /** Delivers one mapped page to every waiter; concurrent prefetch/navigation builds coalesce. */
    private void completeArticlePage(
            TopicSnapshot snapshot, int page, ThreadData data, String error) {
        List<OnHttpCallBack<ThreadData>> waiters;
        synchronized (snapshot) {
            waiters = snapshot.pageWaiters.remove(page);
        }
        if (waiters == null) return;
        for (OnHttpCallBack<ThreadData> waiter : waiters) {
            if (data != null) waiter.onSuccess(data);
            else waiter.onError(TextUtils.isEmpty(error)
                    ? "LINUX DO 数据格式暂时无法解析" : error);
        }
    }

    private void parseCategories(String json) {
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> categories =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);
        synchronized (mCategories) {
            mCategories.clear();
            mCategories.putAll(categories);
        }
    }

    private TopicListInfo parseTopics(String json) {
        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> categoryRecords;
        synchronized (mCategories) {
            categoryRecords = new HashMap<>(mCategories);
        }
        List<LinuxDoTopicPayloadParser.TopicRecord> topics =
                LinuxDoTopicPayloadParser.parseTopicsWithCategoryRecords(json, categoryRecords);
        TopicListInfo result = new TopicListInfo();
        result.setName(LinuxDoConstants.BOARD_NAME);
        result.curTime = (int) (System.currentTimeMillis() / 1000L);
        for (LinuxDoTopicPayloadParser.TopicRecord topic : topics) {
            ThreadPageInfo row = new ThreadPageInfo();
            row.setTid(topic.id);
            row.setFid(topic.categoryId);
            row.setParentFid(topic.boardId == topic.categoryId ? 0 : topic.boardId);
            row.setBoard(topic.categoryName == null ? LinuxDoConstants.BOARD_NAME : topic.categoryName);
            row.setBoardIconUrl(topic.categoryIconUrl);
            row.setTags(topic.tags);
            row.setVisibility(topic.visibility);
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
        updateEnabledReactions(root.getJSONArray("valid_reactions"));
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

    private void updateEnabledReactions(JSONArray values) {
        // A topic response is not an authoritative site palette: some projections expose only
        // eight values and omit custom emoji. Keep the picker fixed to LinuxDoReactionAssets so
        // every topic uses the same complete, locally available set of ten official reactions.
    }

    /**
     * Decode the response once, but map only the first floor on the critical path. Discourse's
     * topic endpoint already returns the first twenty posts, so issuing a second "first post"
     * request would add latency. The normal page builder maps the remaining floors after this
     * preview has been handed to the UI.
     */
    private TopicSnapshot parseTopicSnapshotAndWarmFirstFloor(String json) {
        TopicSnapshot snapshot = parseTopicSnapshot(json);
        if (snapshot.stream.isEmpty()) return snapshot;
        Integer firstId = snapshot.stream.get(0);
        if (snapshot.posts.containsKey(firstId)) {
            snapshot.firstFloorPreview = buildThreadData(
                    snapshot, java.util.Collections.singletonList(firstId));
            snapshot.firstFloorPreview.setProgressivePreview(true);
        }
        return snapshot;
    }

    private ThreadData parseFirstFloorPreview(int topicId, String json) {
        JSONObject root = JSON.parseObject(json);
        JSONObject postStream = root == null ? null : root.getJSONObject("post_stream");
        JSONArray posts = postStream == null
                ? (root == null ? null : root.getJSONArray("posts"))
                : postStream.getJSONArray("posts");
        if (posts == null || posts.isEmpty()) {
            throw new IllegalArgumentException("Missing first post");
        }
        JSONObject post = posts.getJSONObject(0);
        if (post == null || post.getIntValue("post_number") != 1) {
            throw new IllegalArgumentException("Unexpected first post");
        }
        TopicSnapshot snapshot = new TopicSnapshot();
        snapshot.topicId = topicId;
        snapshot.categoryId = firstPositive(
                root == null ? 0 : root.getIntValue("category_id"),
                post.getIntValue("category_id"));
        snapshot.title = firstNonBlank(
                root == null ? null : root.getString("title"),
                post.getString("topic_title"));
        int postId = post.getIntValue("id");
        if (postId <= 0) throw new IllegalArgumentException("Missing first post id");
        snapshot.stream.add(postId);
        snapshot.posts.put(postId, post);
        ThreadData preview = buildThreadData(snapshot, java.util.Collections.singletonList(postId));
        preview.setProgressivePreview(true);
        return preview;
    }

    private static int firstPositive(int first, int second) {
        return first > 0 ? first : Math.max(0, second);
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
        row.setReplyTo(buildReplyTarget(snapshot, post));
        List<ThreadRowInfo.ReplyInfo> knownReplies = buildKnownDirectReplies(snapshot, post);
        row.setDirectReplies(knownReplies);
        row.setDirectReplyCount(Math.max(post.getIntValue("reply_count"), knownReplies.size()));
        row.setBoosts(parseBoosts(post.getJSONArray("boosts")));
        row.setPolls(parsePolls(post.getJSONArray("polls"), post.getJSONObject("polls_votes")));
        row.setContent(cooked);
        row.setFormattedHtmlData(wrapCooked(row.getContent()));
        collectImageUrls(row, cooked);
        row.setScore(LinuxDoPostPayloadParser.resolveLikeCount(post));
        row.setReactions(LinuxDoPostPayloadParser.resolveReactions(post));
        row.setCurrentReaction(LinuxDoPostPayloadParser.resolveCurrentReactionId(post));
        row.setLikedByViewer(LinuxDoPostPayloadParser.isLikedByViewer(post));
        row.setMemberGroup(buildLinuxDoIdentity(snapshot, post));
        row.setPostCount("-");
        row.setJs_escap_avatar(resolveAvatarUrl(post.getString("avatar_template")));
        return row;
    }

    private static ThreadRowInfo.ReplyInfo buildReplyTarget(
            TopicSnapshot snapshot, JSONObject post) {
        int targetNumber = post.getIntValue("reply_to_post_number");
        if (targetNumber <= 0) return null;
        JSONObject target = null;
        for (JSONObject candidate : snapshot.posts.values()) {
            if (candidate != null && candidate.getIntValue("post_number") == targetNumber) {
                target = candidate;
                break;
            }
        }
        if (target != null) return replyInfoFromPost(target);
        ThreadRowInfo.ReplyInfo result = new ThreadRowInfo.ReplyInfo();
        result.setFloor(Math.max(0, targetNumber - 1));
        if (targetNumber - 1 < snapshot.stream.size()) {
            result.setPostId(snapshot.stream.get(targetNumber - 1));
        }
        JSONObject replyToUser = post.getJSONObject("reply_to_user");
        if (replyToUser != null) {
            result.setAuthor(firstNonBlank(replyToUser.getString("username"),
                    replyToUser.getString("name")));
        }
        return result;
    }

    private static List<ThreadRowInfo.ReplyInfo> buildKnownDirectReplies(
            TopicSnapshot snapshot, JSONObject post) {
        int postNumber = post.getIntValue("post_number");
        List<ThreadRowInfo.ReplyInfo> result = new ArrayList<>();
        if (postNumber <= 0) return result;
        for (JSONObject candidate : snapshot.posts.values()) {
            if (candidate != null
                    && candidate.getIntValue("reply_to_post_number") == postNumber) {
                result.add(replyInfoFromPost(candidate));
            }
        }
        result.sort((left, right) -> Integer.compare(left.getFloor(), right.getFloor()));
        return result;
    }

    private static ThreadRowInfo.ReplyInfo replyInfoFromPost(JSONObject post) {
        ThreadRowInfo.ReplyInfo result = new ThreadRowInfo.ReplyInfo();
        if (post == null) return result;
        result.setPostId(post.getIntValue("id"));
        result.setFloor(Math.max(0, post.getIntValue("post_number") - 1));
        result.setAuthor(firstNonBlank(post.getString("username"), post.getString("name")));
        String text = boostPlainText(sanitizeCooked(post.getString("cooked")));
        if (text.length() > 2400) text = text.substring(0, 2400).trim() + "…";
        result.setContent(text);
        result.setAvatarUrl(resolveAvatarUrl(post.getString("avatar_template")));
        return result;
    }

    private static String resolveAvatarUrl(String template) {
        if (TextUtils.isEmpty(template)) return null;
        String avatar = template.replace("{size}", "96");
        if (avatar.startsWith("//")) return "https:" + avatar;
        if (avatar.startsWith("/")) return LinuxDoConstants.ORIGIN + avatar;
        return avatar;
    }

    private static JSONArray postArrayFromPayload(String json) {
        Object value = JSON.parse(json);
        if (value instanceof JSONArray) return (JSONArray) value;
        if (!(value instanceof JSONObject)) return null;
        JSONObject root = (JSONObject) value;
        JSONObject stream = root.getJSONObject("post_stream");
        JSONArray posts = stream == null ? null : stream.getJSONArray("posts");
        return posts != null ? posts : root.getJSONArray("posts");
    }

    private String categoryName(int id) {
        synchronized (mCategories) {
            LinuxDoTopicPayloadParser.CategoryRecord category = mCategories.get(id);
            String name = category == null ? null : category.name;
            return TextUtils.isEmpty(name) ? LinuxDoConstants.BOARD_NAME : name;
        }
    }

    private static String formatIso(String iso) {
        try {
            return DISPLAY_TIME.format(Instant.parse(iso));
        } catch (Exception ignored) {
            return iso == null ? "" : iso;
        }
    }

    private static String sanitizeCooked(String cooked) {
        if (cooked == null) return "";
        String clean = DANGEROUS_CONTAINER.matcher(cooked).replaceAll("");
        clean = DANGEROUS_TAG.matcher(clean).replaceAll("");
        clean = EVENT_HANDLER.matcher(clean).replaceAll("");
        clean = JAVASCRIPT_URL.matcher(clean).replaceAll("$1=$2#$2");
        clean = META_BLOCK.matcher(clean).replaceAll("");
        clean = IMAGE_META.matcher(clean).replaceAll("");
        // Discourse emits ordinary emoji as remote <img class="emoji"> resources. Convert
        // those shortcodes to system emoji before collecting media URLs so reading text never
        // depends on CDN/DNS availability and emoji do not enter the image gallery.
        clean = LinuxDoEmojiRenderer.replaceEmojiImages(clean);
        clean = IMAGE_WITHOUT_LOADING.matcher(clean)
                .replaceAll("<img loading=\"eager\" decoding=\"async\"");
        clean = clean.replace("href=\"//", "href=\"https://")
                .replace("src=\"//", "src=\"https://")
                .replace("href='//", "href='https://")
                .replace("src='//", "src='https://")
                .replace("href=\"/", "href=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("src=\"/", "src=\"" + LinuxDoConstants.ORIGIN + "/")
                .replace("href='/", "href='" + LinuxDoConstants.ORIGIN + "/")
                .replace("src='/", "src='" + LinuxDoConstants.ORIGIN + "/");
        // Discourse often represents uploaded videos as ordinary attachment links. Turn only
        // HTTPS links with a known video suffix into native HTML5 controls so the reader can
        // play them inline; all other links retain their original navigation behavior.
        clean = VIDEO_LINK.matcher(clean).replaceAll(
                "<video controls preload=\"metadata\" playsinline>"
                        + "<source src=\"$1\"></video>");
        return clean;
    }

    private static List<ThreadRowInfo.BoostInfo> parseBoosts(JSONArray boosts) {
        if (boosts == null || boosts.isEmpty()) return java.util.Collections.emptyList();
        List<ThreadRowInfo.BoostInfo> result = new ArrayList<>();
        for (int index = 0; index < boosts.size() && index < 80; index++) {
            JSONObject boost;
            try {
                boost = boosts.getJSONObject(index);
            } catch (RuntimeException ignored) {
                continue;
            }
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
            String text = boostPlainText(content);
            if (TextUtils.isEmpty(text)) continue;
            ThreadRowInfo.BoostInfo info = new ThreadRowInfo.BoostInfo();
            info.setAvatarUrl(avatar);
            info.setContent(text);
            result.add(info);
        }
        return result;
    }

    private static List<ThreadRowInfo.PollInfo> parsePolls(
            JSONArray polls, JSONObject pollsVotes) {
        if (polls == null || polls.isEmpty()) return java.util.Collections.emptyList();
        List<ThreadRowInfo.PollInfo> result = new ArrayList<>();
        for (int index = 0; index < polls.size() && index < 8; index++) {
            JSONObject source;
            try {
                source = polls.getJSONObject(index);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (source == null) continue;
            String name = firstNonBlank(source.getString("name"), "poll");
            JSONArray optionArray = source.getJSONArray("options");
            if (optionArray == null || optionArray.size() < 2) continue;

            ThreadRowInfo.PollInfo poll = new ThreadRowInfo.PollInfo();
            poll.setName(name);
            poll.setTitle(boostPlainText(sanitizeCooked(source.getString("title"))));
            poll.setType(firstNonBlank(source.getString("type"), "regular"));
            poll.setStatus(firstNonBlank(source.getString("status"), "closed"));
            poll.setMin(Math.max(1, source.getIntValue("min")));
            int maximum = source.getIntValue("max");
            poll.setMax(maximum > 0 ? maximum
                    : (poll.isMultiple() ? optionArray.size() : 1));
            poll.setVoters(source.getIntValue("voters"));

            List<ThreadRowInfo.PollOptionInfo> options = new ArrayList<>();
            for (int optionIndex = 0;
                    optionIndex < optionArray.size() && optionIndex < 40;
                    optionIndex++) {
                JSONObject option = optionArray.getJSONObject(optionIndex);
                if (option == null) continue;
                String id = option.getString("id");
                String text = boostPlainText(sanitizeCooked(option.getString("html")));
                if (TextUtils.isEmpty(id) || TextUtils.isEmpty(text)) continue;
                int votes = option.containsKey("votes") ? option.getIntValue("votes") : -1;
                options.add(new ThreadRowInfo.PollOptionInfo(id, text, votes));
            }
            if (options.size() < 2) continue;
            poll.setOptions(options);

            List<String> selected = new ArrayList<>();
            JSONArray ownVotes = pollsVotes == null ? null : pollsVotes.getJSONArray(name);
            if (ownVotes != null) {
                for (int voteIndex = 0; voteIndex < ownVotes.size(); voteIndex++) {
                    Object value = ownVotes.get(voteIndex);
                    String digest = value instanceof JSONObject
                            ? ((JSONObject) value).getString("digest")
                            : value instanceof String ? ((String) value).trim() : null;
                    if (!TextUtils.isEmpty(digest) && !selected.contains(digest)) {
                        selected.add(digest);
                    }
                }
            }
            poll.setSelectedOptionIds(selected);
            result.add(poll);
        }
        return result;
    }

    private static String boostPlainText(String html) {
        if (TextUtils.isEmpty(html)) return "";
        String withEmojiTokens = LinuxDoEmojiRenderer.replaceEmojiImagesWithTokens(html);
        String withoutImages = withEmojiTokens.replaceAll("(?is)<img\\b[^>]*>", "");
        try {
            return Html.fromHtml(withoutImages, Html.FROM_HTML_MODE_LEGACY)
                    .toString().replace('\u00a0', ' ').trim();
        } catch (RuntimeException ignored) {
            return withoutImages.replaceAll("(?is)<br\\s*/?>", "\\n")
                    .replaceAll("(?is)<[^>]+>", "").trim();
        }
    }

    private static void collectImageUrls(ThreadRowInfo row, String html) {
        Matcher matcher = IMAGE_SRC.matcher(html == null ? "" : html);
        while (matcher.find() && row.getImageUrls().size() < 200) {
            String url = matcher.group(1);
            // Embedded official emoji are article decorations, not gallery images.
            if (url != null && !url.regionMatches(true, 0, "data:", 0, 5)) {
                row.addImageUrl(url);
            }
        }
    }

    private static void cacheRenderedPage(
            TopicSnapshot snapshot, int page, ThreadData data) {
        if (snapshot == null || data == null || data.getRowList() == null) return;
        int htmlChars = 0;
        for (ThreadRowInfo row : data.getRowList()) {
            if (row == null || row.getFormattedHtmlData() == null) continue;
            htmlChars += row.getFormattedHtmlData().length();
            if (htmlChars > MAX_CACHED_HTML_CHARS) return;
        }
        synchronized (snapshot) {
            snapshot.renderedPages.put(page, data);
        }
    }

    private static String buildLinuxDoIdentity(TopicSnapshot snapshot, JSONObject post) {
        Set<String> details = new LinkedHashSet<>();
        int userId = post.getIntValue("user_id");
        Object trust = post.get("trust_level");
        if (trust != null) details.add("Lv" + post.getIntValue("trust_level"));
        addNonBlank(details, post.getString("user_title"));
        addNonBlank(details, post.getString("primary_group_name"));
        JSONArray granted = post.getJSONArray("badges_granted");
        if (granted != null) {
            for (int index = 0; index < granted.size() && details.size() < 6; index++) {
                JSONObject badge = granted.getJSONObject(index);
                if (badge != null) addNonBlank(details, badge.getString("name"));
            }
        }
        List<String> topicBadges = snapshot.badgesByUser.get(userId);
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
                ? "LINUX DO 会话已失效，需要完成网络盾验证"
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

    /** Successful article JSON is parsed off-main, so it need not bounce through the UI loop. */
    private abstract static class ArticleSessionCallback extends SessionCallback
            implements LinuxDoWebSession.BackgroundSuccessCallback {
    }

    private abstract static class CriticalArticleSessionCallback extends ArticleSessionCallback
            implements LinuxDoWebSession.CriticalSuccessCallback {
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
        ThreadData firstFloorPreview;
        final Map<Integer, List<String>> badgesByUser = new HashMap<>();
        final Map<Integer, List<OnHttpCallBack<ThreadData>>> pageWaiters = new HashMap<>();
        final LinkedHashMap<Integer, ThreadData> renderedPages =
                new LinkedHashMap<Integer, ThreadData>(RENDERED_PAGE_CACHE_SIZE, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(
                            Map.Entry<Integer, ThreadData> eldest) {
                        return size() > RENDERED_PAGE_CACHE_SIZE;
                    }
                };
    }

    private static final class ArticleWaiter {
        final int page;
        final OnHttpCallBack<ThreadData> callback;
        boolean previewDelivered;

        ArticleWaiter(int page, OnHttpCallBack<ThreadData> callback) {
            this.page = page;
            this.callback = callback;
        }
    }
}
