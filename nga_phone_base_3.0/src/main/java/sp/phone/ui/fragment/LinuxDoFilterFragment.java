package sp.phone.ui.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gov.anzong.androidnga.activity.compose.topic.TopicLocalState;
import gov.anzong.androidnga.ui.fragment.BasePreferenceFragment;
import sp.phone.param.ContentSource;

/** Device-local LINUX DO filters; no server preference or list-binding I/O. */
public final class LinuxDoFilterFragment extends BasePreferenceFragment {

    private TopicLocalState mState;

    @Override
    public void onCreatePreferences(@Nullable Bundle state, @Nullable String rootKey) {
        mState = new TopicLocalState(ContentSource.LINUX_DO);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        EditTextPreference words = new EditTextPreference(requireContext());
        // DialogPreference resolves itself from the screen by key after Fragment recreation.
        // A null key crashes EditTextPreferenceDialogFragmentCompat on every Android version.
        words.setKey("linuxdo_blocked_title_terms_editor");
        words.setTitle("标题屏蔽词");
        words.setDialogTitle("标题屏蔽词（每行一个）");
        words.setSummary("仅在本机匹配 LINUX DO 帖子标题，不使用正则表达式");
        words.setText(joinLines(mState.blockedTitleTermsSnapshot()));
        words.setOnPreferenceChangeListener((preference, value) -> {
            mState.setBlockedTitleTerms(value == null ? "" : value.toString());
            return true;
        });
        screen.addPreference(words);
        addTagPreferences(screen);
        setPreferenceScreen(screen);
    }

    private void addTagPreferences(PreferenceScreen screen) {
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle("已屏蔽标签（点击取消）");
        screen.addPreference(category);
        List<String> tags = new ArrayList<>(mState.hiddenTagSnapshot());
        Collections.sort(tags);
        if (tags.isEmpty()) {
            Preference empty = new Preference(requireContext());
            empty.setTitle("暂无已屏蔽标签");
            empty.setSelectable(false);
            category.addPreference(empty);
            return;
        }
        for (String tag : tags) {
            Preference row = new Preference(requireContext());
            row.setTitle("#" + tag);
            row.setOnPreferenceClickListener(ignored -> {
                mState.unhideTag(tag);
                onCreatePreferences(null, null);
                return true;
            });
            category.addPreference(row);
        }
    }

    private static String joinLines(java.util.Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return android.text.TextUtils.join("\n", sorted);
    }
}
