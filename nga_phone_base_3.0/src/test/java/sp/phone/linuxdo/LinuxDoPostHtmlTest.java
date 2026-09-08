package sp.phone.linuxdo;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class LinuxDoPostHtmlTest {

    @Test
    public void wrapsImagesWithAspectRatioAndViewportGuards() {
        String html = LinuxDoPostHtml.wrap(
                "<p>正文</p><img width='1200' height='300' src='x.jpg'>", 18, false);

        assertTrue(html.contains("name='viewport'"));
        assertTrue(html.contains("padding:0 8px"));
        assertTrue(html.contains("img:not(.emoji):not(.linuxdo-boost-avatar)"));
        assertTrue(html.contains("width:auto!important"));
        assertTrue(html.contains("height:auto!important"));
        assertTrue(html.contains("object-fit:contain!important"));
        // The body is local HTML and remote images are intentionally released only after the
        // first text frame. A preconnect here adds Chromium work without helping that frame.
        assertFalse(html.contains("rel='preconnect'"));
        assertTrue(html.contains(".lightbox-wrapper .meta"));
        assertTrue(html.contains(".linuxdo-boosts"));
        assertTrue(html.contains("flex-wrap:wrap;gap:6px"));
        assertTrue(html.contains("display:inline-flex;align-items:center"));
        assertTrue(html.contains(".linuxdo-boost img.linuxdo-boost-avatar{width:1em!important;height:1em!important;"));
        assertTrue(html.contains("body>:first-child{margin-top:0!important;}"));
        assertTrue(html.contains("body *:last-child{margin-bottom:0!important;"));
        assertTrue(html.contains("p{margin-top:.55em;margin-bottom:0;}"));
        assertTrue(html.contains(".linuxdo-local-emoji{"));
        assertTrue(html.contains("aside.quote{"));
        assertTrue(html.contains(".linuxdo-reply-target{"));
        assertTrue(html.contains("<p>正文</p>"));
    }

    @Test
    public void keepsInlineVideoControlsCompact() {
        String html = LinuxDoPostHtml.wrap(
                "<video controls preload='metadata'><source src='clip.mp4'></video>",
                18, false);

        assertTrue(html.contains("video{display:block;max-width:100%!important;"));
        assertTrue(html.contains("preload='metadata'") || html.contains("preload=\"metadata\""));
        assertTrue(html.contains("controls"));
    }
}
