package gov.anzong.androidnga.activity.fragment

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.MenuProvider
import com.justwen.androidnga.ui.fragment.WebViewFragment
import gov.anzong.androidnga.R
import gov.anzong.androidnga.common.util.NgaRequestPolicy

class ForumWebFragment : WebViewFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_webview, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.menu_open_by_browser) {
                    return startExternalBrowser(requireContext(), webView?.url)
                }
                return false
            }

        }, this)
    }

    private fun startExternalBrowser(context: Context, url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        if (uri.scheme != "https" && uri.scheme != "http") {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(uri)
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            return false
        }
    }

    private fun initWebView() {
        webView?.apply {
            val webSettings = settings
            webSettings.javaScriptEnabled = true
            webSettings.javaScriptCanOpenWindowsAutomatically = false
            webSettings.setSupportMultipleWindows(false)
            webSettings.allowFileAccess = false
            webSettings.allowContentAccess = false
            webSettings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webSettings.loadWithOverviewMode = true
            webSettings.textZoom = 100
            webSettings.setSupportZoom(true)
            webSettings.builtInZoomControls = true
            webSettings.useWideViewPort = true

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    if (NgaRequestPolicy.isTrustedHttps(uri.scheme, uri.host)) {
                        return false
                    }
                    return startExternalBrowser(view.context, uri.toString())
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.title?.let {
                        setTitle(it)
                    }
                    super.onPageFinished(view, url)
                }
            }

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initWebView()
        super.onViewCreated(view, savedInstanceState)
    }
}
