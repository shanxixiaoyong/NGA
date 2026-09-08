package sp.phone.http.bean;

import java.util.List;

/**
 * Source-neutral projection of one reaction attached to a post.
 *
 * <p>The LinuxDo adapter fills this model from Discourse's reactions payload while the
 * article renderer only needs the small, already-normalized display state.  Keeping the
 * projection free of Android and network types means NGA rows can continue to use the same
 * {@link ThreadRowInfo} without pulling source-specific parsing into the UI layer.</p>
 */
public final class PostReaction {
    private final String id;
    private final String type;
    private int count;
    private boolean reacted;

    public PostReaction(String id, String type, int count, boolean reacted) {
        this.id = id == null ? "" : id;
        this.type = type == null ? "" : type;
        this.count = Math.max(0, count);
        this.reacted = reacted;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(0, count);
    }

    public boolean isReacted() {
        return reacted;
    }

    public void setReacted(boolean reacted) {
        this.reacted = reacted;
    }

    public PostReaction copy() {
        return new PostReaction(id, type, count, reacted);
    }

    /**
     * Maps Discourse's common reaction ids to compact native emoji glyphs.
     *
     * <p>Reaction ids are site-configurable, so a phone will inevitably receive an id that is
     * not in this table. Returning the shortcode (for example {@code :partyparrot:}) from this
     * method made the footer look as though an emoji had failed to render. Unknown ids now use a
     * visible glyph instead; the id remains available to the picker as its action label.</p>
     */
    public static String emojiFor(String reactionId) {
        if (reactionId == null || reactionId.trim().isEmpty()) return "🙂";
        switch (reactionId.toLowerCase(java.util.Locale.ROOT)) {
            case "grinning":
                return "😀";
            case "smile":
                return "😄";
            case "smiley":
                return "😃";
            case "grin":
                return "😁";
            case "sweat_smile":
                return "😅";
            case "slightly_smiling_face":
                return "🙂";
            case "wink":
                return "😉";
            case "blush":
                return "😊";
            case "heart":
            case "love":
                return "❤️";
            case "heart_eyes":
                return "😍";
            case "kissing_heart":
                return "😘";
            case "+1":
            case "thumbsup":
            case "like":
                return "👍";
            case "-1":
            case "thumbsdown":
                return "👎";
            case "laughing":
            case "satisfied":
                return "😆";
            case "joy":
                return "😂";
            case "rofl":
            case "rolling_on_the_floor_laughing":
                return "🤣";
            case "partying_face":
            case "partying":
                return "🥳";
            case "pleading_face":
                return "🥺";
            case "sunglasses":
                return "😎";
            case "saluting_face":
            case "salute":
                return "🫡";
            case "melting_face":
                return "🫠";
            case "innocent":
                return "😇";
            case "exploding_head":
            case "mind_blown":
                return "🤯";
            case "star_struck":
                return "🤩";
            case "zany_face":
                return "🤪";
            case "woozy_face":
                return "🥴";
            case "thinking":
            case "thinking_face":
                return "🤔";
            case "open_mouth":
            case "astonished":
                return "😮";
            case "roll_eyes":
            case "rolleyes":
            case "face_with_rolling_eyes":
                return "🙄";
            case "distorted_face":
                return "🫪";
            case "tieba_087":
                return "🎁";
            case "bili_057":
                return "🐶";
            case "confused":
                return "😕";
            case "raised_eyebrow":
            case "face_with_raised_eyebrow":
                return "🤨";
            case "nerd_face":
                return "🤓";
            case "monocle_face":
            case "face_with_monocle":
                return "🧐";
            case "neutral_face":
                return "😐";
            case "expressionless":
                return "😑";
            case "unamused":
                return "😒";
            case "pensive":
                return "😔";
            case "relieved":
                return "😌";
            case "cry":
                return "😢";
            case "sob":
                return "😭";
            case "angry":
                return "😠";
            case "rage":
                return "😡";
            case "scream":
                return "😱";
            case "flushed":
                return "😳";
            case "dizzy_face":
                return "😵";
            case "mask":
                return "😷";
            case "nauseated_face":
                return "🤢";
            case "face_vomiting":
                return "🤮";
            case "smiling_imp":
                return "😈";
            case "clap":
                return "👏";
            case "confetti_ball":
            case "tada":
                return "🎉";
            case "partyparrot":
                return "🦜";
            case "blobcat":
            case "blob_cat":
                return "😺";
            case "doge":
                return "🐶";
            case "hugs":
                return "🤗";
            case "eyes":
                return "👀";
            case "bulb":
                return "💡";
            case "memo":
                return "📝";
            case "rocket":
                return "🚀";
            case "fire":
                return "🔥";
            case "zap":
                return "⚡";
            case "boom":
            case "collision":
                return "💥";
            case "100":
                return "💯";
            case "sparkles":
                return "✨";
            case "white_check_mark":
                return "✅";
            case "heavy_check_mark":
                return "✔️";
            case "x":
                return "❌";
            case "warning":
                return "⚠️";
            case "question":
                return "❓";
            case "exclamation":
                return "❗";
            case "star":
                return "⭐";
            case "star2":
                return "🌟";
            case "coffee":
                return "☕";
            case "trophy":
                return "🏆";
            case "poop":
            case "hankey":
            case "shit":
                return "💩";
            case "skull":
                return "💀";
            case "ghost":
                return "👻";
            case "wave":
                return "👋";
            case "ok_hand":
                return "👌";
            case "muscle":
                return "💪";
            case "pray":
                return "🙏";
            case "raised_hands":
                return "🙌";
            case "handshake":
                return "🤝";
            case "facepalm":
                return "🤦";
            case "shrug":
                return "🤷";
            default:
                // Custom emoji names are not guaranteed to have a native Unicode equivalent.
                // Do not leak their colon shortcode into the compact summary; that is perceived
                // as a broken text rendering. The original id is still shown in the picker.
                return "🙂";
        }
    }

    /** Formats the compact one-line summary shown in a floor footer. */
    public static String formatSummary(List<PostReaction> reactions) {
        if (reactions == null || reactions.isEmpty()) return "";
        StringBuilder summary = new StringBuilder();
        for (PostReaction reaction : reactions) {
            if (reaction == null || reaction.getCount() <= 0) continue;
            if (summary.length() > 0) summary.append("  ");
            summary.append(emojiFor(reaction.getId())).append(' ').append(reaction.getCount());
            if (reaction.isReacted()) summary.append(" ✓");
        }
        return summary.toString();
    }

    /** Formats the LinuxDo-style footer: distinct emoji followed by one combined count. */
    public static String formatCompactSummary(List<PostReaction> reactions) {
        if (reactions == null || reactions.isEmpty()) return "";
        StringBuilder summary = new StringBuilder();
        int total = 0;
        int visibleEmoji = 0;
        for (PostReaction reaction : reactions) {
            if (reaction == null || reaction.getCount() <= 0) continue;
            total += reaction.getCount();
            if (visibleEmoji < 3) {
                if (summary.length() > 0) summary.append(' ');
                summary.append(emojiFor(reaction.getId()));
                visibleEmoji++;
            }
        }
        if (total <= 0) return "";
        return summary.append(' ').append(total).toString();
    }

    /** Returns the main heart/like aggregate projected into the right-side vote count. */
    public static int mainLikeCount(List<PostReaction> reactions) {
        if (reactions == null) return 0;
        int count = 0;
        for (PostReaction reaction : reactions) {
            if (reaction == null) continue;
            if (isMainLikeId(reaction.getId())) {
                count = Math.max(count, reaction.getCount());
            }
        }
        return count;
    }

    public static boolean isMainLikeId(String reactionId) {
        if (reactionId == null) return false;
        String id = reactionId.toLowerCase(java.util.Locale.ROOT);
        return "heart".equals(id) || "love".equals(id) || "like".equals(id)
                || "+1".equals(id) || "thumbsup".equals(id);
    }
}
