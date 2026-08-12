package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxDoTransportPolicyTest {

    @Test
    public void onlyReadOnlySameSiteApiPathsAreAllowed() {
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/latest.json?page=2"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/categories.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/t/42.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/t/42/posts.json?post_ids%5B%5D=1&post_ids%5B%5D=2"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/u/alice.json"));

        assertFalse(LinuxDoTransportPolicy.isAllowedPath("https://linux.do/latest.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/posts.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/post_actions"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/u/a/b.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/latest.rss"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/t/topic/42.rss"));
    }

    @Test
    public void classifiesJsonChallengeAndMalformedResponses() {
        assertEquals(LinuxDoTransportPolicy.ResponseKind.JSON,
                LinuxDoTransportPolicy.classify(200, " {\"topic_list\":{}}"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classify(403, "forbidden"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classify(302, ""));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classify(200, "<html>Just a moment</html>"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.INVALID,
                LinuxDoTransportPolicy.classify(500, "{\"error\":true}"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.INVALID,
                LinuxDoTransportPolicy.classify(200, "[]"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classify(200, "<?xml version=\"1.0\"?><rss/>"));
    }
}
