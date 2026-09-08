package gov.anzong.androidnga.gallery;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small, Android-free helpers shared by the WebView click bridge and gallery activity. */
public final class ImageGalleryPolicy {

    private static final String[] IMAGE_SUFFIXES = {
            ".gif", ".jpg", ".jpeg", ".png", ".bmp", ".webp"
    };

    private ImageGalleryPolicy() {
    }

    /**
     * Treats a URL as an image when its path has a known raster/animated-image suffix.
     * Query strings and fragments are deliberately ignored because CDN image URLs commonly
     * carry cache or resize parameters after the suffix.
     */
    public static boolean isImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return false;
        String path = pathOf(rawUrl);
        String lower = path.toLowerCase(Locale.ROOT);
        for (String suffix : IMAGE_SUFFIXES) {
            if (lower.endsWith(suffix)) return true;
        }
        return false;
    }

    /** Returns a stable URL representation used only for comparing gallery entries. */
    public static String normalizeForComparison(String rawUrl) {
        if (rawUrl == null) return "";
        String value = rawUrl.trim().replace("&amp;", "&");
        if (value.isEmpty()) return "";
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null
                    ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null
                    ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            StringBuilder normalized = new StringBuilder();
            if (!scheme.isEmpty()) normalized.append(scheme).append("://");
            if (!host.isEmpty()) {
                normalized.append(host);
                if (port >= 0) normalized.append(':').append(port);
                normalized.append(uri.getRawPath() == null ? "" : uri.getRawPath());
                if (uri.getRawQuery() != null) normalized.append('?').append(uri.getRawQuery());
            } else {
                normalized.append(value);
            }
            return normalized.toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    /**
     * Finds the clicked image even when WebView has decoded HTML entities, normalized case, or
     * appended a CDN query string. Exact comparison is preferred; a same-origin path fallback
     * then handles cache queries and the thumbnail suffixes used by the NGA image hosts.
     */
    public static int findIndex(List<String> galleryUrls, String currentUrl) {
        if (galleryUrls == null || galleryUrls.isEmpty() || currentUrl == null) return -1;
        String exact = normalizeForComparison(currentUrl);
        for (int index = 0; index < galleryUrls.size(); index++) {
            String candidate = normalizeForComparison(galleryUrls.get(index));
            if (exact.equals(candidate) || exact.equalsIgnoreCase(candidate)) return index;
        }
        String currentPath = pathOf(currentUrl);
        if (currentPath.isEmpty()) return -1;
        for (int index = 0; index < galleryUrls.size(); index++) {
            String candidate = galleryUrls.get(index);
            if (!sameOriginOrRelative(currentUrl, candidate)) continue;
            String candidatePath = pathOf(candidate);
            if (currentPath.equals(candidatePath)
                    || currentPath.equalsIgnoreCase(candidatePath)) return index;
        }
        String currentImagePath = imagePath(currentPath);
        if (currentImagePath.isEmpty()) return -1;
        for (int index = 0; index < galleryUrls.size(); index++) {
            String candidate = galleryUrls.get(index);
            if (!sameOriginOrRelative(currentUrl, candidate)) continue;
            if (currentImagePath.equalsIgnoreCase(imagePath(pathOf(candidate)))) return index;
        }
        return -1;
    }

    /** Copies valid, non-empty entries without exposing the row's mutable list to the Activity. */
    public static String[] copyUrls(List<String> source, String clickedUrl) {
        List<String> result = new ArrayList<>();
        if (source != null) {
            for (String value : source) {
                if (value != null && !value.trim().isEmpty() && !containsEquivalent(result, value)) {
                    result.add(value);
                }
            }
        }
        if (clickedUrl != null && !clickedUrl.trim().isEmpty()
                && findIndex(result, clickedUrl) < 0) {
            result.add(clickedUrl);
        }
        if (result.isEmpty()) return new String[]{clickedUrl == null ? "" : clickedUrl};
        return result.toArray(new String[0]);
    }

    private static String pathOf(String rawUrl) {
        if (rawUrl == null) return "";
        String value = rawUrl.trim().replace("&amp;", "&");
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            return path == null ? "" : path;
        } catch (Exception ignored) {
            int query = value.indexOf('?');
            int fragment = value.indexOf('#');
            int end = value.length();
            if (query >= 0) end = Math.min(end, query);
            if (fragment >= 0) end = Math.min(end, fragment);
            return value.substring(0, end);
        }
    }

    private static boolean containsEquivalent(List<String> values, String candidate) {
        String normalized = normalizeForComparison(candidate);
        for (String value : values) {
            if (normalized.equals(normalizeForComparison(value))) return true;
        }
        return false;
    }

    /**
     * A relative href has no origin to compare. For two absolute URLs, require the same scheme,
     * host and effective port so an unrelated site cannot win merely because its path matches.
     */
    private static boolean sameOriginOrRelative(String first, String second) {
        try {
            URI left = new URI(first.trim());
            URI right = new URI(second == null ? "" : second.trim());
            boolean leftHasHost = left.getHost() != null;
            boolean rightHasHost = right.getHost() != null;
            if (!leftHasHost || !rightHasHost) return true;
            String leftScheme = left.getScheme() == null
                    ? "" : left.getScheme().toLowerCase(Locale.ROOT);
            String rightScheme = right.getScheme() == null
                    ? "" : right.getScheme().toLowerCase(Locale.ROOT);
            if (!leftScheme.equals(rightScheme)) return false;
            String leftHost = left.getHost().toLowerCase(Locale.ROOT);
            String rightHost = right.getHost().toLowerCase(Locale.ROOT);
            if (!leftHost.equals(rightHost)) return false;
            return effectivePort(left) == effectivePort(right);
        } catch (Exception ignored) {
            return true;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** Canonicalizes the common NGA thumbnail suffix without changing the original URL passed to Glide. */
    private static String imagePath(String path) {
        if (path == null) return "";
        String value = path;
        String lower = value.toLowerCase(Locale.ROOT);
        String[] suffixes = {".thumb_s.jpg", ".thumb_ss.jpg", ".medium.jpg", ".thumb.jpg"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }
}
