package sp.phone.linuxdo;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxDoPostHtmlTest {

    @Test
    public void wrapsImagesWithAspectRatioAndViewportGuards() {
        String html = LinuxDoPostHtml.wrap(
                "<p>正文</p><img width='1200' height='300' src='x.jpg'>", 18, false);

        assertTrue(html.contains("name='viewport'"));
        assertTrue(html.contains("padding:0 8px"));
        assertTrue(html.contains("img:not(.emoji)"));
        assertTrue(html.contains("width:auto!important"));
        assertTrue(html.contains("height:auto!important"));
        assertTrue(html.contains("object-fit:contain!important"));
        assertTrue(html.contains("body>:first-child{margin-top:0!important;}"));
        assertTrue(html.contains("body *:last-child{margin-bottom:0!important;"));
        assertTrue(html.contains("p{margin-top:.55em;margin-bottom:0;}"));
        assertTrue(html.contains("<p>正文</p>"));
    }
}
