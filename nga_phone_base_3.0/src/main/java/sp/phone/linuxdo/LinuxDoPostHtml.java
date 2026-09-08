package sp.phone.linuxdo;

/** Builds the small source-specific wrapper used by the native article WebView. */
final class LinuxDoPostHtml {

    static String wrap(String cooked, int textSize, boolean nightMode) {
        String style = nightMode ? "style_dark.css" : "style_light.css";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/style.css'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/" + style + "'>"
                + "<style>"
                + "html,body{margin:0;padding:0;}"
                + "body{box-sizing:border-box;padding:0 8px;font-size:" + textSize + "px;}"
                + "body>:first-child{margin-top:0!important;}"
                + "body *:last-child{margin-bottom:0!important;padding-bottom:0!important;}"
                + "p{margin-top:.55em;margin-bottom:0;}"
                + ".linuxdo-reply-target{display:inline-flex;align-items:center;max-width:100%;"
                + "box-sizing:border-box;margin:0 0 .5em;padding:.24em .58em;border-radius:.7em;"
                + "background:rgba(128,128,128,.13);color:#777;font-size:.82em;line-height:1.35;}"
                + "aside.quote{display:block;box-sizing:border-box;margin:.55em 0;padding:.5em .65em;"
                + "border-left:3px solid rgba(0,150,120,.72);border-radius:0 .45em .45em 0;"
                + "background:rgba(128,128,128,.11);overflow-wrap:anywhere;}"
                + "aside.quote>.title{display:flex;align-items:center;gap:.3em;margin:0 0 .3em;"
                + "font-size:.86em;font-weight:600;color:#777;}"
                + "aside.quote>.title img.avatar{width:1.25em!important;height:1.25em!important;"
                + "border-radius:50%;object-fit:cover!important;}"
                + "aside.quote blockquote{margin:0;padding:0;border:0;}"
                + "aside.quote blockquote>p:first-child{margin-top:0;}"
                + "aside.quote .quote-controls{display:none!important;}"
                + ".linuxdo-local-emoji{display:inline;white-space:nowrap;font-size:1.08em;"
                + "line-height:1;vertical-align:-.08em;}"
                + "img.linuxdo-local-emoji-image{display:inline-block!important;"
                + "width:1.08em!important;height:1.08em!important;max-width:none!important;"
                + "margin:0 .04em!important;object-fit:contain!important;vertical-align:-.12em;}"
                + "img:not(.emoji):not(.linuxdo-boost-avatar){max-width:100%!important;width:auto!important;"
                + "height:auto!important;object-fit:contain!important;}"
                + ".lightbox-wrapper,.lightbox-wrapper>a{max-width:100%!important;}"
                + ".lightbox-wrapper .meta,.lightbox-wrapper .filename,"
                + ".lightbox-wrapper .informations,.image-source-link{display:none!important;}"
                + ".linuxdo-boosts{display:flex;flex-wrap:wrap;gap:6px;margin-top:.65em;}"
                + ".linuxdo-boost{display:inline-flex;align-items:center;max-width:100%;"
                + "padding:3px 8px;border-radius:14px;background:rgba(128,128,128,.14);"
                + "font-size:.86em;line-height:1.35;}"
                + ".linuxdo-boost p{display:inline;margin:0;}"
                + ".linuxdo-boost img.linuxdo-boost-avatar{width:1em!important;height:1em!important;"
                + "border-radius:50%;margin-right:.28em;object-fit:cover!important;"
                + "vertical-align:middle;flex:none;}"
                + "video{display:block;max-width:100%!important;width:auto!important;"
                + "height:auto!important;margin:.65em 0 0;background:rgba(0,0,0,.08);}"
                + "</style></head><body>"
                + (cooked == null ? "" : cooked) + "</body></html>";
    }

    private LinuxDoPostHtml() {
    }
}
