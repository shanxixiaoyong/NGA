package sp.phone.linuxdo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android-free decoder for the small Discourse payload subset used by the topic list. */
final class LinuxDoTopicPayloadParser {

    private static final Pattern EXPLICIT_LEVEL = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])(?:lv|level|trust[_\\s-]*level)[_\\s-]*([1-3])(?:\\s*区)?(?:$|[^0-9])");
    private static final Pattern LEVEL_SUFFIX = Pattern.compile(
            "(?i)\\s*[-–—·|/_]*\\s*(?:lv|level|trust[_\\s-]*level)[_\\s-]*[1-3]\\s*(?:区)?\\s*$");

    static Map<Integer, String> parseCategories(String json) {
        Map<Integer, CategoryRecord> records = parseCategoryRecords(json);
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, CategoryRecord> entry : records.entrySet()) {
            result.put(entry.getKey(), entry.getValue().name);
        }
        return result;
    }

    static Map<Integer, CategoryRecord> parseCategoryRecords(String json) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing categories");
        JSONObject list = root.getJSONObject("category_list");
        JSONArray categories = list == null
                ? root.getJSONArray("categories") : list.getJSONArray("categories");
        if (categories == null) throw new IllegalArgumentException("Missing categories");
        Map<Integer, CategoryRecord> result = new HashMap<>();
        for (int index = 0; index < categories.size(); index++) {
            JSONObject category = categories.getJSONObject(index);
            if (category == null) continue;
            addCategory(result, category, 0);
            Object rawChildren = category.get("subcategory_list");
            JSONArray children = rawChildren instanceof JSONArray
                    ? (JSONArray) rawChildren : rawChildren instanceof JSONObject
                    ? ((JSONObject) rawChildren).getJSONArray("categories") : null;
            if (children == null) continue;
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                JSONObject child = children.getJSONObject(childIndex);
                if (child != null) addCategory(result, child, category.getIntValue("id"));
            }
        }
        for (CategoryRecord record : result.values()) {
            CategoryRecord parent = result.get(record.parentId);
            if (parent != null && isBlank(record.iconUrl)) {
                record.iconUrl = parent.iconUrl;
            }
            boolean accessPartition = parent != null
                    && (record.visibility != null || isPublicPartition(record));
            record.boardId = accessPartition ? parent.id : record.id;
            if (accessPartition) {
                record.name = parent.name;
            } else if (record.visibility != null) {
                String baseName = LEVEL_SUFFIX.matcher(record.name == null ? "" : record.name)
                        .replaceFirst("").trim();
                if (!baseName.isEmpty()) record.name = baseName;
            }
        }
        return result;
    }

    private static void addCategory(Map<Integer, CategoryRecord> result, JSONObject category,
                                    int fallbackParentId) {
        if (category == null) return;
        CategoryRecord record = new CategoryRecord();
        record.id = category.getIntValue("id");
        record.name = category.getString("name");
        record.slug = category.getString("slug");
        record.parentId = category.containsKey("parent_category_id")
                ? category.getIntValue("parent_category_id") : fallbackParentId;
        record.visibility = resolveVisibility(category);
        record.iconUrl = categoryIconUrl(category);
        result.put(record.id, record);
    }

    private static String categoryIconUrl(JSONObject category) {
        if (category == null) return null;
        Object logo = category.get("uploaded_logo");
        if (logo instanceof JSONObject) {
            String url = ((JSONObject) logo).getString("url");
            if (!isBlank(url)) return normalizeIconUrl(url);
        }
        String[] keys = {"uploaded_logo_url", "icon_url"};
        for (String key : keys) {
            String url = category.getString(key);
            if (!isBlank(url)) return normalizeIconUrl(url);
        }
        return null;
    }

    private static String normalizeIconUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String resolveVisibility(JSONObject category) {
        int minimum = Integer.MAX_VALUE;
        JSONArray permissions = category.getJSONArray("group_permissions");
        if (permissions != null) {
            for (int index = 0; index < permissions.size(); index++) {
                JSONObject permission = permissions.getJSONObject(index);
                String group = permission == null ? null : permission.getString("group_name");
                if (group == null || !group.startsWith("trust_level_")) continue;
                try {
                    int level = Integer.parseInt(group.substring("trust_level_".length()));
                    if (level >= 1 && level <= 3) minimum = Math.min(minimum, level);
                } catch (NumberFormatException ignored) { }
            }
        }
        String[] numericKeys = {
                "minimum_required_trust_level", "required_trust_level", "min_trust_level"
        };
        for (String key : numericKeys) {
            Integer level = category.getInteger(key);
            if (level != null && level >= 1 && level <= 3) minimum = Math.min(minimum, level);
        }
        if (minimum == Integer.MAX_VALUE) {
            minimum = explicitLevel(category.getString("name"));
        }
        if (minimum == Integer.MAX_VALUE) {
            minimum = explicitLevel(category.getString("slug"));
        }
        // read_restricted alone does not reveal a trust boundary, so never invent one.
        return minimum == Integer.MAX_VALUE ? null : "Lv" + minimum;
    }

    private static int explicitLevel(String value) {
        if (value == null) return Integer.MAX_VALUE;
        Matcher matcher = EXPLICIT_LEVEL.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }

    private static boolean isPublicPartition(CategoryRecord record) {
        String name = record.name == null ? "" : record.name.trim();
        String slug = record.slug == null ? "" : record.slug.trim();
        return "公开".equals(name) || "公开区".equals(name)
                || "public".equalsIgnoreCase(name) || "public".equalsIgnoreCase(slug);
    }

    static List<TopicRecord> parseTopics(String json, Map<Integer, String> categories) {
        return parseTopics(json, categories, Collections.emptyMap());
    }

    /** Decodes topics while retaining the access partition resolved from site categories. */
    static List<TopicRecord> parseTopicsWithCategoryRecords(
            String json, Map<Integer, CategoryRecord> categories) {
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, String> visibility = new HashMap<>();
        Map<Integer, String> icons = new HashMap<>();
        Map<Integer, Integer> boardIds = new HashMap<>();
        for (Map.Entry<Integer, CategoryRecord> entry : categories.entrySet()) {
            CategoryRecord record = entry.getValue();
            if (record == null) continue;
            names.put(entry.getKey(), record.name);
            boardIds.put(entry.getKey(), record.boardId);
            if (record.visibility != null) {
                visibility.put(entry.getKey(), record.visibility);
            }
            if (!isBlank(record.iconUrl)) {
                icons.put(entry.getKey(), record.iconUrl);
            }
        }
        return parseTopics(json, names, visibility, icons, boardIds);
    }

    private static List<TopicRecord> parseTopics(
            String json,
            Map<Integer, String> categories,
            Map<Integer, String> visibilityByCategory) {
        return parseTopics(json, categories, visibilityByCategory, Collections.emptyMap(),
                Collections.emptyMap());
    }

    private static List<TopicRecord> parseTopics(
            String json,
            Map<Integer, String> categories,
            Map<Integer, String> visibilityByCategory,
            Map<Integer, String> iconByCategory,
            Map<Integer, Integer> boardIdByCategory) {
        JSONObject root = JSON.parseObject(json);
        if (root == null) throw new IllegalArgumentException("Missing topic list");
        Map<Integer, JSONObject> users = usersById(root.getJSONArray("users"));
        JSONObject topicList = root.getJSONObject("topic_list");
        JSONArray topics = topicList == null ? null : topicList.getJSONArray("topics");
        if (topics == null) throw new IllegalArgumentException("Missing topic list");
        List<TopicRecord> result = new ArrayList<>(topics.size());
        for (int index = 0; index < topics.size(); index++) {
            JSONObject topic = topics.getJSONObject(index);
            if (topic == null) continue;
            TopicRecord row = new TopicRecord();
            row.id = topic.getIntValue("id");
            row.categoryId = topic.getIntValue("category_id");
            Integer boardId = boardIdByCategory.get(row.categoryId);
            row.boardId = boardId == null || boardId == 0 ? row.categoryId : boardId;
            row.categoryName = categories.get(row.categoryId);
            row.visibility = visibilityByCategory.get(row.categoryId);
            row.categoryIconUrl = iconByCategory.get(row.categoryId);
            row.title = topic.getString("title");
            row.tags = parseTags(topic.getJSONArray("tags"));
            row.replyCount = Math.max(0, topic.getIntValue("posts_count") - 1);
            row.createdAt = epochSeconds(topic.getString("created_at"));
            row.lastPostedAt = epochSeconds(firstNonBlank(
                    topic.getString("bumped_at"), topic.getString("last_posted_at")));
            JSONArray posters = topic.getJSONArray("posters");
            if (posters != null && !posters.isEmpty()) {
                row.authorId = posters.getJSONObject(0).getIntValue("user_id");
                JSONObject user = users.get(row.authorId);
                row.author = user == null ? "" : firstNonBlank(
                        user.getString("name"), user.getString("username"));
            }
            row.lastPoster = topic.getString("last_poster_username");
            result.add(row);
        }
        return result;
    }

    private static String parseTags(JSONArray tags) {
        if (tags == null || tags.isEmpty()) return null;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < tags.size() && index < 5; index++) {
            Object raw = tags.get(index);
            String tag;
            if (raw instanceof JSONObject) {
                tag = ((JSONObject) raw).getString("name");
            } else {
                tag = raw == null ? null : String.valueOf(raw);
            }
            if (tag == null || tag.trim().isEmpty()) continue;
            if (result.length() > 0) result.append("  ");
            result.append('#').append(tag.trim());
        }
        return result.length() == 0 ? null : result.toString();
    }

    private static Map<Integer, JSONObject> usersById(JSONArray users) {
        if (users == null) return Collections.emptyMap();
        Map<Integer, JSONObject> result = new HashMap<>();
        for (int index = 0; index < users.size(); index++) {
            JSONObject user = users.getJSONObject(index);
            if (user != null) result.put(user.getIntValue("id"), user);
        }
        return result;
    }

    private static int epochSeconds(String iso) {
        try {
            return (int) Instant.parse(iso).getEpochSecond();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }

    static final class TopicRecord {
        int id;
        int categoryId;
        int boardId;
        String categoryName;
        String title;
        int replyCount;
        int createdAt;
        int lastPostedAt;
        int authorId;
        String author;
        String lastPoster;
        String tags;
        String visibility;
        String categoryIconUrl;
    }

    static final class CategoryRecord {
        int id;
        int parentId;
        int boardId;
        String name;
        String slug;
        String visibility;
        String iconUrl;
    }

    private LinuxDoTopicPayloadParser() {
    }
}
