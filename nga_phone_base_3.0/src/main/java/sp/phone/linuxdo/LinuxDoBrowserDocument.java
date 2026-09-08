package sp.phone.linuxdo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.regex.Pattern;

/** Builds a lightweight browser-style topic page from Discourse's canonical topic JSON. */
public final class LinuxDoBrowserDocument {

    private static final Pattern DANGEROUS_BLOCK = Pattern.compile(
            "(?is)<(?:script|style|iframe|object|embed|form)\\b[^>]*>.*?</(?:script|style|iframe|object|embed|form)\\s*>");
    private static final Pattern DANGEROUS_TAG = Pattern.compile(
            "(?is)</?(?:script|style|iframe|object|embed|form)\\b[^>]*>");
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile(
            "(?i)(href|src)\\s*=\\s*(['\"])\\s*javascript:[^'\"]*\\2");
    private static final Pattern IMAGE_META = Pattern.compile(
            "(?is)<div\\b[^>]*class=(['\"])[^'\"]*(?:meta|informations|image-source-link)[^'\"]*\\1[^>]*>.*?</div>");

    public static String fromTopicJson(String json, boolean nightMode) throws Exception {
        JSONObject root = JSON.parseObject(json == null ? "{}" : json);
        String title = first(root.getString("title"), "LINUX DO");
        JSONObject stream = root.getJSONObject("post_stream");
        JSONArray posts = stream == null ? null : stream.getJSONArray("posts");
        if (posts == null || posts.isEmpty()) {
            throw new IllegalArgumentException("missing topic posts");
        }

        String background = nightMode ? "#171717" : "#fffaf0";
        String foreground = nightMode ? "#e7e7e7" : "#202020";
        String muted = nightMode ? "#999" : "#777";
        String divider = nightMode ? "#353535" : "#e6dfd3";
        StringBuilder html = new StringBuilder(Math.max(8192, json.length()));
        html.append("<!doctype html><html><head><meta charset='utf-8'>")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
                .append("<base href='https://linux.do/'>")
                .append("<style>html,body{margin:0;padding:0;background:").append(background)
                .append(";color:").append(foreground)
                .append(";font-family:sans-serif;line-height:1.62}body{overflow-wrap:anywhere}")
                .append(".topic-title{font-size:1.28rem;line-height:1.38;font-weight:700;padding:18px 16px 14px;")
                .append("border-bottom:1px solid ").append(divider).append("}")
                .append("article{padding:14px 14px 18px;border-bottom:1px solid ").append(divider).append("}")
                .append("header{display:flex;align-items:center;gap:9px;margin-bottom:10px}")
                .append("header img{width:34px;height:34px;border-radius:50%;object-fit:cover;flex:none}")
                .append(".who{min-width:0;flex:1}.name{font-weight:650}.meta{font-size:.78rem;color:")
                .append(muted).append("}.floor{font-size:.8rem;color:").append(muted).append("}")
                .append(".cooked{font-size:1rem}.cooked>:first-child{margin-top:0}.cooked>:last-child{margin-bottom:0}")
                .append(".cooked img:not(.emoji){display:block;max-width:100%!important;width:auto!important;height:auto!important;")
                .append("margin:.55em auto;object-fit:contain}.cooked img.emoji{display:inline-block;width:1.15em;height:1.15em;")
                .append("vertical-align:-.18em}.lightbox-wrapper,.lightbox-wrapper>a{max-width:100%!important}")
                .append("aside.quote,blockquote{margin:.6em 0;padding:.5em .7em;border-left:3px solid #15977f;")
                .append("background:rgba(128,128,128,.1)}pre{overflow:auto}video{max-width:100%;height:auto}")
                .append("a{color:#168b78;text-decoration:none}</style></head><body>")
                .append("<div class='topic-title'>").append(escape(title)).append("</div>");

        for (int index = 0; index < posts.size(); index++) {
            JSONObject post = posts.getJSONObject(index);
            if (post == null) continue;
            int parsedNumber = post.getIntValue("post_number");
            int number = Math.max(1, parsedNumber > 0 ? parsedNumber : index + 1);
            String username = first(post.getString("name"), post.getString("username"), "LINUX DO");
            String avatarTemplate = first(post.getString("avatar_template"), "");
            String avatar = absoluteUrl(avatarTemplate.replace("{size}", "96"));
            String cooked = sanitize(first(post.getString("cooked"), ""));
            html.append("<article id='post_").append(number).append("'><header>");
            if (!avatar.isEmpty()) {
                html.append("<img src='").append(attribute(avatar)).append("' alt=''>");
            }
            html.append("<div class='who'><div class='name'>").append(escape(username)).append("</div>")
                    .append("<div class='meta'>").append(escape(first(post.getString("created_at"), "")))
                    .append("</div></div><div class='floor'>#").append(number).append("</div></header>")
                    .append("<div class='cooked'>").append(cooked).append("</div></article>");
        }
        return html.append("</body></html>").toString();
    }

    private static String sanitize(String cooked) {
        String clean = DANGEROUS_BLOCK.matcher(cooked == null ? "" : cooked).replaceAll("");
        clean = DANGEROUS_TAG.matcher(clean).replaceAll("");
        clean = EVENT_HANDLER.matcher(clean).replaceAll("");
        clean = JAVASCRIPT_URL.matcher(clean).replaceAll("$1=$2#$2");
        clean = IMAGE_META.matcher(clean).replaceAll("");
        clean = LinuxDoEmojiRenderer.replaceEmojiImages(clean);
        clean = clean.replace("href=\"//", "href=\"https://")
                .replace("src=\"//", "src=\"https://")
                .replace("href='//", "href='https://")
                .replace("src='//", "src='https://")
                .replace("href=\"/", "href=\"https://linux.do/")
                .replace("src=\"/", "src=\"https://linux.do/")
                .replace("href='/", "href='https://linux.do/")
                .replace("src='/", "src='https://linux.do/");
        return clean;
    }

    private static String absoluteUrl(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String url = value.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return LinuxDoConstants.ORIGIN + url;
        return url.startsWith("https://") ? url : "";
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return "";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String attribute(String value) {
        return escape(value);
    }

    private LinuxDoBrowserDocument() {
    }
}
