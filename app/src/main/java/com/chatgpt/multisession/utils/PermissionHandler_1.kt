
package com.chatgpt.multisession.utils

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

class PermissionHandler(private val activity: ComponentActivity) {
    private var callback: ((Boolean) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        callback?.invoke(allGranted)
        callback = null
    }

    fun requestPermissions(permissions: Array<String>, onResult: (Boolean) -> Unit) {
        callback = onResult
        launcher.launch(permissions)
    }
}
