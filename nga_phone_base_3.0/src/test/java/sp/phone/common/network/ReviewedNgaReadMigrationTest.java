package sp.phone.common.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ReviewedNgaReadMigrationTest {

    @Test
    public void primaryModelsUseOnlyReviewedTransportForReads() throws IOException {
        String topicModel = source("sp/phone/mvp/model/TopicListModel.java");
        String articleModel = source("sp/phone/mvp/model/ArticleListModel.java");

        assertTrue(topicModel.contains("mReadTransport.topicList(url)"));
        assertTrue(topicModel.contains("mReadTransport.topicList(getUrl(i, param))"));
        assertFalse(topicModel.contains("mService.get("));

        assertTrue(articleModel.contains("mReadTransport.articleList(url, header)"));
        assertFalse(articleModel.contains("RetrofitService"));
        assertFalse(articleModel.contains("mService.get("));
    }

    @Test
    public void applicationDoesNotInstallGlobalCookieProvider() throws IOException {
        String application = source("gov/anzong/androidnga/NgaClientApp.java");

        assertFalse(application.contains("setCookieProvider"));
    }

    private static String source(String relativePath) throws IOException {
        return new String(
                Files.readAllBytes(Paths.get("src/main/java").resolve(relativePath)),
                StandardCharsets.UTF_8);
    }
}
