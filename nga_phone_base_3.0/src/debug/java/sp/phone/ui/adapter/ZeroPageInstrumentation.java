package sp.phone.ui.adapter;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.SwitchPreference;

import java.util.Arrays;

import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.activity.SettingsActivity;
import sp.phone.ui.fragment.SettingsFragment;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ContentSource;
import sp.phone.param.ParamKey;

/** On-device regression checks against the actual adapter and preference store. */
public class ZeroPageInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        SharedPreferences prefs = PreferenceUtils.getDefaultPreferences();
        String key = PreferenceKey.KEY_SHOW_NGA_TOP_LIKED_PAGE;
        boolean hadValue = prefs.contains(key);
        boolean oldValue = prefs.getBoolean(key, true);
        ZeroPageTestActivity host = null;
        SettingsActivity settings = null;
        int resultCode = Activity.RESULT_CANCELED;
        try {
            settings = (SettingsActivity) startActivitySync(new Intent(getTargetContext(), SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsActivity settingsActivity = settings;
            runOnMainSync(() -> {
                settingsActivity.getSupportFragmentManager().executePendingTransactions();
                SettingsFragment fragment = (SettingsFragment) settingsActivity.getSupportFragmentManager()
                        .findFragmentByTag(SettingsFragment.class.getSimpleName());
                SwitchPreference toggle = fragment.findPreference(key);
                check(toggle != null && toggle.isEnabled(), "real settings switch exists");
                toggle.setChecked(true);
                toggle.setChecked(false);
                check(!prefs.getBoolean(key, true), "settings switch saves disabled");
                toggle.setChecked(true);
                check(prefs.getBoolean(key, false), "settings switch saves enabled");
            });
            Intent intent = new Intent(getTargetContext(), ZeroPageTestActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            host = (ZeroPageTestActivity) startActivitySync(intent);
            ZeroPageTestActivity activity = host;
            runOnMainSync(() -> {
                ArticleListParam param = new ArticleListParam();
                param.tid = 12345;
                param.source = ContentSource.NGA;
                prefs.edit().remove(key).commit();
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), true);
                prefs.edit().putBoolean(key, false).commit();
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                check(!prefs.getBoolean(key, true), "disabled preference persisted");
                prefs.edit().putBoolean(key, true).commit();
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), true);

                param.source = ContentSource.LINUX_DO;
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                param.source = ContentSource.NGA;
                param.pid = 456;
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                param.pid = 0;
                param.authorId = 789;
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                param.authorId = 0;
                param.searchPost = 1;
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                param.searchPost = 0;
                param.loadCache = true;
                checkPager(new ArticlePagerAdapter(activity.getSupportFragmentManager(), param), false);
                param.loadCache = false;
                ArticlePagerAdapter filtered = new ArticlePagerAdapter(activity.getSupportFragmentManager(), param);
                filtered.setPageIndexList(Arrays.asList("1", "3"));
                check(!filtered.hasTopLikedPage() && filtered.getCount() == 2, "filtered pages exclude zero");
                check(filtered.getServerPageAt(1) == 3, "filtered server page retained");
            });
            result.putString("result", "PASS: actual settings persistence, default on, disabled, re-enabled, request pages, page counts, floor restore, page selector, source and filtered routes");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("result", "FAIL: " + android.util.Log.getStackTraceString(failure));
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (hadValue) editor.putBoolean(key, oldValue); else editor.remove(key);
            editor.commit();
            if (host != null) {
                ZeroPageTestActivity activity = host;
                runOnMainSync(activity::finish);
            }
            if (settings != null) {
                SettingsActivity activity = settings;
                runOnMainSync(activity::finish);
            }
        }
        finish(resultCode, result);
    }

    private void checkPager(ArticlePagerAdapter pager, boolean enabled) {
        int offset = enabled ? 1 : 0;
        check(pager.hasTopLikedPage() == enabled, "zero page presence");
        pager.setCount(4);
        check(pager.getCount() == 4 + offset && pager.getNormalPageCount() == 4, "normal/total page count");
        check(pager.getPageTitle(0).toString().equals(enabled ? "0" : "1"), "initial page title");
        check(pager.getServerPageAt(0) == (enabled ? 0 : 1), "initial server page");
        check(pager.getAdapterPositionForPageSelection(0) == offset, "page one selector");
        check(pager.getAdapterPositionForPageSelection(3) == 3 + offset, "last page selector");
        check(pager.getAdapterPositionForFloor(0) == offset, "main floor restore");
        check(pager.getAdapterPositionForFloor(40) == 2 + offset, "read floor restore");
        for (int i = 0; i < pager.getCount(); i++) {
            ArticleListParam request = pager.getItem(i).getArguments().getParcelable(ParamKey.KEY_PARAM);
            check(request.page == i + 1 - offset, "request maps to server page");
            check(request.topLikedPage == (enabled && i == 0), "only zero requests high likes");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
