package com.justwen.androidnga.base.network.login

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class NgaLoginClient internal constructor(
    private val baseUrl: HttpUrl,
    private val clientBuilder: OkHttpClient.Builder,
) {
    constructor() : this(
        requireNotNull(HttpUrl.parse(BASE_URL)),
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS),
    )

    fun createSession(): NgaLoginSession = Session(baseUrl, clientBuilder)

    internal class Session(
        private val baseUrl: HttpUrl,
        builder: OkHttpClient.Builder,
    ) : NgaLoginSession {
        private val cookieJar = TemporaryCookieJar()
        private val client = builder
            .cookieJar(cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        private val random = SecureRandom()
        private val operationLock = Any()
        private var operationGeneration = 0
        private var activeCall: Call? = null
        private var captchaRequestId: String? = null

        override fun submit(
            account: String,
            accountType: NgaLoginAccountType,
            password: CharSequence,
            captcha: CharSequence?,
        ): NgaLoginResult {
            val generation = currentGeneration()
            if (account.isBlank() || password.isBlank()) {
                return NgaLoginResult.Failure(
                    NgaLoginFailureKind.INVALID_CREDENTIALS,
                    "请输入账号和密码",
                )
            }
            val encrypted = try {
                NgaLoginPasswordCipher.encrypt(password)
            } catch (_: Exception) {
                return NgaLoginResult.Failure(
                    NgaLoginFailureKind.PROTOCOL_CHANGED,
                    "密码无法加密，请改用网页登录",
                    retryable = false,
                )
            }

            val form = FormBody.Builder()
                .add("__lib", "login")
                .add("__output", "1")
                .add("__act", "login")
                .add("name", account.trim())
                .add("type", accountType.wireValue)
                .add("password", encrypted)
                .add("__inchst", "UTF-8")
                .apply {
                    if (captcha != null) {
                        val requestId = captchaRequestId
                            ?: return NgaLoginResult.Failure(
                                NgaLoginFailureKind.CAPTCHA_REQUIRED,
                                "请先加载图形验证码",
                            )
                        add("rid", requestId)
                        add("captcha", captcha.toString().trim())
                        add("prid", newRequestId("P"))
                    }
                }
                .build()

            val request = Request.Builder()
                .url(requireNotNull(baseUrl.resolve("nuke.php")))
                .header("User-Agent", USER_AGENT)
                .header("Referer", requireNotNull(baseUrl.resolve(LOGIN_PAGE)).toString())
                .post(form)
                .build()
            val responseResult = execute(request, LOGIN_RESPONSE_LIMIT, generation)
            if (responseResult is ExchangeResult.Failure) return responseResult.failure

            val parsed = NgaLoginResponseParser.parse((responseResult as ExchangeResult.Success).bytes)
            if (parsed !is NgaLoginResult.Success) return parsed
            return completeQuickCookie(parsed, generation)
        }

        override fun refreshCaptcha(): NgaCaptchaResult {
            val id = newRequestId("login")
            val generation = synchronized(operationLock) {
                captchaRequestId = id
                operationGeneration
            }
            val url = requireNotNull(baseUrl.resolve("login_check_code.php"))
                .newBuilder()
                .addQueryParameter("id", id)
                .addQueryParameter("from", "login")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", requireNotNull(baseUrl.resolve(LOGIN_PAGE)).toString())
                .get()
                .build()
            return when (val result = execute(request, CAPTCHA_RESPONSE_LIMIT, generation)) {
                is ExchangeResult.Failure -> NgaCaptchaResult.Failure(
                    result.failure.copy(
                        kind = NgaLoginFailureKind.CAPTCHA_IMAGE,
                        message = "验证码加载失败，请重试",
                    ),
                )
                is ExchangeResult.Success -> if (result.bytes.isEmpty()) {
                    NgaCaptchaResult.Failure(
                        NgaLoginResult.Failure(
                            NgaLoginFailureKind.CAPTCHA_IMAGE,
                            "验证码图片为空，请刷新重试",
                        ),
                    )
                } else {
                    NgaCaptchaResult.Success(result.bytes)
                }
            }
        }

        override fun cancel() {
            val call = synchronized(operationLock) {
                operationGeneration++
                captchaRequestId = null
                activeCall.also { activeCall = null }
            }
            cookieJar.clear()
            call?.cancel()
            client.connectionPool().evictAll()
        }

        private fun completeQuickCookie(
            success: NgaLoginResult.Success,
            generation: Int,
        ): NgaLoginResult {
            val form = FormBody.Builder()
                .add("uid", success.uid)
                .add("cid", success.cid)
                .build()
            val request = Request.Builder()
                .url(requireNotNull(baseUrl.resolve("nuke.php?__lib=login&__act=login_set_cookie_quick&__output=9")))
                .header("User-Agent", USER_AGENT)
                .header("Referer", requireNotNull(baseUrl.resolve(LOGIN_PAGE)).toString())
                .post(form)
                .build()
            return when (val completion = execute(request, QUICK_RESPONSE_LIMIT, generation)) {
                is ExchangeResult.Success -> success
                is ExchangeResult.Failure -> completion.failure
            }
        }

        private fun execute(request: Request, limit: Int, generation: Int): ExchangeResult {
            val call = synchronized(operationLock) {
                if (generation != operationGeneration) return cancelledExchange()
                client.newCall(request).also { activeCall = it }
            }
            return try {
                call.execute().use { response ->
                    when {
                        response.code() in 300..399 -> ExchangeResult.Failure(
                            NgaLoginResult.Failure(
                                NgaLoginFailureKind.REDIRECTED,
                                "登录服务返回了未允许的跳转",
                                response.code(),
                                retryable = false,
                            ),
                        )
                        !response.isSuccessful -> ExchangeResult.Failure(
                            NgaLoginResult.Failure(
                                if (response.code() == 429) NgaLoginFailureKind.RATE_LIMITED else NgaLoginFailureKind.HTTP,
                                if (response.code() == 429) "请求过于频繁，请稍后再试" else "登录服务暂时不可用 (${response.code()})",
                                response.code(),
                            ),
                        )
                        else -> ExchangeResult.Success(readBounded(response, limit))
                    }
                }
            } catch (_: ResponseTooLargeException) {
                ExchangeResult.Failure(
                    NgaLoginResult.Failure(
                        NgaLoginFailureKind.MALFORMED_RESPONSE,
                        "登录服务返回的数据过大",
                        retryable = false,
                    ),
                )
            } catch (_: SocketTimeoutException) {
                ExchangeResult.Failure(
                    NgaLoginResult.Failure(NgaLoginFailureKind.TIMEOUT, "连接超时，请检查网络后重试"),
                )
            } catch (_: IOException) {
                ExchangeResult.Failure(
                    NgaLoginResult.Failure(NgaLoginFailureKind.NETWORK, "网络连接失败，请检查网络后重试"),
                )
            } finally {
                synchronized(operationLock) {
                    if (activeCall === call) activeCall = null
                }
            }
        }

        private fun currentGeneration(): Int = synchronized(operationLock) { operationGeneration }

        private fun cancelledExchange() = ExchangeResult.Failure(
            NgaLoginResult.Failure(NgaLoginFailureKind.NETWORK, "登录请求已取消"),
        )

        private fun readBounded(response: Response, limit: Int): ByteArray {
            val body = response.body() ?: return ByteArray(0)
            val declaredLength = body.contentLength()
            if (declaredLength > limit) throw ResponseTooLargeException()
            val output = ByteArrayOutputStream(minOf(limit, 8192))
            val buffer = ByteArray(8192)
            body.byteStream().use { input ->
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > limit) throw ResponseTooLargeException()
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        }

        private fun newRequestId(prefix: String): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            return prefix + bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private sealed class ExchangeResult {
        data class Success(val bytes: ByteArray) : ExchangeResult()
        data class Failure(val failure: NgaLoginResult.Failure) : ExchangeResult()
    }

    private class ResponseTooLargeException : IOException()

    private class TemporaryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
            received.forEach { incoming ->
                cookies.removeAll { it.name() == incoming.name() && it.domain() == incoming.domain() && it.path() == incoming.path() }
                if (incoming.expiresAt() > System.currentTimeMillis()) cookies += incoming
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookies.removeAll { it.expiresAt() <= System.currentTimeMillis() }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        fun clear() = cookies.clear()
    }

    companion object {
        private const val BASE_URL = "https://bbs.nga.cn/"
        private const val LOGIN_PAGE = "nuke.php?__lib=login&__act=account&login"
        private const val USER_AGENT = "NGA-Just-Works Android"
        private const val LOGIN_RESPONSE_LIMIT = 256 * 1024
        private const val CAPTCHA_RESPONSE_LIMIT = 1024 * 1024
        private const val QUICK_RESPONSE_LIMIT = 64 * 1024
    }
}
