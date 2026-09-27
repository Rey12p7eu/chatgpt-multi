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
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    // WebView cache: maksimal 3 akun aktif
    private val webViewCache = mutableMapOf<String, WebView>()
    private val MAX_CACHE = 3

    // File chooser
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

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
                colorScheme = if (
                    androidx.compose.foundation.isSystemInDarkTheme()
                ) {
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

        // Monitor jaringan
        LaunchedEffect(Unit) {
            networkMonitor.isOnline.collect { online ->
                isOffline = !online
            }
        }

        // Buat akun pertama otomatis
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
                                        WebViewProfileManager
                                            .clearCacheOnly(it)
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
                                        (idx + 1) %
                                            accounts.size
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

                        // Penting:
                        // hanya SATU AndroidView untuk satu WebView.
                        key(account.id) {

                            AndroidView(
                                modifier =
                                    Modifier.fillMaxSize(),

                                factory = { ctx ->

                                    getOrCreateWebView(
                                        ctx,
                                        account
                                    ).also { wv ->

                                        currentWebView = wv

                                        if (
                                            wv.url
                                                .isNullOrEmpty()
                                        ) {
                                            wv.loadUrl(
                                                "https://chatgpt.com/"
                                            )
                                        }
                                    }
                                },

                                update = { wv ->

                                    currentWebView = wv

                                    if (
                                        wv.url
                                            .isNullOrEmpty()
                                    ) {
                                        wv.loadUrl(
                                            "https://chatgpt.com/"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (isOffline) {

                    Card(
                        modifier = Modifier
                            .align(
                                androidx.compose.ui.Alignment
                                    .BottomCenter
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
                            androidx.compose.ui.Alignment
                                .TopCenter
                        )
                        .padding(top = 8.dp)
                ) {

                    switchMessage?.let { message ->

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
                                text = message,

                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            16.dp,
                                        vertical =
                                            8.dp
                                    )
                            )
                        }

                        LaunchedEffect(message) {

                            kotlinx.coroutines.delay(
                                1500
                            )

                            switchMessage = null
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

                        showAccounts = false
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

                        webViewCache[acc.id]?.let { wv ->

                            try {
                                wv.stopLoading()
                            } catch (_: Exception) {
                            }

                            try {
                                wv.destroy()
                            } catch (_: Exception) {
                            }

                            webViewCache.remove(
                                acc.id
                            )
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
               
