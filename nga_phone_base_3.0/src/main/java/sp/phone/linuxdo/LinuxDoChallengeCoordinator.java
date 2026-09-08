package sp.phone.linuxdo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide single-flight gate for the LINUX DO managed challenge.
 *
 * <p>Native topic requests and mutations can fail at the same time when Cloudflare asks for a
 * challenge.  Starting one WebView per failed request is both wasteful and harmful: each page
 * would have a different browser state and the last page could overwrite the useful clearance
 * cookie.  The transport records that a challenge is required and the navigation layer acquires
 * one short-lived verification lease.  The normal JSON/QUIC paths remain independent of this
 * gate.</p>
 */
public final class LinuxDoChallengeCoordinator {

    public enum State {
        CLEAR,
        REQUIRED,
        VERIFYING
    }

    private static final AtomicBoolean VERIFICATION_IN_FLIGHT = new AtomicBoolean(false);
    private static volatile State state = State.CLEAR;

    private LinuxDoChallengeCoordinator() {
    }

    /** Marks a response as requiring the official first-party verification page. */
    public static void markRequired() {
        if (!VERIFICATION_IN_FLIGHT.get()) state = State.REQUIRED;
    }

    /**
     * Acquires the single verification lease. Returns false when another verification Activity
     * is already visible, so callers must not launch a second WebView.
     */
    public static boolean tryBeginVerification() {
        if (!VERIFICATION_IN_FLIGHT.compareAndSet(false, true)) return false;
        state = State.VERIFYING;
        return true;
    }

    /** Completes the active lease and records whether the native session accepted its cookies. */
    public static void finishVerification(boolean success) {
        VERIFICATION_IN_FLIGHT.set(false);
        state = success ? State.CLEAR : State.REQUIRED;
    }

    /** Releases a cancelled/failed Activity without claiming that verification succeeded. */
    public static void cancelVerification() {
        VERIFICATION_IN_FLIGHT.set(false);
        state = State.REQUIRED;
    }

    /** Clears a stale gate after a successful account session, without touching another lease. */
    public static void clearIfIdle() {
        if (!VERIFICATION_IN_FLIGHT.get()) state = State.CLEAR;
    }

    public static boolean isVerificationInFlight() {
        return VERIFICATION_IN_FLIGHT.get();
    }

    public static State state() {
        return state;
    }
}
