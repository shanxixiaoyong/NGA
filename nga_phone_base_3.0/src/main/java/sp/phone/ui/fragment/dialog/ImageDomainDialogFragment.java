package sp.phone.ui.fragment.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.NgaImageHost;

/**
 * 「图片域名」的选择页：四个单选项，第四项直接就是一个输入框。
 *
 * <p>刻意**不**做成两个并列的 Preference 行——自定义值是「自定义」这个选项的参数，
 * 不是独立设置；拆成两行会让输入框在未选自定义时白占一行且常灰着。
 * 同理，第四项不带「自定义」标签：输入框自己就说明了它是什么。
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

    /** 单选项在布局里的顺序，与 {@code R.array.image_domain_value} 及 MODE_* 同序。 */
    private static final int[] RADIO_IDS = {
            R.id.rb_image_domain_auto,
            R.id.rb_image_domain_default,
            R.id.rb_image_domain_alt,
            R.id.rb_image_domain_custom,
    };

    private final RadioButton[] mRadioButtons = new RadioButton[RADIO_IDS.length];

    private EditText mCustomEditText;

    private String[] mEntryValues;

    private int mSelectedIndex;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View contentView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_image_domain, null);
        mCustomEditText = contentView.findViewById(R.id.et_image_domain_custom);

        // 前三项的文案只有 arrays.xml 一份，布局里不写死，免得选项列表出现第三处副本。
        // 该数组在 lib_base_common，故走全限定 R（仓内既有写法，见 WebViewClientEx）。
        String[] entries = getResources()
                .getStringArray(gov.anzong.androidnga.common.R.array.image_domain);
        mEntryValues = getResources()
                .getStringArray(gov.anzong.androidnga.common.R.array.image_domain_value);
        for (int i = 0; i < RADIO_IDS.length; i++) {
            RadioButton button = contentView.findViewById(RADIO_IDS[i]);
            mRadioButtons[i] = button;
            // 第四项（MODE_CUSTOM）不设文案——它旁边就是输入框。
            if (i != NgaImageHost.MODE_CUSTOM && i < entries.length) {
                button.setText(entries[i]);
            }
            final int index = i;
            button.setOnClickListener(v -> select(index, true));
        }

        String customValue = "";
        try {
            customValue = PreferenceUtils.getData(
                    PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, "");
        } catch (RuntimeException ignored) {
            // A damaged legacy value must not prevent the settings page from opening.
            // The empty value is intentionally safe until the user supplies a new address.
        }
        mCustomEditText.setText(customValue);
        // 输入框常开，动它即视为选中「自定义」——它就长在那一项上，
        // 要求先点中旁边那个小圈才肯让人打字属于自找麻烦。
        mCustomEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                select(NgaImageHost.MODE_CUSTOM, false);
            }
        });
        mCustomEditText.setOnClickListener(v -> select(NgaImageHost.MODE_CUSTOM, false));

        // 初次绑定不抢焦点，免得打开对话框就弹一脸软键盘。
        select(readSelectedIndex(), false);

        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.setting_image_domain)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /**
     * 勾选指定项并维护互斥。
     *
     * <p>互斥是手工的：布局用的是 {@code LinearLayout} 而非 {@code RadioGroup}，
     * 因为第四项的圈必须和输入框同处一个横向容器才能同行，而 {@code RadioGroup}
     * 只管直接子节点。
     */
    private void select(int index, boolean focusCustomInput) {
        mSelectedIndex = index;
        for (int i = 0; i < mRadioButtons.length; i++) {
            mRadioButtons[i].setChecked(i == index);
        }
        if (index != NgaImageHost.MODE_CUSTOM) {
            mCustomEditText.setError(null);
        } else if (focusCustomInput) {
            mCustomEditText.requestFocus();
        }
    }

    @Override
    protected boolean onPositiveClick() {
        String custom = mCustomEditText.getText().toString();

        // 填了却填错时当场拦下：静默存个坏值再回退默认，用户只会看到「设了没用」。
        // 留空是合法的「未配置」，读取侧回退默认。
        if (mSelectedIndex == NgaImageHost.MODE_CUSTOM
                && !custom.trim().isEmpty()
                && NgaImageHost.sanitizeBaseUrlInput(custom) == null) {
            mCustomEditText.setError(getString(R.string.setting_image_domain_custom_invalid));
            mCustomEditText.requestFocus();
            return false;
        }

        // 原样存用户输入，归一化留给读取侧，这样下次打开还能看到自己填的那行字。
        PreferenceUtils.putData(PreferenceKey.KEY_IMAGE_DOMAIN_CUSTOM, custom);
        PreferenceUtils.putData(
                PreferenceKey.KEY_IMAGE_DOMAIN, mEntryValues[mSelectedIndex]);
        NgaImageHost.invalidate();

        if (getParentFragment() instanceof OnImageDomainSavedListener) {
            ((OnImageDomainSavedListener) getParentFragment()).onImageDomainSaved();
        }
        return true;
    }

    private int readSelectedIndex() {
        try {
            String storedValue = PreferenceUtils.getData(PreferenceKey.KEY_IMAGE_DOMAIN, "0");
            for (int i = 0; i < mEntryValues.length && i < RADIO_IDS.length; i++) {
                if (mEntryValues[i].equals(storedValue)) {
                    return i;
                }
            }
        } catch (RuntimeException ignored) {
            // A legacy/corrupt preference may have a non-string type; show 自动 rather than
            // allowing ListPreference's read path to crash the settings dialog.
        }
        return NgaImageHost.MODE_AUTO;
    }
}
