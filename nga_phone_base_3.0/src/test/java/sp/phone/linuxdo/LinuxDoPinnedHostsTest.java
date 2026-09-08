package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinuxDoPinnedHostsTest {

    @Test
    public void mapsOnlyExactFirstPartyHostToBothCloudflareEdges() throws Exception {
        List<InetAddress> addresses = LinuxDoPinnedHosts.lookup("LINUX.DO");
        Set<String> values = new HashSet<>();
        for (InetAddress address : addresses) values.add(address.getHostAddress());

        assertEquals(4, addresses.size());
        assertTrue(values.contains("104.20.16.234"));
        assertTrue(values.contains("172.66.166.61"));
        assertTrue(values.contains("104.18.2.161"));
        assertTrue(values.contains("104.18.3.161"));
        assertTrue(LinuxDoPinnedHosts.lookup("cdn.linux.do").isEmpty());
        assertFalse(LinuxDoPinnedHosts.contains("notlinux.do"));
    }

    @Test
    public void rotatesFirstAddressForFreshConnections() throws Exception {
        String first = LinuxDoPinnedHosts.lookup("linux.do").get(0).getHostAddress();
        String second = LinuxDoPinnedHosts.lookup("linux.do").get(0).getHostAddress();
        assertFalse(first.equals(second));
    }
}
