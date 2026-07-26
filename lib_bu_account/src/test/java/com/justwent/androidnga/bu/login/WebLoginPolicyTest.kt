package com.justwent.androidnga.bu.login

import com.justwen.androidnga.base.network.login.NgaLoginSessionContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val cookies = WebLoginActivity.parseCookies(
            "notngaPassportUid=bad; ngaPassportUid=42; ngaPassportCid=session=value",
        )

        assertEquals("42", cookies["ngaPassportUid"])
        assertEquals("session=value", cookies["ngaPassportCid"])
        assertEquals("bad", cookies["notngaPassportUid"])
    }

    @Test
    fun usernameDecoderHandlesDoubleEncodingAndMalformedInput() {
        assertEquals("reader name", WebLoginActivity.decodeUsername("reader%2520name"))
        assertEquals("", WebLoginActivity.decodeUsername("%broken"))
    }

    @Test
    fun accountResultRequiresPositiveUidAndCookieSafeCid() {
        assertTrue(NgaLoginSessionContract.isValid("42", "session=value"))
        assertFalse(NgaLoginSessionContract.isValid("0", "session-value"))
        assertFalse(NgaLoginSessionContract.isValid("reader", "session-value"))
        assertFalse(NgaLoginSessionContract.isValid("42", "value; injected=1"))
        assertFalse(NgaLoginSessionContract.isValid("42", "value\nheader"))
    }
}
