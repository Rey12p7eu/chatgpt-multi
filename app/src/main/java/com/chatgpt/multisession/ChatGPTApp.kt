
package com.chatgpt.multisession

import android.app.Application
import android.os.Build
import android.webkit.WebView
import com.chatgpt.multisession.storage.AccountStorage

class ChatGPTApp : Application() {
    lateinit var accountStorage: AccountStorage
        private set

    override fun onCreate() {
        super.onCreate()
        // WebView data directory suffix must be set before any WebView is created
        // For multi-process isolation fallback (not needed for Profile API, but safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("main")
            } catch (e: Exception) {
                // ignore if already set
            }
        }
        accountStorage = AccountStorage(this)
    }
}
