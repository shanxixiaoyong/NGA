package com.justwent.androidnga.bu.login

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import com.justwent.androidnga.bu.UserManager
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

class LoginViewModel : ViewModel() {

    companion object {

        const val URL_LOGIN = "https://ngabbs.com/nuke.php?__lib=login&__act=account&login"

        const val TAG_UID: String = "ngaPassportUid"

        const val TAG_CID: String = "ngaPassportCid"

        const val TAG_USER_NAME: String = "ngaPassportUrlencodedUname"
    }

    private var loginResult: Boolean = false

    var currentUrl: String = URL_LOGIN

    fun checkLoginResult(url: String = currentUrl): Boolean {
        if (loginResult) {
            return true
        }
        val cookieStr = CookieManager.getInstance().getCookie(url)
        cookieStr?.let {
            loginResult = parseCookie(it)
        }
        return loginResult
    }

    private fun parseCookie(cookies: String): Boolean {
        var uid: String? = null
        var cid: String? = null
        var userName: String? = null

        for (item in cookies.split(";".toRegex())) {
            val cookie = item.trim()
            if (cookie.startsWith("$TAG_UID=")) {
                uid = cookie.substring(TAG_UID.length + 1)
            } else if (cookie.startsWith("$TAG_CID=")) {
                cid = cookie.substring(TAG_CID.length + 1)
            } else if (cookie.startsWith("$TAG_USER_NAME=")) {
                userName = cookie.substring(TAG_USER_NAME.length + 1)
                try {
                    // 这里需要解析两遍，不是bug
                    userName = URLDecoder.decode(userName, "gbk")
                    userName = URLDecoder.decode(userName, "gbk")
                } catch (_: UnsupportedEncodingException) {
                    return false
                }
            }
        }

        if (uid.isNullOrEmpty() || cid.isNullOrEmpty() || userName.isNullOrEmpty()) {
            return false
        }

        return UserManager.addUser(uid, cid, userName)
    }

}
