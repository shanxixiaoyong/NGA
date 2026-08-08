package sp.phone.common;

import android.content.SharedPreferences;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.NgaImageHost;

/**
 * @author yangyihang
 */
public class VersionUpgradeHelper {

    public static void upgrade() {
        upgradeBookmarkBoards();
        upgradeSettings();
    }

    private static void upgradeBookmarkBoards() {
        SharedPreferences sp = ContextUtils.getSharedPreferences(PreferenceKey.PERFERENCE);
        if (sp.contains(PreferenceKey.BOOKMARK_BOARD)) {
            String data = sp.getString(PreferenceKey.BOOKMARK_BOARD, "");
            sp.edit().remove(PreferenceKey.BOOKMARK_BOARD).apply();
            PreferenceUtils.putData(PreferenceKey.BOOKMARK_BOARD, data);
        }
    }

    private static void upgradeSettings() {
        SharedPreferences sp = PreferenceUtils.getDefaultPreferences();
        upgradeImageDomainMode(sp);

        SharedPreferences.Editor editor = sp.edit();
        if (sp.contains(PreferenceKey.DOWNLOAD_AVATAR_NO_WIFI)) {
            boolean value = sp.getBoolean(PreferenceKey.DOWNLOAD_AVATAR_NO_WIFI, true);
            String newValue = value ? "0" : "2";
            editor.putString(ContextUtils.getString(gov.anzong.androidnga.common.R.string.pref_load_avatar_strategy), newValue)
                    .remove(PreferenceKey.DOWNLOAD_AVATAR_NO_WIFI)
                    .apply();
        }

        if (sp.contains(PreferenceKey.DOWNLOAD_IMG_NO_WIFI)) {
            boolean value = sp.getBoolean(PreferenceKey.DOWNLOAD_IMG_NO_WIFI, true);
            String newValue = value ? "0" : "2";
            editor.putString(ContextUtils.getString(gov.anzong.androidnga.common.R.string.pref_load_pic_strategy), newValue)
                    .remove(PreferenceKey.DOWNLOAD_IMG_NO_WIFI)
                    .apply();
        }
    }

    private static void upgradeImageDomainMode(SharedPreferences preferences) {
        Object migrationMarker = preferences.getAll()
                .get(PreferenceKey.KEY_IMAGE_DOMAIN_MODE_MIGRATED);
        boolean migrationComplete = Boolean.TRUE.equals(migrationMarker);
        if (migrationComplete) {
            return;
        }

        Object stored = preferences.getAll().get(PreferenceKey.KEY_IMAGE_DOMAIN);
        String storedValue;
        if (stored == null) {
            storedValue = null;
        } else if (stored instanceof String) {
            storedValue = (String) stored;
        } else {
            storedValue = "";
        }

        String migratedValue = migrateImageDomainModeValue(storedValue, false);
        SharedPreferences.Editor editor = preferences.edit();
        if (migratedValue != null) {
            editor.putString(PreferenceKey.KEY_IMAGE_DOMAIN, migratedValue);
        }
        editor.putBoolean(PreferenceKey.KEY_IMAGE_DOMAIN_MODE_MIGRATED, true).apply();
        NgaImageHost.invalidate();
    }

    /** 不依赖 Android 的旧编号迁移表，供 JVM 契约测试覆盖首次与重复执行。 */
    static String migrateImageDomainModeValue(String storedValue, boolean migrationComplete) {
        if (migrationComplete || storedValue == null) {
            return storedValue;
        }
        switch (storedValue) {
            case "0":
                return "0";
            case "1":
                return "2";
            case "2":
                return "3";
            default:
                return "0";
        }
    }

}
