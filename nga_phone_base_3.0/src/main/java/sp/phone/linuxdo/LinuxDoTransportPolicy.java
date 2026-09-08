package sp.phone.linuxdo;

import java.util.regex.Pattern;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Android-free security and response classification rules for the WebView transport. */
public final class LinuxDoTransportPolicy {
    private static final int MAX_PAGE_OR_OFFSET = 10_000;
    private static final Pattern ALLOWED_PATH = Pattern.compile(
            "^/(?:latest\\.json(?:\\?page=\\d+)?|new\\.json(?:\\?page=\\d+)?|"
                    + "unread\\.json(?:\\?page=\\d+)?|top\\.json\\?period=[A-Za-z0-9_-]+&page=\\d+|"
                    + "categories\\.json|site\\.json|"
                    + "c/(?:[A-Za-z0-9._%~-]+/)*\\d+\\.json\\?page=\\d+|"
                    + "t/\\d+\\.json|t/\\d+/posts\\.json\\?post_number=2|"
                    + "t/\\d+/posts\\.json\\?post_ids(?:%5B%5D|\\[\\])=\\d+"
                    + "(?:&post_ids(?:%5B%5D|\\[\\])=\\d+)*|u/[A-Za-z0-9._%~-]+\\.json|"
                    + "posts/\\d+/reply-history\\.json|"
                    + "u/[A-Za-z0-9._%~-]+/(?:summary|activity)\\.json(?:\\?offset=\\d+)?|"
                    + "user_actions\\.json\\?username=[A-Za-z0-9._%~-]+&offset=\\d+&filter=4%2C5|"
                    + "user-badges/[A-Za-z0-9._%~-]+\\.json|"
                    + "search\\.json\\?q=[A-Za-z0-9._%~+%-]{1,1800}&page=\\d+|"
                    + "notifications\\.json\\?offset=\\d+|notifications/totals\\.json|"
                    + "session/(?:csrf|current)\\.json)$");
    private static final Pattern ALLOWED_MUTATION_PATH = Pattern.compile(
            "^/(?:session(?:\\.json)?|posts\\.json|post_actions(?:/\\d+\\.json)?|"
                    + "(?:captcha/)?hcaptcha/create\\.json|"
                    + "discourse-boosts/posts/\\d+/boosts|"
                    + "discourse-reactions/posts/\\d+/custom-reactions/"
                    + "[A-Za-z0-9_+-]{1,64}/toggle\\.json|"
                    + "polls/vote|"
                    + "notifications/(?:mark-read|read)\\.json)$");

    enum ResponseKind {
        JSON,
        VERIFICATION_REQUIRED,
        INVALID
    }

    static boolean isAllowedPath(String path) {
        if (path == null || !ALLOWED_PATH.matcher(path).matches()) return false;
        return boundedQueryNumber(path, "page")
                && boundedQueryNumber(path, "offset");
    }

    private static boolean boundedQueryNumber(String path, String key) {
        int start = path.indexOf("?" + key + "=");
        if (start < 0) start = path.indexOf("&" + key + "=");
        if (start < 0) return true;
        start += key.length() + 2;
        int end = path.indexOf('&', start);
        if (end < 0) end = path.length();
        try {
            long value = Long.parseLong(path.substring(start, end));
            return value >= 0 && value <= MAX_PAGE_OR_OFFSET;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** The visible login gate may fetch only its first-party document through native DoH. */
    static boolean isAllowedLoginPath(String path) {
        return "/login".equals(path);
    }

    static boolean isAllowedMutationPath(String path) {
        return path != null && ALLOWED_MUTATION_PATH.matcher(path).matches();
    }

    static boolean isAllowedAvatarHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "linux.do".equals(normalized) || normalized.endsWith(".linux.do");
    }

    /**
     * First-party and LINUX DO's static CDN hosts used by inline avatars and emoji images.
     * Keeping this allowlist here prevents the WebView media bridge from becoming a general
     * purpose proxy while still allowing the same DoH-isolated transport for reaction assets.
     */
    public static boolean isAllowedMediaHost(String host) {
        if (isAllowedAvatarHost(host)) return true;
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("(?:cdn[0-9]*|linuxdo-uploads\\.s3)\\.ldstatic\\.com");
    }

    /**
     * Cloudflare's managed challenge runs a same-page iframe on this exact host.
     * It must stay inside the login WebView; arbitrary third-party navigation must
     * still be handed off to the system browser.
     */
    static boolean isAllowedChallengeHost(String scheme, String host, int port,
            String userInfo) {
        if (!"https".equalsIgnoreCase(scheme) || port != -1
                || (userInfo != null && !userInfo.isEmpty()) || host == null) {
            return false;
        }
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        // Cloudflare Turnstile and hCaptcha both render their managed challenge in
        // cross-origin frames. They must stay in the same proxied WebView so the
        // resulting clearance/token is tied to the login session instead of being
        // handed to an external browser with a different cookie jar.
        return "challenges.cloudflare.com".equals(normalized)
                || "hcaptcha.com".equals(normalized)
                || normalized.endsWith(".hcaptcha.com");
    }

    /**
     * Turnstile creates an isolated iframe document while it is bootstrapping.
     * These URLs are browser-internal and must never be handed to an external
     * activity by the login WebView.
     */
    static boolean isAllowedChallengeDocument(String url) {
        // about:blank and about:srcdoc are opaque URIs (their value is not exposed
        // consistently as Uri.getPath() across Android WebView releases), so keep
        // the comparison on the complete normalized URL instead of guessing from
        // scheme/host/path components.
        return "about:blank".equalsIgnoreCase(url)
                || "about:srcdoc".equalsIgnoreCase(url);
    }

    static ResponseKind classify(int status, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (status == 401 || status == 403 || (status >= 300 && status < 400)
                || trimmed.startsWith("<")
                || containsChallengeMarker(trimmed)) {
            return ResponseKind.VERIFICATION_REQUIRED;
        }
        return status >= 200 && status < 300
                && (trimmed.startsWith("{") || trimmed.startsWith("["))
                ? ResponseKind.JSON : ResponseKind.INVALID;
    }

    /** Classifies native responses while retaining Cloudflare's explicit challenge header. */
    static ResponseKind classify(
            int status, String body, Map<String, List<String>> headers) {
        return hasChallengeHeader(headers)
                ? ResponseKind.VERIFICATION_REQUIRED : classify(status, body);
    }

    static ResponseKind classifyMutation(int status, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (status == 401 || status == 403 || (status >= 300 && status < 400)
                || trimmed.startsWith("<")
                || containsChallengeMarker(trimmed)) {
            return ResponseKind.VERIFICATION_REQUIRED;
        }
        return status >= 200 && status < 300
                ? ResponseKind.JSON : ResponseKind.INVALID;
    }

    static ResponseKind classifyMutation(
            int status, String body, Map<String, List<String>> headers) {
        return hasChallengeHeader(headers)
                ? ResponseKind.VERIFICATION_REQUIRED : classifyMutation(status, body);
    }

    private static boolean containsChallengeMarker(String body) {
        return body.contains("cf-chl-")
                || body.contains("cf_chl_opt")
                || body.contains("challenge-platform")
                || body.toLowerCase(Locale.ROOT).contains("just a moment");
    }

    private static boolean hasChallengeHeader(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return false;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"cf-mitigated".equalsIgnoreCase(entry.getKey())) continue;
            List<String> values = entry.getValue();
            if (values == null) continue;
            for (String value : values) {
                if (value != null && "challenge".equalsIgnoreCase(value.trim())) return true;
            }
        }
        return false;
    }

    private LinuxDoTransportPolicy() {
    }
}
