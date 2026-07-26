package gov.anzong.androidnga.common.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Shared trust boundary for NGA HTTP and WebView requests. */
public final class NgaRequestPolicy {

    public static final String DEFAULT_USER_AGENT = "NgaJustWorks/Android";

    private static final Set<String> TRUSTED_HTTPS_HOSTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "bbs.ngacn.cc",
                    "bbs.nga.cn",
                    "nga.178.com",
                    "nga.donews.com",
                    "ngabbs.com"
            ))
    );

    private NgaRequestPolicy() {
    }

    /**
     * Session material may only be sent to an exact, known NGA HTTPS host.
     * Substring and suffix matching are intentionally forbidden.
     */
    public static boolean isTrustedHttps(String scheme, String host) {
        if (scheme == null || host == null || !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return TRUSTED_HTTPS_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    public static boolean isOfficialImpersonation(String userAgent) {
        return userAgent != null
                && userAgent.toLowerCase(Locale.ROOT).contains("nga_official");
    }
}
