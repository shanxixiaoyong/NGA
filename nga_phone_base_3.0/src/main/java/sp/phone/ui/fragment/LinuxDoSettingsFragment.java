package sp.phone.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.http.OnHttpCallBack;
import gov.anzong.androidnga.ui.fragment.BasePreferenceFragment;
import sp.phone.linuxdo.LinuxDoDohConfig;
import sp.phone.linuxdo.LinuxDoHttpSession;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoRepository;

/** LINUX DO-only account and source settings, reachable from that board's toolbar. */
public final class LinuxDoSettingsFragment extends BasePreferenceFragment {

    private Preference mAccount;
    private Preference mVerification;
    private Preference mNotifications;
    private boolean mLoggedIn;
    private boolean mNeedsVerification;

    @Override
    public void onCreatePreferences(@Nullable Bundle state, @Nullable String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());

        PreferenceCategory account = category(screen, "账号与通知");
        mAccount = action(account, "登录 LINUX DO", "登录或切换 LINUX DO 账号",
                ignored -> {
                    LinuxDoNavigation.openLogin(requireContext(), mLoggedIn);
                    return true;
                });
        mVerification = action(account, "网络盾验证", "账号仍登录但浏览被拦截时使用",
                ignored -> {
                    LinuxDoNavigation.openVerification(requireContext());
                    return true;
                });
        action(account, "我的资料", "查看当前登录用户的资料",
                ignored -> { LinuxDoNavigation.openProfile(requireContext(), null); return true; });
        mNotifications = action(account, "通知中心", "回复、提及、点赞与关注更新",
                ignored -> { LinuxDoNavigation.openNotifications(requireContext()); return true; });

        PreferenceCategory reading = category(screen, "浏览与连接");
        action(reading, "板块与流", "浏览分类、子板块以及最新/新帖/未读/热门流",
                ignored -> {
                    LinuxDoNavigation.openCategoryDirectory(requireContext());
                    return true;
                });
        action(reading, "屏蔽标签与标题词", "管理仅作用于 LINUX DO 的本地过滤规则",
                ignored -> {
                    Intent intent = new Intent(requireContext(), LauncherSubActivity.class);
                    intent.putExtra("fragment", LinuxDoFilterFragment.class.getName());
                    startActivity(intent);
                    return true;
                });

        EditTextPreference doh = new EditTextPreference(requireContext());
        doh.setKey(PreferenceKey.KEY_LINUX_DO_DOH_URL);
        doh.setTitle("专用 DNS over HTTPS");
        doh.setDialogTitle("LINUX DO 原生请求专用 DoH");
        doh.setOnBindEditTextListener(editText -> editText.setSingleLine(true));
        doh.setDefaultValue(LinuxDoDohConfig.DEFAULT_URL);
        doh.setSummary(LinuxDoDohConfig.currentUrl());
        doh.setOnPreferenceChangeListener((preference, value) -> {
            String url = value == null ? "" : value.toString().trim();
            if (!LinuxDoDohConfig.isValid(url)) {
                ToastUtils.error("请输入完整的 HTTPS DoH 地址");
                return false;
            }
            preference.setSummary(url);
            LinuxDoHttpSession.getInstance().invalidateClient();
            return true;
        });
        reading.addPreference(doh);

        setPreferenceScreen(screen);
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle("LINUX DO 设置");
        refreshAccountState();
    }

    private void refreshAccountState() {
        if (mAccount == null || mVerification == null || mNotifications == null) return;
        LinuxDoRepository.getInstance().loadCurrentUsername(new OnHttpCallBack<String>() {
            @Override public void onSuccess(String username) {
                if (!isAdded()) return;
                mLoggedIn = !TextUtils.isEmpty(username);
                mNeedsVerification = false;
                mAccount.setTitle(mLoggedIn ? "切换 LINUX DO 账号" : "登录 LINUX DO");
                mAccount.setSummary(mLoggedIn
                        ? "当前账号：@" + username + "；点击后会先安全退出旧账号"
                        : "未登录；点击进入登录页");
                mVerification.setSummary("当前访问正常；需要时可单独重新验证");
            }
            @Override public void onError(String error) {
                if (isAdded()) {
                    mLoggedIn = false;
                    mNeedsVerification = error != null && error.contains("会话已失效");
                    mAccount.setTitle("登录 LINUX DO");
                    mAccount.setSummary(mNeedsVerification
                            ? "账号信息不会在网络验证时被清除"
                            : "未检测到有效登录；点击重新登录");
                    mVerification.setSummary(mNeedsVerification
                            ? "访问被网络盾拦截；点击验证后返回原页面"
                            : "账号仍登录但浏览被拦截时使用");
                }
            }
        });
        LinuxDoRepository.getInstance().loadUnreadNotificationTotal(new OnHttpCallBack<Integer>() {
            @Override public void onSuccess(Integer total) {
                if (!isAdded()) return;
                int count = total == null ? 0 : Math.max(0, total);
                mNotifications.setSummary(count > 0
                        ? "有 " + count + " 条未读通知" : "暂无未读通知");
            }
            @Override public void onError(String ignored) { }
        });
    }

    private PreferenceCategory category(PreferenceScreen screen, String title) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(title);
        screen.addPreference(category);
        return category;
    }

    private Preference action(
            PreferenceCategory category,
            String title,
            String summary,
            Preference.OnPreferenceClickListener listener) {
        Preference preference = new Preference(requireContext());
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setOnPreferenceClickListener(listener);
        category.addPreference(preference);
        return preference;
    }
}
