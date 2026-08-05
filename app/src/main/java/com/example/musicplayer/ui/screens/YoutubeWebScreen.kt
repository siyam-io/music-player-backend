package com.example.musicplayer.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.musicplayer.data.Song
import com.example.musicplayer.data.YoutubeDownloader
import com.example.musicplayer.data.YoutubeVideo
import com.example.musicplayer.viewmodel.MusicViewModel

private const val TAG = "YoutubeWebScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeWebScreen(
    viewModel: MusicViewModel
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com") }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isResolvingAudio by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    val currentVideoId = remember(currentUrl) {
        extractVideoId(currentUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "YouTube Web",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (currentVideoId != null) "Video Detected - Ready to Play/Download" else "Browse YouTube & Sign In",
                            fontSize = 12.sp,
                            color = if (currentVideoId != null) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { webViewInstance?.goBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            if (currentVideoId != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 60.dp)
                ) {
                    // Play Audio in Background Button
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!isResolvingAudio) {
                                isResolvingAudio = true
                                Toast.makeText(context, "Resolving audio for background play...", Toast.LENGTH_SHORT).show()
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
                        icon = {
                            if (isResolvingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio")
                            }
                        },
                        text = { Text(if (isResolvingAudio) "Resolving..." else "Play Audio") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    )

                    // Download MP3 Button
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                Toast.makeText(context, "Preparing MP3 download...", Toast.LENGTH_SHORT).show()
                                val ytVideo = YoutubeVideo(
                                    id = currentVideoId,
                                    title = "YouTube Track $currentVideoId",
                                    artist = "YouTube",
                                    durationText = "00:00",
                                    thumbnail = "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg"
                                )
                                YoutubeDownloader.startAudioDownload(context, ytVideo) { success ->
                                    isDownloading = false
                                    if (success) {
                                        Toast.makeText(context, "MP3 Download Started! Check Notifications", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        icon = {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Download MP3")
                            }
                        },
                        text = { Text("Download MP3") },
                        containerColor = Color(0xFF1DB954),
                        contentColor = Color.Black
                    )
                }
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                            // Chrome desktop/mobile User-Agent to allow Google Account Sign-In without browser restriction block
                            userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SQ3A.220705.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.210 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
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
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl("https://m.youtube.com")
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
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
            url.substringAfter("youtu.be/").substringBefore("&").substringBefore("?")
        } else if (url.contains("youtube.com/shorts/")) {
            url.substringAfter("youtube.com/shorts/").substringBefore("&").substringBefore("?")
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}
