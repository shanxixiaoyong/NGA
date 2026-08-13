package sp.phone.linuxdo;

/** Builds the small source-specific wrapper used by the native article WebView. */
final class LinuxDoPostHtml {

    static String wrap(String cooked, int textSize, boolean nightMode) {
        String style = nightMode ? "style_dark.css" : "style_light.css";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<link rel='preconnect' href='https://linux.do'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/style.css'>"
                + "<link rel='stylesheet' href='file:///android_asset/html/" + style + "'>"
                + "<style>"
                + "html,body{margin:0;padding:0;}"
                + "body{box-sizing:border-box;padding:0 8px;font-size:" + textSize + "px;}"
                + "body>:first-child{margin-top:0!important;}"
                + "body *:last-child{margin-bottom:0!important;padding-bottom:0!important;}"
                + "p{margin-top:.55em;margin-bottom:0;}"
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
                + "video{max-width:100%!important;height:auto!important;}"
                + "</style></head><body>"
                + (cooked == null ? "" : cooked) + "</body></html>";
    }

    private LinuxDoPostHtml() {
    }
}
