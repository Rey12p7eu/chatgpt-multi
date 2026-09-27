
package com.chatgpt.multisession.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil

class DownloadHandler(private val context: Context) {
    fun handleDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
        val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("User-Agent", userAgent)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            setDescription("Downloading $filename")
            setTitle(filename)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    }
}
