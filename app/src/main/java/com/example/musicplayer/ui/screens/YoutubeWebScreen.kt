package com.example.musicplayer.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
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
                                val activity = context as? Activity ?: return
                                val decorView = activity.window.decorView as? ViewGroup ?: return

                                customViewCallback = callback
                                customView = view

                                (view?.parent as? ViewGroup)?.removeView(view)
                                decorView.addView(
                                    view,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    activity.window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                    activity.window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                } else {
                                    @Suppress("DEPRECATION")
                                    activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                                }
                                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }

                            override fun onHideCustomView() {
                                super.onHideCustomView()
                                val activity = context as? Activity
                                val decorView = activity?.window?.decorView as? ViewGroup

                                if (customView != null) {
                                    decorView?.removeView(customView)
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null
                                    customView = null
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    activity?.window?.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                } else {
                                    @Suppress("DEPRECATION")
                                    activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                                }
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                val actualUrl = url ?: view?.url
                                if (actualUrl != null) {
                                    currentUrl = actualUrl
                                    canGoBack = view?.canGoBack() == true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                val actualUrl = url ?: view?.url
                                if (actualUrl != null) {
                                    currentUrl = actualUrl
                                    canGoBack = view?.canGoBack() == true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                val actualUrl = url ?: view?.url
                                if (actualUrl != null) {
                                    currentUrl = actualUrl
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

        // VidMate-Style Fast Download Button (ALWAYS Available!)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    val activeUrl = webViewInstance?.url ?: currentUrl
                    val activeVideoId = extractVideoId(activeUrl) ?: currentVideoId
                    if (activeVideoId == null) {
                        Toast.makeText(context, "Please open a YouTube video to download 🎵", Toast.LENGTH_SHORT).show()
                    } else if (!isFetchingDownloadOptions) {
                        isFetchingDownloadOptions = true
                        showDownloadSheet = true
                        availableDownloadOptions = emptyList()
                        resolvedVideoTitle = "Fetching song title..."
                        coroutineScope.launch(Dispatchers.IO) {
                            val title = YoutubeDownloader.fetchVideoTitle(activeVideoId)
                            withContext(Dispatchers.Main) {
                                resolvedVideoTitle = title
                            }
                        }
                        YoutubeDownloader.resolveAllDownloadOptions(activeVideoId) { options ->
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

        // Download Options Bottom Sheet (Audio & Video Formats)
        if (showDownloadSheet) {
            val activeUrl = webViewInstance?.url ?: currentUrl
            val activeVideoId = extractVideoId(activeUrl) ?: currentVideoId
            if (activeVideoId != null) {
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
