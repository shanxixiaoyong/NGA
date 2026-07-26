package sp.phone.param;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import sp.phone.common.network.FoundationMutationGate;

public class HttpPostClientTest {

    @Test
    public void acceptsOnlyTrustedHttpsNgaEndpoints() {
        assertTrue(HttpPostClient.isTrustedNgaUrl("https://bbs.nga.cn/post.php"));
        assertTrue(HttpPostClient.isTrustedNgaUrl("HTTPS://NGABBS.COM:443/post.php"));

        assertFalse(HttpPostClient.isTrustedNgaUrl("http://bbs.nga.cn/post.php"));
        assertFalse(HttpPostClient.isTrustedNgaUrl("https://bbs.nga.cn.example.com/post.php"));
        assertFalse(HttpPostClient.isTrustedNgaUrl("https://evil.example/post.php"));
        assertFalse(HttpPostClient.isTrustedNgaUrl("https://bbs.nga.cn:8443/post.php"));
        assertFalse(HttpPostClient.isTrustedNgaUrl("https://user@bbs.nga.cn/post.php"));
    }

    @Test
    public void rejectsUnsafeRequestsBeforeOpeningAConnection() {
        HttpPostClient client = new HttpPostClient(
                "http://bbs.nga.cn/post.php",
                "ngaPassportCid=secret",
                FoundationMutationGate.Operation.TOPIC_POST);
        assertNull(client.post_body("post_content=hello"));

        HttpPostClient headerInjection = new HttpPostClient(
                "https://bbs.nga.cn/post.php",
                "Cookie\r\nX-Leak: value",
                FoundationMutationGate.Operation.POST_COMMENT);
        assertNull(headerInjection.post_body("post_content=hello"));
    }

    @Test
    public void deniedMutationNeverInvokesConnectionOpener() {
        AtomicInteger openCount = new AtomicInteger();
        HttpPostClient client = new HttpPostClient(
                "https://bbs.nga.cn/post.php",
                "ngaPassportCid=secret",
                FoundationMutationGate.Operation.TOPIC_POST,
                url -> {
                    openCount.incrementAndGet();
                    throw new AssertionError("connection opener must not be invoked");
                });

        assertNull(client.post_body("post_content=hello"));
        assertTrue(openCount.get() == 0);
    }
}
