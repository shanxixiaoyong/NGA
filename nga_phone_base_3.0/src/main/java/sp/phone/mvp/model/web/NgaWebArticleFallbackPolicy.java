package sp.phone.mvp.model.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import sp.phone.param.ArticleListParam;

/** Pure URL policy for the bounded NGA web-page recovery transport. */
public final class NgaWebArticleFallbackPolicy {

    private static final Set<String> ALLOWED_HOSTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "bbs.nga.cn",
                    "bbs.ngacn.cc",
                    "nga.178.com",
                    "nga.donews.com",
                    "ngabbs.com")));

    public static String buildReadUrl(String configuredDomain, ArticleListParam param) {
        if (param == null || (param.tid <= 0 && param.pid <= 0)) {
            throw new IllegalArgumentException("A thread id or post id is required");
        }
        URI base = parse(configuredDomain);
        if (!isAllowedBase(base)) {
            throw new IllegalArgumentException("Unsupported NGA domain");
        }
        StringBuilder query = new StringBuilder("page=")
                .append(Math.max(1, param.page));
        if (param.tid > 0) query.append("&tid=").append(param.tid);
        if (param.pid > 0) query.append("&pid=").append(param.pid);
        if (param.authorId != 0) query.append("&authorid=").append(param.authorId);
        try {
            return new URI("https", null, base.getHost().toLowerCase(Locale.ROOT),
                    -1, "/read.php", query.toString(), null).toASCIIString();
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("Invalid NGA article URL", impossible);
        }
    }

    public static boolean isAllowedReadUrl(String rawUrl) {
        URI uri = parseOrNull(rawUrl);
        return uri != null
                && isExactHttpsHost(uri)
                && "/read.php".equals(uri.getPath())
                && uri.getFragment() == null;
    }

    /** Allows only the target page and NGA's same-host, target-bound first-visit interstitial. */
    public static boolean isAllowedNavigationUrl(String rawUrl) {
        if (isAllowedReadUrl(rawUrl)) return true;
        URI interstitial = parseOrNull(rawUrl);
        if (interstitial == null
                || !isExactHttpsHost(interstitial)
                || !"/misc/adpage_insert_2.html".equals(interstitial.getPath())
                || interstitial.getFragment() != null
                || interstitial.getRawQuery() == null) {
            return false;
        }
        URI target = parseOrNull(interstitial.getRawQuery());
        return target != null
                && isAllowedReadUrl(target.toString())
                && interstitial.getHost().equalsIgnoreCase(target.getHost());
    }

    private static boolean isAllowedBase(URI uri) {
        if (!isExactHttpsHost(uri)) return false;
        String path = uri.getPath();
        return (path == null || path.isEmpty() || "/".equals(path))
                && uri.getRawQuery() == null
                && uri.getFragment() == null;
    }

    private static boolean isExactHttpsHost(URI uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && uri.getPort() == -1
                && uri.getUserInfo() == null;
    }

    private static URI parse(String rawUrl) {
        URI uri = parseOrNull(rawUrl);
        if (uri == null) throw new IllegalArgumentException("Invalid URL");
        return uri;
    }

    private static URI parseOrNull(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            return new URI(rawUrl.trim());
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private NgaWebArticleFallbackPolicy() {
    }
}
