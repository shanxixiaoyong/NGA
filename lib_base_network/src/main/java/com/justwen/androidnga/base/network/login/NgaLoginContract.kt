package com.justwen.androidnga.base.network.login

enum class NgaLoginAccountType(val wireValue: String) {
    USERNAME(""),
    EMAIL("mail"),
    USER_ID("id"),
    PHONE("phone"),
}

enum class NgaLoginFailureKind {
    INVALID_CREDENTIALS,
    CAPTCHA_REQUIRED,
    CAPTCHA_INCORRECT,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    HTTP,
    REDIRECTED,
    MALFORMED_RESPONSE,
    PROTOCOL_CHANGED,
    CAPTCHA_IMAGE,
}

sealed class NgaLoginResult {
    data class Success(
        val uid: String,
        val cid: String,
        val username: String,
    ) : NgaLoginResult() {
        override fun toString(): String = "Success(uid=$uid, cid=<redacted>, username=$username)"
    }

    data class Failure(
        val kind: NgaLoginFailureKind,
        val message: String,
        val httpStatus: Int? = null,
        val retryable: Boolean = true,
    ) : NgaLoginResult()
}

object NgaLoginSessionContract {
    private const val MAX_UID_LENGTH = 32
    private const val MAX_CID_LENGTH = 4096

    fun isValid(uid: String, cid: String): Boolean =
        uid.isNotEmpty() &&
            uid.length <= MAX_UID_LENGTH &&
            uid.all(Char::isDigit) &&
            uid.any { it != '0' } &&
            cid.isNotEmpty() &&
            cid.length <= MAX_CID_LENGTH &&
            cid.all(::isCookieValueCharacter)

    private fun isCookieValueCharacter(value: Char): Boolean =
        value.code in 0x21..0x7e && value !in charArrayOf('"', ',', ';', '\\')
}

sealed class NgaCaptchaResult {
    data class Success(val imageBytes: ByteArray) : NgaCaptchaResult()
    data class Failure(val failure: NgaLoginResult.Failure) : NgaCaptchaResult()
}

interface NgaLoginSession {
    fun submit(
        account: String,
        accountType: NgaLoginAccountType,
        password: CharSequence,
        captcha: CharSequence? = null,
    ): NgaLoginResult

    fun refreshCaptcha(): NgaCaptchaResult

    fun cancel()
}
