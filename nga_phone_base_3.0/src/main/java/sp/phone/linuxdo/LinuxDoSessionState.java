package sp.phone.linuxdo;

import gov.anzong.androidnga.base.util.PreferenceUtils;

/** Stores readiness only. Authentication material remains in Android's Cookie store. */
public final class LinuxDoSessionState {

    private static final String KEY_READY = "linuxdo_native_session_ready_v1";

    public static boolean isReady() {
        return PreferenceUtils.getData(KEY_READY, false);
    }

    public static void setReady(boolean ready) {
        PreferenceUtils.putData(KEY_READY, ready);
    }

    private LinuxDoSessionState() {
    }
}
