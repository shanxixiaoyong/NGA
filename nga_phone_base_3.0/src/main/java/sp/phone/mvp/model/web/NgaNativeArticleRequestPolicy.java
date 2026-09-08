package sp.phone.mvp.model.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import sp.phone.param.ArticleListParam;

/** Defines the two native NGA THREAD.PAGE wire representations. */
public final class NgaNativeArticleRequestPolicy {

    private static final Set<String> ALLOWED_HOSTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "bbs.nga.cn",
                    "bbs.ngacn.cc",
                    "nga.178.com",
                    "nga.donews.com",
                    "ngabbs.com")));

    public enum WireFormat {
        LEGACY_GB18030(8, Charset.forName("GB18030")),
        UTF8_ARRAYS(11, StandardCharsets.UTF_8);

        private final int output;
        private final Charset charset;

        WireFormat(int output, Charset charset) {
            this.output = output;
            this.charset = charset;
        }

        public Charset charset() {
            return charset;
        }
    }

    public static String buildReadUrl(
            String configuredDomain,
            ArticleListParam param,
            int requestedPage,
            WireFormat format) {
        if (param == null || (param.tid <= 0 && param.pid <= 0)) {
            throw new IllegalArgumentException("A thread id or post id is required");
        }
        if (format == null) throw new IllegalArgumentException("A wire format is required");
        URI base = parse(configuredDomain);
        if (!isAllowedBase(base)) {
            throw new IllegalArgumentException("Unsupported NGA domain");
        }
        StringBuilder query = new StringBuilder("page=")
                .append(Math.max(1, requestedPage))
                .append("&__output=").append(format.output)
                .append("&noprefix&v2");
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

    private static boolean isAllowedBase(URI uri) {
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getPort() != -1
                || uri.getUserInfo() != null) {
            return false;
        }
        String path = uri.getPath();
        return (path == null || path.isEmpty() || "/".equals(path))
                && uri.getRawQuery() == null
                && uri.getFragment() == null;
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid URL");
        }
        try {
            return new URI(rawUrl.trim());
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Invalid URL", error);
        }
    }

    private NgaNativeArticleRequestPolicy() {
    }
}
