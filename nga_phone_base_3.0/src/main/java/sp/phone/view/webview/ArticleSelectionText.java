package sp.phone.view.webview;

/**
 * Pure text helpers for the article selection toolbar.
 *
 * <p>The module has no Robolectric and does not return default values for stubbed framework
 * classes, so this class must stay free of {@code android.*} imports to remain unit testable.
 */
final class ArticleSelectionText {

    private static final String JS_NULL = "null";

    private ArticleSelectionText() {
    }

    /**
     * Decodes the JSON value handed back by {@code WebView.evaluateJavascript}.
     *
     * <p>A string result arrives quoted and escaped; a missing selection arrives as the literal
     * {@code null}. Anything else is passed through untouched.
     */
    static String decodeEvaluatedString(String rawValue) {
        if (rawValue == null || rawValue.isEmpty() || JS_NULL.equals(rawValue)) {
            return "";
        }
        if (rawValue.length() < 2 || rawValue.charAt(0) != '"'
                || rawValue.charAt(rawValue.length() - 1) != '"') {
            return rawValue;
        }

        StringBuilder decoded = new StringBuilder(rawValue.length() - 2);
        for (int index = 1; index < rawValue.length() - 1; index++) {
            char current = rawValue.charAt(index);
            if (current != '\\' || index == rawValue.length() - 2) {
                decoded.append(current);
                continue;
            }

            char escaped = rawValue.charAt(++index);
            switch (escaped) {
                case 'b':
                    decoded.append('\b');
                    break;
                case 'f':
                    decoded.append('\f');
                    break;
                case 'n':
                    decoded.append('\n');
                    break;
                case 'r':
                    decoded.append('\r');
                    break;
                case 't':
                    decoded.append('\t');
                    break;
                case 'u':
                    index = appendUnicodeEscape(rawValue, index, decoded);
                    break;
                default:
                    // Covers \" \\ \/ and any escape the engine did not need to expand.
                    decoded.append(escaped);
                    break;
            }
        }
        return decoded.toString();
    }

    private static int appendUnicodeEscape(String rawValue, int escapeIndex, StringBuilder target) {
        int start = escapeIndex + 1;
        int end = start + 4;
        if (end > rawValue.length() - 1) {
            target.append('u');
            return escapeIndex;
        }
        try {
            target.append((char) Integer.parseInt(rawValue.substring(start, end), 16));
        } catch (NumberFormatException e) {
            target.append('u');
            return escapeIndex;
        }
        return end - 1;
    }

    /**
     * {@code String.trim()} only strips characters up to U+0020, so forum text pasted with
     * no-break or ideographic spaces would survive it. Walk code points and combine both Unicode
     * predicates instead.
     */
    static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }
}
