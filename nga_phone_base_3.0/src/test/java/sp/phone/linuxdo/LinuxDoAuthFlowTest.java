package sp.phone.linuxdo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LinuxDoAuthFlowTest {
    @Test public void retriesRejectOldCallbacks() {
        LinuxDoAuthFlow flow = new LinuxDoAuthFlow();
        long old = flow.begin(), current = flow.begin();
        assertFalse(flow.move(old, LinuxDoAuthFlow.State.COMPLETE));
        assertEquals(LinuxDoAuthFlow.State.CONNECTING, flow.state());
        assertTrue(flow.move(current, LinuxDoAuthFlow.State.WEB));
    }
    @Test public void closedScreenCannotBeCompletedByNetwork() {
        LinuxDoAuthFlow flow = new LinuxDoAuthFlow();
        long token = flow.begin();
        flow.close();
        assertFalse(flow.accepts(token));
        assertFalse(flow.move(token, LinuxDoAuthFlow.State.COMPLETE));
    }
    @Test public void completedScreenIgnoresLateFailure() {
        LinuxDoAuthFlow flow = new LinuxDoAuthFlow();
        long token = flow.begin();
        assertTrue(flow.move(token, LinuxDoAuthFlow.State.COMPLETE));
        assertFalse(flow.move(token, LinuxDoAuthFlow.State.ERROR));
        assertEquals(LinuxDoAuthFlow.State.COMPLETE, flow.state());
    }
    @Test public void credentialsAreRestrictedToExactHttpsOrigin() {
        assertTrue(LinuxDoAuthFlow.isFirstParty("https://linux.do/login"));
        assertTrue(LinuxDoAuthFlow.isFirstParty("https://linux.do:443/login"));
        for (String url : new String[]{"http://linux.do/login", "https://linux.do.evil.test",
                "https://user@linux.do/login", "https://linux.do:444/login", "https://connect.linux.do",
                "javascript:alert(1)", "https://linux.do%2fevil.test", "//linux.do/login", "", null}) {
            assertFalse(String.valueOf(url), LinuxDoAuthFlow.isFirstParty(url));
        }
    }
    @Test public void quotedCredentialsCannotInjectJavascriptOrSubmit() {
        String script = LinuxDoAuthScripts.fill("a'\"\\\n", "x</script>\"\n");
        assertTrue(script.contains(org.json.JSONObject.quote("a'\"\\\n")));
        assertTrue(script.contains(org.json.JSONObject.quote("x</script>\"\n")));
        assertFalse(script.contains(".submit("));
        assertFalse(script.contains(".click("));
    }
    @Test public void verificationProbeIsNotAnAccountLoginProbe() {
        String verification = LinuxDoAuthScripts.probe(true, "probe");
        assertTrue(verification.contains("fetch('/latest.json'"));
        assertFalse(verification.contains("/session/current.json"));
        assertTrue(LinuxDoAuthScripts.probe(false, "probe").contains("fetch('/session/current.json'"));
    }
}
