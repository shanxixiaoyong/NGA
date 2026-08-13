package sp.phone.linuxdo;

import java.util.regex.Pattern;

/** Android-free security and response classification rules for the WebView transport. */
final class LinuxDoTransportPolicy {
    private static final Pattern ALLOWED_PATH = Pattern.compile(
            "^/(?:latest\\.json(?:\\?page=\\d+)?|categories\\.json|"
                    + "t/\\d+\\.json|t/\\d+/posts\\.json\\?post_ids(?:%5B%5D|\\[\\])=\\d+"
                    + "(?:&post_ids(?:%5B%5D|\\[\\])=\\d+)*|u/[A-Za-z0-9._%~-]+\\.json|"
                    + "session/(?:csrf|current)\\.json)$");
    private static final Pattern ALLOWED_MUTATION_PATH = Pattern.compile(
            "^/(?:posts\\.json|post_actions|"
                    + "discourse-boosts/posts/\\d+/boosts)$");

    enum ResponseKind {
        JSON,
        VERIFICATION_REQUIRED,
        INVALID
    }

    static boolean isAllowedPath(String path) {
        return path != null && ALLOWED_PATH.matcher(path).matches();
    }

    static boolean isAllowedMutationPath(String path) {
        return path != null && ALLOWED_MUTATION_PATH.matcher(path).matches();
    }

    static boolean isAllowedAvatarHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "linux.do".equals(normalized) || normalized.endsWith(".linux.do");
    }

    static ResponseKind classify(int status, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (status == 401 || status == 403 || (status >= 300 && status < 400)
                || trimmed.startsWith("<")
                || trimmed.contains("cf-chl-") || trimmed.contains("Just a moment")) {
            return ResponseKind.VERIFICATION_REQUIRED;
        }
        return status >= 200 && status < 300 && trimmed.startsWith("{")
                ? ResponseKind.JSON : ResponseKind.INVALID;
    }

    static ResponseKind classifyMutation(int status, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (status == 401 || status == 403 || (status >= 300 && status < 400)
                || trimmed.startsWith("<")
                || trimmed.contains("cf-chl-") || trimmed.contains("Just a moment")) {
            return ResponseKind.VERIFICATION_REQUIRED;
        }
        return status >= 200 && status < 300
                ? ResponseKind.JSON : ResponseKind.INVALID;
    }

    private LinuxDoTransportPolicy() {
    }
}
