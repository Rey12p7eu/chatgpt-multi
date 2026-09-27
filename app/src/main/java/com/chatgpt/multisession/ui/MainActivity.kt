package com.chatgpt.multisession.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
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

class MainActivity : ComponentActivity() {

    private lateinit var repository: AccountRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var downloadHandler: DownloadHandler
    private lateinit var permissionHandler: PermissionHandler

    private val webViewCache = mutableMapOf<String, WebView>()
    private val MAX_CACHE = 3

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val uris = if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->

                    val clip = data.clipData

                    if (clip != null) {
                        Array(clip.itemCount) { i ->
                            clip.getItemAt(i).uri
                        }
                    } else {
                        data.data?.let {
                            arrayOf(it)
                        } ?: emptyArray()
                    }
                } ?: emptyArray()
            } else {
                emptyArray()
            }

            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
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
    fun MainScreen() {

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
                                Icons.Default.Menu,
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
                                Icons.Default.Refresh,
                                contentDescription = "Reload"
                            )
                        }

                        var menu by remember {
                            mutableStateOf(false)
                        }

                        IconButton(
                            onClick = {
                                menu = true
                            }
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More"
                            )
                        }

                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = {
                                menu = false
                            }
                        ) {

                            DropdownMenuItem(
                                text = {
                                    Text("Open in browser")
                                },
                                onClick = {

                                    menu = false

                                    currentWebView?.url?.let { url ->
                                        startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(url)
                                            )
                                        )
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("Clear cache (active)")
                                },
                                onClick = {

                                    menu = false

                                    currentWebView?.let {
                                        WebViewProfileManager.clearCacheOnly(it)
                                    }

                                    Toast.makeText(
                                        context,
                                        "Cache cleared",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text("Clear session (active)")
                                },
                                onClick = {

                                    menu = false

                                    currentWebView?.let {
                                        WebViewProfileManager
                                            .clearSessionForAccount(it)

                                        it.loadUrl(
                                            "https://chatgpt.com/"
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        ) { padding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {

                if (activeAccount != null) {

                    val account = activeAccount

                    SwipeAccountSwitcher(
                        modifier = Modifier.fillMaxSize(),

                        onSwipeLeft = {

                            val idx =
                                accounts.indexOf(account)

                            if (
                                idx >= 0 &&
                                accounts.size > 1
                            ) {

                                val next =
                                    accounts[
                                        (idx + 1) % accounts.size
                                    ]

                                scope.launch {

                                    repository.setActive(
                                        next.id
                                    )

                                    switchMessage =
                                        "Switched to: ${next.displayName}"
                                }
                            }
                        },

                        onSwipeRight = {

                            val idx =
                                accounts.indexOf(account)

                            if (
                                idx >= 0 &&
                                accounts.size > 1
                            ) {

                                val prev =
                                    accounts[
                                        if (idx - 1 < 0) {
                                            accounts.size - 1
                                        } else {
                                            idx - 1
                                        }
                                    ]

                                scope.launch {

                                    repository.setActive(
                                        prev.id
                                    )

                                    switchMessage =
                                        "Switched to: ${prev.displayName}"
                                }
                            }
                        }
                    ) {

                        key(account.id) {

                            AndroidView(
                                modifier =
                                    Modifier.fillMaxSize(),

                                factory = { ctx ->

                                    val webView =
                                        getOrCreateWebView(
                                            ctx,
                                            account
                                        )

                                    currentWebView =
                                        webView

                                    if (
                                        webView.url
                                            .isNullOrEmpty()
                                    ) {
                                        webView.loadUrl(
                                            "https://chatgpt.com/"
                                        )
                                    }

                                    webView
                                },

                                update = { webView ->
                                    currentWebView =
                                        webView
                                }
                            )
                        }
                    }
                }

                if (isOffline) {

                    Card(
                        modifier = Modifier
                            .align(
                                Alignment.BottomCenter
                            )
                            .padding(16.dp),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .errorContainer
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier.padding(12.dp),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {

                            Text(
                                text =
                                    "Offline - check connection",

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onErrorContainer
                            )

                            TextButton(
                                onClick = {
                                    currentWebView
                                        ?.reload()
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible =
                        switchMessage != null,

                    enter =
                        fadeIn() +
                            slideInVertically(),

                    exit =
                        fadeOut() +
                            slideOutVertically(),

                    modifier = Modifier
                        .align(
                            Alignment.TopCenter
                        )
                        .padding(top = 8.dp)
                ) {

                    switchMessage?.let {

                        Card(
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .secondaryContainer
                                )
                        ) {

                            Text(
                                it,
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            16.dp,
                                        vertical =
                                            8.dp
                                    )
                            )
                        }

                        LaunchedEffect(it) {

                            kotlinx.coroutines.delay(
                                1500
                            )

                            switchMessage =
                                null
                        }
                    }
                }
            }
        }

        if (showAccounts) {

            AccountManagerSheet(
                accounts = accounts,
                activeId = activeId,

                onDismiss = {
                    showAccounts = false
                },

                onSelect = { acc ->

                    lifecycleScope.launch {

                        repository.setActive(
                            acc.id
                        )

                        showAccounts =
                            false
                    }
                },

                onAdd = { name ->

                    lifecycleScope.launch {

                        val newAcc =
                            repository.addAccount(
                                name
                            )

                        repository.setActive(
                            newAcc.id
                        )
                    }
                },

                onRename = { acc, newName ->

                    lifecycleScope.launch {

                        repository.rename(
                            acc.id,
                            newName
                        )
                    }
                },

                onDelete = { acc ->

                    lifecycleScope.launch {

                        if (accounts.size <= 1) {

                            Toast.makeText(
                                context,
                                "Cannot delete last account",
                                Toast.LENGTH_SHORT
                            ).show()

                            return@launch
                        }

                        webViewCache[acc.id]
                            ?.let { wv ->

                                try {
                                    wv.destroy()
                                } catch (_: Exception) {
                                }

                                webViewCache
                                    .remove(acc.id)
                            }

                        WebViewProfileManager
                            .deleteProfileData(
                                context,
                                acc
                            )

                        repository.delete(
                            acc.id
                        )

                        if (
                            activeId ==
                            acc.id
                        ) {

                            val remaining =
                                repository
                                    .accountsFlow
                                    .first()

                            remaining
                                .firstOrNull()
                                ?.let {

                                    repository
                                        .setActive(
                                            it.id
                                        )
                                }
                        }
                    }
                },

                onClearCache = { acc ->

                    webViewCache[acc.id]
                        ?.let {

                            WebViewProfileManager
                                .clearCacheOnly(
                                    it
                                )
                        }

                    Toast.makeText(
                        context,
                        "Cache cleared: ${acc.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                },

                onClearSession = { acc ->

                    webViewCache[acc.id]
                        ?.let {

                            WebViewProfileManager
                                .clearSessionForAccount(
                                    it
                                )

                            it.loadUrl(
                                "https://chatgpt.com/"
                            )
                        }

                    Toast.makeText(
                        context,
                        "Session cleared: ${acc.displayName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun getOrCreateWebView(
        context: android.content.Context,
        account: AccountProfile
    ): WebView {

        webViewCache[account.id]
            ?.let {
                return it
            }

        if (
            webViewCache.size >=
            MAX_CACHE
        ) {

            val toRemove =
                webViewCache
                    .keys
                    .firstOrNull()

            if (toRemove != null) {

                val oldWebView =
                    webViewCache
                        .remove(toRemove)

                oldWebView?.let {

                    try {
                        it.stopLoading()
                    } catch (_: Exception) {
                    }

                    try {
                        it.onPause()
                    } catch (_: Exception) {
                    }

                    try {
                        it.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val webView =
            WebViewProfileManager
                .createWebViewForAccount(
                    context,
                    account
                )

        webView.webViewClient =
            ChatGPTWebViewClient(

                onPageFinished = { _ ->

                    lifecycleScope.launch {

                        repository
                            .updateLastUsed(
                                account.id
                            )
                    }
                },

                onRenderGone = {

                    try {

                        webView.reload()

                    } catch (_: Exception) {
                    }
                }
            )

        webView.webChromeClient =
            ChatGPTChromeClient(

                activity = this,

                permissionHandler =
                    permissionHandler,

                fileChooserLauncher = {
                        callback,
                        params ->

                    filePathCallback =
                        callback

                    val intent =
                        params.createIntent()

                    try {

                        filePickerLauncher
                            .launch(intent)

                    } catch (_: Exception) {

                        val fallback =
                            Intent(
                                Intent.ACTION_GET_CONTENT
                            ).apply {

                                addCategory(
                                    Intent.CATEGORY_OPENABLE
                                )

                                type =
                                    "*/*"

                                putExtra(
                                    Intent.EXTRA_ALLOW_MULTIPLE,
                                    params.mode ==
                                        android.webkit
                                            .WebChromeClient
                                            .FileChooserParams
                                            .MODE_OPEN_MULTIPLE
                                )
                            }

                        filePickerLauncher
                            .launch(fallback)
                    }
                }
            )

        webView.setDownloadListener {
                url,
                userAgent,
                contentDisposition,
                mimeType,
                _ ->

            downloadHandler
                .handleDownload(
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType
                )
        }

        webViewCache[account.id] =
            webView

        if (
            webView.url
                .isNullOrEmpty()
        ) {

            webView.loadUrl(
                "https://chatgpt.com/"
            )
        }

        return webView
    }

    override fun onBackPressed() {

        val activeId =
            runCatching {

                kotlinx.coroutines
                    .runBlocking {

                        repository
                            .activeIdFlow
                            .first()
                    }
            }.getOrNull()

        val webView =
            activeId?.let {
                webViewCache[it]
            }

        if (
            webView?.canGoBack() ==
            true
        ) {

            webView.goBack()

        } else {

            super.onBackPressed()
        }
    }

    override fun onDestroy() {

        webViewCache
            .values
            .forEach {

                try {
                    it.onPause()
                } catch (_: Exception) {
                }
            }

        super.onDestroy()
    }
}