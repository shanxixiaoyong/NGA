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

/** Android-free decoder for the small Discourse payload subset used by the topic list. */
final class LinuxDoTopicPayloadParser {

    static Map<Integer, String> parseCategories(String json) {
        JSONObject root = JSON.parseObject(json);
        JSONObject list = root.getJSONObject("category_list");
        JSONArray categories = list == null ? null : list.getJSONArray("categories");
        if (categories == null) throw new IllegalArgumentException("Missing categories");
        Map<Integer, String> result = new HashMap<>();
        for (int index = 0; index < categories.size(); index++) {
            JSONObject category = categories.getJSONObject(index);
            result.put(category.getIntValue("id"), category.getString("name"));
            JSONArray children = category.getJSONArray("subcategory_list");
            if (children == null) continue;
            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                JSONObject child = children.getJSONObject(childIndex);
                result.put(child.getIntValue("id"), child.getString("name"));
            }
        }
        return result;
    }

    static List<TopicRecord> parseTopics(String json, Map<Integer, String> categories) {
        JSONObject root = JSON.parseObject(json);
        Map<Integer, JSONObject> users = usersById(root.getJSONArray("users"));
        JSONObject topicList = root.getJSONObject("topic_list");
        JSONArray topics = topicList == null ? null : topicList.getJSONArray("topics");
        if (topics == null) throw new IllegalArgumentException("Missing topic list");
        List<TopicRecord> result = new ArrayList<>(topics.size());
        for (int index = 0; index < topics.size(); index++) {
            JSONObject topic = topics.getJSONObject(index);
            TopicRecord row = new TopicRecord();
            row.id = topic.getIntValue("id");
            row.categoryId = topic.getIntValue("category_id");
            row.categoryName = categories.get(row.categoryId);
            row.title = topic.getString("title");
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

    private static Map<Integer, JSONObject> usersById(JSONArray users) {
        if (users == null) return Collections.emptyMap();
        Map<Integer, JSONObject> result = new HashMap<>();
        for (int index = 0; index < users.size(); index++) {
            JSONObject user = users.getJSONObject(index);
            result.put(user.getIntValue("id"), user);
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
        String categoryName;
        String title;
        int replyCount;
        int createdAt;
        int lastPostedAt;
        int authorId;
        String author;
        String lastPoster;
    }

    private LinuxDoTopicPayloadParser() {
    }
}
