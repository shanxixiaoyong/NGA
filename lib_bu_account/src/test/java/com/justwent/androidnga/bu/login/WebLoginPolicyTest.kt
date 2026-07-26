package com.justwent.androidnga.bu.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebLoginPolicyTest {
    @Test
    fun allowsOnlyExactHttpsNgaHosts() {
        assertTrue(WebLoginPolicy.isAllowed("https://ngabbs.com/nuke.php"))
        assertTrue(WebLoginPolicy.isAllowed("https://bbs.nga.cn/"))
        assertTrue(WebLoginPolicy.isAllowed("https://BBS.NGACN.CC:443/path"))

        assertFalse(WebLoginPolicy.isAllowed("http://bbs.nga.cn/"))
        assertFalse(WebLoginPolicy.isAllowed("https://bbs.nga.cn.example.com/"))
        assertFalse(WebLoginPolicy.isAllowed("https://example.com/?next=bbs.nga.cn"))
        assertFalse(WebLoginPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(WebLoginPolicy.isAllowed("file:///tmp/page.html"))
        assertFalse(WebLoginPolicy.isAllowed("https://user@bbs.nga.cn/"))
        assertFalse(WebLoginPolicy.isAllowed("https://bbs.nga.cn:8443/"))
        assertFalse(WebLoginPolicy.isAllowed(null))
    }

    @Test
    fun cookieParserUsesExactNamesAndKeepsEqualsInValues() {
        val cookies = WebLoginPolicy.parseCookies(
            "notngaPassportUid=bad; ngaPassportUid=42; ngaPassportCid=session=value; malformed",
        )

        assertEquals("42", cookies["ngaPassportUid"])
        assertEquals("session=value", cookies["ngaPassportCid"])
        assertEquals("bad", cookies["notngaPassportUid"])
        assertFalse(cookies.containsKey("malformed"))
    }

    @Test
    fun loginSessionDecodesUsernameAndFallsBackToUid() {
        assertEquals(
            WebLoginPolicy.LoginSession("42", "session=value", "reader name"),
            WebLoginPolicy.extractLoginSession(
                "ngaPassportUid=42; ngaPassportCid=session=value; " +
                    "ngaPassportUrlencodedUname=reader%2520name",
            ),
        )
        assertEquals(
            WebLoginPolicy.LoginSession("42", "session-value", "42"),
            WebLoginPolicy.extractLoginSession(
                "ngaPassportUid=42; ngaPassportCid=session-value; " +
                    "ngaPassportUrlencodedUname=%broken",
            ),
        )
        assertEquals("", WebLoginPolicy.decodeUsername("%broken"))
    }

    @Test
    fun accountResultRequiresBoundedPositiveAsciiUidAndCookieSafeCid() {
        assertTrue(WebLoginPolicy.isValidSession("42", "session=value"))
        assertTrue(WebLoginPolicy.isValidSession("1".repeat(32), "x".repeat(4096)))

        assertFalse(WebLoginPolicy.isValidSession("", "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("0", "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("000", "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("reader", "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("１２", "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("1".repeat(33), "session-value"))
        assertFalse(WebLoginPolicy.isValidSession("42", ""))
        assertFalse(WebLoginPolicy.isValidSession("42", "x".repeat(4097)))
        assertFalse(WebLoginPolicy.isValidSession("42", "value; injected=1"))
        assertFalse(WebLoginPolicy.isValidSession("42", "value,other"))
        assertFalse(WebLoginPolicy.isValidSession("42", "value\\other"))
        assertFalse(WebLoginPolicy.isValidSession("42", "value\nheader"))
        assertFalse(WebLoginPolicy.isValidSession("42", "value header"))
    }

    @Test
    fun loginSessionRejectsMissingOrInvalidCookieValues() {
        assertNull(WebLoginPolicy.extractLoginSession("ngaPassportUid=42"))
        assertNull(
            WebLoginPolicy.extractLoginSession(
                "ngaPassportUid=42; ngaPassportCid=value\"injected",
            ),
        )
        assertNull(
            WebLoginPolicy.extractLoginSession(
                "ngaPassportUid=42; ngaPassportCid= session-value",
            ),
        )
        assertNull(
            WebLoginPolicy.extractLoginSession(
                "notngaPassportUid=42; ngaPassportCid=session-value",
            ),
        )
    }

    @Test
    fun pageCompletionNeverChecksPersistedCookies() {
        assertFalse(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.PAGE_FINISHED,
                WebLoginPolicy.LOGIN_URL,
            ),
        )
    }

    @Test
    fun exactLegacySuccessSignalAndUserExitCanCheckCookies() {
        assertTrue(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.LOGIN_CONFIRM,
                WebLoginPolicy.LOGIN_URL,
                "登录成功 是否返回首页",
            ),
        )
        assertFalse(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.LOGIN_CONFIRM,
                WebLoginPolicy.LOGIN_URL,
                "普通确认信息",
            ),
        )
        assertFalse(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.LOGIN_CONFIRM,
                WebLoginPolicy.LOGIN_URL,
                "登录成功 是否返回首页（已处理）",
            ),
        )
        assertFalse(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.LOGIN_CONFIRM,
                "https://bbs.nga.cn/nuke.php?__lib=login&__act=account&login",
                "登录成功 是否返回首页",
            ),
        )
        assertTrue(
            WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.USER_EXIT,
                "https://bbs.nga.cn/thread.php?fid=-7",
            ),
        )
    }

    @Test
    fun completionTriggersRejectNonAllowedOrigins() {
        WebLoginPolicy.CompletionTrigger.values().forEach { trigger ->
            assertFalse(
                WebLoginPolicy.shouldCheckCookies(
                    trigger,
                    "https://example.com/login",
                    "登录成功 是否返回首页",
                ),
            )
        }
    }
}
