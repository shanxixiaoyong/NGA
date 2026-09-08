package sp.phone.linuxdo;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.webkit.CookieManager;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Returns the isolated login process's exact-origin session to the main process. */
public final class LinuxDoSessionHandoff extends ResultReceiver {

    public static final int RESULT_SUCCESS = 1;
    public static final String KEY_COOKIE = "linuxdo_cookie";

    private final WeakReference<Context> mContext;
    private final boolean mOpenList;

    LinuxDoSessionHandoff(Context context, boolean openList) {
        super(new Handler(Looper.getMainLooper()));
        mContext = new WeakReference<>(context.getApplicationContext());
        mOpenList = openList;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        Context context = mContext.get();
        if (context == null || resultCode != RESULT_SUCCESS || resultData == null) return;
        String cookie = resultData.getString(KEY_COOKIE, "");
        importCookieHeader(cookie, () -> {
            LinuxDoSessionState.setReady(true);
            LinuxDoHttpSession.getInstance().invalidateCsrfToken();
            Toast.makeText(context, "LINUX DO 登录成功", Toast.LENGTH_SHORT).show();
            if (mOpenList) LinuxDoNavigation.openNativeList(context);
        });
    }

    static void importCookieHeader(String header, Runnable completion) {
        List<String> pairs = parseCookiePairs(header);
        if (pairs.isEmpty()) return;
        CookieManager manager = CookieManager.getInstance();
        importNext(manager, pairs, 0, completion);
    }

    private static void importNext(
            CookieManager manager, List<String> pairs, int index, Runnable completion) {
        if (index >= pairs.size()) {
            manager.flush();
            completion.run();
            return;
        }
        manager.setCookie(LinuxDoConstants.ORIGIN,
                pairs.get(index) + "; Path=/; Secure",
                ignored -> importNext(manager, pairs, index + 1, completion));
    }

    static List<String> parseCookiePairs(String header) {
        if (header == null || header.trim().isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String part : header.split(";")) {
            String pair = part.trim();
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;
            String name = pair.substring(0, separator).trim();
            String value = pair.substring(separator + 1).trim();
            if (!isCookieName(name) || containsControl(value)) continue;
            result.add(name + '=' + value);
        }
        return result;
    }

    private static boolean isCookieName(String name) {
        if (name.isEmpty() || name.length() > 128) return false;
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (value <= 0x20 || value >= 0x7f
                    || "()<>@,;:\\\"/[]?={}".indexOf(value) >= 0) return false;
        }
        return true;
    }

    private static boolean containsControl(String value) {
        if (value.length() > 4096) return true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\r' || character == '\n' || character == '\0') return true;
        }
        return false;
    }
}
