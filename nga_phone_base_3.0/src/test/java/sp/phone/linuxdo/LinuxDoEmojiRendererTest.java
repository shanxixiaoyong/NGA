package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxDoEmojiRendererTest {

    @Test
    public void officialReactionBecomesExactOfflineArtwork() {
        String html = LinuxDoEmojiRenderer.replaceEmojiImages(
                "<p>A<img class=\"emoji only-emoji\" alt=\":laughing:\" "
                        + "src=\"https://cdn.ldstatic.com/emoji.png\">B</p>");

        assertTrue(html.contains("class=\"emoji linuxdo-local-emoji-image\""));
        assertTrue(html.contains("data-emoji=\"laughing\""));
        assertTrue(html.contains("data:image/png;base64,"));
        assertFalse(html.contains("cdn.ldstatic.com"));
    }

    @Test
    public void attributeOrderAndSingleQuotesAreAccepted() {
        String html = LinuxDoEmojiRenderer.replaceEmojiImages(
                "<img title=':confetti_ball:' src='remote.png' class='foo emoji bar'>");

        assertTrue(html.contains("data-emoji=\"confetti_ball\""));
        assertTrue(html.contains("data:image/png;base64,"));
        assertFalse(html.contains("remote.png"));
    }

    @Test
    public void actualUnicodeAltIsKeptAndOrdinaryImagesStayUntouched() {
        String input = "<img class='emoji' alt='🫡' src='remote.png'>"
                + "<img class='photo' src='photo.jpg'>";
        String html = LinuxDoEmojiRenderer.replaceEmojiImages(input);

        assertTrue(html.contains("🫡"));
        assertTrue(html.contains("src='photo.jpg'"));
    }

    @Test
    public void missingEmojiCodeUsesVisibleOfflineFallback() {
        assertEquals("<span class=\"linuxdo-local-emoji\" role=\"img\">🙂</span>",
                LinuxDoEmojiRenderer.replaceEmojiImages(
                        "<img class=\"emoji\" src=\"remote.png\">"));
    }

    @Test
    public void rollingEyesAliasStaysOfflineAndVisible() {
        String html = LinuxDoEmojiRenderer.replaceEmojiImages(
                "<img class='emoji' data-emoji='roll_eyes' src='remote.png'>");

        assertTrue(html.contains("🙄"));
        assertFalse(html.contains("remote.png"));
    }
}
