package com.justwent.androidnga.bu.login

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.justwen.androidnga.base.network.login.NgaLoginSessionContract
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import java.io.ByteArrayInputStream
import java.net.URLDecoder

class WebLoginActivity : BaseComposeActivity() {
    private var webView: WebView? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "网页登录"
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun ContentView() {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    settings.apply {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = false
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return !WebLoginPolicy.isAllowed(request?.url?.toString())
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            if (WebLoginPolicy.isAllowed(request?.url?.toString())) {
                                return super.shouldInterceptRequest(view, request)
                            }
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            tryComplete(url)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            tryComplete(url)
                            view?.postDelayed({ tryComplete(url) }, 750)
                            result?.cancel()
                            return true
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    loadUrl(LOGIN_URL)
                }
            },
        )
    }

    private fun tryComplete(url: String?) {
        if (completed || !WebLoginPolicy.isAllowed(url)) return
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        val values = parseCookies(cookies)
        val uid = values[TAG_UID].orEmpty()
        val cid = values[TAG_CID].orEmpty()
        if (!NgaLoginSessionContract.isValid(uid, cid)) return
        completed = true
        val username = decodeUsername(values[TAG_USERNAME].orEmpty()).ifBlank { uid }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_UID, uid)
                .putExtra(EXTRA_CID, cid)
                .putExtra(EXTRA_USERNAME, username),
        )
        finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_UID = "login_uid"
        const val EXTRA_CID = "login_cid"
        const val EXTRA_USERNAME = "login_username"

        private const val LOGIN_URL = "https://bbs.nga.cn/nuke.php?__lib=login&__act=account&login"
        private const val TAG_UID = "ngaPassportUid"
        private const val TAG_CID = "ngaPassportCid"
        private const val TAG_USERNAME = "ngaPassportUrlencodedUname"

        internal fun parseCookies(cookies: String): Map<String, String> = cookies
            .split(';')
            .mapNotNull { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) null else item.substring(0, separator).trim() to item.substring(separator + 1).trim()
            }
            .toMap()

        internal fun decodeUsername(value: String): String = try {
            URLDecoder.decode(URLDecoder.decode(value, "GB18030"), "GB18030")
        } catch (_: IllegalArgumentException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
