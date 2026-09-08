package sp.phone.linuxdo;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinuxDoChallengeCoordinatorTest {
    @After
    public void reset() {
        LinuxDoChallengeCoordinator.cancelVerification();
    }

    @Test
    public void verificationIsSingleFlight() {
        LinuxDoChallengeCoordinator.markRequired();
        assertTrue(LinuxDoChallengeCoordinator.tryBeginVerification());
        assertFalse(LinuxDoChallengeCoordinator.tryBeginVerification());
        assertEquals(LinuxDoChallengeCoordinator.State.VERIFYING,
                LinuxDoChallengeCoordinator.state());
    }

    @Test
    public void successfulVerificationClearsChallenge() {
        LinuxDoChallengeCoordinator.markRequired();
        assertTrue(LinuxDoChallengeCoordinator.tryBeginVerification());
        LinuxDoChallengeCoordinator.finishVerification(true);
        assertFalse(LinuxDoChallengeCoordinator.isVerificationInFlight());
        assertEquals(LinuxDoChallengeCoordinator.State.CLEAR,
                LinuxDoChallengeCoordinator.state());
    }

    @Test
    public void cancelledVerificationRemainsRequired() {
        LinuxDoChallengeCoordinator.markRequired();
        assertTrue(LinuxDoChallengeCoordinator.tryBeginVerification());
        LinuxDoChallengeCoordinator.cancelVerification();
        assertFalse(LinuxDoChallengeCoordinator.isVerificationInFlight());
        assertEquals(LinuxDoChallengeCoordinator.State.REQUIRED,
                LinuxDoChallengeCoordinator.state());
    }
}
