package com.justwen.androidnga.cloud;

import android.content.Context;

import java.util.Map;

/**
 * @author yangyihang
 */
public class CloudServerManager {

    public static void init(Context context) {
        // Remote telemetry is intentionally disabled by default.
    }

    public static void putCrashData(Context context, String key, String value) {
        // Never upload user/session/content data to a third-party crash service.
    }

    public static void pingBack(Context context, String event) {
        // Diagnostics remain local and redacted.
    }

    public static void pingBack(Context context, String event, Map<String, String> map) {
        // Diagnostics remain local and redacted.
    }

    public static void checkUpgrade() {

    }
}
