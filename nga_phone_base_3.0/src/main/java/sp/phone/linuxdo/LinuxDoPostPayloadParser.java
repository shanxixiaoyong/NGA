package sp.phone.linuxdo;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sp.phone.http.bean.PostReaction;

/** Pure tolerant projections from a Discourse post payload. */
final class LinuxDoPostPayloadParser {

    static int resolveLikeCount(JSONObject post) {
        int direct = nonNegativeInt(post == null ? null : post.get("like_count"), -1);
        if (direct > 0) return direct;
        Object rawActions = post == null ? null : post.get("actions_summary");
        JSONArray actions = rawActions instanceof JSONArray ? (JSONArray) rawActions : null;
        int actionLike = -1;
        if (actions != null) {
            for (int index = 0; index < actions.size(); index++) {
                JSONObject action = actions.getJSONObject(index);
                if (action == null) continue;
                int type = nonNegativeInt(action.get("id"), -1);
                if (type < 0) type = nonNegativeInt(action.get("post_action_type_id"), -1);
                if (type == 2) {
                    actionLike = Math.max(0, nonNegativeInt(action.get("count"), 0));
                    break;
                }
            }
        }
        int reactionLike = reactionLikeCount(post);
        if (reactionLike > 0) return reactionLike;
        return actionLike >= 0 ? actionLike : Math.max(0, direct);
    }

    /**
     * Projects Discourse's optional custom reaction payload into a bounded, duplicate-free list.
     * Discourse installations have returned both an array and a keyed object over time, so this
     * parser accepts either shape and simply skips malformed entries instead of failing a whole
     * article page.
     */
    static List<PostReaction> resolveReactions(JSONObject post) {
        LinkedHashMap<String, MutableReaction> reactions = new LinkedHashMap<>();
        if (post == null) return new ArrayList<>();
        String current = resolveCurrentReactionId(post);
        appendReactionValue(post.get("reactions"), reactions, current);
        appendReactionValue(post.get("emoji_reactions"), reactions, current);

        int likeCount = directOrActionLikeCount(post);
        if (likeCount > 0 && !reactions.containsKey("heart")) {
            reactions.put("heart", new MutableReaction("heart", "emoji", likeCount,
                    isLikeReaction(current)));
        }
        if (current != null && !reactions.containsKey(current)) {
            // Some responses expose the viewer's reaction separately from aggregate counts.
            // Keep a one-count optimistic projection so the selected glyph is still visible.
            reactions.put(current, new MutableReaction(current, "emoji", 1,
                    true));
        }
        List<PostReaction> result = new ArrayList<>(reactions.size());
        for (MutableReaction reaction : reactions.values()) {
            if (reaction.count <= 0) continue;
            result.add(new PostReaction(reaction.id, reaction.type, reaction.count,
                    reaction.reacted || reaction.id.equals(current)));
        }
        return result;
    }

    static String resolveCurrentReactionId(JSONObject post) {
        if (post == null) return null;
        String current = reactionId(post.get("current_user_reaction"));
        if (current == null) current = reactionId(post.get("current_user_reactions"));
        if (current == null && booleanValue(post.get("current_user_used_main_reaction"), false)) {
            current = "heart";
        }
        return current;
    }

    static boolean isLikedByViewer(JSONObject post) {
        if (post == null) return false;
        // The reactions plugin serializes the viewer's main reaction as
        // current_user_reaction={id: "heart"} rather than a separate boolean.
        // Treat only the configured main-like ids as the core like state; a custom
        // reaction such as "clap" must not make the thumb-up action look selected.
        if (isLikeReaction(resolveCurrentReactionId(post))) return true;
        return booleanValue(post.get("liked_by_current_user"), false)
                || booleanValue(post.get("current_user_liked"), false)
                || booleanValue(post.get("current_user_used_main_reaction"), false);
    }

    static boolean isSafeReactionId(String reaction) {
        return safeReactionId(reaction) != null;
    }

    private static void appendReactionValue(
            Object raw,
            LinkedHashMap<String, MutableReaction> destination,
            String currentReaction) {
        if (raw instanceof JSONArray) {
            JSONArray values = (JSONArray) raw;
            for (int index = 0; index < values.size(); index++) {
                appendReactionObject(values.get(index), destination, currentReaction, null);
            }
            return;
        }
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            String directId = firstReactionId(object);
            if (directId != null) {
                appendReactionObject(object, destination, currentReaction, directId);
                return;
            }
            // A few proxy responses use {"heart": 4, "clap": 2} rather than an array.
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                String id = safeReactionId(entry.getKey());
                if (id != null && entry.getValue() instanceof JSONObject) {
                    appendReactionObject(entry.getValue(), destination, currentReaction, id);
                    continue;
                }
                int count = nonNegativeInt(entry.getValue(), -1);
                if (id != null && count >= 0) {
                    mergeReaction(destination, id, "emoji", count,
                            id.equals(currentReaction));
                }
            }
        }
    }

    private static void appendReactionObject(
            Object raw,
            LinkedHashMap<String, MutableReaction> destination,
            String currentReaction,
            String keyedId) {
        if (!(raw instanceof JSONObject)) return;
        JSONObject object = (JSONObject) raw;
        String id = keyedId == null ? firstReactionId(object) : keyedId;
        id = safeReactionId(id);
        if (id == null) return;
        String type = firstNonBlank(object.getString("type"),
                object.getString("reaction_type"), "emoji");
        int count = firstCount(object);
        if (count < 0) count = id.equals(currentReaction) ? 1 : 0;
        mergeReaction(destination, id, type, count, id.equals(currentReaction)
                || booleanValue(object.get("reacted"), false)
                || booleanValue(object.get("current_user_reaction"), false));
    }

    private static void mergeReaction(
            LinkedHashMap<String, MutableReaction> destination,
            String id,
            String type,
            int count,
            boolean reacted) {
        MutableReaction existing = destination.get(id);
        if (existing == null) {
            destination.put(id, new MutableReaction(id, type, Math.max(0, count), reacted));
            return;
        }
        // The same reaction can be present in both `reactions` and `emoji_reactions`.
        // Counts are aggregate values, so retain the largest projection rather than double it.
        existing.count = Math.max(existing.count, Math.max(0, count));
        existing.reacted = existing.reacted || reacted;
    }

    private static String firstReactionId(JSONObject object) {
        if (object == null) return null;
        return firstNonBlank(object.getString("id"), object.getString("reaction_value"),
                object.getString("reaction"), object.getString("name"),
                object.getString("key"));
    }

    private static String reactionId(Object raw) {
        if (raw instanceof String) return safeReactionId((String) raw);
        if (raw instanceof JSONObject) return safeReactionId(firstReactionId((JSONObject) raw));
        if (raw instanceof JSONArray) {
            JSONArray values = (JSONArray) raw;
            for (int index = 0; index < values.size(); index++) {
                String id = reactionId(values.get(index));
                if (id != null) return id;
            }
        }
        return null;
    }

    private static String safeReactionId(String id) {
        if (id == null) return null;
        String normalized = id.trim();
        return normalized.matches("[A-Za-z0-9_+-]{1,64}") ? normalized : null;
    }

    private static int firstCount(JSONObject object) {
        if (object == null) return -1;
        String[] keys = {"count", "reaction_users_count", "users_count", "like_count"};
        for (String key : keys) {
            int count = nonNegativeInt(object.get(key), -1);
            if (count >= 0) return count;
        }
        return -1;
    }

    private static int reactionLikeCount(JSONObject post) {
        if (post == null) return 0;
        Object raw = post.get("reactions");
        int count = findLikeCount(raw);
        if (count > 0) return count;
        return findLikeCount(post.get("emoji_reactions"));
    }

    private static int findLikeCount(Object raw) {
        if (raw instanceof JSONArray) {
            JSONArray values = (JSONArray) raw;
            int best = 0;
            for (int index = 0; index < values.size(); index++) {
                Object value = values.get(index);
                if (!(value instanceof JSONObject)) continue;
                JSONObject object = (JSONObject) value;
                String id = safeReactionId(firstReactionId(object));
                if (isLikeReaction(id)) best = Math.max(best, firstCount(object));
            }
            return Math.max(0, best);
        }
        if (raw instanceof JSONObject) {
            JSONObject object = (JSONObject) raw;
            String directId = firstReactionId(object);
            if (directId != null && isLikeReaction(directId)) return Math.max(0, firstCount(object));
            int best = 0;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                if (!isLikeReaction(safeReactionId(entry.getKey()))) continue;
                Object value = entry.getValue();
                int count = value instanceof JSONObject
                        ? firstCount((JSONObject) value) : nonNegativeInt(value, 0);
                best = Math.max(best, Math.max(0, count));
            }
            return best;
        }
        return 0;
    }

    private static int directOrActionLikeCount(JSONObject post) {
        int direct = nonNegativeInt(post == null ? null : post.get("like_count"), -1);
        if (direct > 0) return direct;
        Object rawActions = post == null ? null : post.get("actions_summary");
        if (rawActions instanceof JSONArray) {
            JSONArray actions = (JSONArray) rawActions;
            for (int index = 0; index < actions.size(); index++) {
                JSONObject action = actions.getJSONObject(index);
                if (action == null) continue;
                int type = nonNegativeInt(action.get("id"), -1);
                if (type < 0) type = nonNegativeInt(action.get("post_action_type_id"), -1);
                if (type == 2) return Math.max(0, nonNegativeInt(action.get("count"), 0));
            }
        }
        return Math.max(0, direct);
    }

    private static boolean isLikeReaction(String id) {
        if (id == null) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        return "heart".equals(normalized) || "love".equals(normalized)
                || "like".equals(normalized) || "+1".equals(normalized)
                || "thumbsup".equals(normalized);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static boolean booleanValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof Number) return ((Number) raw).intValue() != 0;
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if ("true".equalsIgnoreCase(value) || "1".equals(value)) return true;
            if ("false".equalsIgnoreCase(value) || "0".equals(value)) return false;
        }
        return fallback;
    }

    private static final class MutableReaction {
        final String id;
        final String type;
        int count;
        boolean reacted;

        MutableReaction(String id, String type, int count, boolean reacted) {
            this.id = id;
            this.type = type;
            this.count = count;
            this.reacted = reacted;
        }
    }

    private static int nonNegativeInt(Object value, int fallback) {
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return number >= 0 && number <= Integer.MAX_VALUE ? (int) number : fallback;
        }
        if (value instanceof String) {
            try {
                long number = Long.parseLong(((String) value).trim());
                return number >= 0 && number <= Integer.MAX_VALUE ? (int) number : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private LinuxDoPostPayloadParser() {
    }
}
