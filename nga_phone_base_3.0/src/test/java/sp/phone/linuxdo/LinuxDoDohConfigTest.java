package sp.phone.linuxdo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import okhttp3.HttpUrl;

public class LinuxDoDohConfigTest {

    @Test
    public void acceptsOnlyCredentialFreeHttpsResolverUrls() {
        assertTrue(LinuxDoDohConfig.isValid("https://dns.alidns.com/dns-query"));
        assertFalse(LinuxDoDohConfig.isValid("http://dns.example/dns-query"));
        assertFalse(LinuxDoDohConfig.isValid("https://user:pass@dns.example/dns-query"));
        assertFalse(LinuxDoDohConfig.isValid("https://dns.example/dns-query?name=linux.do"));
        assertFalse(LinuxDoDohConfig.isValid("not a url"));
    }

    @Test
    public void knownResolversHaveSystemDnsIndependentBootstrapAddresses() {
        assertEquals(2, LinuxDoDohConfig.bootstrapAddresses(
                HttpUrl.get("https://cloudflare-dns.com/dns-query")).size());
        assertEquals(2, LinuxDoDohConfig.bootstrapAddresses(
                HttpUrl.get("https://dns.alidns.com/dns-query")).size());
        assertTrue(LinuxDoDohConfig.bootstrapAddresses(
                HttpUrl.get("https://custom.example/dns-query")).isEmpty());
    }

    @Test
    public void platformQuicPathIsLimitedToTheCloudflareDnsQueryEndpoint() {
        assertTrue(LinuxDoDohConfig.usesCloudflareDefault(
                HttpUrl.get("https://cloudflare-dns.com/dns-query")));
        assertFalse(LinuxDoDohConfig.usesCloudflareDefault(
                HttpUrl.get("https://cloudflare-dns.com/other")));
        assertFalse(LinuxDoDohConfig.usesCloudflareDefault(
                HttpUrl.get("https://dns.alidns.com/dns-query")));
    }
}
