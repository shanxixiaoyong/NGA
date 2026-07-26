package com.justwent.androidnga.bu.login

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.base.activity.ARouterConstants
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import gov.anzong.androidnga.common.util.NgaRequestPolicy
import gov.anzong.androidnga.base.util.ToastUtils

@Route(path = ARouterConstants.ACTIVITY_LOGIN)
class LoginActivity : BaseComposeActivity() {

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this)[LoginViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ToastUtils.info("不支持QQ和微博登录");
    }

    @Composable
    override fun ContentView() {
        LoginWebView(url = LoginViewModel.URL_LOGIN, onLoginCallback = {
            if (viewModel.checkLoginResult(it)) {
                setResult(RESULT_OK)
                clearWebViewCookies()
                finish()
            }
        })
    }

    private fun clearWebViewCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    override fun finish() {
        if (viewModel.checkLoginResult()) {
            setResult(RESULT_OK)
        }
        super.finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    fun LoginWebView(url: String, onLoginCallback: (String) -> Unit) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return true
                            if (NgaRequestPolicy.isTrustedHttps(uri.scheme, uri.host)) {
                                viewModel.currentUrl = uri.toString()
                                return false
                            }
                            // Do not let an untrusted redirect run inside the login WebView.
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            if (NgaRequestPolicy.isTrustedHttps(
                                    Uri.parse(loadedUrl).scheme,
                                    Uri.parse(loadedUrl).host
                                )
                            ) {
                                viewModel.currentUrl = loadedUrl
                                onLoginCallback(loadedUrl)
                            }
                            super.onPageFinished(view, loadedUrl)
                        }
                    }
                    getSettings().apply {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    loadUrl(url)
                }
            }, update = { })
    }

}
