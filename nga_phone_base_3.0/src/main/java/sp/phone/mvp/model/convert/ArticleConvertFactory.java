package sp.phone.mvp.model.convert;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import gov.anzong.androidnga.Utils;
import gov.anzong.androidnga.common.util.NgaImageHost;
import gov.anzong.androidnga.core.HtmlConvertFactory;
import gov.anzong.androidnga.core.data.AttachmentData;
import gov.anzong.androidnga.core.data.CommentData;
import gov.anzong.androidnga.core.data.HtmlData;
import sp.phone.common.ForumConstants;
import sp.phone.common.PhoneConfiguration;
import sp.phone.common.UserManagerImpl;
import sp.phone.http.bean.Attachment;
import sp.phone.http.bean.ThreadData;
import sp.phone.http.bean.ThreadRowInfo;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.theme.ThemeManager;
import sp.phone.util.FunctionUtils;
import sp.phone.util.StringUtils;

/**
 * Created by Justwen on 2017/12/3.
 */

public class ArticleConvertFactory {

    public static ThreadData getArticleInfo(String js) {
        return parseArticleInfo(js).getData();
    }

    public static ParseOutcome parseWebArticleInfo(String js) {
        return parseArticleInfo(js, WebRenderOptions.fromPreferences());
    }

    static ParseOutcome parseWebArticleInfo(
            String js, int textSize, boolean nightMode, boolean showSignature) {
        return parseArticleInfo(js,
                new WebRenderOptions(textSize, nightMode, showSignature));
    }

    /**
     * Parse a THREAD.PAGE payload while retaining only redacted failure metadata.
     * Raw authenticated payloads and post content must never enter logs or diagnostics.
     */
    public static ParseOutcome parseArticleInfo(String js) {
        return parseArticleInfo(js, null);
    }

    private static ParseOutcome parseArticleInfo(String js, WebRenderOptions webRenderOptions) {
        String stage = "normalize";
        try {
            if (js == null || js.isEmpty()) {
                return ParseOutcome.empty();
            } else if (js.contains("/*error fill content")) {
                js = js.substring(0, js.indexOf("/*error fill content"));
            }

            js = js.replaceAll("/\\*\\$js\\$\\*/", "")
                    .replaceAll("\"content\":\\+(\\d+),", "\"content\":\"+$1\",")
                    .replaceAll("\"subject\":\\+(\\d+),", "\"subject\":\"+$1\",")
                    .replaceAll("\"content\":(0\\d+),", "\"content\":\"$1\",")
                    .replaceAll("\"subject\":(0\\d+),", "\"subject\":\"$1\",")
                    .replaceAll("\"author\":(0\\d+),", "\"author\":\"$1\",")
                    .replaceAll("\"alterinfo\":\"\\[(\\w|\\s)+\\]\\s+\",", ""); //部分页面打不开的问题
            stage = "root-json";
            js = stripKnownRootEnvelope(js);
            JSONObject root = JSON.parseObject(js);
            Object rawData = root.get("data");
            JSONObject obj = rawData instanceof JSONObject ? (JSONObject) rawData : null;
            if (obj == null) {
                return ParseOutcome.empty();
            }
            // Recognized NGA error payloads are classified by ErrorConvertFactory.
            if (obj.get("__MESSAGE") != null && obj.get("__R") == null) {
                return ParseOutcome.empty();
            }
            stage = "row-list";
            if (webRenderOptions == null && containsWebFallbackRows(obj)) {
                throw new PayloadParseException(
                        "row-list", -1, -1,
                        new IllegalStateException("Rendered web rows require the web parser"));
            }
            List<ThreadRowInfo> rowList = buildThreadRowList(obj, webRenderOptions);
            stage = "thread-info";
            ThreadPageInfo threadInfo = buildThreadPageInfo(obj, rowList);
            ThreadData data = new ThreadData();
            data.setRawData(js);
            data.setThreadInfo(threadInfo);
            data.setRowList(rowList);
            stage = "page-count";
            int allRows = resolveAllRows(obj.get("__ROWS"), threadInfo, data.getRowList());
            data.set__ROWS(allRows);
            data.setRowNum(data.getRowList().size());
            return ParseOutcome.success(data);
        } catch (PayloadParseException e) {
            ParseDiagnostic diagnostic = e.toDiagnostic();
            return ParseOutcome.failure(diagnostic);
        } catch (Exception e) {
            ParseDiagnostic diagnostic = "root-json".equals(stage)
                    ? ParseDiagnostic.forRootJson(js, e)
                    : ParseDiagnostic.of(stage, -1, -1, e);
            return ParseOutcome.failure(diagnostic);
        }
    }

    /** Remove only wrappers that NGA itself uses around JSON responses. */
    static String stripKnownRootEnvelope(String payload) {
        if (payload == null || payload.isEmpty()) return payload;
        int start = 0;
        while (start < payload.length()) {
            char value = payload.charAt(start);
            if (value == '\ufeff' || Character.isWhitespace(value)) start++;
            else break;
        }
        String prefix = "window.script_muti_get_var_store=";
        if (payload.startsWith(prefix, start)) {
            start += prefix.length();
            while (start < payload.length() && Character.isWhitespace(payload.charAt(start))) {
                start++;
            }
            int end = payload.length();
            while (end > start && Character.isWhitespace(payload.charAt(end - 1))) end--;
            if (end > start && payload.charAt(end - 1) == ';') end--;
            return payload.substring(start, end);
        }
        return start == 0 ? payload : payload.substring(start);
    }

    private static boolean containsWebFallbackRows(JSONObject data) {
        Object rawRows = data.get("__R");
        if (!(rawRows instanceof JSONObject)) return false;
        for (Object value : ((JSONObject) rawRows).values()) {
            if (value instanceof JSONObject
                    && ((JSONObject) value).getBooleanValue("__WEB_FALLBACK_HTML")) {
                return true;
            }
        }
        return false;
    }


    static ThreadPageInfo buildThreadPageInfo(
            JSONObject obj, List<ThreadRowInfo> rows) {
        Object rawThread = obj.get("__T");
        JSONObject subObj = rawThread instanceof JSONObject ? (JSONObject) rawThread : null;
        if (subObj != null && subObj.size() == 1 && subObj.get("0") instanceof JSONObject) {
            subObj = subObj.getJSONObject("0");
        }
        if (subObj != null) {
            try {
                return JSONObject.toJavaObject(subObj, ThreadPageInfo.class);
            } catch (RuntimeException ignored) {
                // Synthesize the small page header below; valid rows remain usable.
            }
        }
        ThreadPageInfo recovered = new ThreadPageInfo();
        if (rows == null || rows.isEmpty()) return recovered;
        ThreadRowInfo first = rows.get(0);
        recovered.setTid(first.getTid());
        recovered.setFid(first.getFid());
        recovered.setAuthor(first.getAuthor());
        recovered.setAuthorId(first.getAuthorid());
        recovered.setSubject(first.getSubject());
        recovered.setPid(first.getPid());
        recovered.setPostDate(nonNegativeInt(first.getPostdate(), 0));
        recovered.setReplies(Math.max(0, resolveAllRows(obj.get("__ROWS"), null, rows) - 1));
        return recovered;
    }

    private static List<ThreadRowInfo> buildThreadRowList(
            JSONObject obj, WebRenderOptions webRenderOptions) {
        Object rawRows = obj.get("__R");
        JSONObject subObj = rawRows instanceof JSONObject ? (JSONObject) rawRows : null;
        Object rawUserInfoMap = obj.get("__U");
        JSONObject userInfoMap = rawUserInfoMap instanceof JSONObject
                ? (JSONObject) rawUserInfoMap : null;
        if (subObj == null) {
            return new ArrayList<>();
        }
        int rows = Math.max(nonNegativeInt(obj.get("__R__ROWS"), -1), numericEntryCount(subObj));
        String attachmentsPrefix = resolveAttachmentsPrefix(obj);
        return convertJsObjToList(
                subObj, rows, userInfoMap, attachmentsPrefix, webRenderOptions);
    }

    private static int resolveAllRows(
            Object rawCount, ThreadPageInfo threadInfo, List<ThreadRowInfo> rows) {
        int explicit = nonNegativeInt(rawCount, -1);
        if (explicit >= 0) return explicit;
        int inferred = rows == null ? 0 : rows.size();
        if (threadInfo != null && threadInfo.getReplies() >= 0) {
            inferred = Math.max(inferred, threadInfo.getReplies() + 1);
        }
        if (rows != null) {
            for (ThreadRowInfo row : rows) {
                if (row != null && row.getLou() >= 0) inferred = Math.max(inferred, row.getLou() + 1);
            }
        }
        return inferred;
    }

    private static int numericEntryCount(JSONObject object) {
        int max = 0;
        for (String key : object.keySet()) {
            try {
                int index = Integer.parseInt(key);
                if (index >= 0 && index < Integer.MAX_VALUE) max = Math.max(max, index + 1);
            } catch (NumberFormatException ignored) {
                // Metadata keys do not contribute to the page row range.
            }
        }
        return max;
    }

    private static int nonNegativeInt(Object value, int fallback) {
        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return number >= 0 && number <= Integer.MAX_VALUE ? (int) number : fallback;
        }
        if (value instanceof String) {
            try {
                long number = Long.parseLong(((String) value).trim());
                return number >= 0 && number <= Integer.MAX_VALUE ? (int) number : fallback;
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 从当前 THREAD.PAGE 的 data 中提取并解析页面级附件前缀。 */
    static String resolveAttachmentsPrefix(JSONObject data) {
        String serverAttachmentBaseView = null;
        if (data != null) {
            Object globalValue = data.get("__GLOBAL");
            if (globalValue instanceof JSONObject) {
                Object rawValue = ((JSONObject) globalValue).get("_ATTACH_BASE_VIEW");
                if (rawValue instanceof String) {
                    serverAttachmentBaseView = (String) rawValue;
                }
            }
        }
        return NgaImageHost.attachmentsPrefix(serverAttachmentBaseView);
    }

    private static List<ThreadRowInfo> convertJsObjToList(
            JSONObject rowMap,
            int count,
            JSONObject userInfoMap,
            String attachmentsPrefix,
            WebRenderOptions webRenderOptions) {
        List<ThreadRowInfo> rowList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Object obj = rowMap.get(String.valueOf(i));
            JSONObject rowObj;
            if (obj instanceof JSONObject) {
                rowObj = (JSONObject) obj;
            } else {
                continue;
            }
            ThreadRowInfo row;
            try {
                row = JSONObject.toJavaObject(rowObj, ThreadRowInfo.class);
            } catch (RuntimeException ignored) {
                continue;
            }
            try { buildRowIpLocation(row, rowObj, userInfoMap); } catch (RuntimeException ignored) { }
            try { buildRowHotReplay(row, rowObj); } catch (RuntimeException ignored) { }
            try { buildRowComment(
                    row, rowObj, userInfoMap, attachmentsPrefix, webRenderOptions);
            } catch (RuntimeException ignored) { }
            try { buildRowClientInfo(row, rowObj); } catch (RuntimeException ignored) { }
            try { buildRowUserInfo(row, userInfoMap); } catch (RuntimeException ignored) { }
            try { buildRowVote(row, rowObj); } catch (RuntimeException ignored) { }
            try {
                buildRowContent(row, rowObj, attachmentsPrefix, webRenderOptions);
            } catch (RuntimeException ignored) {
                if (rowObj.getBooleanValue("__WEB_FALLBACK_HTML")) {
                    // Rendered web HTML is accepted only through parseWebArticleInfo().
                    continue;
                }
                row.setFormattedHtmlData(row.getContent() == null ? "" : row.getContent());
            }
            rowList.add(row);
        }
        return rowList;
    }

    /** Resolve locality once from THREAD.PAGE; the adapter only binds the normalized field. */
    static void buildRowIpLocation(
            ThreadRowInfo row, JSONObject rowObj, JSONObject userInfoMap) {
        JSONObject userInfo = pageUserInfo(row, userInfoMap);
        String username = userInfo == null ? null : userInfo.getString("username");
        if (row.getISANONYMOUS() || (username != null && username.startsWith("#anony_"))) {
            row.setIpLoc(null);
            return;
        }
        String value = normalizedIpLocation(rowObj.get("ipLoc"));
        if (value == null) value = normalizedIpLocation(row.getIpLoc());
        if (value == null) value = normalizedIpLocation(rowObj.get("ip_loc"));
        if (value == null) value = normalizedIpLocation(rowObj.get("ipLocation"));
        if (value == null) value = normalizedIpLocation(rowObj.get("ip_location"));
        if (value == null && userInfo != null) value = normalizedIpLocation(userInfo.get("ipLoc"));
        if (value == null && userInfo != null) value = normalizedIpLocation(userInfo.get("ip_loc"));
        if (value == null && userInfo != null) value = normalizedIpLocation(userInfo.get("ipLocation"));
        if (value == null && userInfo != null) value = normalizedIpLocation(userInfo.get("ip_location"));
        row.setIpLoc(value);
    }

    private static JSONObject pageUserInfo(ThreadRowInfo row, JSONObject userInfoMap) {
        if (row == null || row.getAuthorid() == 0 || userInfoMap == null) return null;
        Object value = userInfoMap.get(String.valueOf(row.getAuthorid()));
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    private static String normalizedIpLocation(Object raw) {
        if (!(raw instanceof String)) return null;
        String value = ((String) raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static void buildRowContent(
            ThreadRowInfo row,
            JSONObject rowObj,
            String attachmentsPrefix,
            WebRenderOptions webRenderOptions) {
        if (row.getContent() == null) {
            row.setContent(row.getSubject());
            row.setSubject(null);
        }
        if (rowObj.getBooleanValue("__WEB_FALLBACK_HTML")) {
            if (webRenderOptions == null) {
                throw new IllegalStateException("Web article rows require render options");
            }
            JSONArray imageUrls = rowObj.getJSONArray("__WEB_IMAGE_URLS");
            if (imageUrls != null) {
                for (Object rawUrl : imageUrls) {
                    if (rawUrl instanceof String && !((String) rawUrl).isEmpty()) {
                        row.addImageUrl((String) rawUrl);
                    }
                }
            }
            String signatureHtml = rowObj.getString("__WEB_SIGNATURE_HTML");
            row.setFormattedHtmlData(NgaWebArticleHtml.wrap(
                    row.getSubject(),
                    row.getContent(),
                    webRenderOptions.showSignature ? signatureHtml : null,
                    webRenderOptions.textSize,
                    webRenderOptions.nightMode));
            return;
        }
        if (!StringUtils.isEmpty(row.getFromClient())
                && row.getFromClient().startsWith("103 ")
                && !StringUtils.isEmpty(row.getContent())) {
            row.setContent(StringUtils.unescape(row.getContent()));
        }
        List<String> imageUrls = new ArrayList<>();
        String ngaHtml = HtmlConvertFactory.convert(
                buildHtmlData(row, attachmentsPrefix), imageUrls);
        row.getImageUrls().addAll(imageUrls);
        row.setFormattedHtmlData(ngaHtml);
    }

    private static HtmlData buildHtmlData(ThreadRowInfo row, String attachmentsPrefix) {
        HtmlData htmlData = new HtmlData(row.getContent());
        htmlData.setAttachmentsPrefix(attachmentsPrefix);
        htmlData.setAlertInfo(row.getAlterinfo());
        htmlData.setDarkMode(ThemeManager.getInstance().isNightMode());
        htmlData.setInBackList(row.get_isInBlackList());
        htmlData.setTextSize(PhoneConfiguration.getInstance().getTopicContentSize());
        htmlData.setEmotionSize(PhoneConfiguration.getInstance().getEmoticonSize());
        htmlData.setSignature(PhoneConfiguration.getInstance().isShowSignature() ? row.getSignature() : null);
        htmlData.setVote(row.getVote());
        htmlData.setSubject(row.getSubject());
        htmlData.setShowImage(PhoneConfiguration.getInstance().isImageLoadEnabled());
        htmlData.setNGAHost(Utils.getNGAHost());
        htmlData.pid = String.valueOf(row.pid);
        htmlData.tid = String.valueOf(row.tid);
        htmlData.uid = String.valueOf(row.getAuthorid());
        if (row.getAttachs() != null) {
            List<AttachmentData> attachments = new ArrayList<>();
            for (Map.Entry<String, Attachment> entry : row.getAttachs().entrySet()) {
                AttachmentData data = new AttachmentData();
                data.setAttachUrl(entry.getValue().getAttachurl());
                data.setThumb(entry.getValue().getThumb());
                attachments.add(data);
            }
            htmlData.setAttachmentList(attachments);
        }

        if (row.getComments() != null) {
            List<CommentData> comments = new ArrayList<>();
            for (ThreadRowInfo value : row.getComments()) {
                CommentData comment = new CommentData();
                comment.setAuthor(value.getAuthor());
                comment.setContent(value.getContent());
                comment.setPostTime(value.getPostdate());
                comment.setAvatarUrl(FunctionUtils.parseAvatarUrl(value.getJs_escap_avatar()));
                comments.add(comment);
            }
            htmlData.setCommentList(comments);
        }
        return htmlData;
    }

    private static void buildRowVote(ThreadRowInfo row, JSONObject rowObj) {
        String vote = rowObj.getString("vote");
        if (!StringUtils.isEmpty(vote)) {
            row.setVote(vote);
        }
    }

    //热门回复
    private static void buildRowHotReplay(ThreadRowInfo row, JSONObject rowObj) {
        String hotObj = rowObj.getString("17");
        if (hotObj != null) {
            row.hotReplies = new ArrayList<>();
            String[] hots = hotObj.split(",");
            for (String hot : hots) {
                if (!TextUtils.isEmpty(hot)) {
                    row.hotReplies.add(hot);
                }
            }
        }
    }

    //解析贴条
    static void buildRowComment(
            ThreadRowInfo row,
            JSONObject rowObj,
            JSONObject userInfoMap,
            String attachmentsPrefix) {
        buildRowComment(row, rowObj, userInfoMap, attachmentsPrefix, null);
    }

    private static void buildRowComment(
            ThreadRowInfo row,
            JSONObject rowObj,
            JSONObject userInfoMap,
            String attachmentsPrefix,
            WebRenderOptions webRenderOptions) {
        Object rawComment = rowObj.get("comment");
        JSONObject commObj = rawComment instanceof JSONObject ? (JSONObject) rawComment : null;
        if (commObj != null) {
            row.setComments(convertJsObjToList(
                    commObj, commObj.size(), userInfoMap, attachmentsPrefix,
                    webRenderOptions));
        }
    }

    private static final class WebRenderOptions {
        final int textSize;
        final boolean nightMode;
        final boolean showSignature;

        WebRenderOptions(int textSize, boolean nightMode, boolean showSignature) {
            this.textSize = Math.max(8, Math.min(72, textSize));
            this.nightMode = nightMode;
            this.showSignature = showSignature;
        }

        static WebRenderOptions fromPreferences() {
            PhoneConfiguration configuration = PhoneConfiguration.getInstance();
            return new WebRenderOptions(
                    configuration.getTopicContentSize(),
                    ThemeManager.getInstance().isNightMode(),
                    configuration.isShowSignature());
        }
    }

    public static final class ParseOutcome {
        private final ThreadData data;
        private final ParseDiagnostic diagnostic;

        private ParseOutcome(ThreadData data, ParseDiagnostic diagnostic) {
            this.data = data;
            this.diagnostic = diagnostic;
        }

        static ParseOutcome success(ThreadData data) {
            return new ParseOutcome(data, null);
        }

        static ParseOutcome empty() {
            return new ParseOutcome(null, null);
        }

        static ParseOutcome failure(ParseDiagnostic diagnostic) {
            return new ParseOutcome(null, diagnostic);
        }

        public ThreadData getData() {
            return data;
        }

        public ParseDiagnostic getDiagnostic() {
            return diagnostic;
        }
    }

    public static final class ParseDiagnostic {
        private static final Pattern ERROR_OFFSET_PATTERN = Pattern.compile(
                "(?:pos|position)\\s*[:=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

        private final String stage;
        private final int rowIndex;
        private final int floor;
        private final String causeType;
        private final String payloadShape;
        private final int payloadLength;
        private final int errorOffset;
        private final String errorTokenClass;
        private final String reasonCode;

        private ParseDiagnostic(
                String stage,
                int rowIndex,
                int floor,
                String causeType,
                String payloadShape,
                int payloadLength,
                int errorOffset,
                String errorTokenClass,
                String reasonCode) {
            this.stage = stage;
            this.rowIndex = rowIndex;
            this.floor = floor;
            this.causeType = causeType;
            this.payloadShape = payloadShape;
            this.payloadLength = payloadLength;
            this.errorOffset = errorOffset;
            this.errorTokenClass = errorTokenClass;
            this.reasonCode = reasonCode;
        }

        static ParseDiagnostic of(String stage, int rowIndex, int floor, Throwable cause) {
            return new ParseDiagnostic(stage, rowIndex, floor,
                    cause == null ? "Unknown" : cause.getClass().getSimpleName(),
                    null, -1, -1, null, null);
        }

        static ParseDiagnostic forRootJson(String payload, Throwable cause) {
            int offset = extractErrorOffset(cause);
            return new ParseDiagnostic(
                    "root-json",
                    -1,
                    -1,
                    cause == null ? "Unknown" : cause.getClass().getSimpleName(),
                    classifyPayloadShape(payload),
                    payload == null ? 0 : payload.length(),
                    offset,
                    classifyTokenAt(payload, offset),
                    classifyReason(cause));
        }

        private static int extractErrorOffset(Throwable cause) {
            String message = cause == null ? null : cause.getMessage();
            if (message == null) return -1;
            Matcher matcher = ERROR_OFFSET_PATTERN.matcher(message);
            if (!matcher.find()) return -1;
            try {
                long value = Long.parseLong(matcher.group(1));
                return value > Integer.MAX_VALUE ? -1 : (int) value;
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private static String classifyPayloadShape(String payload) {
            if (payload == null || payload.isEmpty()) return "empty";
            for (int i = 0; i < payload.length(); i++) {
                char value = payload.charAt(i);
                if (value == '\ufeff' || Character.isWhitespace(value)) continue;
                if (value == '{') return "json-object";
                if (value == '[') return "json-array";
                if (value == '<') return "html";
                return "other";
            }
            return "empty";
        }

        private static String classifyTokenAt(String payload, int offset) {
            if (payload == null || offset < 0 || offset >= payload.length()) return "unavailable";
            char value = payload.charAt(offset);
            if (value < 0x20) return Character.isWhitespace(value) ? "whitespace" : "control";
            if (Character.isWhitespace(value)) return "whitespace";
            if (value == '"') return "quote";
            if (value == '\\') return "backslash";
            if (value == '{' || value == '}') return "brace";
            if (value == '[' || value == ']') return "bracket";
            if (value == ':' || value == ',') return "separator";
            if (value >= '0' && value <= '9') return "digit";
            if ((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')) {
                return "ascii-letter";
            }
            return value > 0x7f ? "non-ascii" : "other";
        }

        private static String classifyReason(Throwable cause) {
            String message = cause == null ? null : cause.getMessage();
            if (message == null) return "parser-rejected";
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("unclosed string") || normalized.contains("unclosed str")) {
                return "unclosed-string";
            }
            if (normalized.contains("illegal escape")) return "illegal-escape";
            if (normalized.contains("eof")) return "unexpected-eof";
            if (normalized.contains("expect")) return "expected-token";
            if (normalized.contains("syntax error")) return "syntax-error";
            return "parser-rejected";
        }

        public String getStage() {
            return stage;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public int getFloor() {
            return floor;
        }

        public String getCauseType() {
            return causeType;
        }

        public String getPayloadShape() {
            return payloadShape;
        }

        public int getPayloadLength() {
            return payloadLength;
        }

        public int getErrorOffset() {
            return errorOffset;
        }

        public String getErrorTokenClass() {
            return errorTokenClass;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String toUserMessage(int tid, int page) {
            StringBuilder builder = new StringBuilder("原生帖子解析失败")
                    .append("\ntid=").append(tid)
                    .append("  page=").append(page)
                    .append("\nstage=").append(stage);
            if (rowIndex >= 0) builder.append("  row=").append(rowIndex);
            if (floor >= 0) builder.append("  floor=").append(floor);
            builder.append("\ncause=").append(causeType);
            if (payloadShape != null) {
                builder.append("\nshape=").append(payloadShape)
                        .append("  length=").append(payloadLength)
                        .append("\nreason=").append(reasonCode)
                        .append("  offset=").append(errorOffset)
                        .append("  token=").append(errorTokenClass);
            }
            return builder
                    .append("\n\n请截图此信息反馈；诊断不包含帖子正文或登录信息。")
                    .toString();
        }

    }

    private static final class PayloadParseException extends RuntimeException {
        private final String stage;
        private final int rowIndex;
        private final int floor;

        PayloadParseException(String stage, int rowIndex, int floor, Throwable cause) {
            super(cause);
            this.stage = stage;
            this.rowIndex = rowIndex;
            this.floor = floor;
        }

        ParseDiagnostic toDiagnostic() {
            return ParseDiagnostic.of(stage, rowIndex, floor, getCause());
        }
    }

    private static void buildRowClientInfo(ThreadRowInfo row, JSONObject rowObj) {
        String client = rowObj.getString("from_client");
        if (!StringUtils.isEmpty(client)) {
            row.setFromClient(client);
            if (!client.trim().equals("")) {
                String clientAppCode;
                if (client.contains(" ")) {
                    clientAppCode = client.substring(0, client.indexOf(' '));
                } else {
                    clientAppCode = client;
                }
                if (clientAppCode.equals("1") || clientAppCode.equals("7") || clientAppCode.equals("101")) {
                    row.setFromClientModel("ios");
                } else if (clientAppCode.equals("103") || clientAppCode.equals("9")) {
                    row.setFromClientModel("wp");
                } else if (!clientAppCode.equals("8") && !clientAppCode.equals("100")) {
                    row.setFromClientModel("unknown");
                } else {
                    row.setFromClientModel("android");
                }
            }
        }
    }

    private static void buildRowUserInfo(ThreadRowInfo row, JSONObject userInfoMap) {
        if (row.getAuthorid() == 0 || userInfoMap == null) {
            return;
        }
        JSONObject userInfo = pageUserInfo(row, userInfoMap);
        if (userInfo == null) {
            return;
        }
        String username = userInfo.getString("username");
        if (username == null) {
            return;
        }
        Object rawGroupInfo = userInfoMap.get("__GROUPS");
        JSONObject groupObj = rawGroupInfo instanceof JSONObject
                ? (JSONObject) rawGroupInfo : null;
        int uid = row.getAuthorid();
        row.set_IsInBlackList(UserManagerImpl.getInstance().checkBlackList(String.valueOf(uid)));
        String t1 = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥";
        String t2 = "王李张刘陈杨黄吴赵周徐孙马朱胡林郭何高罗郑梁谢宋唐许邓冯韩曹曾彭萧蔡潘田董袁于余叶蒋杜苏魏程吕丁沈任姚卢傅钟姜崔谭廖范汪陆金石戴贾韦夏邱方侯邹熊孟秦白江阎薛尹段雷黎史龙陶贺顾毛郝龚邵万钱严赖覃洪武莫孔汤向常温康施文牛樊葛邢安齐易乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿关兰焦俞左柳甘祝包宁尚符舒阮柯纪梅童凌毕单季裴霍涂成苗谷盛曲翁冉骆蓝路游辛靳管柴蒙鲍华喻祁蒲房滕屈饶解牟艾尤阳时穆农司卓古吉缪简车项连芦麦褚娄窦戚岑景党宫费卜冷晏席卫米柏宗瞿桂全佟应臧闵苟邬边卞姬师和仇栾隋商刁沙荣巫寇桑郎甄丛仲虞敖巩明佘池查麻苑迟邝 ";
        if (username.length() == 39 && username.startsWith("#anony_")) {
            StringBuilder builder = new StringBuilder();
            int i = 6;
            for (int j = 0; j < 6; j++) {
                int pos;
                if (j == 0 || j == 3) {
                    pos = Integer.valueOf(username.substring(i + 1, i + 2), 16);
                    builder.append(t1.charAt(pos));
                } else {
                    pos = Integer.valueOf(username.substring(i, i + 2), 16);
                    builder.append(t2.charAt(pos));
                }
                i += 2;
            }
            row.setAuthor(builder.toString());
            row.setISANONYMOUS(true);
        } else {
            row.setAuthor(username);
        }
        row.setJs_escap_avatar(userInfo.getString("avatar"));
        row.setYz(userInfo.getString("yz"));
        row.setMuteTime(userInfo.getString("mute_time"));
        try {
            row.setAurvrc(Integer.valueOf(userInfo.getString("rvrc")));
        } catch (Exception e) {
            row.setAurvrc(0);
        }
        row.setSignature(userInfo.getString("signature"));

        try {
            row.setPostCount(userInfo.getString("postnum"));
            row.setReputation(Float.parseFloat(userInfo.getString("rvrc")) / 10.0f);
            if (groupObj != null && groupObj.getJSONObject(userInfo.getString("memberid")) != null) {
                row.setMemberGroup(groupObj.getJSONObject(userInfo.getString("memberid")).getString("0"));
            }
        } catch (Exception e) {
        }

        JSONObject obj = userInfo.getJSONObject("buffs");
        if (obj != null) {
            for (String id : ForumConstants.BUFF_MUTE_IDS) {
                if (obj.containsKey(id)) {
                    row.setMuted(true);
                    break;
                }
            }
        }
    }

}
