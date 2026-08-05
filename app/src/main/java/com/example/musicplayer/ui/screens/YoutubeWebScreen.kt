package com.example.musicplayer.ui.screens

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.musicplayer.data.Song
import com.example.musicplayer.data.YoutubeDownloader
import com.example.musicplayer.data.YoutubeVideo
import com.example.musicplayer.ui.components.DownloadOptionsBottomSheet
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

private const val TAG = "YoutubeWebScreen"

private val AD_HOSTS = setOf(
    "googleads.g.doubleclick.net",
    "pubads.g.doubleclick.net",
    "pagead2.googlesyndication.com",
    "googleadservices.com",
    "static.doubleclick.net",
    "adservice.google.com"
)

private fun isAdHost(urlStr: String): Boolean {
    return try {
        val host = Uri.parse(urlStr).host?.lowercase() ?: return false
        AD_HOSTS.any { host == it || host.endsWith(".$it") } || urlStr.contains("/pagead/")
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeWebScreen(
    viewModel: MusicViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com") }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isResolvingAudio by remember { mutableStateOf(false) }
    var isFetchingDownloadOptions by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var availableDownloadOptions by remember { mutableStateOf<List<DownloadOption>>(emptyList()) }
    var customView by remember { mutableStateOf<View?>(null) }

    // Periodically poll WebView URL to catch YouTube SPA AJAX navigation
    LaunchedEffect(webViewInstance) {
        while (true) {
            val liveUrl = webViewInstance?.url
            if (liveUrl != null && liveUrl != currentUrl) {
                currentUrl = liveUrl
                canGoBack = webViewInstance?.canGoBack() == true
            }
            delay(300)
        }
    }

    val currentVideoId = remember(currentUrl) {
        extractVideoId(currentUrl)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 100% Fullscreen Web View Container
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // Desktop/Mobile Safari User-Agent to allow Google Sign-In seamlessly
                        userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SQ3A.220705.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.210 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""
                            if (isAdHost(reqUrl)) {
                                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            if (url != null) {
                                currentUrl = url
                            }
                            canGoBack = canGoBack()
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            if (url != null) {
                                currentUrl = url
                            }
                            canGoBack = canGoBack()

                            // Built-in AdBlocker + Auto-Skip Video Ads Script
                            val adBlockJs = """
                                (function() {
                                    var css = '.ytp-ad-overlay-container, .ytp-ad-message-container, .video-ads, .ytp-ad-module, ytd-ad-slot-renderer, #player-ads, .ad-showing, .ad-interrupting { display: none !important; }';
                                    var style = document.createElement('style');
                                    style.type = 'text/css';
                                    style.appendChild(document.createTextNode(css));
                                    (document.head || document.documentElement).appendChild(style);

                                    setInterval(function() {
                                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-slot');
                                        if (skipBtn) {
                                            skipBtn.click();
                                        }
                                        var adVideo = document.querySelector('.ad-showing video');
                                        if (adVideo && !isNaN(adVideo.duration)) {
                                            adVideo.currentTime = adVideo.duration;
                                        }
                                    }, 400);
                                })();
                            """.trimIndent()

                            view?.evaluateJavascript(adBlockJs, null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            super.onShowCustomView(view, callback)
                            customView = view
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                            customView = null
                        }
                    }

                    loadUrl("https://m.youtube.com")
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Bar
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Floating Minimal Top Back Navigation Bar
        if (canGoBack) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp, start = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
        }

        // VidMate Style Floating Action Buttons (Always Visible at Bottom Right)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 90.dp)
        ) {
            // Play Audio in Background Button
            FloatingActionButton(
                onClick = {
                    if (currentVideoId == null) {
                        Toast.makeText(context, "Open any YouTube video to play audio in background!", Toast.LENGTH_SHORT).show()
                    } else if (!isResolvingAudio) {
                        isResolvingAudio = true
                        Toast.makeText(context, "Extracting audio for background playback...", Toast.LENGTH_SHORT).show()
                        YoutubeDownloader.resolveAudioUrl(currentVideoId) { audioUrl ->
                            isResolvingAudio = false
                            if (audioUrl != null) {
                                val videoSong = Song(
                                    id = currentVideoId.hashCode().toLong(),
                                    title = "YouTube Track ($currentVideoId)",
                                    artist = "YouTube",
                                    album = "YouTube Web",
                                    uri = Uri.parse(audioUrl),
                                    path = audioUrl,
                                    duration = 0L,
                                    albumArtUri = Uri.parse("https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg")
                                )
                                viewModel.playSong(videoSong)
                                Toast.makeText(context, "Playing audio in background 🎵", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to extract audio stream", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(54.dp)
            ) {
                if (isResolvingAudio) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio in Background")
                }
            }

            // VidMate Style Fast Download Button (Audio & Video Formats)
            ExtendedFloatingActionButton(
                onClick = {
                    if (currentVideoId == null) {
                        Toast.makeText(context, "Play any video on YouTube to download MP3/MP4!", Toast.LENGTH_SHORT).show()
                    } else if (!isFetchingDownloadOptions) {
                        isFetchingDownloadOptions = true
                        showDownloadSheet = true
                        availableDownloadOptions = emptyList()
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
                text = { Text(if (currentVideoId != null) "⚡ Download" else "⚡ Fast Download", fontWeight = FontWeight.Bold) },
                containerColor = Color(0xFF1DB954),
                contentColor = Color.Black,
                shape = RoundedCornerShape(26.dp)
            )
        }

        // Fullscreen Video View Overlay
        if (customView != null) {
            AndroidView(
                factory = {
                    FrameLayout(it).apply {
                        addView(
                            customView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Download Options Bottom Sheet (Audio & Video Formats)
        if (showDownloadSheet && currentVideoId != null) {
            DownloadOptionsBottomSheet(
                videoTitle = "YouTube Track ($currentVideoId)",
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
            url.substringAfter("youtu.be/").substringBefore("&").substringBefore("?")
        } else if (url.contains("youtube.com/shorts/")) {
            url.substringAfter("youtube.com/shorts/").substringBefore("&").substringBefore("?")
        } else if (url.contains("embed/")) {
            url.substringAfter("embed/").substringBefore("&").substringBefore("?")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
