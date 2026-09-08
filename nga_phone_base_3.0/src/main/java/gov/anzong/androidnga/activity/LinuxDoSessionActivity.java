package gov.anzong.androidnga.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONObject;
import gov.anzong.androidnga.R;
import sp.phone.linuxdo.*;

/** UI coordinator only. Official pages own authentication; no credential/token HTTP replay. */
public final class LinuxDoSessionActivity extends BaseActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinuxDoAuthFlow flow = new LinuxDoAuthFlow();
    private LinuxDoAuthFlow.Mode mode;
    private LinuxDoAuthBrowser browser;
    private FrameLayout container;
    private TextView hint;
    private ProgressBar progress;
    private Button action, switchMode, account, retry;
    private long token, automaticUntil;
    private int checkGeneration;
    private boolean ready, checking, visible, autofilled;
    private boolean verificationLease;
    private String target;

    @Override protected boolean shouldRunMainProcessResumeTasks() { return false; }

    @Override protected void onCreate(@Nullable Bundle savedState) {
        setToolbarEnabled(true);
        super.onCreate(savedState);
        setContentView(R.layout.activity_linux_do_session);
        setupToolbar();
        hint = findViewById(R.id.session_hint);
        container = findViewById(R.id.web_container);
        progress = findViewById(R.id.session_progress);
        action = findViewById(R.id.enter_linux_do);
        switchMode = findViewById(R.id.session_switch);
        account = findViewById(R.id.session_account);
        retry = findViewById(R.id.session_retry);
        target = getIntent().getStringExtra(LinuxDoNavigation.EXTRA_BROWSER_URL);
        mode = target != null ? LinuxDoAuthFlow.Mode.BROWSER
                : getIntent().getBooleanExtra(LinuxDoNavigation.EXTRA_LOGIN_ONLY, true)
                ? LinuxDoAuthFlow.Mode.LOGIN : LinuxDoAuthFlow.Mode.VERIFICATION;
        verificationLease = getIntent().getBooleanExtra(
                LinuxDoNavigation.EXTRA_VERIFICATION_LEASE, false);
        if (mode == LinuxDoAuthFlow.Mode.VERIFICATION && !verificationLease) {
            // Defensive path for older callers that launch the Activity directly.
            verificationLease = LinuxDoChallengeCoordinator.tryBeginVerification();
            if (!verificationLease) {
                finish();
                return;
            }
        }
        if (mode == LinuxDoAuthFlow.Mode.BROWSER && !LinuxDoAuthFlow.isFirstParty(target)) {
            finish(); return;
        }
        action.setOnClickListener(v -> check(true));
        retry.setOnClickListener(v -> connect());
        account.setOnClickListener(v -> credentials());
        account.setOnLongClickListener(v -> { switchAccount(); return true; });
        switchMode.setOnClickListener(v -> {
            LinuxDoAuthFlow.Mode next = mode == LinuxDoAuthFlow.Mode.VERIFICATION
                    ? LinuxDoAuthFlow.Mode.LOGIN : LinuxDoAuthFlow.Mode.VERIFICATION;
            if (next == LinuxDoAuthFlow.Mode.VERIFICATION && !verificationLease) {
                if (!LinuxDoChallengeCoordinator.tryBeginVerification()) {
                    hint.setText("网络验证已在另一个页面进行中。");
                    return;
                }
                verificationLease = true;
            } else if (mode == LinuxDoAuthFlow.Mode.VERIFICATION && verificationLease) {
                LinuxDoChallengeCoordinator.cancelVerification();
                verificationLease = false;
            }
            mode = next;
            target = null;
            if (ready) openPage(); else connect();
        });
        retry.setOnLongClickListener(v -> { LinuxDoNavigation.editDoh(this); return true; });
        connect();
        if (getIntent().getBooleanExtra(LinuxDoNavigation.EXTRA_RESET_SESSION, false)) switchAccount();
    }

    private void connect() {
        token = flow.begin();
        long attempt = token;
        handler.removeCallbacksAndMessages(null);
        checking = false;
        ++checkGeneration;
        ready = false;
        autofilled = false;
        if (browser != null) { browser.close(); browser = null; }
        labels();
        hint.setText("正在连接 LINUX DO…");
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        action.setEnabled(false);
        // Do not put the official login/challenge page behind the old
        // process-wide CONNECT tunnel.  The tunnel used a Java socket and
        // could not preserve Chromium's TLS/HTTP3/challenge behaviour.  Let
        // WebView own the complete browser network stack; native feed requests
        // continue to use LinuxDoHttpSession's DoH/Cronet path.
        browser = new LinuxDoAuthBrowser(LinuxDoSessionActivity.this, container,
                new LinuxDoAuthBrowser.Listener() {
                    @Override public void onPage() {
                        if (!alive(attempt)) return;
                        ready = true;
                        checking = false;
                        ++checkGeneration;
                        flow.move(token, LinuxDoAuthFlow.State.WEB);
                        action.setEnabled(true);
                        hint.setText(instructions());
                        if (mode == LinuxDoAuthFlow.Mode.LOGIN && !autofilled) fillRemembered();
                        automaticUntil = android.os.SystemClock.elapsedRealtime() + 120_000;
                        scheduleCheck();
                    }
                    @Override public void onError(String message) {
                        if (alive(attempt)) fail(message);
                    }
                    @Override public void onProgress(int value) {
                        if (!alive(attempt)) return;
                        progress.setIndeterminate(false);
                        progress.setProgress(value);
                        progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
                    }
                });
        ready = true;
        openPage();
    }

    private void labels() {
        boolean login = mode == LinuxDoAuthFlow.Mode.LOGIN;
        boolean web = mode == LinuxDoAuthFlow.Mode.BROWSER;
        setTitle(web ? "浏览器模式" : login ? "LINUX DO 登录" : "网络验证");
        action.setText(web ? "返回帖子" : login ? "检查登录结果" : "我已完成验证");
        switchMode.setText(mode == LinuxDoAuthFlow.Mode.VERIFICATION ? "返回登录" : "网络验证");
        account.setVisibility(login ? View.VISIBLE : View.GONE);
    }

    private String instructions() {
        if (mode == LinuxDoAuthFlow.Mode.BROWSER) return "原版网页 · 专用连接";
        return mode == LinuxDoAuthFlow.Mode.LOGIN
                ? "请在官方页面登录并验证；需要记住密码请点“账号”。完成后会自动检查。"
                : "仅验证当前网络，不会退出账号。完成后自动检查，也可点下方按钮确认。";
    }

    private void openPage() {
        labels();
        checking = false;
        ++checkGeneration;
        automaticUntil = 0;
        handler.removeCallbacks(autoCheck);
        flow.move(token, LinuxDoAuthFlow.State.WEB);
        hint.setText(instructions());
        action.setEnabled(true);
        browser.load(mode == LinuxDoAuthFlow.Mode.BROWSER ? target
                : LinuxDoConstants.ORIGIN + (mode == LinuxDoAuthFlow.Mode.LOGIN ? "/login" : "/latest"));
    }

    private final Runnable autoCheck = () -> check(false);
    private void scheduleCheck() {
        handler.removeCallbacks(autoCheck);
        if (visible && mode != LinuxDoAuthFlow.Mode.BROWSER
                && android.os.SystemClock.elapsedRealtime() < automaticUntil
                && flow.state() == LinuxDoAuthFlow.State.WEB) handler.postDelayed(autoCheck, 4_000);
    }

    private void check(boolean manual) {
        if (mode == LinuxDoAuthFlow.Mode.BROWSER) { if (manual) finish(); return; }
        if (!ready || browser == null || checking || !visible
                || flow.state() == LinuxDoAuthFlow.State.COMPLETE) return;
        if (mode == LinuxDoAuthFlow.Mode.LOGIN && !autofilled) fillRemembered();
        long attempt = token;
        LinuxDoAuthFlow.Mode requestedMode = mode;
        checking = true;
        int checkId = ++checkGeneration;
        flow.move(token, LinuxDoAuthFlow.State.CHECKING);
        if (manual) hint.setText("正在确认网页会话与帖子连接…");
        // Watchdog also covers a redirect destroying the JavaScript execution context.
        Runnable timeout = () -> {
            if (alive(attempt) && checking && requestedMode == mode && checkId == checkGeneration) {
                checking = false;
                flow.move(token, LinuxDoAuthFlow.State.WEB);
                if (manual) hint.setText("结果暂未确认，可继续完成页面验证后再检查。");
            }
        };
        handler.postDelayed(timeout, 25_000);
        browser.probe(mode == LinuxDoAuthFlow.Mode.VERIFICATION, (kind, username) -> {
            if (!alive(attempt) || requestedMode != mode || checkId != checkGeneration) return;
            if ("account".equals(kind) || "verified".equals(kind)) {
                CookieManager.getInstance().flush();
                LinuxDoHttpSession.getInstance().invalidateCsrfToken();
                // Browser success alone is not enough: the native feed must accept its cookies.
                LinuxDoHttpSession.getInstance().fetch(
                        mode == LinuxDoAuthFlow.Mode.LOGIN ? "/session/current.json" : "/latest.json",
                        new LinuxDoWebSession.Callback() {
                            @Override public void onSuccess(String json) {
                                handler.post(() -> {
                                    if (!alive(attempt) || requestedMode != mode || checkId != checkGeneration) return;
                                    handler.removeCallbacks(timeout);
                                    try {
                                        JSONObject root = new JSONObject(json);
                                        JSONObject user = root.optJSONObject("current_user");
                                        boolean matched = requestedMode == LinuxDoAuthFlow.Mode.LOGIN
                                                ? user != null && !username.isEmpty()
                                                    && username.equals(user.optString("username"))
                                                : root.optJSONObject("topic_list") != null;
                                        if (matched) complete();
                                        else pending("网页已完成，但帖子会话尚未同步。请点检查重试。", false);
                                    } catch (Exception ignored) {
                                        pending("帖子连接返回异常，请稍后检查。账号不会被清除。", false);
                                    }
                                });
                            }
                            @Override public void onFailure(LinuxDoWebSession.Failure failure) {
                                handler.post(() -> {
                                    if (!alive(attempt) || requestedMode != mode || checkId != checkGeneration) return;
                                    handler.removeCallbacks(timeout);
                                    pending("网页会话已完成，但帖子通道暂未通过。可进入网络验证；不需要反复登录。", false);
                                });
                            }
                        });
            } else {
                handler.removeCallbacks(timeout);
                pending(manual ? ("guest".equals(kind) ? "尚未登录，请在官方页面完成登录。"
                        : "网络结果尚未通过。请完成网页验证；连接失败可点重试。") : instructions(), true);
            }
        });
    }

    private void pending(String message, boolean poll) {
        checking = false;
        flow.move(token, LinuxDoAuthFlow.State.WEB);
        hint.setText(message);
        if (poll) scheduleCheck();
    }

    private void complete() {
        checking = false;
        if (!visible) { flow.move(token, LinuxDoAuthFlow.State.WEB); return; }
        flow.move(token, LinuxDoAuthFlow.State.COMPLETE);
        if (verificationLease) {
            LinuxDoChallengeCoordinator.finishVerification(true);
            verificationLease = false;
        } else if (mode == LinuxDoAuthFlow.Mode.LOGIN) {
            LinuxDoChallengeCoordinator.clearIfIdle();
        }
        handler.removeCallbacksAndMessages(null);
        LinuxDoSessionState.setReady(true);
        setResult(RESULT_OK);
        android.widget.Toast.makeText(this, mode == LinuxDoAuthFlow.Mode.LOGIN
                ? "登录完成，帖子连接已确认" : "网络验证完成", android.widget.Toast.LENGTH_SHORT).show();
        finish();
    }

    private void fail(String message) {
        checking = false;
        ++checkGeneration;
        automaticUntil = 0;
        handler.removeCallbacks(autoCheck);
        flow.move(token, LinuxDoAuthFlow.State.ERROR);
        if (mode == LinuxDoAuthFlow.Mode.VERIFICATION && verificationLease) {
            LinuxDoChallengeCoordinator.cancelVerification();
            verificationLease = false;
        }
        progress.setVisibility(View.GONE);
        hint.setText(message);
        action.setEnabled(true);
    }

    private void credentials() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this), password = new EditText(this);
        name.setSingleLine(true); name.setHint("账号或邮箱");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password.setSingleLine(true); password.setHint("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        name.setSaveEnabled(false); password.setSaveEnabled(false);
        CheckBox remember = new CheckBox(this);
        remember.setText("记住账号密码（加密保存在本机）");
        LinuxDoRememberedLogin.Entry saved = LinuxDoRememberedLogin.load();
        if (saved != null) { name.setText(saved.identifier); password.setText(saved.password); }
        remember.setChecked(saved != null);
        form.addView(name); form.addView(password); form.addView(remember);
        new AlertDialog.Builder(this).setTitle("账号自动填写")
                .setMessage("只填入官方登录框，不代替你提交登录或完成人机验证。")
                .setView(form).setNegativeButton("取消", null)
                .setNeutralButton("清除记忆", (d, w) -> LinuxDoRememberedLogin.clear())
                .setPositiveButton("填入网页", (d, w) -> {
                    String identifier = name.getText().toString().trim();
                    String secret = password.getText().toString();
                    if (identifier.isEmpty() || secret.isEmpty()) return;
                    if (remember.isChecked()) LinuxDoRememberedLogin.save(identifier, secret);
                    else LinuxDoRememberedLogin.clear();
                    if (browser != null) browser.fill(identifier, secret, ok -> {
                        autofilled = ok;
                        hint.setText(ok ? "已填入，请在官方页面点击登录并完成验证。"
                                : "登录框还未出现。先完成网络验证或打开登录表单，再点账号填入。");
                    });
                    password.setText("");
                }).show();
    }

    private void switchAccount() {
        new AlertDialog.Builder(this).setTitle("切换账号")
                .setMessage("仅清除本机的账号会话，保留网络验证状态和加密保存的账号密码。")
                .setNegativeButton("取消", null)
                .setPositiveButton("切换", (d, w) -> {
                    LinuxDoUserApiAuth.clearCredential();
                    CookieManager cookies = CookieManager.getInstance();
                    java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(4);
                    for (String name : new String[]{"_t", "_forum_session"}) {
                        for (String domain : new String[]{"", "; Domain=linux.do"}) {
                            cookies.setCookie(LinuxDoConstants.ORIGIN,
                                    name + "=; Path=/; Max-Age=0; Secure" + domain, done -> {
                                        if (pending.decrementAndGet() == 0 && !isFinishing() && !isDestroyed()) {
                                            cookies.flush();
                                            LinuxDoHttpSession.getInstance().invalidateCsrfToken();
                                            mode = LinuxDoAuthFlow.Mode.LOGIN;
                                            connect();
                                        }
                                    });
                        }
                    }
                }).show();
    }

    private void fillRemembered() {
        LinuxDoRememberedLogin.Entry saved = LinuxDoRememberedLogin.load();
        if (saved != null && browser != null) browser.fill(saved.identifier, saved.password, false,
                ok -> autofilled = ok);
    }

    private boolean alive(long attempt) {
        return flow.accepts(attempt) && !isFinishing() && !isDestroyed();
    }
    @Override protected void onResume() { super.onResume(); visible = true; scheduleCheck(); }
    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(autoCheck);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (verificationLease) {
            LinuxDoChallengeCoordinator.cancelVerification();
            verificationLease = false;
        }
        flow.close();
        handler.removeCallbacksAndMessages(null);
        if (browser != null) browser.close();
        super.onDestroy();
    }
}
