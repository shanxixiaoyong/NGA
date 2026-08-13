package sp.phone.mvp.model.convert;

/** Wraps already-rendered, sanitized NGA web content in the native reader theme. */
final class NgaWebArticleHtml {

    static String wrap(
            String subject,
            String contentHtml,
            String signatureHtml,
            int textSize,
            boolean nightMode) {
        String style = nightMode ? "style_dark.css" : "style_light.css";
        StringBuilder body = new StringBuilder();
        if (!isEmpty(subject)) {
            body.append("<div class='title'>")
                    .append(escapeHtml(subject))
                    .append("</div><br>");
        }
        if (!isEmpty(contentHtml)) {
            body.append(contentHtml);
        }
        if (!isEmpty(signatureHtml)) {
            body.append("<div class='nga-web-signature'>")
                    .append(signatureHtml)
                    .append("</div>");
        }
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/style.css'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/" + style + "'>"
                + "<style>"
                + "html,body{margin:0;padding:0;}"
                + "body{box-sizing:border-box;padding:0 8px;font-size:" + textSize + "px;}"
                + "body>:first-child{margin-top:0!important;}"
                + "body>:last-child{margin-bottom:0!important;}"
                + "p{margin-top:.55em;margin-bottom:0;}"
                + "img:not(.emoticon){max-width:100%!important;width:auto!important;"
                + "height:auto!important;object-fit:contain!important;}"
                + "video,audio{max-width:100%!important;height:auto!important;}"
                + "table{max-width:100%!important;display:block;overflow-x:auto;}"
                + "pre{white-space:pre-wrap;overflow-wrap:anywhere;}"
                + ".nga-web-signature{margin-top:.75em;padding-top:.5em;"
                + "border-top:1px solid rgba(128,128,128,.35);}"
                + "</style></head><body>" + body + "</body></html>";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&': escaped.append("&amp;"); break;
                case '<': escaped.append("&lt;"); break;
                case '>': escaped.append("&gt;"); break;
                case '\"': escaped.append("&quot;"); break;
                case '\'': escaped.append("&#39;"); break;
                default: escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private NgaWebArticleHtml() {
    }
}
