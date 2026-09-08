package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinuxDoTransportPolicyTest {

    @Test
    public void onlyReadOnlySameSiteApiPathsAreAllowed() {
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/latest.json?page=2"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/new.json?page=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/unread.json?page=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/top.json?period=weekly&page=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/categories.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/site.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/c/dev/42.json?page=0"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/t/42/posts.json?post_number=2"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath(
                "/t/42/posts.json?post_number=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/c/42.json?page=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/t/42.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/t/42/summary.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/t/42/posts.json?post_ids%5B%5D=1&post_ids%5B%5D=2"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/posts/42/reply-history.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/c/development/lv1/42.json?page=0"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/u/alice.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/u/alice/summary.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/u/alice/activity.json?offset=30"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath(
                "/user_actions.json?username=alice&offset=30&filter=4%2C5"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/user-badges/alice.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/search.json?q=hello%20order%3Alatest&page=1"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/notifications.json?offset=0"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/notifications/totals.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/session/csrf.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedPath("/session/current.json"));

        assertFalse(LinuxDoTransportPolicy.isAllowedPath("https://linux.do/latest.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/posts.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/post_actions"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/u/a/b.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/latest.rss"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/top.json?page=1"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath(
                "/top.json?period=weekly&sort=all&page=1"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/c/dev/42.json?topic=1"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/latest.json?page=10001"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/notifications.json?offset=10001"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/u/alice/activity.json?offset=10001"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath(
                "/user_actions.json?username=alice&offset=10001&filter=4%2C5"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath(
                "/user_actions.json?username=alice&offset=30&filter=1%2C2"));
        assertFalse(LinuxDoTransportPolicy.isAllowedPath("/t/topic/42.rss"));
    }

    @Test
    public void loginDocumentIsTheOnlyHtmlBootstrapPath() {
        assertTrue(LinuxDoTransportPolicy.isAllowedLoginPath("/login"));
        assertFalse(LinuxDoTransportPolicy.isAllowedLoginPath("/login?next=/latest"));
        assertFalse(LinuxDoTransportPolicy.isAllowedLoginPath("/session/current.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedLoginPath("https://linux.do/login"));
    }

    @Test
    public void onlyFixedPostMutationsAreAllowed() {
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/session"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/session.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/captcha/hcaptcha/create.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/hcaptcha/create.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/posts.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/post_actions"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/post_actions/42.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/discourse-boosts/posts/42/boosts"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/discourse-reactions/posts/42/custom-reactions/confetti_ball/toggle.json"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath("/polls/vote"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/notifications/mark-read.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath("/posts/42.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath("/session/current.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/captcha/hcaptcha/create.json?token=x"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath("https://linux.do/posts.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/discourse-boosts/posts/42/boosts?raw=x"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/discourse-reactions/posts/42/custom-reactions/../toggle.json"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMutationPath(
                "/discourse-reactions/posts/42/custom-reactions/emoji/toggle"));
    }

    @Test
    public void onlyLinuxDoAvatarHostsUseTheDedicatedSession() {
        assertTrue(LinuxDoTransportPolicy.isAllowedAvatarHost("linux.do"));
        assertTrue(LinuxDoTransportPolicy.isAllowedAvatarHost("CDN.LINUX.DO"));
        assertFalse(LinuxDoTransportPolicy.isAllowedAvatarHost("evil-linux.do"));
        assertFalse(LinuxDoTransportPolicy.isAllowedAvatarHost("linux.do.example.com"));
        assertFalse(LinuxDoTransportPolicy.isAllowedAvatarHost(null));
    }

    @Test
    public void emojiCdnHostsUseTheDedicatedMediaSession() {
        assertTrue(LinuxDoTransportPolicy.isAllowedMediaHost("cdn.ldstatic.com"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMediaHost("cdn3.ldstatic.com"));
        assertTrue(LinuxDoTransportPolicy.isAllowedMediaHost(
                "linuxdo-uploads.s3.ldstatic.com"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMediaHost("cdn.ldstatic.com.evil.test"));
        assertFalse(LinuxDoTransportPolicy.isAllowedMediaHost("example.com"));
    }

    @Test
    public void onlyKnownChallengeOriginsStayInLoginWebView() {
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "challenges.cloudflare.com", -1, ""));
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "HTTPS", "CHALLENGES.CLOUDFLARE.COM", -1, null));
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "hcaptcha.com", -1, ""));
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "newassets.hcaptcha.com", -1, ""));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "http", "challenges.cloudflare.com", -1, ""));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "evil.cloudflare.com", -1, ""));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "hcaptcha.com.evil.test", -1, ""));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "challenges.cloudflare.com", 443, ""));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeHost(
                "https", "challenges.cloudflare.com", -1, "user:pass"));
    }

    @Test
    public void turnstileInternalDocumentsStayInLoginWebView() {
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeDocument(
                "about:blank"));
        assertTrue(LinuxDoTransportPolicy.isAllowedChallengeDocument(
                "about:srcdoc"));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeDocument(
                "https://linux.do/blank"));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeDocument(
                "about://evil/blank"));
        assertFalse(LinuxDoTransportPolicy.isAllowedChallengeDocument(
                "javascript:alert(1)"));
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
        assertEquals(LinuxDoTransportPolicy.ResponseKind.JSON,
                LinuxDoTransportPolicy.classify(200, "[]"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.INVALID,
                LinuxDoTransportPolicy.classify(200, "plain text"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classify(200, "<?xml version=\"1.0\"?><rss/>"));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.JSON,
                LinuxDoTransportPolicy.classifyMutation(204, ""));
        assertEquals(LinuxDoTransportPolicy.ResponseKind.VERIFICATION_REQUIRED,
                LinuxDoTransportPolicy.classifyMutation(403, "{}"));
    }
}
