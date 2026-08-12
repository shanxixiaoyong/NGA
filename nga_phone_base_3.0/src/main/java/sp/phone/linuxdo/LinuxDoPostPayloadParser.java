package sp.phone.linuxdo;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/** Pure tolerant projections from a Discourse post payload. */
final class LinuxDoPostPayloadParser {

    static int resolveLikeCount(JSONObject post) {
        int direct = nonNegativeInt(post == null ? null : post.get("like_count"), -1);
        if (direct > 0) return direct;
        Object rawActions = post == null ? null : post.get("actions_summary");
        JSONArray actions = rawActions instanceof JSONArray ? (JSONArray) rawActions : null;
        if (actions != null) {
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
