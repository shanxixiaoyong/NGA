package sp.phone.mvp.model.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSONObject;

import org.junit.Before;
import org.junit.Test;

import gov.anzong.androidnga.common.util.NgaImageHost;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;

public class ArticleConvertFactoryTest {

    @Before
    public void resetImageHostPreferenceCache() {
        NgaImageHost.invalidate();
    }

    @Test
    public void resolvesValidPageAttachmentBaseView() {
        JSONObject data = pageDataWithAttachmentBaseView(
                "https://page.example/attachments/");

        assertEquals("https://page.example/attachments",
                ArticleConvertFactory.resolveAttachmentsPrefix(data));
    }

    @Test
    public void missingGlobalFallsBackWithoutChangingOtherPageData() {
        JSONObject data = new JSONObject();
        data.put("__ROWS", 3);

        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(data));
        assertEquals(3, data.getIntValue("__ROWS"));
    }

    @Test
    public void missingMalformedOrNonStringFieldFallsBack() {
        JSONObject missingField = new JSONObject();
        missingField.put("__GLOBAL", new JSONObject());
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(missingField));

        JSONObject malformedGlobal = new JSONObject();
        malformedGlobal.put("__GLOBAL", "bad");
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(malformedGlobal));

        JSONObject nonStringField = pageDataWithAttachmentBaseView(123);
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(nonStringField));

        JSONObject invalidField = pageDataWithAttachmentBaseView(
                "https://img.nga.cn/not-attachments");
        assertEquals(NgaImageHost.DEFAULT_ATTACHMENTS_PREFIX,
                ArticleConvertFactory.resolveAttachmentsPrefix(invalidField));
    }

    @Test
    public void localityPrefersRowThenPageUserWithoutAnotherRequest() {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setAuthorid(42);
        JSONObject rowData = new JSONObject();
        rowData.put("ipLoc", "广东");
        JSONObject user = new JSONObject();
        user.put("username", "tester");
        user.put("ipLoc", "上海");
        JSONObject users = new JSONObject();
        users.put("42", user);

        ArticleConvertFactory.buildRowIpLocation(row, rowData, users);
        assertEquals("广东", row.getIpLoc());

        rowData.remove("ipLoc");
        row.setIpLoc(null);
        ArticleConvertFactory.buildRowIpLocation(row, rowData, users);
        assertEquals("上海", row.getIpLoc());
    }

    @Test
    public void anonymousLocalityIsSuppressed() {
        ThreadRowInfo row = new ThreadRowInfo();
        row.setAuthorid(42);
        JSONObject rowData = new JSONObject();
        rowData.put("ipLoc", "广东");
        JSONObject user = new JSONObject();
        user.put("username", "#anony_12345678901234567890123456789012");
        JSONObject users = new JSONObject();
        users.put("42", user);

        ArticleConvertFactory.buildRowIpLocation(row, rowData, users);
        assertNull(row.getIpLoc());
    }

    @Test
    public void malformedOptionalCommentDoesNotRejectTheWholePage() {
        ThreadRowInfo row = new ThreadRowInfo();
        JSONObject rowData = new JSONObject();
        rowData.put("comment", "unexpected");

        ArticleConvertFactory.buildRowComment(row, rowData, null, "https://img.example");

        assertNull(row.getComments());
    }

    @Test
    public void malformedRootJsonReportsOnlyShapeOffsetAndTokenClass() {
        String payload = "{\"data\":{\"content\":\"PRIVATE_ROOT_MARKER\"}]}";

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getData());
        ArticleConvertFactory.ParseDiagnostic diagnostic = outcome.getDiagnostic();
        assertNotNull(diagnostic);
        assertEquals("root-json", diagnostic.getStage());
        assertEquals("json-object", diagnostic.getPayloadShape());
        assertEquals(payload.length(), diagnostic.getPayloadLength());
        assertEquals("JSONException", diagnostic.getCauseType());
        assertTrue(diagnostic.getErrorOffset() >= 0);
        assertNotNull(diagnostic.getErrorTokenClass());
        assertNotNull(diagnostic.getReasonCode());
        assertFalse(diagnostic.toUserMessage(123, 1).contains("PRIVATE_ROOT_MARKER"));
    }

    @Test
    public void unescapedQuoteInTextIsRejectedWithoutGuessing() {
        String payload = "{\"data\":{"
                + "\"__ROWS\":0,"
                + "\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"before \\\"quoted\\\" after\"},"
                + "\"__R\":{},"
                + "\"__U\":{}}}";
        payload = payload.replace("\\\"quoted\\\"", "\"quoted\"");

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getData());
        assertNotNull(outcome.getDiagnostic());
        assertEquals("root-json", outcome.getDiagnostic().getStage());
    }

    @Test
    public void truncatedPayloadVariantsAreRejectedWithoutTailRepair() {
        String[] payloads = {
                "{\"data\":{\"__T\":{\"tid\":123,\"subject\":\"fixture",
                "{\"data\":{\"__T\":{\"tid\":123,\"subject\":\"fixture\\",
                "{\"data\":{\"__T\":{\"tid\":123},\"__R\":{},\"__U\":\"",
                "{\"data\":{\"__T\":{\"tid\":123},\"__R\":{\"0\":{\"content\":\"cut"
        };
        for (String payload : payloads) {
            ArticleConvertFactory.ParseOutcome outcome =
                    ArticleConvertFactory.parseArticleInfo(payload);
            assertNull(outcome.getData());
            assertNotNull(outcome.getDiagnostic());
            assertEquals("root-json", outcome.getDiagnostic().getStage());
        }
    }

    @Test
    public void missingAndStringPageCountsAreRecovered() {
        String missing = "{\"data\":{"
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\",\"replies\":2},"
                + "\"__R\":{},\"__U\":{}}}";
        ArticleConvertFactory.ParseOutcome missingOutcome =
                ArticleConvertFactory.parseArticleInfo(missing);
        assertNull(missingOutcome.getDiagnostic());
        assertEquals(3, missingOutcome.getData().get__ROWS());

        String encoded = "{\"data\":{"
                + "\"__ROWS\":\"12\",\"__R__ROWS\":\"0\","
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\"},"
                + "\"__R\":{},\"__U\":{}}}";
        ArticleConvertFactory.ParseOutcome encodedOutcome =
                ArticleConvertFactory.parseArticleInfo(encoded);
        assertNull(encodedOutcome.getDiagnostic());
        assertEquals(12, encodedOutcome.getData().get__ROWS());
    }

    @Test
    public void completeJsonInsideKnownNgaWrapperStillParses() {
        String payload = "window.script_muti_get_var_store={\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"first second\"},"
                + "\"__R\":{},\"__U\":{}}};";

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
    }

    @Test
    public void sanitizedWebSnapshotUsesNativeRowsAndReaderTheme() {
        String snapshot = "{\"data\":{"
                + "\"__ROWS\":1,\"__R__ROWS\":1,"
                + "\"__T\":{\"tid\":123,\"fid\":7,\"subject\":\"topic\",\"replies\":0},"
                + "\"__R\":{\"0\":{"
                + "\"tid\":123,\"fid\":7,\"pid\":99,\"authorid\":0,"
                + "\"author\":\"tester\",\"postdate\":\"2026-08-13\",\"lou\":0,"
                + "\"subject\":\"<unsafe>\",\"content\":\"<p>body</p>\","
                + "\"__WEB_FALLBACK_HTML\":true,"
                + "\"__WEB_IMAGE_URLS\":[\"https://img4.nga.cn/a.png\"],"
                + "\"__WEB_SIGNATURE_HTML\":\"<span>signature</span>\"}},"
                + "\"__U\":{}}}";

        ArticleConvertFactory.ParseOutcome webOutcome =
                ArticleConvertFactory.parseWebArticleInfo(snapshot, 18, false, true);
        assertNull(webOutcome.getDiagnostic());
        assertNotNull(webOutcome.getData());
        assertEquals(1, webOutcome.getData().getRowList().size());
        ThreadRowInfo row = webOutcome.getData().getRowList().get(0);
        assertEquals(99, row.getPid());
        assertEquals("https://img4.nga.cn/a.png", row.getImageUrls().get(0));
        assertTrue(row.getFormattedHtmlData().contains("&lt;unsafe&gt;"));
        assertTrue(row.getFormattedHtmlData().contains("<p>body</p>"));
        assertTrue(row.getFormattedHtmlData().contains("signature"));
        assertTrue(row.getFormattedHtmlData().contains("font-size:18px"));

        ArticleConvertFactory.ParseOutcome nativeOutcome =
                ArticleConvertFactory.parseArticleInfo(snapshot);
        assertNull(nativeOutcome.getData());
        assertNotNull(nativeOutcome.getDiagnostic());
        assertEquals("row-list", nativeOutcome.getDiagnostic().getStage());
    }

    @Test
    public void missingThreadInfoIsSynthesizedFromFirstValidRow() {
        JSONObject page = new JSONObject();
        ThreadRowInfo row = new ThreadRowInfo();
        row.setTid(321);
        row.fid = 7;
        row.setAuthor("tester");
        row.setAuthorid(9);
        row.setSubject("title");
        java.util.List<ThreadRowInfo> rows = java.util.Collections.singletonList(row);

        assertEquals(321, ArticleConvertFactory.buildThreadPageInfo(page, rows).getTid());
        assertEquals("title", ArticleConvertFactory.buildThreadPageInfo(page, rows).getSubject());
    }

    @Test
    public void webHtmlWrapperEscapesSubjectAndKeepsResponsiveMedia() {
        String html = NgaWebArticleHtml.wrap(
                "<script>", "<img src='https://img4.nga.cn/a.png'>", null, 20, true);

        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("style_dark.css"));
        assertTrue(html.contains("padding:0 8px"));
        assertTrue(html.contains("max-width:100%!important"));
    }

    private static JSONObject pageDataWithAttachmentBaseView(Object value) {
        JSONObject global = new JSONObject();
        global.put("_ATTACH_BASE_VIEW", value);
        JSONObject data = new JSONObject();
        data.put("__GLOBAL", global);
        return data;
    }
}
