package com.justwent.androidnga.bu.login

import com.justwen.androidnga.base.network.login.NgaCaptchaResult
import com.justwen.androidnga.base.network.login.NgaLoginAccountType
import com.justwen.androidnga.base.network.login.NgaLoginFailureKind
import com.justwen.androidnga.base.network.login.NgaLoginResult
import com.justwen.androidnga.base.network.login.NgaLoginSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginViewModelTest {
    @Test
    fun successMovesThroughOneRequestAndCanBeConsumed() {
        val session = FakeSession(
            loginResults = mutableListOf(NgaLoginResult.Success("42", "session-value", "reader")),
        )
        val model = LoginViewModel(session, DirectScheduler())
        model.updateAccount("reader")
        model.updateAccountType(NgaLoginAccountType.EMAIL)

        model.submit("example-input", null)

        assertEquals(LoginPhase.SUCCESS, model.uiState.phase)
        assertEquals("42", model.uiState.success?.uid)
        assertEquals(NgaLoginAccountType.EMAIL, session.lastAccountType)
        assertFalse(model.uiState.toString().contains("example-input"))
        model.consumeSuccess()
        assertEquals(LoginPhase.IDLE, model.uiState.phase)
        assertNull(model.uiState.success)
    }

    @Test
    fun captchaRequiredWrongAndRefreshRemainInChallengeState() {
        val session = FakeSession(
            loginResults = mutableListOf(
                failure(NgaLoginFailureKind.CAPTCHA_REQUIRED, "请输入图形验证码"),
                failure(NgaLoginFailureKind.CAPTCHA_INCORRECT, "验证码不正确，请重新输入"),
            ),
            captchaResults = mutableListOf(
                NgaCaptchaResult.Success("first-image".toByteArray()),
                NgaCaptchaResult.Success("second-image".toByteArray()),
                NgaCaptchaResult.Success("third-image".toByteArray()),
            ),
        )
        val model = LoginViewModel(session, DirectScheduler())
        model.updateAccount("reader")

        model.submit("example-input", null)
        assertEquals(LoginPhase.CAPTCHA_REQUIRED, model.uiState.phase)
        assertEquals("first-image", String(model.uiState.captchaImage!!))
        assertEquals(0, model.uiState.clearSensitiveSignal)

        model.submit("example-input", "ABC123")
        assertEquals(LoginPhase.CAPTCHA_REQUIRED, model.uiState.phase)
        assertEquals("验证码不正确，请重新输入", model.uiState.errorMessage)
        assertEquals("ABC123", session.lastCaptcha)
        assertEquals("second-image", String(model.uiState.captchaImage!!))

        model.refreshCaptcha()
        assertEquals("third-image", String(model.uiState.captchaImage!!))
        assertNull(model.uiState.errorMessage)
    }

    @Test
    fun errorCanRecoverWithoutLosingAccountIdentity() {
        val session = FakeSession(
            loginResults = mutableListOf(
                failure(NgaLoginFailureKind.INVALID_CREDENTIALS, "账号或密码不正确"),
                NgaLoginResult.Success("42", "session-value", "reader"),
            ),
        )
        val model = LoginViewModel(session, DirectScheduler())
        model.updateAccount("reader")

        model.submit("first-input", null)
        assertEquals(LoginPhase.ERROR, model.uiState.phase)
        assertEquals("reader", model.uiState.account)
        assertEquals(1, model.uiState.clearSensitiveSignal)
        model.submit("second-input", null)
        assertEquals(LoginPhase.SUCCESS, model.uiState.phase)
        assertEquals(2, session.submitCount)
    }

    @Test
    fun unexpectedSessionFailureClearsSensitiveInput() {
        val session = object : NgaLoginSession {
            override fun submit(
                account: String,
                accountType: NgaLoginAccountType,
                password: CharSequence,
                captcha: CharSequence?,
            ): NgaLoginResult = error("unexpected")

            override fun refreshCaptcha(): NgaCaptchaResult = error("unused")

            override fun cancel() = Unit
        }
        val model = LoginViewModel(session, DirectScheduler())
        model.updateAccount("reader")

        model.submit("example-input", null)

        assertEquals(LoginPhase.ERROR, model.uiState.phase)
        assertEquals("登录请求失败，请稍后重试", model.uiState.errorMessage)
        assertEquals(1, model.uiState.clearSensitiveSignal)
    }

    @Test
    fun duplicateSubmitIsIgnoredWhileRequestIsQueued() {
        val scheduler = QueuedScheduler()
        val session = FakeSession(
            loginResults = mutableListOf(NgaLoginResult.Success("42", "session-value", "reader")),
        )
        val model = LoginViewModel(session, scheduler)
        model.updateAccount("reader")

        model.submit("first-input", null)
        model.submit("second-input", null)

        assertEquals(1, scheduler.pendingCount)
        assertEquals(LoginPhase.SUBMITTING, model.uiState.phase)
        scheduler.runNext()
        assertEquals(1, session.submitCount)
        assertEquals(LoginPhase.SUCCESS, model.uiState.phase)
    }

    @Test
    fun blankCaptchaDoesNotStartAnotherRequest() {
        val session = FakeSession(
            loginResults = mutableListOf(failure(NgaLoginFailureKind.CAPTCHA_REQUIRED, "请输入图形验证码")),
            captchaResults = mutableListOf(NgaCaptchaResult.Success("image".toByteArray())),
        )
        val model = LoginViewModel(session, DirectScheduler())
        model.updateAccount("reader")
        model.submit("example-input", null)

        model.submit("example-input", "")

        assertEquals(1, session.submitCount)
        assertEquals("请输入图形验证码", model.uiState.errorMessage)
    }

    @Test
    fun clearingViewModelCancelsActiveWorkAndTemporarySession() {
        val scheduler = TrackingScheduler()
        val session = FakeSession(loginResults = mutableListOf())
        val model = LoginViewModel(session, scheduler)
        model.updateAccount("reader")
        model.submit("example-input", null)

        LoginViewModel::class.java.getDeclaredMethod("onCleared").apply {
            isAccessible = true
        }.invoke(model)

        assertTrue(session.cancelled)
        assertTrue(scheduler.workCancelled)
        assertTrue(scheduler.closed)
    }

    @Test
    fun hostStopClearsChallengeAndInvalidatesLateResult() {
        val scheduler = QueuedScheduler()
        val session = FakeSession(
            loginResults = mutableListOf(NgaLoginResult.Success("42", "session-value", "reader")),
        )
        val model = LoginViewModel(session, scheduler)
        model.updateAccount("reader")
        model.submit("example-input", null)

        model.onHostStopped()
        scheduler.runNext()

        assertTrue(session.cancelled)
        assertEquals(LoginPhase.IDLE, model.uiState.phase)
        assertEquals("reader", model.uiState.account)
        assertNull(model.uiState.success)
        assertEquals(1, model.uiState.clearSensitiveSignal)
    }

    private fun failure(kind: NgaLoginFailureKind, message: String) =
        NgaLoginResult.Failure(kind, message)
}

private class FakeSession(
    private val loginResults: MutableList<NgaLoginResult>,
    private val captchaResults: MutableList<NgaCaptchaResult> = mutableListOf(),
) : NgaLoginSession {
    var submitCount = 0
    var lastAccountType: NgaLoginAccountType? = null
    var lastCaptcha: String? = null
    var cancelled = false

    override fun submit(
        account: String,
        accountType: NgaLoginAccountType,
        password: CharSequence,
        captcha: CharSequence?,
    ): NgaLoginResult {
        submitCount++
        lastAccountType = accountType
        lastCaptcha = captcha?.toString()
        return loginResults.removeAt(0)
    }

    override fun refreshCaptcha(): NgaCaptchaResult = captchaResults.removeAt(0)

    override fun cancel() {
        cancelled = true
    }
}

private class DirectScheduler : LoginWorkScheduler {
    override fun schedule(block: () -> Unit): LoginWork {
        block()
        return NoOpWork
    }

    override fun dispatch(block: () -> Unit) = block()
    override fun close() = Unit
}

private class QueuedScheduler : LoginWorkScheduler {
    private val pending = ArrayDeque<() -> Unit>()
    val pendingCount: Int get() = pending.size

    override fun schedule(block: () -> Unit): LoginWork {
        pending.addLast(block)
        return NoOpWork
    }

    override fun dispatch(block: () -> Unit) = block()
    override fun close() = Unit
    fun runNext() = pending.removeFirst().invoke()
}

private class TrackingScheduler : LoginWorkScheduler {
    var workCancelled = false
    var closed = false

    override fun schedule(block: () -> Unit): LoginWork = object : LoginWork {
        override fun cancel() {
            workCancelled = true
        }
    }

    override fun dispatch(block: () -> Unit) = block()

    override fun close() {
        closed = true
    }
}

private object NoOpWork : LoginWork {
    override fun cancel() = Unit
}
