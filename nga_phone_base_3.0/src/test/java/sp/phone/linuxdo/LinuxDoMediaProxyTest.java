package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LinuxDoMediaProxyTest {
    @Test
    public void roundTripsOnlyTheFixedMediaProxyRoute() {
        String source = "https://cdn.linux.do/uploads/default/original/2X/a/file name.png?x=1";
        String proxy = LinuxDoMediaProxy.wrap(source);
        assertEquals(source, LinuxDoMediaProxy.unwrap(proxy));
        assertNull(LinuxDoMediaProxy.unwrap("https://example.com/?src=" + source));
        assertNull(LinuxDoMediaProxy.unwrap(proxy + "&extra=1"));
    }
}
