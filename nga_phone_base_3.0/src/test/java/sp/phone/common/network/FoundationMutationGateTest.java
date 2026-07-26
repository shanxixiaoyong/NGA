package sp.phone.common.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FoundationMutationGateTest {

    @Test
    public void foundationBuildDeniesEveryKnownMutationOperation() {
        for (FoundationMutationGate.Operation operation
                : FoundationMutationGate.Operation.values()) {
            assertFalse(operation.name(), FoundationMutationGate.isAllowed(operation));
        }
        assertFalse(FoundationMutationGate.isAllowed(null));
    }

    @Test
    public void legacyTransportsCheckExplicitOperationBeforeOpeningConnection()
            throws IOException {
        String postClient = source("sp/phone/param/HttpPostClient.java");
        assertBefore(postClient,
                "FoundationMutationGate.isAllowed(operation)",
                "parseTrustedNgaUrl(urlString)");
        assertBefore(postClient,
                "FoundationMutationGate.isAllowed(operation)",
                "connectionOpener.open(url)");

        String avatarUpload = source("sp/phone/task/AvatarFileUploadTask.java");
        assertBefore(avatarUpload,
                "FoundationMutationGate.Operation.AVATAR_FILE_UPLOAD",
                "url.openConnection()");
    }

    @Test
    public void mutationCallersDeclareReviewedOperationIdentity() throws IOException {
        assertTrue(source("sp/phone/task/TopicPostTask.java")
                .contains("FoundationMutationGate.Operation.TOPIC_POST"));
        assertTrue(source("sp/phone/task/PostCommentTask.java")
                .contains("FoundationMutationGate.Operation.POST_COMMENT"));
        assertTrue(source("gov/anzong/androidnga/activity/AvatarPostActivity.java")
                .contains("FoundationMutationGate.Operation.AVATAR_PROFILE_UPDATE"));
    }

    @Test
    public void unauthenticatedImageDownloadDoesNotUseMutationGate() throws IOException {
        String httpUtil = source("sp/phone/util/HttpUtil.java");

        assertTrue(httpUtil.contains("url.openConnection()"));
        assertFalse(httpUtil.contains("FoundationMutationGate"));
    }

    @Test
    public void legacyAuthenticatedHttpUtilReadIsRemoved() throws IOException {
        String httpUtil = source("sp/phone/util/HttpUtil.java");

        assertFalse(httpUtil.contains("getHtml(String uri, String cookie)"));
        assertFalse(httpUtil.contains("setRequestProperty(\"Cookie\""));
    }

    private static void assertBefore(String source, String gate, String networkAction) {
        int gateIndex = source.indexOf(gate);
        int networkIndex = source.indexOf(networkAction);
        assertTrue("missing gate: " + gate, gateIndex >= 0);
        assertTrue("missing network action: " + networkAction, networkIndex >= 0);
        assertTrue("gate must precede network action", gateIndex < networkIndex);
    }

    private static String source(String relativePath) throws IOException {
        return new String(
                Files.readAllBytes(Paths.get("src/main/java").resolve(relativePath)),
                StandardCharsets.UTF_8);
    }
}
