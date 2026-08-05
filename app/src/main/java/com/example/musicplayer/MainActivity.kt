package com.example.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.musicplayer.data.Song
import com.example.musicplayer.ui.screens.LibraryScreen
import com.example.musicplayer.ui.screens.NowPlayingScreen
import com.example.musicplayer.ui.theme.MusicPlayerTheme
import com.example.musicplayer.viewmodel.MusicViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private var isPermissionGranted by mutableStateOf(false)

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.refreshSongs(applicationContext)
        }
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val readGranted = permissions[readPermission] ?: false
        isPermissionGranted = readGranted
        if (readGranted) {
            viewModel.initialize(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.deleteRequestFlow.collectLatest { request ->
                deleteRequestLauncher.launch(request)
            }
        }

        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionsToRequest = mutableListOf(readPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        isPermissionGranted = ContextCompat.checkSelfPermission(
            this, readPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (isPermissionGranted) {
            viewModel.initialize(applicationContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }

        handlePlayIntent(intent)

        setContent {
            MusicPlayerTheme {
                var showNowPlaying by remember { mutableStateOf(false) }

                if (isPermissionGranted) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LibraryScreen(
                            viewModel = viewModel,
                            onMiniPlayerClick = { showNowPlaying = true }
                        )

                        AnimatedVisibility(
                            visible = showNowPlaying,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            NowPlayingScreen(
                                viewModel = viewModel,
                                onDismiss = { showNowPlaying = false }
                            )
                        }
                    }
                } else {
                    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    val permissionsToRequest = mutableListOf(readPermission)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    PermissionRequestScreen(
                        onRequestPermission = {
                            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePlayIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionGranted) {
            viewModel.refreshSongs(applicationContext)
        }
    }

    private fun handlePlayIntent(intent: Intent?) {
        if (intent?.action == "PLAY_DOWNLOADED_SONG") {
            val filePath = intent.getStringExtra("FILE_PATH")
            val title = intent.getStringExtra("TITLE") ?: "Downloaded Track"
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    viewModel.refreshSongs(applicationContext)
                    val downloadedSong = Song(
                        id = filePath.hashCode().toLong(),
                        title = title,
                        artist = "Downloaded",
                        album = "Downloads",
                        uri = Uri.fromFile(file),
                        path = filePath,
                        duration = 0L,
                        albumArtUri = null
                    )
                    viewModel.playSong(downloadedSong)
                    Toast.makeText(applicationContext, "Playing $title 🎵", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Premium Player",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Please grant local storage permissions to scan and play your music files.",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Grant Permission")
            }
        }
    }
}
