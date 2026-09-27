
package com.chatgpt.multisession.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.chatgpt.multisession.ChatGPTApp
import com.chatgpt.multisession.data.AccountProfile
import com.chatgpt.multisession.data.AccountRepository
import com.chatgpt.multisession.utils.DownloadHandler
import com.chatgpt.multisession.utils.NetworkMonitor
import com.chatgpt.multisession.utils.PermissionHandler
import com.chatgpt.multisession.webview.ChatGPTChromeClient
import com.chatgpt.multisession.webview.ChatGPTWebViewClient
import com.chatgpt.multisession.webview.WebViewProfileManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var repository: AccountRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var downloadHandler: DownloadHandler
    private lateinit var permissionHandler: PermissionHandler

    private val webViewCache = mutableMapOf<String, WebView>()
    private val maxCache = 3

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            val uris: Array<Uri> =
                if (result.resultCode == RESULT_OK) {
                    val data = result.data

                    if (data?.clipData != null) {
                        val clipData = data.clipData!!
                        Array(clipData.itemCount) { index ->
                            clipData.getItemAt(index).uri
                        }
                    } else if (data?.data != null) {
                        arrayOf(data.data!!)
                    } else {
                        emptyArray()
                    }
                } else {
                    emptyArray()
                }

            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ChatGPTApp

        repository = AccountRepository(app.accountStorage)
        networkMonitor = NetworkMonitor(this)
        downloadHandler = DownloadHandler(this)
        permissionHandler = PermissionHandler(this)

        setContent {
            MaterialTheme(
                colorScheme =
                    if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    }
            ) {
                MainScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {

        val context = LocalContext.current

        val accounts by repository.accountsFlow.collectAsState(
            initial = emptyList()
        )

        val activeId by repository.activeIdFlow.collectAsState(
            initial = null
        )

        var showAccounts by remember {
            mutableStateOf(false)
        }

        var switchMessage by remember {
            mutableStateOf<String?>(null)
        }

        var isOffline by remember {
            mutableStateOf(false)
        }

        var currentWebView by remember {
            mutableStateOf<WebView?>(null)
        }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            networkMonitor.isOnline.collect { online ->
                isOffline = !online
            }
        }

        LaunchedEffect(accounts) {
            if (accounts.isEmpty()) {
                repository.addAccount("Personal")
            }
        }

        val activeAccount =
            accounts.find { it.id == activeId }
                ?: accounts.firstOrNull()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            activeAccount?.displayName
                                ?: "ChatGPT Multi"
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                showAccounts = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Accounts"
                            )
                        }
                    },
                    actions = {

                        IconButton(
                            onClick = {
                                currentWebView?.reload()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload"
                            )
                        }

                        var menuExpanded by remember {
                            mutableStateOf(false)
                        }

                        IconButton(
                            onClick = {
                                menuExpanded = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = {
                                menuExpanded = false
                            }
                        ) {

                            DropdownMenuItem(
                                text = {
                                    Text("Open in browser")
                                },
                                onClick = {
                                    menuExpanded = false

                                    currentWebView?.url?.let { url ->
                                        startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse
