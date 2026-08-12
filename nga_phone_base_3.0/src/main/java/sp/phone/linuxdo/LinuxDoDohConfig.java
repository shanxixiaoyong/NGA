package sp.phone.linuxdo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import okhttp3.HttpUrl;

/** Validated preference boundary for the LINUX DO-only DNS-over-HTTPS resolver. */
public final class LinuxDoDohConfig {

    public static final String DEFAULT_URL = "https://cloudflare-dns.com/dns-query";

    public static String currentUrl() {
        String stored = PreferenceUtils.getData(
                PreferenceKey.KEY_LINUX_DO_DOH_URL, DEFAULT_URL);
        return isValid(stored) ? stored.trim() : DEFAULT_URL;
    }

    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        HttpUrl url = HttpUrl.parse(value.trim());
        return url != null
                && "https".equalsIgnoreCase(url.scheme())
                && url.username().isEmpty()
                && url.password().isEmpty()
                && url.query() == null
                && url.fragment() == null;
    }

    /**
     * Bootstrap known public resolvers without asking the possibly blocked
     * system DNS to resolve the DoH endpoint first.
     */
    static List<InetAddress> bootstrapAddresses(HttpUrl url) {
        if (url == null) return Collections.emptyList();
        String host = url.host();
        if ("cloudflare-dns.com".equalsIgnoreCase(host)) {
            return literalAddresses("104.16.111.25", "104.16.112.25");
        }
        if ("dns.alidns.com".equalsIgnoreCase(host)) {
            return literalAddresses("223.5.5.5", "223.6.6.6");
        }
        return Collections.emptyList();
    }

    static boolean usesCloudflareDefault(HttpUrl url) {
        return url != null
                && "cloudflare-dns.com".equalsIgnoreCase(url.host())
                && "/dns-query".equals(url.encodedPath());
    }

    private static List<InetAddress> literalAddresses(String... values) {
        List<InetAddress> result = new ArrayList<>(values.length);
        for (String value : values) {
            try {
                result.add(InetAddress.getByName(value));
            } catch (UnknownHostException ignored) {
                // These are numeric literals; failure simply keeps system bootstrap.
            }
        }
        return result;
    }

    private LinuxDoDohConfig() {
    }
}
