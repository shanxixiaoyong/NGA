package gov.anzong.androidnga.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import gov.anzong.androidnga.R;
import sp.phone.linuxdo.LinuxDoConstants;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoHttpSession;
import sp.phone.linuxdo.LinuxDoSessionState;
import sp.phone.linuxdo.LinuxDoWebSession;
import sp.phone.util.ActivityUtils;

/** User-visible Cloudflare/login gate for the isolated linux.do session. */
public final class LinuxDoSessionActivity extends BaseActivity {

    private LinuxDoWebSession mSession;
    private TextView mHint;
    private Button mEnterButton;
    private boolean mProbeInFlight;
    private boolean mLaunchingNativeList;
    private boolean mLoginOnly;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setToolbarEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linux_do_session);
        setupToolbar();
        setTitle(LinuxDoConstants.BOARD_NAME);
        mLoginOnly = getIntent().getBooleanExtra(LinuxDoNavigation.EXTRA_LOGIN_ONLY, false);
        mHint = findViewById(R.id.session_hint);
        mEnterButton = findViewById(R.id.enter_linux_do);
        if (mLoginOnly) {
            setTitle("登录 LINUX DO");
            mHint.setText(R.string.linuxdo_login_hint);
            mEnterButton.setText(R.string.linuxdo_confirm_login);
        }
        mEnterButton.setOnClickListener(ignored -> probeAndEnter());
        mSession = LinuxDoWebSession.getInstance();
        mSession.attach(this, (FrameLayout) findViewById(R.id.web_container),
                this::probeAndEnter);
        if (mLoginOnly) mSession.showLoginPage();
    }

    private void probeAndEnter() {
        if (mProbeInFlight || mLaunchingNativeList) return;
        mProbeInFlight = true;
        mEnterButton.setEnabled(false);
        mHint.setText(R.string.linuxdo_session_checking);
        mSession.fetch(mLoginOnly ? "/session/current.json" : "/latest.json?page=0",
                new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                mProbeInFlight = false;
                if (mLoginOnly) {
                    try {
                        if (new JSONObject(json).optJSONObject("current_user") == null) {
                            mEnterButton.setEnabled(true);
                            mHint.setText(R.string.linuxdo_not_logged_in);
                            return;
                        }
                    } catch (Exception error) {
                        mEnterButton.setEnabled(true);
                        mHint.setText(R.string.linuxdo_session_not_ready);
                        return;
                    }
                    LinuxDoSessionState.setReady(true);
                    LinuxDoHttpSession.getInstance().invalidateCsrfToken();
                    mLaunchingNativeList = true;
                    mSession.detachToApplication(getApplicationContext());
                    ActivityUtils.showToast("LINUX DO 登录成功");
                    finish();
                    return;
                }
                launchNativeList();
            }

            @Override
            public void onFailure(LinuxDoWebSession.Failure failure) {
                mProbeInFlight = false;
                mEnterButton.setEnabled(true);
                mHint.setText(failure == LinuxDoWebSession.Failure.VERIFICATION_REQUIRED
                        ? R.string.linuxdo_session_verify_again
                        : R.string.linuxdo_session_not_ready);
            }
        });
    }

    private void launchNativeList() {
        mLaunchingNativeList = true;
        LinuxDoSessionState.setReady(true);
        mSession.detachToApplication(getApplicationContext());
        LinuxDoNavigation.openNativeList(this);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (!mLaunchingNativeList && isFinishing() && mSession != null) {
            mSession.destroyNow();
        }
        super.onDestroy();
    }
}
