package sp.phone.linuxdo;

import android.content.Context;
import android.content.Intent;

import gov.anzong.androidnga.activity.LinuxDoSessionActivity;
import gov.anzong.androidnga.activity.TopicListActivity;
import sp.phone.param.ContentSource;
import sp.phone.param.ParamKey;
import sp.phone.param.TopicListParam;

public final class LinuxDoNavigation {
    public static final String EXTRA_LOGIN_ONLY = "linuxdo_login_only";
    private LinuxDoNavigation() {
    }

    public static void openVerification(Context context) {
        Intent intent = new Intent(context, LinuxDoSessionActivity.class);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openLogin(Context context) {
        Intent intent = new Intent(context, LinuxDoSessionActivity.class);
        intent.putExtra(EXTRA_LOGIN_ONLY, true);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openNativeList(Context context) {
        TopicListParam param = new TopicListParam();
        param.source = ContentSource.LINUX_DO;
        param.fid = LinuxDoConstants.BOARD_FID;
        param.title = LinuxDoConstants.BOARD_NAME;
        Intent intent = new Intent(context, TopicListActivity.class);
        intent.putExtra(ParamKey.KEY_PARAM, param);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
