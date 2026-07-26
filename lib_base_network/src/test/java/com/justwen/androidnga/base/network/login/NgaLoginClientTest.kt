package com.justwen.androidnga.base.network.login

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NgaLoginClientTest {
    private lateinit var server: MockWebServer
    private val charset = Charset.forName("GB18030")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun successUsesOneCredentialSubmitThenQuickCompletion() {
        server.enqueue(jsonResponse(successBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val session = client().createSession()

        val result = session.submit("reader", NgaLoginAccountType.EMAIL, "example-input")

        assertEquals(NgaLoginResult.Success("42", "session-value", "reader"), result)
        val login = server.takeRequest()
        assertEquals("/nuke.php", login.path)
        val fields = decodeForm(login.body.readUtf8())
        assertEquals("login", fields["__act"])
        assertEquals("mail", fields["type"])
        assertEquals("reader", fields["name"])
        assertNotNull(fields["password"])
        val quick = server.takeRequest()
        assertTrue(quick.path!!.contains("login_set_cookie_quick"))
        assertEquals("42", decodeForm(quick.body.readUtf8())["uid"])
    }

    @Test
    fun captchaRefreshAndResubmitReuseTemporarySession() {
        server.enqueue(
            jsonResponse("{\"error\":{\"0\":\"请输入验证码\"}}")
                .addHeader("Set-Cookie", "temporary=marker; Path=/"),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("image-bytes"))
        server.enqueue(jsonResponse("{\"error\":{\"0\":\"验证码错误\"}}"))
        val session = client().createSession()

        assertFailure(session.submit("reader", NgaLoginAccountType.USERNAME, "example-input"), NgaLoginFailureKind.CAPTCHA_REQUIRED)
        val image = session.refreshCaptcha()
        assertTrue(image is NgaCaptchaResult.Success)
        assertEquals("image-bytes", String((image as NgaCaptchaResult.Success).imageBytes))
        assertFailure(
            session.submit("reader", NgaLoginAccountType.USERNAME, "example-input", "ABC123"),
            NgaLoginFailureKind.CAPTCHA_INCORRECT,
        )

        server.takeRequest()
        val imageRequest = server.takeRequest()
        assertTrue(imageRequest.path!!.startsWith("/login_check_code.php?id=login"))
        assertTrue(imageRequest.getHeader("Cookie")!!.contains("temporary=marker"))
        val retryFields = decodeForm(server.takeRequest().body.readUtf8())
        assertEquals("ABC123", retryFields["captcha"])
        assertTrue(retryFields["rid"]!!.startsWith("login"))
    }

    @Test
    fun rejectsNonSuccessAndRedirectWithoutFollowing() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val first = client().createSession().submit("reader", NgaLoginAccountType.USER_ID, "example-input")
        assertFailure(first, NgaLoginFailureKind.HTTP)

        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.com/"))
        val second = client().createSession().submit("reader", NgaLoginAccountType.PHONE, "example-input")
        assertFailure(second, NgaLoginFailureKind.REDIRECTED)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun controlsCredentialMalformedAndMissingSessionResponses() {
        server.enqueue(jsonResponse("{\"error\":[\"账号或密码错误\",\"1\"]}"))
        val credentials = client().createSession()
            .submit("reader", NgaLoginAccountType.USERNAME, "example-input")
        assertFailure(credentials, NgaLoginFailureKind.INVALID_CREDENTIALS)

        server.enqueue(jsonResponse("not-json"))
        val malformed = client().createSession()
            .submit("reader", NgaLoginAccountType.USERNAME, "example-input")
        assertFailure(malformed, NgaLoginFailureKind.MALFORMED_RESPONSE)

        server.enqueue(jsonResponse("{\"data\":{\"3\":{\"uid\":\"42\"}}}"))
        val missingSession = client().createSession()
            .submit("reader", NgaLoginAccountType.USERNAME, "example-input")
        assertFailure(missingSession, NgaLoginFailureKind.PROTOCOL_CHANGED)
    }

    @Test
    fun timeoutIsClassified() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val builder = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .writeTimeout(100, TimeUnit.MILLISECONDS)
        val result = NgaLoginClient(server.url("/"), builder).createSession()
            .submit("reader", NgaLoginAccountType.USERNAME, "example-input")

        assertFailure(result, NgaLoginFailureKind.TIMEOUT)
    }

    @Test
    fun cancellationStopsCurrentExchangeAndSessionCanBeReused() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val session = client().createSession()
        val firstResult = AtomicReference<NgaLoginResult>()
        val worker = Thread {
            firstResult.set(session.submit("reader", NgaLoginAccountType.USERNAME, "example-input"))
        }
        worker.start()
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))

        session.cancel()
        worker.join(2_000)

        assertFalse(worker.isAlive)
        assertFailure(firstResult.get(), NgaLoginFailureKind.NETWORK)
        assertEquals(1, server.requestCount)

        server.enqueue(jsonResponse(successBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        assertTrue(
            session.submit("reader", NgaLoginAccountType.USERNAME, "example-input") is NgaLoginResult.Success,
        )
    }

    @Test
    fun accountTypeWireMappingIsClosed() {
        assertEquals(listOf("", "mail", "id", "phone"), NgaLoginAccountType.values().map { it.wireValue })
        assertFalse(NgaLoginAccountType.values().map { it.wireValue }.contains("nickname"))
    }

    private fun client(): NgaLoginClient = NgaLoginClient(server.url("/"), OkHttpClient.Builder())

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "text/javascript; charset=GBK")
        .setBody(okio.Buffer().write(body.toByteArray(charset)))

    private fun successBody() =
        "window.script_muti_get_var_store={\"data\":[null,null,null,{\"uid\":\"42\",\"token\":\"session-value\",\"username\":\"reader\"}]}"

    private fun decodeForm(body: String): Map<String, String> = body.split('&').associate { item ->
        val separator = item.indexOf('=')
        URLDecoder.decode(item.substring(0, separator), "UTF-8") to
            URLDecoder.decode(item.substring(separator + 1), "UTF-8")
    }

    private fun assertFailure(result: NgaLoginResult, kind: NgaLoginFailureKind) {
        assertTrue(result is NgaLoginResult.Failure)
        assertEquals(kind, (result as NgaLoginResult.Failure).kind)
    }
}
