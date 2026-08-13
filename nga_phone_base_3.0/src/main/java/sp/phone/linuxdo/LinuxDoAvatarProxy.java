package sp.phone.linuxdo;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Fixed local marker for routing inline Boost avatars through the isolated transport. */
public final class LinuxDoAvatarProxy {
    private static final String PREFIX =
            "https://linux.do/__nga_avatar_proxy?src=";

    static String wrap(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isEmpty()) return "";
        try {
            return PREFIX + URLEncoder.encode(sourceUrl, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            return "";
        }
    }

    public static String unwrap(String requestUrl) {
        if (requestUrl == null || !requestUrl.startsWith(PREFIX)) return null;
        String encoded = requestUrl.substring(PREFIX.length());
        if (encoded.isEmpty() || encoded.indexOf('&') >= 0 || encoded.indexOf('#') >= 0) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(encoded, "UTF-8");
            return decoded.isEmpty() ? null : decoded;
        } catch (IllegalArgumentException | UnsupportedEncodingException error) {
            return null;
        }
    }

    private LinuxDoAvatarProxy() {
    }
}
