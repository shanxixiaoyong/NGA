package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LinuxDoAvatarProxyTest {
    @Test
    public void roundTripsOnlyTheFixedProxyShape() {
        String source = "https://linux.do/user_avatar/linux.do/a/48/1_2.png?x=1&y=2";
        String proxy = LinuxDoAvatarProxy.wrap(source);

        assertEquals(source, LinuxDoAvatarProxy.unwrap(proxy));
        assertNull(LinuxDoAvatarProxy.unwrap("https://example.com/?src=" + source));
        assertNull(LinuxDoAvatarProxy.unwrap(
                "https://linux.do/__nga_avatar_proxy?src=%ZZ"));
    }
}
