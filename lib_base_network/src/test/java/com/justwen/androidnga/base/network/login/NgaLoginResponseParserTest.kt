package com.justwen.androidnga.base.network.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class NgaLoginResponseParserTest {
    private val charset = Charset.forName("GB18030")

    @Test
    fun parsesWrappedArraySuccess() {
        val result = parse(
            "window.script_muti_get_var_store={\"data\":[null,null,null,{\"uid\":\"42\",\"token\":\"session-value\",\"username\":\"reader\"}]};",
        )

        assertEquals(NgaLoginResult.Success("42", "session-value", "reader"), result)
    }

    @Test
    fun parsesPureObjectSuccess() {
        val result = parse(
            "{\"data\":{\"3\":{\"uid\":73,\"token\":\"session-value\",\"username\":\"reader\"}}}",
        )

        assertEquals(NgaLoginResult.Success("73", "session-value", "reader"), result)
    }

    @Test
    fun classifiesArrayAndObjectErrors() {
        val credentials = parse("{\"error\":[\"账号或密码错误\",\"1\"]}")
        val captcha = parse("{\"error\":{\"0\":\"请输入验证码\",\"1\":\"2\"}}")
        val wrongCaptcha = parse("{\"error\":{\"0\":\"验证码错误\"}}")

        assertFailureKind(credentials, NgaLoginFailureKind.INVALID_CREDENTIALS)
        assertFailureKind(captcha, NgaLoginFailureKind.CAPTCHA_REQUIRED)
        assertFailureKind(wrongCaptcha, NgaLoginFailureKind.CAPTCHA_INCORRECT)
    }

    @Test
    fun malformedAndMissingSessionFailClosed() {
        assertFailureKind(parse("not-json"), NgaLoginFailureKind.MALFORMED_RESPONSE)
        assertFailureKind(
            parse("{\"data\":{\"3\":{\"uid\":\"42\"}}}"),
            NgaLoginFailureKind.PROTOCOL_CHANGED,
        )
        assertFailureKind(
            parse("{\"data\":{\"3\":{\"uid\":\"0\",\"token\":\"session-value\"}}}"),
            NgaLoginFailureKind.PROTOCOL_CHANGED,
        )
        assertFailureKind(
            parse("{\"data\":{\"3\":{\"uid\":\"42\",\"token\":\"value; injected=1\"}}}"),
            NgaLoginFailureKind.PROTOCOL_CHANGED,
        )
    }

    @Test
    fun successfulResultDoesNotRenderSessionToken() {
        val result = parse(
            "{\"data\":{\"3\":{\"uid\":\"42\",\"token\":\"session-value\",\"username\":\"reader\"}}}",
        )

        assertTrue(result is NgaLoginResult.Success)
        assertFalse(result.toString().contains("session-value"))
        assertTrue(result.toString().contains("<redacted>"))
    }

    private fun parse(value: String): NgaLoginResult = NgaLoginResponseParser.parse(value.toByteArray(charset))

    private fun assertFailureKind(result: NgaLoginResult, expected: NgaLoginFailureKind) {
        assertTrue(result is NgaLoginResult.Failure)
        assertEquals(expected, (result as NgaLoginResult.Failure).kind)
    }
}
