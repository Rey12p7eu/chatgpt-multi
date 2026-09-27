
package com.chatgpt.multisession.webview

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.chatgpt.multisession.utils.PermissionHandler

class ChatGPTChromeClient(
    private val activity: Activity,
    private val permissionHandler: PermissionHandler,
    private val fileChooserLauncher: (ValueCallback<Array<Uri>>, FileChooserParams) -> Unit
) : WebChromeClient() {

    private var customView: android.view.View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        fileChooserLauncher(filePathCallback, fileChooserParams)
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = request.resources
        val needed = mutableListOf<String>()
        for (res in resources) {
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> needed.add(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> needed.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isEmpty()) {
            request.grant(resources)
        } else {
            permissionHandler.requestPermissions(toRequest.toTypedArray()) { granted ->
                if (granted) {
                    request.grant(resources)
                } else {
                    request.deny()
                }
            }
        }
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // Let default handling happen
        return super.onJsAlert(view, url, message, result)
    }

    // Fullscreen video support
    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
        customView = view
        customViewCallback = callback
        // You could add view to decor
        super.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        super.onHideCustomView()
    }
}
