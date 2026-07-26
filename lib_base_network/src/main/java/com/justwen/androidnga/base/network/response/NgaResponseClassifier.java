package com.justwen.androidnga.base.network.response;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single bounded owner for transport decoding and coarse NGA response classification. */
public final class NgaResponseClassifier {

    private static final Pattern CHARSET_PATTERN = Pattern.compile(
            "(?:^|;)\\s*charset\\s*=\\s*\\\"?([^;\\\"\\s]+)",
            Pattern.CASE_INSENSITIVE);

    public ClassifiedNgaResponse classify(RawNgaResponse response) {
        if (response == null) {
            throw new NullPointerException("response");
        }

        int status = response.getStatusCode();
        if (status == 429) {
            return outcome(ClassifiedNgaResponse.Type.RATE_LIMIT, status, null,
                    response.firstHeader("Retry-After"));
        }

        Decoded decoded = decode(response.getRawBytes(), response.firstHeader("Content-Type"));
        if (decoded.error) {
            return outcome(ClassifiedNgaResponse.Type.DECODE_ERROR, status, null, null);
        }
        String text = sanitize(decoded.value);
        String normalized = text.toLowerCase(Locale.ROOT);

        if (isChallenge(normalized)) {
            return outcome(ClassifiedNgaResponse.Type.CHALLENGE, status, null, null);
        }
        if (status == 401 || status == 403) {
            return outcome(ClassifiedNgaResponse.Type.AUTH_REQUIRED, status, null, null);
        }
        if (status < 200 || status >= 400) {
            return outcome(ClassifiedNgaResponse.Type.HTTP_ERROR, status, null, null);
        }
        if (text.trim().isEmpty()) {
            return outcome(ClassifiedNgaResponse.Type.EMPTY, status, null, null);
        }
        if (isSiteMessage(normalized, response.firstHeader("Content-Type"))) {
            return outcome(ClassifiedNgaResponse.Type.SITE_MESSAGE, status, null, null);
        }
        return outcome(ClassifiedNgaResponse.Type.PAYLOAD, status, text, null);
    }

    public ClassifiedNgaResponse classifyFailure(Throwable failure) {
        if (failure instanceof ResponseTooLargeException) {
            return outcome(ClassifiedNgaResponse.Type.RESPONSE_TOO_LARGE, 0, null, null);
        }
        return outcome(ClassifiedNgaResponse.Type.NETWORK_ERROR, 0, null, null);
    }

    private static ClassifiedNgaResponse outcome(
            ClassifiedNgaResponse.Type type,
            int status,
            String payload,
            String retryAfter
    ) {
        return ClassifiedNgaResponse.of(type, status, payload, retryAfter);
    }

    private static Decoded decode(byte[] bytes, String contentType) {
        Charset declared = declaredCharset(contentType);
        if (contentType != null && containsCharsetParameter(contentType) && declared == null) {
            return Decoded.error();
        }
        if (declared != null) {
            return strictDecode(bytes, declared);
        }

        Decoded utf8 = strictDecode(bytes, StandardCharsets.UTF_8);
        if (!utf8.error) {
            return utf8;
        }
        try {
            return strictDecode(bytes, Charset.forName("GB18030"));
        } catch (RuntimeException unavailable) {
            return Decoded.error();
        }
    }

    private static Charset declaredCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    private static boolean containsCharsetParameter(String contentType) {
        return CHARSET_PATTERN.matcher(contentType).find()
                || contentType.toLowerCase(Locale.ROOT).contains("charset");
    }

    private static Decoded strictDecode(byte[] bytes, Charset charset) {
        try {
            String value = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return Decoded.value(value);
        } catch (CharacterCodingException invalid) {
            return Decoded.error();
        }
    }

    private static String sanitize(String value) {
        String sanitized = value;
        if (!sanitized.isEmpty() && sanitized.charAt(0) == '\ufeff') {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.startsWith(")]}'")) {
            int newline = sanitized.indexOf('\n');
            sanitized = newline >= 0 ? sanitized.substring(newline + 1) : "";
        }
        if (sanitized.startsWith("for(;;);")) {
            sanitized = sanitized.substring("for(;;);".length());
        }
        return sanitized;
    }

    private static boolean isChallenge(String normalized) {
        return normalized.contains("captcha")
                || normalized.contains("cf-chl-")
                || normalized.contains("challenge-platform")
                || normalized.contains("\u9a8c\u8bc1\u7801");
    }

    private static boolean isSiteMessage(String normalized, String contentType) {
        boolean htmlContentType = contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains("text/html");
        return htmlContentType
                || normalized.startsWith("<!doctype html")
                || normalized.startsWith("<html")
                || normalized.contains("<title>");
    }

    private static final class Decoded {
        final String value;
        final boolean error;

        private Decoded(String value, boolean error) {
            this.value = value;
            this.error = error;
        }

        static Decoded value(String value) {
            return new Decoded(value, false);
        }

        static Decoded error() {
            return new Decoded("", true);
        }
    }
}
