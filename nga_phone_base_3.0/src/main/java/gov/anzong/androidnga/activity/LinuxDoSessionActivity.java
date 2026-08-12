package gov.anzong.androidnga.activity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import gov.anzong.androidnga.R;
import sp.phone.linuxdo.LinuxDoConstants;
import sp.phone.linuxdo.LinuxDoNavigation;
import sp.phone.linuxdo.LinuxDoSessionState;
import sp.phone.linuxdo.LinuxDoWebSession;

/** User-visible Cloudflare/login gate for the read-only linux.do source. */
public final class LinuxDoSessionActivity extends BaseActivity {

    private LinuxDoWebSession mSession;
    private TextView mHint;
    private Button mEnterButton;
    private boolean mProbeInFlight;
    private boolean mLaunchingNativeList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setToolbarEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_linux_do_session);
        setupToolbar();
        setTitle(LinuxDoConstants.BOARD_NAME);
        mHint = findViewById(R.id.session_hint);
        mEnterButton = findViewById(R.id.enter_linux_do);
        mEnterButton.setOnClickListener(ignored -> probeAndEnter());
        mSession = LinuxDoWebSession.getInstance();
        mSession.attach(this, (FrameLayout) findViewById(R.id.web_container),
                this::probeAndEnter);
    }

    private void probeAndEnter() {
        if (mProbeInFlight || mLaunchingNativeList) return;
        mProbeInFlight = true;
        mEnterButton.setEnabled(false);
        mHint.setText(R.string.linuxdo_session_checking);
        mSession.fetch("/latest.json?page=0", new LinuxDoWebSession.Callback() {
            @Override
            public void onSuccess(String json) {
                mProbeInFlight = false;
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
