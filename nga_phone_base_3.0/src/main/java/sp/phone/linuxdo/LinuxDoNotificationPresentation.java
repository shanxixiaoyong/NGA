package sp.phone.linuxdo;

import java.util.Locale;

/** Shared compact labels/icons for native LINUX DO notification surfaces. */
public final class LinuxDoNotificationPresentation {
    public enum Group { REPLY, REACTION, BOOST, SYSTEM }

    public static Group group(String type) {
        String value = normalize(type);
        if (value.contains("boost")) return Group.BOOST;
        if (value.contains("reaction") || value.contains("like")) return Group.REACTION;
        if (value.contains("repl") || value.contains("mention") || value.contains("quot")) {
            return Group.REPLY;
        }
        return Group.SYSTEM;
    }

    public static String label(String type) {
        String value = normalize(type);
        if (value.contains("boost")) return "Boost";
        if (value.contains("solv") || value.contains("accepted_answer")) return "已解决";
        if (value.contains("reaction")) return "表情";
        if (value.contains("like")) return "点赞";
        if (value.contains("repl")) return "回复";
        if (value.contains("mention")) return "提及";
        if (value.contains("quot")) return "引用";
        if (value.contains("watching") || value.contains("topic")) return "主题更新";
        if (value.contains("badge")) return "徽章";
        if (value.contains("bookmark")) return "收藏提醒";
        if (value.contains("chat") || value.contains("message")) return "消息";
        return "通知";
    }

    public static String icon(String type) {
        String label = label(type);
        switch (label) {
            case "Boost": return "🚀";
            case "点赞": return "👍";
            case "表情": return "☺";
            case "回复": return "↩";
            case "提及": return "@";
            case "引用": return "❝";
            case "主题更新": return "●";
            case "徽章": return "🏅";
            case "收藏提醒": return "🔖";
            case "消息": return "💬";
            default: return "🔔";
        }
    }

    public static String titlePrefix(String type) {
        return icon(type) + "  " + label(type);
    }

    /** Natural-language action used beside the actor; topic title is rendered separately. */
    public static String action(String type) {
        String value = normalize(type);
        if (value.contains("liked_consolidated")) return "多人赞了你的帖子";
        if (value.contains("like")) return "赞了你的帖子";
        if (value.contains("reaction")) return "回应了你的帖子";
        if (value.contains("boost")) return "Boost 了你的帖子";
        if (value.contains("accepted_answer") || value.contains("solv")) return "采纳了答案";
        if (value.contains("repl")) return "回复了你";
        if (value.contains("mention")) return "提及了你";
        if (value.contains("quot")) return "引用了你";
        if (value.contains("watching_category") || value.contains("watching_tag")) {
            return "关注的板块或标签有更新";
        }
        if (value.contains("watching") || value.contains("topic") || value.contains("posted")) {
            return "主题有新动态";
        }
        if (value.contains("badge")) return "获得了徽章";
        if (value.contains("bookmark")) return "收藏提醒";
        if (value.contains("chat") || value.contains("message")) return "发来消息";
        return "发来通知";
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private LinuxDoNotificationPresentation() { }
}
