package com.admin.bandhan17.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.admin.bandhan17.app.R
import com.admin.bandhan17.app.download.BlobDownloadBridge
import com.admin.bandhan17.app.download.DownloadCompletedInfo
import com.admin.bandhan17.app.download.DownloadHandler
import com.admin.bandhan17.app.download.DownloadRequest
import com.admin.bandhan17.app.network.NetworkMonitor
import com.admin.bandhan17.app.security.BiometricAuthManager
import com.admin.bandhan17.app.security.BiometricAuthResult
import com.admin.bandhan17.app.security.BiometricStatus
import com.admin.bandhan17.app.ui.theme.BandhanCyan
import com.admin.bandhan17.app.ui.theme.BandhanEmeraldPrimary
import com.admin.bandhan17.app.update.UpdateManager
import com.admin.bandhan17.app.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TARGET_URL = "https://bandhan17.website/admin"

// Standard Mobile Chrome User-Agent without WebView markers to enable Google OAuth login inside WebView
private const val CHROME_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

private val DOWNLOAD_EXTENSIONS = setOf(
    // Documents & Spreadsheets
    "pdf", "xlsx", "xls", "csv", "tsv", "doc", "docx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp",
    // Video
    "mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v",
    // Audio
    "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "amr", "wma",
    // Archives
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso",
    // Packages & Code
    "apk", "json", "xml", "sql"
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BandhanMainScreen(
    networkMonitor: NetworkMonitor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnline by networkMonitor.isOnlineFlow.collectAsState(initial = networkMonitor.isCurrentlyConnected())

    var isPageLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    var hasInitialStartupCompleted by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var swipeRefreshLayoutInstance by remember { mutableStateOf<SwipeRefreshLayout?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    // Download Management States
    var pendingDownloadRequest by remember { mutableStateOf<DownloadRequest?>(null) }
    var lastCompletedDownload by remember { mutableStateOf<DownloadCompletedInfo?>(null) }

    // In-App Update State & Management
    val updateManager = remember { UpdateManager() }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // Native Biometric & Device Security Layer
    val biometricAuthManager = remember { BiometricAuthManager() }
    var isUnlocked by remember { mutableStateOf(false) }
    var biometricStatus by remember { mutableStateOf(biometricAuthManager.checkBiometricStatus(context)) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var backgroundTimestamp by remember { mutableLongStateOf(0L) }

    fun triggerAuth() {
        val act = context as? FragmentActivity
        if (act == null) {
            isUnlocked = true
            return
        }
        val status = biometricAuthManager.checkBiometricStatus(context)
        biometricStatus = status
        authErrorMessage = null

        when (status) {
            BiometricStatus.AVAILABLE -> {
                biometricAuthManager.authenticate(
                    activity = act,
                    title = context.getString(R.string.security_prompt_title),
                    subtitle = context.getString(R.string.security_prompt_subtitle),
                    description = context.getString(R.string.security_prompt_desc)
                ) { result ->
                    when (result) {
                        is BiometricAuthResult.Success -> {
                            isUnlocked = true
                            authErrorMessage = null
                        }
                        is BiometricAuthResult.Error -> {
                            authErrorMessage = result.message
                        }
                        is BiometricAuthResult.Failed -> {
                            authErrorMessage = context.getString(R.string.security_auth_failed)
                        }
                    }
                }
            }
            BiometricStatus.NO_HARDWARE,
            BiometricStatus.UNAVAILABLE -> {
                // If device does not support biometric or credentials, grant access without blocking
                isUnlocked = true
            }
            BiometricStatus.NONE_ENROLLED -> {
                // Show message on SecurityLockScreen with options to setup or proceed
            }
        }
    }

    // Auto-prompt on launch when app starts
    LaunchedEffect(Unit) {
        if (!isUnlocked) {
            triggerAuth()
        }
    }

    // Auto-lock when application is sent to background and resumed after timeout
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundTimestamp = SystemClock.elapsedRealtime()
                }
                Lifecycle.Event.ON_START -> {
                    if (isUnlocked && backgroundTimestamp > 0L) {
                        val elapsed = SystemClock.elapsedRealtime() - backgroundTimestamp
                        // 10 second session timeout
                        if (elapsed > 10_000L) {
                            isUnlocked = false
                            authErrorMessage = null
                            triggerAuth()
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // File Chooser state
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Back press handling for exit confirmation
    var backPressedOnce by remember { mutableStateOf(false) }

    // File Picker / Camera Activity Result Launcher
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileUploadCallback ?: return@rememberLauncherForActivityResult
        var results: Array<Uri>? = null

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val dataString = data?.dataString
            val clipData = data?.clipData

            if (clipData != null && clipData.itemCount > 0) {
                results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else if (dataString != null) {
                results = arrayOf(Uri.parse(dataString))
            } else if (cameraPhotoUri != null) {
                // If camera took photo
                results = arrayOf(cameraPhotoUri!!)
            }
        }

        callback.onReceiveValue(results)
        fileUploadCallback = null
        cameraPhotoUri = null
    }

    // Permission launcher for camera/storage when user selects file upload
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Continue regardless; file picker will offer available sources
    }

    // Permission launcher for Android 13+ status bar notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Broadcast receiver for completed system DownloadManager downloads
    DisposableEffect(context) {
        val downloadCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (downloadId != -1L) {
                        try {
                            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = dm.query(query)
                            cursor?.use { c ->
                                if (c.moveToFirst()) {
                                    val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                    val status = if (statusIndex >= 0) c.getInt(statusIndex) else -1
                                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                        val titleIndex = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                        val uriIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                        val mimeIndex = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                                        val sizeIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                                        val title = if (titleIndex >= 0) c.getString(titleIndex) else "downloaded_file"
                                        val localUriStr = if (uriIndex >= 0) c.getString(uriIndex) else null
                                        val mimeType = if (mimeIndex >= 0) c.getString(mimeIndex) ?: "application/octet-stream" else "application/octet-stream"
                                        val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L

                                        val uri = localUriStr?.let { Uri.parse(it) }
                                        if (uri != null) {
                                            val resolvedFile = if (uri.scheme == "file") File(uri.path ?: "") else null
                                            val resolvedUri = if (uri.scheme == "file" && resolvedFile != null) {
                                                try {
                                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resolvedFile)
                                                } catch (_: Exception) {
                                                    uri
                                                }
                                            } else {
                                                uri
                                            }

                                            val completedInfo = DownloadCompletedInfo(
                                                fileName = title,
                                                mimeType = mimeType,
                                                fileUri = resolvedUri,
                                                file = resolvedFile,
                                                fileSizeBytes = size
                                            )
                                            lastCompletedDownload = completedInfo

                                            // Show interactive notification with Open and Share action buttons
                                            DownloadHandler.showCompletedNotification(
                                                context = context,
                                                notificationId = downloadId.toInt(),
                                                fileName = title,
                                                mimeType = mimeType,
                                                fileUri = resolvedUri,
                                                file = resolvedFile,
                                                fileSizeBytes = size
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadCompleteReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadCompleteReceiver, intentFilter)
        }

        onDispose {
            try {
                context.unregisterReceiver(downloadCompleteReceiver)
            } catch (_: Throwable) {}
        }
    }

    // Safety fallback: Dismiss Splash Screen after 10s if network hangs or times out
    LaunchedEffect(Unit) {
        delay(10000)
        if (showSplash) {
            hasInitialStartupCompleted = true
            showSplash = false
        }
    }

    // Auto-check for updates in background when app enters and is online
    LaunchedEffect(isOnline) {
        if (isOnline && updateState is UpdateState.Idle) {
            delay(2000) // Brief delay to prioritize initial page loading resources
            updateManager.checkForUpdates().collect { state ->
                // Show update dialog ONLY if an actual new version is found
                if (state is UpdateState.UpdateAvailable) {
                    updateState = state
                }
            }
        }
    }

    // Reload when coming back online if error occurred
    LaunchedEffect(isOnline) {
        if (isOnline && hasError) {
            hasError = false
            webViewInstance?.reload()
        }
    }

    BackHandler(enabled = true) {
        if (!isUnlocked) {
            if (backPressedOnce) {
                (context as? Activity)?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "অ্যাপ বন্ধ করতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        } else if (popupWebView != null) {
            popupWebView?.destroy()
            popupWebView = null
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            if (backPressedOnce) {
                (context as? Activity)?.finish()
            } else {
                backPressedOnce = true
                Toast.makeText(context, "অ্যাপ বন্ধ করতে আবার ব্যাক চাপুন", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    delay(2000)
                    backPressedOnce = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Main Web Content with SwipeRefreshLayout for pull-to-refresh
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("bandhan_webview"),
            factory = { ctx ->
                val swipeRefreshLayout = SwipeRefreshLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setColorSchemeColors(
                        0xFF0F6B56.toInt(),
                        0xFF00A3B5.toInt(),
                        0xFFF59E0B.toInt()
                    )
                }

                val webView = WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Enable Cookies (including third-party cookies for OAuth session persistence)
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }

                        // Custom Chrome User Agent to allow Google Sign-In inside WebView
                        userAgentString = CHROME_USER_AGENT

                        // Short-term caching
                        cacheMode = if (isOnline) {
                            WebSettings.LOAD_DEFAULT
                        } else {
                            WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }
                    }

                    // Enable smooth scrolling and touch events
                                      // JavaScript Interface Bridge for Client-Side Blob / Base64 Downloads (Statement & File export features)
                    addJavascriptInterface(
                        BlobDownloadBridge(ctx) { req ->
                            pendingDownloadRequest = req
                        },
                        BlobDownloadBridge.JS_INTERFACE_NAME
                    )

                    // Native Download Listener for HTTP/HTTPS & Blob downloads
                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                        if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) {
                            BlobDownloadBridge.downloadBlobUrl(
                                webView = this,
                                blobOrDataUrl = url,
                                suggestedFileName = DownloadHandler.resolveFileName(url, contentDisposition, mimetype),
                                mimeType = mimetype
                            )
                        } else {
                            val guessedMime = DownloadHandler.resolveMimeType(url, mimetype, contentDisposition)
                            val fileName = DownloadHandler.resolveFileName(url, contentDisposition, guessedMime)
                            pendingDownloadRequest = DownloadRequest(
                                url = url,
                                suggestedFileName = fileName,
                                mimeType = guessedMime,
                                contentDisposition = contentDisposition,
                                userAgent = userAgent,
                                contentLength = contentLength
                            )
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isPageLoading = true
                            hasError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoading = false
                            swipeRefreshLayout.isRefreshing = false
                            CookieManager.getInstance().flush()

                            // Inject Blob and Statement download interceptor JavaScript hook
                            view?.evaluateJavascript(BlobDownloadBridge.INTERCEPTOR_JS, null)

                            // Initial startup lifecycle: dismiss splash screen ONLY after initial page load is finished and rendered
                            if (!hasInitialStartupCompleted) {
                                hasInitialStartupCompleted = true
                                coroutineScope.launch {
                                    // 250ms buffer ensures WebView paints its rendered DOM before splash fades out
                                    delay(250)
                                    showSplash = false

                                    // Check for updates in background after startup
                                    delay(1500)
                                    if (networkMonitor.isCurrentlyConnected() && updateState is UpdateState.Idle) {
                                        updateManager.checkForUpdates().collect { state ->
                                            if (state is UpdateState.UpdateAvailable) {
                                                updateState = state
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                isPageLoading = false
                                swipeRefreshLayout.isRefreshing = false
                                if (!hasInitialStartupCompleted) {
                                    hasInitialStartupCompleted = true
                                    showSplash = false
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // Proceed on benign SSL issues in test/staging
                            handler?.proceed()
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            val urlString = uri.toString()
                            val scheme = uri.scheme ?: ""

                            // Handle Blob or Data URLs
                            if (urlString.startsWith("blob:", ignoreCase = true) || urlString.startsWith("data:", ignoreCase = true)) {
                                view?.let {
                                    BlobDownloadBridge.downloadBlobUrl(it, urlString, null, null)
                                }
                                return true
                            }

                            // Handle any downloadable file extension (mp4, mp3, zip, rar, xlsx, doc, pdf, apk, etc.)
                            val path = uri.path.orEmpty().lowercase()
                            val ext = path.substringAfterLast(".", "")
                            if (ext in DOWNLOAD_EXTENSIONS) {
                                val guessedMime = DownloadHandler.resolveMimeType(urlString, null, null)
                                val fileName = DownloadHandler.resolveFileName(urlString, null, guessedMime)
                                pendingDownloadRequest = DownloadRequest(
                                    url = urlString,
                                    suggestedFileName = fileName,
                                    mimeType = guessedMime,
                                    userAgent = settings.userAgentString
                                )
                                return true
                            }

                            // Keep web links, Google auth, and in-app domain URLs inside WebView
                            if (scheme == "http" || scheme == "https") {
                                return false
                            }

                            // Handle external non-web schemes (tel:, mailto:, whatsapp:, etc.)
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                ctx.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                true
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            loadProgress = newProgress / 100f
                            if (newProgress >= 100) {
                                isPageLoading = false
                                swipeRefreshLayout.isRefreshing = false
                            }
                        }

                        // Google OAuth & Multi-Window Handling (e.g., Popups, Statement generators)
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val childWebView = WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                    allowContentAccess = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    userAgentString = CHROME_USER_AGENT
                                }
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                addJavascriptInterface(
                                    BlobDownloadBridge(ctx) { req ->
                                        pendingDownloadRequest = req
                                    },
                                    BlobDownloadBridge.JS_INTERFACE_NAME
                                )

                                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                    if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) {
                                        BlobDownloadBridge.downloadBlobUrl(
                                            webView = this,
                                            blobOrDataUrl = url,
                                            suggestedFileName = DownloadHandler.resolveFileName(url, contentDisposition, mimetype),
                                            mimeType = mimetype
                                        )
                                    } else {
                                        val guessedMime = DownloadHandler.resolveMimeType(url, mimetype, contentDisposition)
                                        val fileName = DownloadHandler.resolveFileName(url, contentDisposition, guessedMime)
                                        pendingDownloadRequest = DownloadRequest(
                                            url = url,
                                            suggestedFileName = fileName,
                                            mimeType = guessedMime,
                                            contentDisposition = contentDisposition,
                                            userAgent = userAgent,
                                            contentLength = contentLength
                                        )
                                    }
                                    popupWebView?.destroy()
                                    popupWebView = null
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(BlobDownloadBridge.INTERCEPTOR_JS, null)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        val uri = request?.url

                                        // Handle blob or direct document download inside popup
                                        if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) {
                                            view?.let {
                                                BlobDownloadBridge.downloadBlobUrl(it, url, null, null)
                                            }
                                            popupWebView?.destroy()
                                            popupWebView = null
                                            return true
                                        }

                                        val path = uri?.path.orEmpty().lowercase()
                                        val ext = path.substringAfterLast(".", "")
                                        if (ext in DOWNLOAD_EXTENSIONS) {
                                            val guessedMime = DownloadHandler.resolveMimeType(url, null, null)
                                            val fileName = DownloadHandler.resolveFileName(url, null, guessedMime)
                                            pendingDownloadRequest = DownloadRequest(
                                                url = url,
                                                suggestedFileName = fileName,
                                                mimeType = guessedMime,
                                                userAgent = settings.userAgentString
                                            )
                                            popupWebView?.destroy()
                                            popupWebView = null
                                            return true
                                        }

                                        // If redirecting back to main website or auth callback, let it load in main view
                                        if (url.contains("bandhan17.website")) {
                                            webViewInstance?.loadUrl(url)
                                            popupWebView?.destroy()
                                            popupWebView = null
                                            return true
                                        }
                                        return false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        popupWebView?.destroy()
                                        popupWebView = null
                                    }
                                }
                            }

                            popupWebView = childWebView
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = childWebView
                            resultMsg?.sendToTarget()
                            return true
                        }

                        override fun onCloseWindow(window: WebView?) {
                            super.onCloseWindow(window)
                            popupWebView?.destroy()
                            popupWebView = null
                        }

                        // File Upload & Camera Chooser
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            // Cancel previous pending callback if any
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = filePathCallback

                            try {
                                // Request permissions if needed
                                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                }

                                val photoFile = createImageFile(ctx)
                                val photoUri = FileProvider.getUriForFile(
                                    ctx,
                                    "${ctx.packageName}.fileprovider",
                                    photoFile
                                )
                                cameraPhotoUri = photoUri

                                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                }

                                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf", "*/*"))
                                }

                                val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                                    putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                                    putExtra(Intent.EXTRA_TITLE, "Select File or Take Photo")
                                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
                                }

                                fileChooserLauncher.launch(chooserIntent)
                                return true
                            } catch (e: Exception) {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = null
                                return false
                            }
                        }
                    }

                    loadUrl(TARGET_URL)
                }

                swipeRefreshLayout.setOnRefreshListener {
                    if (networkMonitor.isCurrentlyConnected()) {
                        webView.reload()
                    } else {
                        swipeRefreshLayout.isRefreshing = false
                        hasError = true
                    }
                }

                swipeRefreshLayout.addView(webView)
                webViewInstance = webView
                swipeRefreshLayoutInstance = swipeRefreshLayout

                swipeRefreshLayout
            },
            update = { layout ->
                swipeRefreshLayoutInstance = layout
            }
        )

        // Child Popup WebView (for OAuth / Dialog Windows)
        if (popupWebView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { popupWebView!! }
                )
            }
        }

        // Offline / No Internet Screen
        if (hasError || (!isOnline && isPageLoading)) {
            OfflineScreen(
                onRetry = {
                    hasError = false
                    isPageLoading = true
                    webViewInstance?.reload()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Splash Screen Overlay
        SplashScreen(
            visible = showSplash,
            modifier = Modifier.fillMaxSize()
        )

        // Native Security Lock Screen (Topmost layer: completely blocks access until verified)
        if (!isUnlocked) {
            SecurityLockScreen(
                biometricStatus = biometricStatus,
                errorMessage = authErrorMessage,
                onAuthenticateClick = {
                    triggerAuth()
                },
                onOpenSettingsClick = {
                    biometricAuthManager.openSecuritySettings(context)
                },
                onBypassClick = {
                    isUnlocked = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Native In-App Update Dialog (shown automatically when an update is available)
        UpdateDialog(
            updateState = updateState,
            onDismiss = {
                downloadJob?.cancel()
                updateState = UpdateState.Idle
            },
            onStartDownload = { info ->
                downloadJob?.cancel()
                downloadJob = coroutineScope.launch {
                    updateManager.downloadApk(context, info).collect { state ->
                        updateState = state
                    }
                }
            },
            onCancelDownload = {
                downloadJob?.cancel()
                updateState = UpdateState.Idle
            },
            onInstallApk = { apkFile ->
                val launched = updateManager.launchPackageInstaller(context, apkFile)
                if (!launched) {
                    Toast.makeText(context, "ইনস্টলার খুলতে ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenPermissionSettings = {
                updateManager.openUnknownAppInstallSettings(context)
            },
            canInstallPackages = updateManager.canRequestPackageInstalls(context),
            onRetry = {
                coroutineScope.launch {
                    updateManager.checkForUpdates().collect { state ->
                        updateState = state
                    }
                }
            }
        )

        // Download Confirmation Dialog (allows renaming, shows file category, size, destination)
        pendingDownloadRequest?.let { request ->
            DownloadConfirmDialog(
                request = request,
                onConfirm = { editedFileName ->
                    pendingDownloadRequest = null
                    if (request.isBlobOrBase64) {
                        DownloadHandler.executeBase64Download(
                            context = context,
                            request = request,
                            customFileName = editedFileName,
                            onCompleted = { completedInfo ->
                                lastCompletedDownload = completedInfo
                            }
                        )
                    } else {
                        DownloadHandler.executeHttpDownload(
                            context = context,
                            request = request,
                            customFileName = editedFileName
                        )
                    }
                },
                onDismiss = {
                    pendingDownloadRequest = null
                }
            )
        }

        // Download Completed Banner (floating card with Open and Share action buttons)
        DownloadCompletedBanner(
            completedInfo = lastCompletedDownload,
            onOpen = { info ->
                DownloadHandler.openDownloadedFile(
                    context = context,
                    fileUri = info.fileUri,
                    mimeType = info.mimeType,
                    file = info.file
                )
            },
            onShare = { info ->
                DownloadHandler.shareDownloadedFile(
                    context = context,
                    fileUri = info.fileUri,
                    mimeType = info.mimeType,
                    file = info.file,
                    fileName = info.fileName
                )
            },
            onDismiss = {
                lastCompletedDownload = null
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile(imageFileName, ".jpg", storageDir)
}
