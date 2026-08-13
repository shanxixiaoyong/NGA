package sp.phone.mvp.model.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import sp.phone.param.ArticleListParam;

public class NgaWebArticleFallbackPolicyTest {

    @Test
    public void buildsTheExactWebPageForTheRequestedNativePage() {
        ArticleListParam param = new ArticleListParam();
        param.tid = 47348853;
        param.page = 3;
        param.authorId = 42;

        assertEquals(
                "https://bbs.nga.cn/read.php?page=3&tid=47348853&authorid=42",
                NgaWebArticleFallbackPolicy.buildReadUrl("https://bbs.nga.cn/", param));
    }

    @Test
    public void postRequestsRetainBothPostAndThreadIdentity() {
        ArticleListParam param = new ArticleListParam();
        param.tid = 123;
        param.pid = 456;

        assertEquals(
                "https://ngabbs.com/read.php?page=1&tid=123&pid=456",
                NgaWebArticleFallbackPolicy.buildReadUrl("https://ngabbs.com", param));
    }

    @Test
    public void navigationAllowsOnlyKnownHttpsReadPages() {
        assertTrue(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://bbs.ngacn.cc/read.php?tid=1"));
        assertTrue(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://nga.donews.com/read.php?page=2&tid=1"));

        assertFalse(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "http://bbs.nga.cn/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://evil.example/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://bbs.nga.cn/nuke.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://user@bbs.nga.cn/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedReadUrl(
                "https://bbs.nga.cn:444/read.php?tid=1"));
    }

    @Test
    public void navigationAllowsOnlySameHostInterstitialBoundToAReadPage() {
        assertTrue(NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(
                "https://bbs.nga.cn/misc/adpage_insert_2.html?"
                        + "https://bbs.nga.cn/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(
                "https://bbs.nga.cn/misc/adpage_insert_2.html?"
                        + "https://ngabbs.com/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(
                "https://bbs.nga.cn/misc/adpage_insert_2.html?"
                        + "https://evil.example/read.php?tid=1"));
        assertFalse(NgaWebArticleFallbackPolicy.isAllowedNavigationUrl(
                "https://bbs.nga.cn/misc/adpage_insert_2.html?"
                        + "https://bbs.nga.cn/nuke.php?tid=1"));
    }

    @Test
    public void missingIdentityAndUntrustedBaseAreRejected() {
        ArticleListParam param = new ArticleListParam();
        assertThrows(IllegalArgumentException.class,
                () -> NgaWebArticleFallbackPolicy.buildReadUrl(
                        "https://bbs.nga.cn", param));
        param.tid = 1;
        assertThrows(IllegalArgumentException.class,
                () -> NgaWebArticleFallbackPolicy.buildReadUrl(
                        "https://evil.example", param));
    }
}
