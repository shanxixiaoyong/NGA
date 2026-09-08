package gov.anzong.androidnga.core.corebuild;

import java.util.List;
import java.util.Locale;

import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.HtmlData;

/**
 * Created by Justwen on 2018/8/28.
 */
public class HtmlAttachmentBuilder implements IHtmlBuild {


    private static StringBuilder buildAudioAttachment(
            StringBuilder ret, AttachmentData attachment, String attachmentsPrefix) {
        String url = attachment.getAttachUrl();
        ret.append("<tr><td><a href='")
                .append(attachmentsPrefix)
                .append("/")
                .append(url)
                .append("'>")
                .append("nga_audio.mp3</a>")
                .append("</td></tr>");
        return ret;
    }

    private static StringBuilder buildVideoAttachment(
            StringBuilder ret, AttachmentData attachment, String attachmentsPrefix) {
        String url = attachment.getAttachUrl();
        String source = escapeAttribute(attachmentsPrefix + "/" + url);
        // Keep a normal link inside the video element as a provider/browser fallback. WebView
        // can play common NGA attachment formats inline, while older providers still expose the
        // original attachment instead of silently dropping it.
        ret.append("<tr><td><video controls='controls' preload='metadata' playsinline "
                + "style='display:block;max-width:100%;width:auto;height:auto;background:#000'>")
                .append("<source src='").append(source).append("'>")
                .append("<a href='").append(source).append("'>nga_video</a>")
                .append("</video></td></tr>");
        return ret;
    }

    private static boolean isVideoAttachment(String attachUrl) {
        String normalized = attachUrl.toLowerCase(Locale.ROOT);
        // Keep the original broad MP4 match because NGA has historically returned attachment
        // paths whose generated name did not contain a dot before the extension. The other
        // formats use the same token matching so query strings and CDN rewriting do not hide the
        // media type.
        return normalized.contains("mp4") || normalized.contains("webm")
                || normalized.contains("mov") || normalized.contains("m4v")
                || normalized.contains("m3u8");
    }

    private static String escapeAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static StringBuilder buildImageAttachment(
            StringBuilder ret,
            AttachmentData attachment,
            int index,
            List<String> imageUrls,
            String attachmentsPrefix) {

        String attachUrl = attachmentsPrefix + "/" + attachment.getAttachUrl();
        String attachUrlThumb = attachUrl;
        String indexStr = String.valueOf(index);
        if ("1".equals(attachment.getThumb())) {
            attachUrlThumb = attachUrlThumb + ".thumb.jpg";
        }
        ret.append("<tr><td>")
                .append(String.format("<button id='show%s' type='button' onclick='displayImg(%s,\"%s\")'>点击显示附件</button>", indexStr, indexStr, attachUrlThumb))
                .append(String.format("<a href=%s>", attachUrl))
                .append(String.format("<img style='max-width:100%%'; id='img%s'/>", indexStr))
                .append("</a></td></tr>");
        if (!imageUrls.contains(attachUrl)) {
            imageUrls.add(attachUrl);
        }

        return ret;
    }

    @Override
    public CharSequence build(HtmlData htmlData, List<String> images) {
        if (htmlData.getAttachmentList() == null || htmlData.getAttachmentList().isEmpty()) {
            return "";
        }
        StringBuilder ret = new StringBuilder();
        ret.append("<br/><br/>附件<hr/><br/>");
        if (htmlData.isDarkMode()) {
            ret.append("<table style='border:1px solid #b9986e;padding:10px;color:#6b2d25;font-size:10'>");
        } else {
            ret.append("<table style='border:1px solid #b9986e;padding:10px;color:#6b2d25;font-size:10'>");
        }
        ret.append("<tbody>");
        int attachmentCount = 0;
        int imageAttachmentCount = 0;
        String attachmentsPrefix = htmlData.getAttachmentsPrefix();

        for (AttachmentData attach : htmlData.getAttachmentList()) {
            String attachUrl = attach.getAttachUrl();
            if (attachUrl == null || attachUrl.trim().isEmpty()) {
                continue;
            }
            if (attachUrl.toLowerCase(Locale.ROOT).contains("mp3")) {
                ret = buildAudioAttachment(ret, attach, attachmentsPrefix);
            } else if (isVideoAttachment(attachUrl)) {
                ret = buildVideoAttachment(ret, attach, attachmentsPrefix);
            } else {
                imageAttachmentCount++;
                buildImageAttachment(
                        ret, attach, imageAttachmentCount, images, attachmentsPrefix);
            }
            attachmentCount++;
        }

        if (imageAttachmentCount > 0) {
            ret.append("<script> function displayImg(a,b){ document.getElementById('img'+a).src=b; document.getElementById('show' + a).style.display='none'; } </script>");
        }
        ret.append("</tbody></table>");
        if (attachmentCount == 0) {
            return "";
        } else {
            return ret;
        }
    }
}
