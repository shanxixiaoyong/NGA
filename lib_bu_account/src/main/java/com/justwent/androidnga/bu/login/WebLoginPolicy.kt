package com.justwent.androidnga.bu.login

import java.net.URI

object WebLoginPolicy {
    private val allowedHosts = setOf("ngabbs.com", "bbs.nga.cn", "bbs.ngacn.cc")

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
}
