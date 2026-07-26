package com.justwent.androidnga.bu.login

import java.net.URI
import java.net.URLDecoder

object WebLoginPolicy {
    private val allowedHosts = setOf("ngabbs.com", "bbs.nga.cn", "bbs.ngacn.cc")

    enum class CompletionTrigger {
        PAGE_FINISHED,
        LOGIN_CONFIRM,
        USER_EXIT,
    }

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        val port = uri.port
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in allowedHosts &&
            uri.rawUserInfo == null &&
            (port == -1 || port == 443)
    }

    fun shouldCheckCookies(
        trigger: CompletionTrigger,
        url: String?,
        message: String? = null,
    ): Boolean {
        if (!isAllowed(url)) return false
        return when (trigger) {
            CompletionTrigger.PAGE_FINISHED -> false
            CompletionTrigger.USER_EXIT -> true
            CompletionTrigger.LOGIN_CONFIRM ->
                url == LOGIN_URL && message == LOGIN_SUCCESS_MESSAGE
        }
    }

    fun isValidSession(uid: String, cid: String): Boolean =
        uid.isNotEmpty() &&
            uid.length <= MAX_UID_LENGTH &&
            uid.all { it in '0'..'9' } &&
            uid.any { it != '0' } &&
            cid.isNotEmpty() &&
            cid.length <= MAX_CID_LENGTH &&
            cid.all(::isCookieValueCharacter)

    internal fun extractLoginSession(cookies: String): LoginSession? {
        val values = parseCookies(cookies)
        val uid = values[TAG_UID].orEmpty()
        val cid = values[TAG_CID].orEmpty()
        if (!isValidSession(uid, cid)) return null
        val username = decodeUsername(values[TAG_USERNAME].orEmpty()).ifBlank { uid }
        return LoginSession(uid, cid, username)
    }

    internal fun parseCookies(cookies: String): Map<String, String> = cookies
        .split(';')
        .mapNotNull { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = item.substring(0, separator).trim()
            if (name.isEmpty()) return@mapNotNull null
            name to item.substring(separator + 1)
        }
        .toMap()

    internal fun decodeUsername(value: String): String = try {
        URLDecoder.decode(URLDecoder.decode(value, USERNAME_CHARSET), USERNAME_CHARSET)
    } catch (_: Exception) {
        ""
    }

    private fun isCookieValueCharacter(value: Char): Boolean =
        value.code in 0x21..0x7e && value !in charArrayOf('"', ',', ';', '\\')

    internal data class LoginSession(
        val uid: String,
        val cid: String,
        val username: String,
    )

    const val LOGIN_URL = "https://ngabbs.com/nuke.php?__lib=login&__act=account&login"

    private const val MAX_UID_LENGTH = 32
    private const val MAX_CID_LENGTH = 4096
    private const val TAG_UID = "ngaPassportUid"
    private const val TAG_CID = "ngaPassportCid"
    private const val TAG_USERNAME = "ngaPassportUrlencodedUname"
    private const val USERNAME_CHARSET = "GB18030"
    private const val LOGIN_SUCCESS_MESSAGE = "登录成功 是否返回首页"
}
