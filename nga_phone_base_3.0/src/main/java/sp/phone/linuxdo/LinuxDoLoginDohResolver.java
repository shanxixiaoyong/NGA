package sp.phone.linuxdo;

import android.content.Context;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Adapter, not a second DNS stack. Native reads and official pages use the same resolver. */
final class LinuxDoLoginDohResolver implements LinuxDoLoginSocksProxy.Resolver {
    LinuxDoLoginDohResolver(Context context, String configuredUrl) { }

    @Override public List<InetAddress> lookup(String host) throws UnknownHostException {
        if (host == null || host.isEmpty() || host.length() > 253 || host.indexOf('\0') >= 0) {
            throw new UnknownHostException("Invalid hostname");
        }
        return LinuxDoHttpSession.getInstance().resolveForBrowser(host);
    }

    @Override public void close() {
        // The native reader owns this shared resolver and its connection pool.
    }
}
