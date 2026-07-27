package gov.anzong.androidnga.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.justwen.androidnga.base.activity.ARouterConstants;
import com.justwen.androidnga.ui.fragment.WebViewFragment;

import gov.anzong.androidnga.BuildConfig;
import gov.anzong.androidnga.R;
import sp.phone.theme.ThemeManager;
import sp.phone.ui.fragment.dialog.VersionUpgradeDialogFragment;
import sp.phone.util.ARouterUtils;
import sp.phone.util.FunctionUtils;

public class AboutActivity extends MaterialAboutActivity {

    private static final String PROJECT_URL = "https://github.com/tophtab/nga-just-works";
    private static final String RELEASES_URL = PROJECT_URL + "/releases";
    private static final String ISSUES_URL = PROJECT_URL + "/issues";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.getInstance().applyAboutTheme(this);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    protected MaterialAboutList getMaterialAboutList(@NonNull Context context) {
        return new MaterialAboutList(buildAppCard(), buildDevelopCard());
    }

    private MaterialAboutCard buildAppCard() {
        MaterialAboutCard.Builder builder = new MaterialAboutCard.Builder();
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.start_title)
                .icon(R.mipmap.ic_launcher)
                .setOnClickAction(() -> new VersionUpgradeDialogFragment().show(getSupportFragmentManager(), null))
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("版本")
                .subText(BuildConfig.VERSION_NAME)
                .icon(R.drawable.ic_about)
                .setOnClickAction(() -> FunctionUtils.openUrlByDefaultBrowser(
                        AboutActivity.this,
                        RELEASES_URL))
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("项目声明")
                .subText("本项目基于 Justwen/NGA-CLIENT-VER-OPEN-SOURCE 二次开发，非 NGA 官方客户端")
                .icon(R.drawable.ic_about)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("License")
                .subText("GNU GPL v2,开放源代码许可")
                .setOnClickAction(() -> {
                    ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                            .withString("url", "file:///android_asset/OSLICENSE.TXT")
                            .withString("fragment", WebViewFragment.class.getName())
                            .navigation(this);
                })
                .icon(R.drawable.ic_license)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("检测更新")
                .setOnClickAction(() -> {
                    ARouterUtils.build(ARouterConstants.ACTIVITY_FRAGMENT_TEMPLATE)
                            .withString("url", RELEASES_URL)
                            .withString("fragment", WebViewFragment.class.getName())
                            .navigation(this);

                })
                .icon(R.drawable.ic_update_24dp)
                .build());

        return builder.build();
    }

    private MaterialAboutCard buildDevelopCard() {
        MaterialAboutCard.Builder builder = new MaterialAboutCard.Builder();
        builder.title("项目与开发");
        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("源代码")
                .subText(PROJECT_URL)
                .setOnClickAction(() -> FunctionUtils.openUrlByDefaultBrowser(AboutActivity.this, PROJECT_URL))
                .icon(R.drawable.ic_github)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("原项目代码")
                .subText("[@竹井詩織里]/[@cfan8]/[@jjimmys]\n[@Moandor]/[@Elrond]/[@Justwen]")
                .setOnLongClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        ARouterUtils.navigation(ARouterConstants.ACTIVITY_DEBUG);
                    }
                })
                .icon(R.drawable.ic_code)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("原项目美工")
                .subText("[@那个惩戒骑]/[@从来不卖萌]")
                .icon(R.drawable.ic_color_lens)
                .build());

        builder.addItem(new MaterialAboutActionItem.Builder()
                .text("问题反馈")
                .subText("GitHub Issues")
                .setOnClickAction(() -> FunctionUtils.openUrlByDefaultBrowser(AboutActivity.this, ISSUES_URL))
                .icon(R.drawable.ic_github)
                .build());

        return builder.build();
    }

    @Nullable
    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.title_about);
    }
}
