package sp.phone.linuxdo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Android-free decoders for native search, profiles and notifications. */
public final class LinuxDoFeaturePayloadParser {

    private static final Pattern CATEGORY_LEVEL_SUFFIX = Pattern.compile(
            "(?i)\\s*[,，\\-–—·|/_]*\\s*(?:lv|level|trust[_\\s-]*level)[_\\s-]*[1-3]\\s*(?:区)?\\s*$");

    /** Discourse's activity endpoint returns a bounded page by default. Keep the
     * native profile surface on the same small page size so paging never grows
     * an in-memory payload without bound. */
    static final int PROFILE_ACTIVITY_PAGE_SIZE = 30;

    static List<SearchRow> parseSearch(String json) {
        return parseSearch(json, LinuxDoRepository.SearchOrder.RELEVANCE,
                LinuxDoRepository.SearchScope.ALL);
    }

    static List<SearchRow> parseSearch(String json, LinuxDoRepository.SearchOrder order) {
        return parseSearch(json, order, LinuxDoRepository.SearchScope.ALL);
    }

    /**
     * Discourse returns several result collections in one response. Keep the
     * decoder tolerant of a missing collection because permissions and search
     * mode can remove any of them.
     */
    static List<SearchRow> parseSearch(
            String json,
            LinuxDoRepository.SearchOrder order,
            LinuxDoRepository.SearchScope scope) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing search response");
        JSONArray posts = root.getJSONArray("posts");
        JSONArray topics = root.getJSONArray("topics");
        JSONArray users = root.getJSONArray("users");
        JSONArray categories = root.getJSONArray("categories");
        if (posts == null && topics == null && users == null && categories == null) {
            throw new IllegalArgumentException("Missing search rows");
        }
        LinuxDoRepository.SearchScope safeScope = scope == null
                ? LinuxDoRepository.SearchScope.ALL : scope;
        Map<Integer, JSONObject> topicMap = byId(topics);
        List<SearchRow> rows = new ArrayList<>();
        Set<Integer> postTopicIds = new HashSet<>();
        if ((safeScope == LinuxDoRepository.SearchScope.ALL
                || safeScope == LinuxDoRepository.SearchScope.POSTS) && posts != null) {
            for (int i = 0; i < posts.size(); i++) {
                JSONObject post = posts.getJSONObject(i);
                if (post == null) continue;
                SearchRow row = new SearchRow();
                row.kind = SearchRow.Kind.POST;
                row.topicId = post.getIntValue("topic_id");
                row.postNumber = Math.max(1, post.getIntValue("post_number"));
                JSONObject topic = topicMap.get(row.topicId);
                row.title = topic == null ? post.getString("topic_title") : topic.getString("title");
                row.excerpt = plain(first(post.getString("blurb"), post.getString("cooked")));
                row.username = post.getString("username");
                row.userId = post.getIntValue("user_id");
                if (topic != null) {
                    row.categoryId = topic.getIntValue("category_id");
                    row.categoryName = first(topic.getString("category_name"),
                            topic.getString("category_slug"));
                    row.tags = parseTags(topic.getJSONArray("tags"));
                    row.replyCount = Math.max(0, topic.getIntValue("posts_count") - 1);
                }
                if (order == LinuxDoRepository.SearchOrder.TOPIC_CREATED && topic != null) {
                    row.createdAt = epoch(topic.getString("created_at"));
                } else {
                    row.createdAt = epoch(post.getString("created_at"));
                }
                if (row.topicId > 0 && row.title != null) {
                    rows.add(row);
                    postTopicIds.add(row.topicId);
                }
            }
        }
        if ((safeScope == LinuxDoRepository.SearchScope.ALL
                || safeScope == LinuxDoRepository.SearchScope.TOPICS) && topics != null) {
            for (int i = 0; i < topics.size(); i++) {
                JSONObject topic = topics.getJSONObject(i);
                if (topic == null) continue;
                SearchRow row = new SearchRow();
                row.kind = SearchRow.Kind.TOPIC;
                row.topicId = topic.getIntValue("id");
                row.postNumber = 1;
                row.title = topic.getString("title");
                row.createdAt = epoch(topic.getString("created_at"));
                row.categoryId = topic.getIntValue("category_id");
                row.categoryName = first(topic.getString("category_name"),
                        topic.getString("category_slug"));
                row.tags = parseTags(topic.getJSONArray("tags"));
                row.replyCount = Math.max(0, topic.getIntValue("posts_count") - 1);
                row.username = topic.getString("last_poster_username");
                // A topic collection accompanies matching posts in normal
                // Discourse responses. Avoid showing the same topic twice.
                if (row.topicId > 0 && row.title != null && !postTopicIds.contains(row.topicId)) {
                    rows.add(row);
                }
            }
        }
        if ((safeScope == LinuxDoRepository.SearchScope.ALL
                || safeScope == LinuxDoRepository.SearchScope.USERS) && users != null) {
            for (int i = 0; i < users.size(); i++) {
                JSONObject user = users.getJSONObject(i);
                if (user == null) continue;
                SearchRow row = new SearchRow();
                row.kind = SearchRow.Kind.USER;
                row.userId = user.getIntValue("id");
                row.username = user.getString("username");
                row.displayName = first(user.getString("name"), row.username);
                row.title = row.displayName;
                row.avatar = avatar(user.getString("avatar_template"), 96);
                row.trustLevel = user.getIntValue("trust_level");
                row.excerpt = user.getString("title");
                row.createdAt = epoch(user.getString("last_seen_at"));
                if (!isBlank(row.username) || !isBlank(row.title)) rows.add(row);
            }
        }
        if ((safeScope == LinuxDoRepository.SearchScope.ALL
                || safeScope == LinuxDoRepository.SearchScope.CATEGORIES) && categories != null) {
            for (int i = 0; i < categories.size(); i++) {
                JSONObject category = categories.getJSONObject(i);
                if (category == null) continue;
                SearchRow row = new SearchRow();
                row.kind = SearchRow.Kind.CATEGORY;
                row.categoryId = category.getIntValue("id");
                row.categoryName = first(category.getString("name"), category.getString("slug"));
                row.slug = category.getString("slug");
                row.title = row.categoryName;
                row.replyCount = Math.max(0, category.getIntValue("topic_count"));
                row.excerpt = plain(first(category.getString("description_text"),
                        category.getString("description")));
                if (!isBlank(row.title) && row.categoryId > 0) rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Category search is intentionally local to the cached/site metadata
     * response. Discourse does not include categories in every /search.json
     * variant, while the site document is already used for the board labels.
     */
    static List<SearchRow> parseCategorySearch(String json, String query) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing categories");
        JSONObject list = root.getJSONObject("category_list");
        JSONArray categories = list == null
                ? root.getJSONArray("categories") : list.getJSONArray("categories");
        if (categories == null) throw new IllegalArgumentException("Missing categories");
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<SearchRow> rows = new ArrayList<>();
        addCategorySearchRows(categories, needle, rows);
        return rows;
    }

    /**
     * Flattens the native Discourse category tree for the compact board picker.
     * The topic-list parser intentionally normalizes level partitions to their
     * parent board name; the directory keeps the child label and parent label
     * separately so users can still distinguish public/Lv1/Lv2 partitions.
     */
    static List<CategoryRow> parseCategoryDirectory(String json) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing categories");
        JSONArray categories = categoryArray(root);
        if (categories == null) throw new IllegalArgumentException("Missing categories");

        Map<Integer, LinuxDoTopicPayloadParser.CategoryRecord> records =
                LinuxDoTopicPayloadParser.parseCategoryRecords(json);
        Map<Integer, JSONObject> rawById = new LinkedHashMap<>();
        collectRawCategories(categories, 0, rawById);
        // Some /categories.json responses are flat and only expose
        // parent_category_id; collectRawCategories already retains those rows.
        List<CategoryRow> rows = new ArrayList<>(rawById.size());
        for (JSONObject raw : rawById.values()) {
            if (raw == null) continue;
            int id = raw.getIntValue("id");
            if (id <= 0) continue;
            LinuxDoTopicPayloadParser.CategoryRecord record = records.get(id);
            String rawName = first(raw.getString("name"), raw.getString("slug"));
            String normalizedName = stripCategoryLevelSuffix(rawName);
            CategoryRow row = new CategoryRow();
            row.categoryId = id;
            row.parentId = raw.containsKey("parent_category_id")
                    ? raw.getIntValue("parent_category_id")
                    : record == null ? 0 : record.parentId;
            row.name = first(normalizedName, rawName);
            row.slug = raw.getString("slug");
            row.slugPath = categorySlugPath(id, rawById);
            row.visibility = record == null ? null : record.visibility;
            row.description = plain(first(raw.getString("description_text"),
                    raw.getString("description")));
            row.topicCount = Math.max(0, raw.getIntValue("topic_count"));
            rows.add(row);
        }
        Map<Integer, String> names = new HashMap<>();
        for (CategoryRow row : rows) names.put(row.categoryId, row.name);
        for (CategoryRow row : rows) {
            row.parentName = row.parentId <= 0 ? "" : names.get(row.parentId);
            if (row.parentName == null) row.parentName = "";
        }
        return rows;
    }

    private static String categorySlugPath(int categoryId, Map<Integer, JSONObject> rawById) {
        List<String> segments = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        int currentId = categoryId;
        while (currentId > 0 && visited.add(currentId)) {
            JSONObject category = rawById.get(currentId);
            if (category == null) break;
            String slug = category.getString("slug");
            if (!isBlank(slug)) segments.add(slug.trim());
            currentId = category.getIntValue("parent_category_id");
        }
        Collections.reverse(segments);
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        return result.toString();
    }

    private static JSONArray categoryArray(JSONObject root) {
        JSONObject list = root.getJSONObject("category_list");
        JSONArray categories = list == null ? null : list.getJSONArray("categories");
        return categories == null ? root.getJSONArray("categories") : categories;
    }

    private static void collectRawCategories(
            JSONArray categories, int fallbackParentId, Map<Integer, JSONObject> out) {
        for (int i = 0; i < categories.size(); i++) {
            JSONObject category = categories.getJSONObject(i);
            if (category == null) continue;
            int id = category.getIntValue("id");
            if (id > 0 && fallbackParentId > 0 && !category.containsKey("parent_category_id")) {
                category.put("parent_category_id", fallbackParentId);
            }
            if (id > 0) out.put(id, category);
            Object rawChildren = category.get("subcategory_list");
            JSONArray children = rawChildren instanceof JSONArray
                    ? (JSONArray) rawChildren : rawChildren instanceof JSONObject
                    ? ((JSONObject) rawChildren).getJSONArray("categories") : null;
            if (children != null) collectRawCategories(children, id, out);
        }
    }

    private static String stripCategoryLevelSuffix(String value) {
        if (isBlank(value)) return value;
        String result = CATEGORY_LEVEL_SUFFIX.matcher(value.trim()).replaceFirst("").trim();
        return result.isEmpty() ? value.trim() : result;
    }

    private static void addCategorySearchRows(
            JSONArray categories, String needle, List<SearchRow> rows) {
        for (int i = 0; i < categories.size() && rows.size() < 100; i++) {
            JSONObject category = categories.getJSONObject(i);
            if (category == null) continue;
            String name = first(category.getString("name"), category.getString("slug"));
            String slug = category.getString("slug");
            String description = plain(first(category.getString("description_text"),
                    category.getString("description")));
            String haystack = (first(name, "") + " " + first(slug, "") + " "
                    + first(description, "")).toLowerCase(java.util.Locale.ROOT);
            if (needle.isEmpty() || haystack.contains(needle)) {
                SearchRow row = new SearchRow();
                row.kind = SearchRow.Kind.CATEGORY;
                row.categoryId = category.getIntValue("id");
                row.categoryName = name;
                row.slug = slug;
                row.title = name;
                row.replyCount = Math.max(0, category.getIntValue("topic_count"));
                row.excerpt = description;
                if (row.categoryId > 0 && !isBlank(row.title)) rows.add(row);
            }
            Object rawChildren = category.get("subcategory_list");
            JSONArray children = rawChildren instanceof JSONArray
                    ? (JSONArray) rawChildren : rawChildren instanceof JSONObject
                    ? ((JSONObject) rawChildren).getJSONArray("categories") : null;
            if (children != null) addCategorySearchRows(children, needle, rows);
        }
    }

    static Profile parseProfile(String userJson, String summaryJson, String badgesJson) {
        return parseProfile(userJson, summaryJson, badgesJson, null);
    }

    static Profile parseProfile(
        String userJson, String summaryJson, String badgesJson, String activityJson) {
        JSONObject root = JSON.parseObject(userJson);
        JSONObject user = root == null ? null : root.getJSONObject("user");
        if (user == null) throw new IllegalArgumentException("Missing profile");
        Profile profile = new Profile();
        profile.username = user.getString("username");
        profile.name = user.getString("name");
        profile.avatar = avatar(user.getString("avatar_template"), 144);
        profile.bio = plain(first(user.getString("bio_cooked"), user.getString("bio_raw")));
        profile.location = user.getString("location");
        profile.title = user.getString("title");
        profile.primaryGroup = user.getString("primary_group_name");
        profile.trustLevel = user.getIntValue("trust_level");
        profile.joinedAt = epoch(user.getString("created_at"));
        profile.lastSeenAt = epoch(user.getString("last_seen_at"));
        profile.postCount = firstPositive(user.getIntValue("post_count"),
                user.getIntValue("posts_count"));
        profile.topicCount = firstPositive(user.getIntValue("topic_count"),
                user.getIntValue("topics_count"));
        profile.timeRead = Math.max(0, user.getIntValue("time_read"));
        if (summaryJson != null) {
            try {
                JSONObject summaryRoot = JSON.parseObject(summaryJson);
                if (summaryRoot != null) parseSummary(profile, summaryRoot);
            } catch (RuntimeException ignored) { }
        }
        if (badgesJson != null) {
            try {
                JSONObject badgesRoot = JSON.parseObject(badgesJson);
                if (badgesRoot != null) parseBadges(profile, badgesRoot);
            } catch (RuntimeException ignored) { }
        }
        if (activityJson != null) {
            try {
                JSONObject activityRoot = JSON.parseObject(activityJson);
                if (activityRoot != null) parseActivity(profile, activityRoot);
            } catch (RuntimeException ignored) { }
        }
        return profile;
    }

    private static void parseSummary(Profile profile, JSONObject root) {
        JSONObject summary = root.getJSONObject("user_summary");
        if (summary == null) summary = root;
        profile.daysVisited = firstPositive(summary.getIntValue("days_visited"),
                summary.getIntValue("days_visited_count"));
        profile.likesReceived = firstPositive(summary.getIntValue("likes_received"),
                summary.getIntValue("likes_received_count"));
        profile.likesGiven = Math.max(0, summary.getIntValue("likes_given"));
        profile.postsReadCount = Math.max(0, summary.getIntValue("posts_read_count"));
        profile.topicsEntered = Math.max(0, summary.getIntValue("topics_entered"));
        profile.timeRead = Math.max(profile.timeRead, summary.getIntValue("time_read"));
        profile.postCount = firstPositive(profile.postCount, summary.getIntValue("post_count"));
        profile.topicCount = firstPositive(profile.topicCount,
                summary.getIntValue("topic_count"));
        addProfileTopics(profile.rows, summary.getJSONArray("top_topics"), "热门主题");
        addProfileTopics(profile.rows, summary.getJSONArray("recent_topics"), "最近主题");
        JSONArray replies = summary.getJSONArray("top_replies");
        if (replies != null) {
            for (int i = 0; i < replies.size() && profile.rows.size() < 30; i++) {
                JSONObject reply = replies.getJSONObject(i);
                ProfileRow row = new ProfileRow();
                row.topicId = firstPositive(reply.getIntValue("topic_id"),
                        reply.getIntValue("topic_id_or_post_id"));
                row.postNumber = firstPositive(reply.getIntValue("post_number"),
                        reply.getIntValue("post_number_in_topic"));
                row.title = first(reply.getString("topic_title"), plain(reply.getString("excerpt")));
                row.subtitle = "热门回复";
                row.createdAt = epoch(reply.getString("created_at"));
                row.isReply = true;
                if (row.topicId > 0) profile.rows.add(row);
            }
        }
    }

    private static void addProfileTopics(List<ProfileRow> out, JSONArray topics, String label) {
        if (topics == null) return;
        for (int i = 0; i < topics.size() && out.size() < 30; i++) {
            JSONObject topic = topics.getJSONObject(i);
            ProfileRow row = new ProfileRow();
            row.topicId = firstPositive(topic.getIntValue("id"), topic.getIntValue("topic_id"));
            row.postNumber = 1;
            row.title = first(topic.getString("title"), topic.getString("topic_title"));
            row.subtitle = label + " · " + Math.max(0, topic.getIntValue("posts_count") - 1) + " 回复";
            row.createdAt = epoch(first(topic.getString("last_posted_at"),
                    topic.getString("created_at")));
            if (row.topicId > 0 && row.title != null) out.add(row);
        }
    }

    private static void parseActivity(Profile profile, JSONObject root) {
        List<ProfileRow> activity = parseActivityRows(root);
        profile.activityOffset = activity.size();
        profile.activityHasMore = activity.size() >= PROFILE_ACTIVITY_PAGE_SIZE;
        Set<String> existing = new LinkedHashSet<>();
        for (ProfileRow row : profile.rows) existing.add(row.topicId + ":" + row.postNumber);
        for (ProfileRow row : activity) {
            if (profile.rows.size() >= 40) break;
            if (existing.add(row.topicId + ":" + row.postNumber)) profile.rows.add(row);
        }
    }

    static List<ProfileRow> parseProfileActivity(String json) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) return new ArrayList<>();
        return parseActivityRows(root);
    }

    private static List<ProfileRow> parseActivityRows(JSONObject root) {
        if (root == null) return new ArrayList<>();
        Object rawActions = root.get("user_actions");
        JSONArray actions = rawActions instanceof JSONArray
                ? (JSONArray) rawActions : rawActions instanceof JSONObject
                ? ((JSONObject) rawActions).getJSONArray("actions") : null;
        if (actions == null) actions = root.getJSONArray("actions");
        if (actions == null) return new ArrayList<>();
        List<ProfileRow> rows = new ArrayList<>();
        for (int i = 0; i < actions.size() && rows.size() < PROFILE_ACTIVITY_PAGE_SIZE; i++) {
            JSONObject action = actions.getJSONObject(i);
            if (action == null) continue;
            ProfileRow row = new ProfileRow();
            row.topicId = firstPositive(action.getIntValue("topic_id"),
                    action.getIntValue("topic_id_or_post_id"));
            row.postNumber = firstPositive(action.getIntValue("post_number"),
                    action.getIntValue("post_number_in_topic"));
            row.title = first(action.getString("topic_title"),
                    first(action.getString("title"), plain(action.getString("excerpt"))));
            row.createdAt = epoch(action.getString("created_at"));
            row.isReply = row.postNumber > 1
                    || "reply_created".equals(action.getString("action_type"))
                    || "post_replied".equals(action.getString("action_type"));
            row.subtitle = row.isReply ? "最近回复" : "最近主题";
            if (row.topicId > 0 && !isBlank(row.title)) rows.add(row);
        }
        return rows;
    }

    private static void parseBadges(Profile profile, JSONObject root) {
        Map<Integer, String> names = new HashMap<>();
        JSONArray badges = root.getJSONArray("badges");
        if (badges == null) badges = root.getJSONArray("badge_types");
        if (badges != null) for (int i = 0; i < badges.size(); i++) {
            JSONObject badge = badges.getJSONObject(i);
            if (badge != null) names.put(badge.getIntValue("id"), badge.getString("name"));
        }
        JSONArray granted = root.getJSONArray("user_badges");
        if (granted == null) granted = root.getJSONArray("badges_granted");
        if (granted != null) for (int i = 0; i < granted.size() && profile.badges.size() < 12; i++) {
            JSONObject badge = granted.getJSONObject(i);
            String name = badge == null ? null : names.get(badge.getIntValue("badge_id"));
            if (name != null && !profile.badges.contains(name)) profile.badges.add(name);
        }
    }

    static List<NotificationRow> parseNotifications(String json) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing notifications");
        JSONArray notifications = root.getJSONArray("notifications");
        if (notifications == null) throw new IllegalArgumentException("Missing notifications");
        Map<Integer, String> types = invertTypes(root.getJSONObject("notification_types"));
        List<NotificationRow> rows = new ArrayList<>();
        for (int i = 0; i < notifications.size(); i++) {
            JSONObject item = notifications.getJSONObject(i);
            if (item == null) continue;
            NotificationRow row = new NotificationRow();
            row.id = item.getLongValue("id");
            row.read = item.getBooleanValue("read") || !isBlank(item.getString("read_at"));
            Object rawType = item.get("notification_type");
            if (rawType == null) rawType = item.get("type");
            row.type = rawType instanceof String
                    ? (String) rawType : types.get(item.getIntValue("notification_type"));
            if (isBlank(row.type)) row.type = "notification";
            JSONObject data;
            Object rawData = item.get("data");
            if (rawData instanceof JSONObject) data = (JSONObject) rawData;
            else if (rawData instanceof String) {
                try { data = JSON.parseObject((String) rawData); }
                catch (RuntimeException ignored) { data = new JSONObject(); }
            } else data = new JSONObject();
            if ("notification".equals(row.type) || "custom".equals(row.type)
                    || "custom_event".equals(row.type)) {
                String hintedType = first(data.getString("notification_type"),
                        first(data.getString("type"), data.getString("event")));
                if (!isBlank(hintedType)) row.type = hintedType;
                else {
                    String payload = data.toJSONString().toLowerCase(java.util.Locale.ROOT);
                    if (payload.contains("boost")) row.type = "boosted";
                    else if (payload.contains("reaction")) row.type = "reaction";
                }
            }
            row.topicId = firstPositive(item.getIntValue("topic_id"),
                    data.getIntValue("topic_id"));
            row.postNumber = firstPositive(item.getIntValue("post_number"),
                    data.getIntValue("post_number"));
            row.username = first(data.getString("display_username"),
                    first(data.getString("original_username"),
                            first(data.getString("username"),
                                    first(item.getString("acting_username"),
                                            item.getString("username")))));
            row.avatar = avatar(first(item.getString("acting_user_avatar_template"),
                    first(data.getString("acting_user_avatar_template"),
                            first(data.getString("avatar_template"),
                                    first(data.getString("avatar_url"),
                                            item.getString("avatar_url"))))), 96);
            row.title = first(data.getString("topic_title"),
                    first(data.getString("fancy_title"),
                            first(item.getString("fancy_title"),
                                    first(data.getString("title"), item.getString("title")))));
            row.categoryName = first(data.getString("category_name"),
                    first(data.getString("category"), item.getString("category_name")));
            row.excerpt = plain(first(data.getString("excerpt"),
                    first(data.getString("message"),
                            first(data.getString("description"), item.getString("excerpt")))));
            row.targetUrl = first(data.getString("url"),
                    first(data.getString("topic_url"), item.getString("url")));
            populateNotificationTargetFromUrl(row);
            row.createdAt = epoch(first(item.getString("created_at"),
                    data.getString("created_at")));
            rows.add(row);
        }
        return rows;
    }

    /**
     * Custom/plugin notifications sometimes carry only /t/... in data.url. Recovering its native
     * destination keeps those entries inside the fast topic reader instead of degrading them to a
     * generic web notification. No network or Android URI parser is needed here.
     */
    private static void populateNotificationTargetFromUrl(NotificationRow row) {
        if (row == null || isBlank(row.targetUrl)) return;
        String value = row.targetUrl.trim();
        int marker = value.indexOf("/t/");
        if (marker < 0) return;
        String path = value.substring(marker + 3);
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int fragment = path.indexOf('#');
        if (fragment >= 0) path = path.substring(0, fragment);
        String[] segments = path.split("/");
        int numberIndex = segments.length > 0 && isPositiveInteger(segments[0]) ? 0
                : segments.length > 1 && isPositiveInteger(segments[1]) ? 1 : -1;
        if (numberIndex < 0) return;
        if (row.topicId <= 0) row.topicId = positiveInteger(segments[numberIndex]);
        if (row.postNumber <= 0 && segments.length > numberIndex + 1
                && isPositiveInteger(segments[numberIndex + 1])) {
            row.postNumber = positiveInteger(segments[numberIndex + 1]);
        }
    }

    private static boolean isPositiveInteger(String value) {
        return positiveInteger(value) > 0;
    }

    private static int positiveInteger(String value) {
        if (isBlank(value) || value.length() > 10) return 0;
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(0, parsed);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    static String parseCurrentUsername(String json) {
        JSONObject root = JSON.parseObject(json);
        JSONObject current = root == null ? null : root.getJSONObject("current_user");
        return current == null ? null : current.getString("username");
    }

    static int parseUnreadTotal(String json) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) return 0;
        return Math.max(0, firstPositive(root.getIntValue("unread_notifications"),
                firstPositive(root.getIntValue("unread_count"), root.getIntValue("total"))));
    }

    private static Map<Integer, JSONObject> byId(JSONArray values) {
        if (values == null) return Collections.emptyMap();
        Map<Integer, JSONObject> result = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            JSONObject value = values.getJSONObject(i);
            if (value != null) result.put(value.getIntValue("id"), value);
        }
        return result;
    }

    private static Map<Integer, String> invertTypes(JSONObject object) {
        Map<Integer, String> result = new HashMap<>();
        if (object != null) for (Map.Entry<String, Object> entry : object.entrySet()) {
            try {
                // Discourse has emitted both {"replied": 5} and
                // {"5": "replied"} across endpoint versions.
                int keyId = Integer.parseInt(entry.getKey());
                if (entry.getValue() instanceof String) {
                    result.put(keyId, (String) entry.getValue());
                    continue;
                }
            } catch (NumberFormatException ignored) { }
            if (entry.getValue() instanceof Number) {
                result.put(((Number) entry.getValue()).intValue(), entry.getKey());
            } else if (entry.getValue() instanceof String) {
                try {
                    result.put(Integer.parseInt((String) entry.getValue()), entry.getKey());
                } catch (NumberFormatException ignored) { }
            }
        }
        // Stable fallback names keep older/private payloads readable when the
        // endpoint omits notification_types.
        putFallbackType(result, 1, "mentioned");
        putFallbackType(result, 2, "replied");
        putFallbackType(result, 3, "quoted");
        putFallbackType(result, 4, "edited");
        putFallbackType(result, 5, "liked");
        putFallbackType(result, 6, "private_message");
        putFallbackType(result, 7, "invited_to_private_message");
        putFallbackType(result, 8, "invitee_accepted");
        putFallbackType(result, 9, "posted");
        putFallbackType(result, 10, "moved_post");
        putFallbackType(result, 11, "linked");
        putFallbackType(result, 12, "granted_badge");
        putFallbackType(result, 13, "invited_to_topic");
        putFallbackType(result, 14, "custom");
        putFallbackType(result, 15, "group_mentioned");
        putFallbackType(result, 17, "watching_first_post");
        putFallbackType(result, 18, "topic_reminder");
        putFallbackType(result, 19, "liked_consolidated");
        putFallbackType(result, 24, "bookmark_reminder");
        putFallbackType(result, 25, "reaction");
        putFallbackType(result, 36, "watching_category_or_tag");
        putFallbackType(result, 43, "boost");
        return result;
    }

    /** Parses connect.linux.do's server-rendered progress card without a WebView. */
    static List<TrustRequirement> parseTrustProgress(String html) {
        if (isBlank(html)) throw new IllegalArgumentException("Missing trust progress");
        List<TrustRequirement> rows = new ArrayList<>();
        collectTrustBlocks(html, "tl3-ring", rows);
        collectTrustBlocks(html, "tl3-bar-item", rows);
        collectTrustBlocks(html, "tl3-quota-card", rows);
        collectTrustBlocks(html, "tl3-veto-item", rows);
        if (rows.isEmpty()) throw new IllegalArgumentException("Missing trust metrics");
        return rows;
    }

    private static void collectTrustBlocks(
            String html, String blockClass, List<TrustRequirement> out) {
        Pattern startPattern = Pattern.compile("(?is)<(?:div|li)[^>]*class=['\"][^'\"]*\\b"
                + Pattern.quote(blockClass) + "\\b[^'\"]*['\"][^>]*>");
        Matcher starts = startPattern.matcher(html);
        List<Integer> positions = new ArrayList<>();
        while (starts.find()) positions.add(starts.start());
        for (int index = 0; index < positions.size(); index++) {
            int start = positions.get(index);
            int end = index + 1 < positions.size()
                    ? positions.get(index + 1) : Math.min(html.length(), start + 2400);
            String block = html.substring(start, Math.min(end, start + 2400));
            String label = classText(block, blockClass.contains("veto")
                    ? "tl3-veto-label" : blockClass.contains("quota")
                    ? "tl3-quota-label" : blockClass.contains("ring")
                    ? "tl3-ring-label" : "tl3-bar-label");
            if (isBlank(label)) continue;
            int current = cssNumber(block, "--val");
            int required = cssNumber(block, "--max");
            String numberText = classText(block, blockClass.contains("bar")
                    ? "tl3-bar-nums" : blockClass.contains("quota")
                    ? "tl3-quota-nums" : "tl3-veto-value");
            if ((current <= 0 || required <= 0) && !isBlank(numberText)) {
                Matcher numbers = Pattern.compile("(\\d[\\d,]*)\\s*(?:/|／|of)\\s*(\\d[\\d,]*)")
                        .matcher(numberText);
                if (numbers.find()) {
                    current = parseMetricInt(numbers.group(1));
                    required = parseMetricInt(numbers.group(2));
                } else if (current <= 0) {
                    Matcher single = Pattern.compile("-?\\d[\\d,]*").matcher(numberText);
                    if (single.find()) current = parseMetricInt(single.group());
                }
            }
            boolean met = block.contains(" status-met") || block.contains(" met")
                    || (!block.contains("unmet") && required > 0 && current >= required);
            TrustRequirement.Kind kind = blockClass.contains("ring")
                    ? TrustRequirement.Kind.RING : blockClass.contains("bar")
                    ? TrustRequirement.Kind.BAR : blockClass.contains("quota")
                    ? TrustRequirement.Kind.QUOTA : TrustRequirement.Kind.VETO;
            out.add(new TrustRequirement(label, current, required, numberText, met, kind));
        }
    }

    private static String classText(String block, String className) {
        Matcher matcher = Pattern.compile("(?is)<[^>]*class=['\"][^'\"]*\\b"
                + Pattern.quote(className) + "\\b[^'\"]*['\"][^>]*>(.*?)</[^>]+>")
                .matcher(block);
        return matcher.find() ? plain(matcher.group(1)) : "";
    }

    private static int cssNumber(String block, String name) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + "\\s*:\\s*([0-9.]+)")
                .matcher(block);
        if (!matcher.find()) return 0;
        try { return (int) Math.round(Double.parseDouble(matcher.group(1))); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static int parseMetricInt(String value) {
        try { return Integer.parseInt(value.replace(",", "")); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static void putFallbackType(Map<Integer, String> values, int id, String name) {
        if (!values.containsKey(id)) values.put(id, name);
    }

    private static String avatar(String value, int size) {
        if (value == null) return null;
        String result = value.replace("{size}", String.valueOf(size));
        if (result.startsWith("//")) return "https:" + result;
        if (result.startsWith("/")) return LinuxDoConstants.ORIGIN + result;
        return result;
    }

    private static int epoch(String iso) {
        try { return (int) Instant.parse(iso).getEpochSecond(); }
        catch (Exception ignored) { return 0; }
    }

    private static String plain(String html) {
        if (html == null) return "";
        return html.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private static String parseTags(JSONArray tags) {
        if (tags == null || tags.isEmpty()) return null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tags.size() && i < 5; i++) {
            Object raw = tags.get(i);
            String tag = raw instanceof JSONObject
                    ? ((JSONObject) raw).getString("name") : raw == null ? null : String.valueOf(raw);
            if (isBlank(tag)) continue;
            if (result.length() > 0) result.append("  ");
            result.append('#').append(tag.trim());
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static String first(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int firstPositive(int first, int second) {
        return first > 0 ? first : Math.max(0, second);
    }

    static final class SearchRow {
        enum Kind { TOPIC, POST, USER, CATEGORY }
        Kind kind = Kind.POST;
        int topicId, postNumber, createdAt;
        int userId, categoryId, replyCount, trustLevel;
        String title, displayName, excerpt, username, categoryName, slug, tags, avatar;
    }

    static final class CategoryRow {
        int categoryId, parentId, topicCount;
        String name, parentName, slug, slugPath, description, visibility;
    }
    public static final class NotificationRow {
        long id;
        int topicId, postNumber, createdAt;
        boolean read;
        String type, title, excerpt, username, avatar, categoryName, targetUrl;

        public long getId() { return id; }
        public int getTopicId() { return topicId; }
        public int getPostNumber() { return postNumber; }
        public int getCreatedAt() { return createdAt; }
        public boolean isRead() { return read; }
        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getExcerpt() { return excerpt; }
        public String getUsername() { return username; }
        public String getCategoryName() { return categoryName; }
        public String getTargetUrl() { return targetUrl; }
    }
    static final class ProfileRow {
        int topicId, postNumber, createdAt;
        boolean isReply;
        String title, subtitle;
    }
    static final class Profile {
        String username, name, avatar, bio, location, title, primaryGroup;
        int trustLevel, joinedAt, lastSeenAt, postCount, topicCount, daysVisited, likesReceived;
        int likesGiven, postsReadCount, topicsEntered, timeRead;
        int activityOffset;
        boolean activityHasMore;
        final List<String> badges = new ArrayList<>();
        final List<ProfileRow> rows = new ArrayList<>();
    }
    static final class TrustRequirement {
        enum Kind { RING, BAR, QUOTA, VETO }
        final String label;
        final int current;
        final int required;
        final String displayValue;
        final boolean met;
        final Kind kind;

        TrustRequirement(String label, int current, int required,
                String displayValue, boolean met, Kind kind) {
            this.label = label;
            this.current = Math.max(0, current);
            this.required = Math.max(0, required);
            this.displayValue = displayValue;
            this.met = met;
            this.kind = kind == null ? Kind.BAR : kind;
        }
    }

    private LinuxDoFeaturePayloadParser() { }
}
