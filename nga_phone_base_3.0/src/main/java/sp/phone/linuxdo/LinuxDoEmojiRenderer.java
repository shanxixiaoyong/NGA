package sp.phone.linuxdo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sp.phone.http.bean.PostReaction;

/** One canonical emoji path for article HTML, Boosts, quoted floors and composers. */
public final class LinuxDoEmojiRenderer {

    private static final Pattern IMAGE_TAG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile(
            "(?i)\\bclass\\s*=\\s*(['\"])([^'\"]*)\\1");
    private static final Pattern EMOJI_CLASS = Pattern.compile(
            "(?:^|\\s)emoji(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_ATTRIBUTE = Pattern.compile(
            "(?i)\\b(?:data-emoji|alt|title)\\s*=\\s*(['\"])([^'\"]+)\\1");
    private static final Pattern SAFE_CODE = Pattern.compile("[a-zA-Z0-9_+\\-]{1,64}");
    private static final Pattern SHORTCODE = Pattern.compile(":([a-zA-Z0-9_+\\-]{1,64}):");
    private static final Map<String, Bitmap> BITMAPS = new ConcurrentHashMap<>();

    static String replaceEmojiImages(String html) {
        return replaceEmojiImages(html, false);
    }

    /** Keeps exact local shortcodes in native TextView content such as Boost and reply cards. */
    static String replaceEmojiImagesWithTokens(String html) {
        return replaceEmojiImages(html, true);
    }

    private static String replaceEmojiImages(String html, boolean tokenized) {
        if (html == null || html.isEmpty()) return html == null ? "" : html;
        Matcher images = IMAGE_TAG.matcher(html);
        StringBuffer result = new StringBuffer(html.length());
        while (images.find()) {
            String tag = images.group();
            String replacement = replacementFor(tag, tokenized);
            images.appendReplacement(result, Matcher.quoteReplacement(
                    replacement == null ? tag : replacement));
        }
        images.appendTail(result);
        return result.toString();
    }

    private static String replacementFor(String imageTag, boolean tokenized) {
        Matcher classAttribute = CLASS_ATTRIBUTE.matcher(imageTag);
        if (!classAttribute.find()
                || !EMOJI_CLASS.matcher(classAttribute.group(2)).find()) return null;

        Matcher codeAttribute = CODE_ATTRIBUTE.matcher(imageTag);
        while (codeAttribute.find()) {
            String raw = codeAttribute.group(2) == null ? "" : codeAttribute.group(2).trim();
            String code = normalizeCode(raw);
            if (code != null) {
                if (LinuxDoReactionAssets.contains(code)) {
                    if (tokenized) return ":" + code + ":";
                    String source = LinuxDoReactionAssets.dataUriFor(code);
                    if (source != null) {
                        return "<img class=\"emoji linuxdo-local-emoji-image\" data-emoji=\""
                                + escapeAttribute(code) + "\" alt=\":"
                                + escapeAttribute(code) + ":\" src=\"" + source + "\">";
                    }
                }
                return "<span class=\"linuxdo-local-emoji\" role=\"img\" aria-label=\""
                        + escapeAttribute(code) + "\">" + PostReaction.emojiFor(code) + "</span>";
            }
            if (containsNonAscii(raw)) {
                return "<span class=\"linuxdo-local-emoji\" role=\"img\">"
                        + escapeText(raw) + "</span>";
            }
        }
        return "<span class=\"linuxdo-local-emoji\" role=\"img\">🙂</span>";
    }

    /** Renders the ten official shortcodes with the same embedded PNGs used by the picker. */
    public static CharSequence renderLocalEmoji(Context context, String value, int iconDp) {
        String safe = value == null ? "" : value;
        if (context == null || safe.indexOf(':') < 0) return safe;
        Matcher matcher = SHORTCODE.matcher(safe);
        SpannableStringBuilder result = new SpannableStringBuilder();
        int previous = 0;
        int size = Math.max(12, Math.round(iconDp
                * context.getResources().getDisplayMetrics().density));
        while (matcher.find()) {
            String id = normalizeCode(matcher.group(1));
            if (!LinuxDoReactionAssets.contains(id)) continue;
            result.append(safe, previous, matcher.start());
            int start = result.length();
            result.append('\uFFFC');
            Bitmap bitmap = bitmapFor(id);
            if (bitmap == null) {
                result.replace(start, start + 1, PostReaction.emojiFor(id));
            } else {
                BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
                drawable.setBounds(0, 0, size, size);
                result.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BASELINE),
                        start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            previous = matcher.end();
        }
        if (previous == 0) return safe;
        result.append(safe, previous, safe.length());
        return result;
    }

    private static Bitmap bitmapFor(String id) {
        Bitmap cached = BITMAPS.get(id);
        if (cached != null && !cached.isRecycled()) return cached;
        byte[] bytes = LinuxDoReactionAssets.bytesFor(id);
        if (bytes == null || bytes.length == 0) return null;
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (decoded != null) BITMAPS.put(id, decoded);
        return decoded;
    }

    private static String normalizeCode(String raw) {
        if (raw == null) return null;
        String code = raw.trim();
        while (code.length() >= 2 && code.startsWith(":") && code.endsWith(":")) {
            code = code.substring(1, code.length() - 1).trim();
        }
        if (!SAFE_CODE.matcher(code).matches()) return null;
        return code.toLowerCase(Locale.ROOT);
    }

    private static boolean containsNonAscii(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7f) return true;
        }
        return false;
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private LinuxDoEmojiRenderer() {
    }
}
