package sp.phone.linuxdo;

import okhttp3.Dns;

/** DNS resolver whose platform resources are released with the owning HTTP client. */
interface LinuxDoCloseableDns extends Dns {
    void close();
}
