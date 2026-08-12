package sp.phone.mvp.model.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashSet;

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
    public void unescapedQuoteInTextIsRepairedByTheRealParserEntryPoint() {
        String payload = "{\"data\":{"
                + "\"__ROWS\":0,"
                + "\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"before \\\"quoted\\\" after\"},"
                + "\"__R\":{},"
                + "\"__U\":{}}}";
        payload = payload.replace("\\\"quoted\\\"", "\"quoted\"");

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        ThreadData data = outcome.getData();
        assertNotNull(data);
        assertEquals(0, data.getRowList().size());
    }

    @Test
    public void truncatedTailAtQuoteClosesOnlyKnownJsonContainers() {
        String payload = "{\"data\":{"
                + "\"__ROWS\":0,"
                + "\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\"},"
                + "\"__R\":{},"
                + "\"__U\":\"";

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
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
    public void truncatedDanglingEscapeAndTrailingQuoteAreBoundedlyRecovered() {
        String danglingEscape = "{\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\\";
        ArticleConvertFactory.ParseOutcome escapeOutcome =
                ArticleConvertFactory.parseArticleInfo(danglingEscape);
        assertNull(escapeOutcome.getDiagnostic());
        assertNotNull(escapeOutcome.getData());

        String trailingQuote = "{\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\"},"
                + "\"__R\":{},\"__U\":{}}}\"";
        ArticleConvertFactory.ParseOutcome quoteOutcome =
                ArticleConvertFactory.parseArticleInfo(trailingQuote);
        assertNull(quoteOutcome.getDiagnostic());
        assertNotNull(quoteOutcome.getData());
    }

    @Test
    public void rawQuoteCommaInsideTextDoesNotMasqueradeAsAFieldBoundary() {
        String payload = "{\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"say \\\"hello\\\", then leave\"},"
                + "\"__R\":{},\"__U\":{}}}";
        payload = payload.replace("\\\"hello\\\"", "\"hello\"");

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
    }

    @Test
    public void rawControlCharacterAndKnownNgaWrapperAreRecovered() {
        String payload = "window.script_muti_get_var_store={\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"first\nsecond\"},"
                + "\"__R\":{},\"__U\":{}}};";

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
    }

    @Test
    public void rawQuoteCommaAndTruncatedTailAreRecoveredTogether() {
        String payload = "{\"data\":{"
                + "\"__ROWS\":0,\"__R__ROWS\":0,"
                + "\"__T\":{\"tid\":123,\"subject\":\"say \\\"hello\\\", then leave\"},"
                + "\"__R\":{},\"__U\":\"unfinished";
        payload = payload.replace("\\\"hello\\\"", "\"hello\"");

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
    }

    @Test
    public void damagedFinalDataMemberIsDroppedWithoutLosingRows() {
        String payload = "{\"data\":{" + "\"__ROWS\":1,"
                + "\"__R__ROWS\":1,"
                + "\"__T\":{\"tid\":123,\"subject\":\"fixture\"},"
                + "\"__R\":{\"0\":\"invalid row\"},"
                + "\"__U\":\"broken tail";

        ArticleConvertFactory.ParseOutcome outcome =
                ArticleConvertFactory.parseArticleInfo(payload);

        assertNull(outcome.getDiagnostic());
        assertNotNull(outcome.getData());
        assertEquals(0, outcome.getData().getRowList().size());
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
    public void damagedLastRowKeepsEarlierCompleteRowsMap() {
        String payload = "{\"data\":{\"__T\":{\"tid\":123},\"__R\":{" +
                "\"0\":\"ignored non-object\",\"1\":{\"content\":\"broken";

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        ArticleConvertFactory.addSalvagedNestedObjectCandidates(
                candidates, payload, "__R", 3);

        JSONObject recovered = JSON.parseObject(candidates.iterator().next());
        assertEquals(1, recovered.getJSONObject("data").getJSONObject("__R").size());
    }

    private static JSONObject pageDataWithAttachmentBaseView(Object value) {
        JSONObject global = new JSONObject();
        global.put("_ATTACH_BASE_VIEW", value);
        JSONObject data = new JSONObject();
        data.put("__GLOBAL", global);
        return data;
    }
}
