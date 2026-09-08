package sp.phone.mvp.model.web;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import sp.phone.param.ArticleListParam;

public class NgaNativeArticleRequestPolicyTest {

    @Test
    public void buildsMaintainedAndroidOutput8RouteWithGb18030() {
        ArticleListParam param = new ArticleListParam();
        param.tid = 47426608;

        assertEquals(
                "https://bbs.nga.cn/read.php?page=3&__output=8&noprefix&v2&tid=47426608",
                NgaNativeArticleRequestPolicy.buildReadUrl(
                        "https://bbs.nga.cn", param, 3,
                        NgaNativeArticleRequestPolicy.WireFormat.LEGACY_GB18030));
        assertEquals(Charset.forName("GB18030"),
                NgaNativeArticleRequestPolicy.WireFormat.LEGACY_GB18030.charset());
    }

    @Test
    public void buildsUtf8StructuredFallbackWithoutChangingThreadIdentity() {
        ArticleListParam param = new ArticleListParam();
        param.tid = 123;
        param.pid = 456;
        param.authorId = 789;

        assertEquals(
                "https://ngabbs.com/read.php?page=7&__output=11&noprefix&v2"
                        + "&tid=123&pid=456&authorid=789",
                NgaNativeArticleRequestPolicy.buildReadUrl(
                        "https://ngabbs.com", param, 7,
                        NgaNativeArticleRequestPolicy.WireFormat.UTF8_ARRAYS));
        assertEquals(StandardCharsets.UTF_8,
                NgaNativeArticleRequestPolicy.WireFormat.UTF8_ARRAYS.charset());
    }
}
