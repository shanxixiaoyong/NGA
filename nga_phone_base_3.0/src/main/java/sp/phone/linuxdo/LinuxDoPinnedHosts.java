package sp.phone.linuxdo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * App-local hosts entry for the first-party LINUX DO origin.
 *
 * <p>The URL, HTTP Host header and TLS SNI all remain {@code linux.do}; only name resolution is
 * replaced. Never use these addresses as an HTTPS URL because that would break certificate and
 * Cloudflare virtual-host validation.</p>
 */
final class LinuxDoPinnedHosts {

    private static final String HOST = "linux.do";
    // Seeded from two independent public resolvers. Keep a pair so a failed edge can be retried
    // without consulting DNS during login or foreground reading.
    private static final String[] IPV4 = {
            "104.20.16.234", "172.66.166.61",
            // Previously observed edge pair retained as a no-DNS fallback for regional routes.
            "104.18.2.161", "104.18.3.161"
    };
    private static final AtomicInteger CURSOR = new AtomicInteger();

    static boolean contains(String hostname) {
        return hostname != null && HOST.equals(hostname.trim().toLowerCase(Locale.US));
    }

    /** Returns both edges and rotates the first choice between connections. */
    static List<InetAddress> lookup(String hostname) throws UnknownHostException {
        if (!contains(hostname)) return Collections.emptyList();
        int first = Math.floorMod(CURSOR.getAndIncrement(), IPV4.length);
        List<InetAddress> result = new ArrayList<>(IPV4.length);
        for (int offset = 0; offset < IPV4.length; offset++) {
            String value = IPV4[(first + offset) % IPV4.length];
            result.add(InetAddress.getByAddress(HOST, ipv4(value)));
        }
        return Collections.unmodifiableList(result);
    }

    private static byte[] ipv4(String value) throws UnknownHostException {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new UnknownHostException(value);
        byte[] address = new byte[4];
        try {
            for (int index = 0; index < parts.length; index++) {
                int octet = Integer.parseInt(parts[index]);
                if (octet < 0 || octet > 255) throw new NumberFormatException();
                address[index] = (byte) octet;
            }
        } catch (NumberFormatException error) {
            throw new UnknownHostException(value);
        }
        return address;
    }

    private LinuxDoPinnedHosts() {
    }
}
