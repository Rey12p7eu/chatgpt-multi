
package com.chatgpt.multisession.webview

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class ChatGPTWebViewClient(
    private val onPageStarted: (() -> Unit)? = null,
    private val onPageFinished: ((String) -> Unit)? = null,
    private val onRenderGone: (() -> Unit)? = null
) : WebViewClient() {

    private val allowedHosts = listOf(
        "chatgpt.com",
        "chat.openai.com",
        "auth.openai.com",
        "auth0.openai.com",
        "openai.com",
        "accounts.google.com",
        "apis.google.com",
        "www.google.com",
        "login.microsoftonline.com",
        "appleid.apple.com"
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val host = request.url.host ?: return false

        // Keep auth and chatgpt inside WebView
        if (allowedHosts.any { host.contains(it) }) {
            return false
        }

        // External links -> open in external browser if not main chat
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // For chatgpt.com links that open help, keep inside
            if (host.contains("help.openai.com")) return false
            // Let WebView handle first, but allow user to open external via menu
            return false
        }

        // Handle custom schemes (mailto, tel, intent)
        try {
            if (url.startsWith("intent://") || url.startsWith("mailto:") || url.startsWith("tel:")) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                view.context.startActivity(intent)
                return true
            }
        } catch (_: Exception) { }

        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted?.invoke()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url != null) onPageFinished?.invoke(url)
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Don't crash, try to recover
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (detail?.didCrash() == true) {
                onRenderGone?.invoke()
                // Return true to handle crash ourselves
                return true
            }
        }
        onRenderGone?.invoke()
        return true
    }
}
