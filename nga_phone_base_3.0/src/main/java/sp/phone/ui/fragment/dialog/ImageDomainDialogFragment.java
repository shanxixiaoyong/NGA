package sp.phone.ui.fragment.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.NgaImageHost;

/**
 * 「图片域名」的选择页：三个单选项 + 「自定义」专属输入框，同处一页。
 *
 * <p>刻意**不**做成两个并列的 Preference 行——自定义值是「自定义」这个选项的参数，
 * 不是独立设置；拆成两行会让输入框在未选自定义时白占一行且常灰着。
 *
 * <p>本类直接读写 {@link PreferenceUtils}，不持有 {@code Preference} 引用，
 * 因此旋转重建后不会拿到失效对象。选中项的 summary 回显由宿主在对话框关闭后自行同步。
 */
public class ImageDomainDialogFragment extends BaseDialogFragment {

    /**
     * 保存成功的回调，由宿主 fragment 实现。
     *
     * <p>本对话框直写 SharedPreferences，而框架的 {@code ListPreference} 还持着旧的内存值，
     * 宿主需要借此把两者拉回一致并刷新 summary。
     */
    public interface OnImageDomainSavedListener {

        void onImageDomainSaved();
    }

    /** 单选项在布局里的顺序，与 {@code R.array.image_domain} 及 PRESET_BASE_URLS 同序。 */
    private static final int[] RADIO_IDS = {
            R.id.rb_image_domain_default,
            R.id.rb_image_domain_alt,
            R.id.rb_image_domain_custom,
    };

    private RadioGroup mRadioGroup;

    private EditText mCustomEditText;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View contentView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_image_domain, null);
        mRadioGroup = contentView.findViewById(R.id.rg_image_domain);
        mCustomEditText = contentView.findViewById(R.id.et_image_domain_custom);

        // 选项文案只有 arrays.xml 一份，布局里不写死，免得选项列表出现第三处副本。
        // 该数组在 lib_base_common，故走全限定 R（仓内既有写法，见 WebViewClientEx）。
        String[] entries = getResources()
                .getStringArray(gov.anzong.androidnga.common.R.array.image_domain);
        for (int i = 0; i < RADIO_IDS.length; i++) {
            RadioButton button = contentView.findViewById(RADIO_IDS[i]);
            if (i < entries.length) {
                button.setText(entries[i]);
            } else {
                button.setVisibility(View.GONE);
            }
        }

        int selected = readSelectedIndex();
        mRadioGroup.check(RADIO_IDS[selected]);
        mCustomEditText.setText(
                PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, ""));
        // 初次绑定不抢焦点，免得打开对话框就弹一脸软键盘；用户手动切到「自定义」时才聚焦。
        syncCustomEnabled(selected, false);
        mRadioGroup.setOnCheckedChangeListener(
                (group, checkedId) -> syncCustomEnabled(indexOfRadio(checkedId), true));

        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.setting_image_domain)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /**
     * 未选「自定义」时置灰输入框，避免让人以为填了就随时生效。
     *
     * <p>{@code android:dependency} 只支持布尔型 Preference，表达不了「选中第三项才启用」，故用代码控制。
     */
    private void syncCustomEnabled(int selectedIndex, boolean focusWhenEnabled) {
        boolean custom = selectedIndex == NgaImageHost.INDEX_CUSTOM;
        mCustomEditText.setEnabled(custom);
        if (!custom) {
            mCustomEditText.setError(null);
        } else if (focusWhenEnabled) {
            mCustomEditText.requestFocus();
        }
    }

    @Override
    protected boolean onPositiveClick() {
        int selected = indexOfRadio(mRadioGroup.getCheckedRadioButtonId());
        String custom = mCustomEditText.getText().toString();

        // 填了却填错时当场拦下：静默存个坏值再回退默认，用户只会看到「设了没用」。
        // 留空是合法的「未配置」，读取侧回退默认。
        if (selected == NgaImageHost.INDEX_CUSTOM
                && !custom.trim().isEmpty()
                && NgaImageHost.sanitizeBaseUrlInput(custom) == null) {
            mCustomEditText.setError(getString(R.string.setting_image_domain_custom_invalid));
            mCustomEditText.requestFocus();
            return false;
        }

        // 原样存用户输入，归一化留给读取侧，这样下次打开还能看到自己填的那行字。
        PreferenceUtils.putData(PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, custom);
        PreferenceUtils.putData(PreferenceKey.KEY_IMAGE_DOMAIN, String.valueOf(selected));
        NgaImageHost.invalidate();

        if (getParentFragment() instanceof OnImageDomainSavedListener) {
            ((OnImageDomainSavedListener) getParentFragment()).onImageDomainSaved();
        }
        return true;
    }

    private int readSelectedIndex() {
        int index;
        try {
            index = Integer.parseInt(
                    PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0"));
        } catch (NumberFormatException e) {
            index = 0;
        }
        return index >= 0 && index < RADIO_IDS.length ? index : 0;
    }

    private static int indexOfRadio(int checkedId) {
        for (int i = 0; i < RADIO_IDS.length; i++) {
            if (RADIO_IDS[i] == checkedId) {
                return i;
            }
        }
        return 0;
    }
}
