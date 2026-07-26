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
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import java.io.ByteArrayInputStream

class WebLoginActivity : BaseComposeActivity() {
    private var webView: WebView? = null
    private var completed = false
    private var currentAllowedUrl: String = WebLoginPolicy.LOGIN_URL

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
                            if (WebLoginPolicy.isAllowed(url)) currentAllowedUrl = requireNotNull(url)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onJsConfirm(
                            view: WebView?,
                            url: String?,
                            message: String?,
                            result: JsResult?,
                        ): Boolean {
                            if (!WebLoginPolicy.shouldCheckCookies(
                                    WebLoginPolicy.CompletionTrigger.LOGIN_CONFIRM,
                                    url,
                                    message,
                                )
                            ) {
                                return super.onJsConfirm(view, url, message, result)
                            }

                            result?.cancel()
                            if (completeFromCookies(url)) {
                                finish()
                            } else {
                                view?.postDelayed({
                                    if (completeFromCookies(url)) finish()
                                }, COOKIE_PROPAGATION_DELAY_MS)
                            }
                            return true
                        }
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    loadUrl(WebLoginPolicy.LOGIN_URL)
                }
            },
        )
    }

    private fun completeFromCookies(url: String?): Boolean {
        if (completed || !WebLoginPolicy.isAllowed(url)) return false
        val cookies = CookieManager.getInstance().getCookie(url) ?: return false
        val session = WebLoginPolicy.extractLoginSession(cookies) ?: return false
        completed = true
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_UID, session.uid)
                .putExtra(EXTRA_CID, session.cid)
                .putExtra(EXTRA_USERNAME, session.username),
        )
        return true
    }

    override fun finish() {
        if (WebLoginPolicy.shouldCheckCookies(
                WebLoginPolicy.CompletionTrigger.USER_EXIT,
                currentAllowedUrl,
            )
        ) {
            completeFromCookies(currentAllowedUrl)
        }
        super.finish()
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

        private const val COOKIE_PROPAGATION_DELAY_MS = 750L
    }
}
