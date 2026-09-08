package sp.phone.linuxdo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class LinuxDoSessionHandoffTest {

    @Test
    public void parsesOnlyCookieNameValuePairs() {
        assertEquals(Arrays.asList("_t=abc==", "cf_clearance=token"),
                LinuxDoSessionHandoff.parseCookiePairs(
                        "_t=abc==; cf_clearance=token; invalid name=value; broken"));
    }

    @Test
    public void rejectsControlCharactersAndEmptyInput() {
        assertEquals(Collections.emptyList(),
                LinuxDoSessionHandoff.parseCookiePairs(null));
        assertEquals(Collections.emptyList(),
                LinuxDoSessionHandoff.parseCookiePairs("_t=line\nbreak"));
    }
}
