package com.justwent.androidnga.bu.login

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.justwen.androidnga.base.network.login.NgaCaptchaResult
import com.justwen.androidnga.base.network.login.NgaLoginAccountType
import com.justwen.androidnga.base.network.login.NgaLoginClient
import com.justwen.androidnga.base.network.login.NgaLoginFailureKind
import com.justwen.androidnga.base.network.login.NgaLoginResult
import com.justwen.androidnga.base.network.login.NgaLoginSession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class LoginPhase {
    IDLE,
    SUBMITTING,
    CAPTCHA_REQUIRED,
    ERROR,
    SUCCESS,
}

data class LoginUiState(
    val account: String = "",
    val accountType: NgaLoginAccountType = NgaLoginAccountType.USERNAME,
    val phase: LoginPhase = LoginPhase.IDLE,
    val errorMessage: String? = null,
    val captchaImage: ByteArray? = null,
    val success: NgaLoginResult.Success? = null,
    val clearSensitiveSignal: Int = 0,
)

class LoginViewModel internal constructor(
    private val session: NgaLoginSession,
    private val scheduler: LoginWorkScheduler,
) : ViewModel() {
    constructor() : this(NgaLoginClient().createSession(), ExecutorLoginWorkScheduler())

    var uiState by mutableStateOf(LoginUiState())
        private set

    private val requestActive = AtomicBoolean(false)
    private val requestGeneration = AtomicInteger(0)
    private var activeWork: LoginWork? = null
    @Volatile
    private var cleared = false

    fun updateAccount(value: String) {
        uiState = uiState.copy(account = value, errorMessage = null)
    }

    fun updateAccountType(value: NgaLoginAccountType) {
        uiState = uiState.copy(accountType = value, errorMessage = null)
    }

    fun submit(password: CharSequence, captcha: CharSequence?) {
        val snapshot = uiState
        if (snapshot.account.isBlank() || password.isBlank()) {
            uiState = snapshot.copy(phase = LoginPhase.ERROR, errorMessage = "请输入账号和密码")
            return
        }
        if (snapshot.phase == LoginPhase.CAPTCHA_REQUIRED && captcha.isNullOrBlank()) {
            uiState = snapshot.copy(errorMessage = "请输入图形验证码")
            return
        }
        scheduleRequest {
            val result = session.submit(
                account = snapshot.account,
                accountType = snapshot.accountType,
                password = password,
                captcha = captcha?.takeIf { snapshot.phase == LoginPhase.CAPTCHA_REQUIRED },
            )
            if (result is NgaLoginResult.Failure &&
                result.kind in setOf(NgaLoginFailureKind.CAPTCHA_REQUIRED, NgaLoginFailureKind.CAPTCHA_INCORRECT)
            ) {
                when (val captchaResult = session.refreshCaptcha()) {
                    is NgaCaptchaResult.Success -> LoginUiState(
                        account = snapshot.account,
                        accountType = snapshot.accountType,
                        phase = LoginPhase.CAPTCHA_REQUIRED,
                        errorMessage = if (result.kind == NgaLoginFailureKind.CAPTCHA_INCORRECT) result.message else null,
                        captchaImage = captchaResult.imageBytes,
                    )
                    is NgaCaptchaResult.Failure -> snapshot.copy(
                        phase = LoginPhase.ERROR,
                        errorMessage = captchaResult.failure.message,
                        captchaImage = null,
                        clearSensitiveSignal = snapshot.clearSensitiveSignal + 1,
                    )
                }
            } else {
                stateForResult(snapshot, result)
            }
        }
    }

    fun refreshCaptcha() {
        val snapshot = uiState
        if (snapshot.phase != LoginPhase.CAPTCHA_REQUIRED) return
        scheduleRequest {
            when (val result = session.refreshCaptcha()) {
                is NgaCaptchaResult.Success -> snapshot.copy(
                    phase = LoginPhase.CAPTCHA_REQUIRED,
                    errorMessage = null,
                    captchaImage = result.imageBytes,
                )
                is NgaCaptchaResult.Failure -> snapshot.copy(
                    phase = LoginPhase.CAPTCHA_REQUIRED,
                    errorMessage = result.failure.message,
                    captchaImage = null,
                )
            }
        }
    }

    fun consumeSuccess() {
        uiState = uiState.copy(phase = LoginPhase.IDLE, success = null, captchaImage = null)
    }

    fun onHostStopped() {
        requestGeneration.incrementAndGet()
        activeWork?.cancel()
        activeWork = null
        requestActive.set(false)
        session.cancel()
        uiState = uiState.copy(
            phase = LoginPhase.IDLE,
            errorMessage = null,
            captchaImage = null,
            success = null,
            clearSensitiveSignal = uiState.clearSensitiveSignal + 1,
        )
    }

    private fun scheduleRequest(block: () -> LoginUiState) {
        if (!requestActive.compareAndSet(false, true)) return
        val generation = requestGeneration.incrementAndGet()
        val fallback = uiState
        uiState = fallback.copy(phase = LoginPhase.SUBMITTING, errorMessage = null)
        try {
            activeWork = scheduler.schedule {
                val next = try {
                    block()
                } catch (_: Exception) {
                    fallback.copy(
                        phase = LoginPhase.ERROR,
                        errorMessage = "登录请求失败，请稍后重试",
                        captchaImage = null,
                        clearSensitiveSignal = fallback.clearSensitiveSignal + 1,
                    )
                }
                scheduler.dispatch {
                    if (cleared || requestGeneration.get() != generation) return@dispatch
                    requestActive.set(false)
                    activeWork = null
                    uiState = next
                }
            }
        } catch (_: RejectedExecutionException) {
            if (requestGeneration.get() == generation) requestActive.set(false)
        }
    }

    private fun stateForResult(snapshot: LoginUiState, result: NgaLoginResult): LoginUiState = when (result) {
        is NgaLoginResult.Success -> snapshot.copy(
            phase = LoginPhase.SUCCESS,
            errorMessage = null,
            captchaImage = null,
            success = result,
        )
        is NgaLoginResult.Failure -> snapshot.copy(
            phase = LoginPhase.ERROR,
            errorMessage = result.message,
            captchaImage = null,
            success = null,
            clearSensitiveSignal = snapshot.clearSensitiveSignal + 1,
        )
    }

    override fun onCleared() {
        cleared = true
        requestGeneration.incrementAndGet()
        activeWork?.cancel()
        session.cancel()
        scheduler.close()
        super.onCleared()
    }
}

internal interface LoginWork {
    fun cancel()
}

internal interface LoginWorkScheduler {
    fun schedule(block: () -> Unit): LoginWork
    fun dispatch(block: () -> Unit)
    fun close()
}

private class ExecutorLoginWorkScheduler : LoginWorkScheduler {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun schedule(block: () -> Unit): LoginWork {
        val future: Future<*> = executor.submit(block)
        return object : LoginWork {
            override fun cancel() {
                future.cancel(true)
            }
        }
    }

    override fun dispatch(block: () -> Unit) {
        mainHandler.post(block)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
