package com.example.musicplayer.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.musicplayer.data.DownloadOption
import com.example.musicplayer.data.YoutubeDownloader
import com.example.musicplayer.ui.components.DownloadOptionsBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeWebScreen(
    onBackToLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com") }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isFetchingDownloadOptions by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var availableDownloadOptions by remember { mutableStateOf<List<DownloadOption>>(emptyList()) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var resolvedVideoTitle by remember { mutableStateOf("YouTube Track") }

    val currentVideoId = remember(currentUrl) {
        extractVideoId(currentUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    WebView(it).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        val settings = settings
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        val cleanUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230803.041) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.105 Mobile Safari/537.36"
                        settings.userAgentString = cleanUserAgent

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                super.onShowCustomView(view, callback)
                                customViewCallback = callback
                                customView = view
                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }

                            override fun onHideCustomView() {
                                super.onHideCustomView()
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                customView = null
                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (url != null) {
                                    currentUrl = url
                                    canGoBack = view?.canGoBack() == true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                if (url != null) {
                                    currentUrl = url
                                    canGoBack = view?.canGoBack() == true
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                if (targetUrl.contains("youtube.com") || targetUrl.contains("youtu.be")) {
                                    return false
                                }
                                return false
                            }
                        }

                        loadUrl("https://m.youtube.com")
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }

        // Top Navigation overlay when browsing inside YouTube
        Row(
            modifier = Modifier
                .padding(top = 12.dp, start = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToLibrary,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home Tab",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (canGoBack) {
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = { webViewInstance?.reload() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // VidMate-Style Fast Download Button (Shown ONLY when inside a video/details page!)
        if (currentVideoId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isFetchingDownloadOptions) {
                            isFetchingDownloadOptions = true
                            showDownloadSheet = true
                            availableDownloadOptions = emptyList()
                            resolvedVideoTitle = "Fetching song title..."
                            coroutineScope.launch(Dispatchers.IO) {
                                val title = YoutubeDownloader.fetchVideoTitle(currentVideoId)
                                withContext(Dispatchers.Main) {
                                    resolvedVideoTitle = title
                                }
                            }
                            YoutubeDownloader.resolveAllDownloadOptions(currentVideoId) { options ->
                                isFetchingDownloadOptions = false
                                availableDownloadOptions = options
                            }
                        }
                    },
                    icon = {
                        if (isFetchingDownloadOptions) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, contentDescription = "Download Options (VidMate)")
                        }
                    },
                    text = { Text("⚡ Download", fontWeight = FontWeight.Bold) },
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(26.dp)
                )
            }
        }

        // Fullscreen Video View Overlay
        if (customView != null) {
            Dialog(
                onDismissRequest = {
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    customView = null
                    (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            (customView?.parent as? ViewGroup)?.removeView(customView)
                            addView(
                                customView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Download Options Bottom Sheet (Audio & Video Formats)
        if (showDownloadSheet && currentVideoId != null) {
            DownloadOptionsBottomSheet(
                videoTitle = resolvedVideoTitle,
                options = availableDownloadOptions,
                isLoading = isFetchingDownloadOptions,
                onDismiss = { showDownloadSheet = false },
                coroutineScope = coroutineScope
            )
        }
    }
}

private fun extractVideoId(url: String): String? {
    return try {
        if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&").substringBefore("?")
        } else if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?")
        } else if (url.contains("/shorts/")) {
            url.substringAfter("/shorts/").substringBefore("?")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
