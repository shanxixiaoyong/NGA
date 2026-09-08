package sp.phone.linuxdo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxDoBrowserDocumentTest {

    @Test
    public void buildsReadableTopicAndRemovesExecutableMarkup() throws Exception {
        String json = "{\"title\":\"A & B\",\"post_stream\":{\"posts\":[{"
                + "\"post_number\":1,\"username\":\"alice\",\"created_at\":\"now\","
                + "\"avatar_template\":\"/avatar/{size}.png\","
                + "\"cooked\":\"<p>Hello<img src='/upload/a.png' onerror='bad()'></p>"
                + "<script>bad()</script>\"}]}}";

        String html = LinuxDoBrowserDocument.fromTopicJson(json, false);

        assertTrue(html.contains("A &amp; B"));
        assertTrue(html.contains("alice"));
        assertTrue(html.contains("https://linux.do/avatar/96.png"));
        assertTrue(html.contains("src='https://linux.do/upload/a.png'"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("onerror"));
    }
}
