package com.example.musicplayer.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.DownloadOption
import com.example.musicplayer.data.ParallelDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    videoTitle: String,
    options: List<DownloadOption>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "⚡ High-Speed Download (VidMate Engine)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1DB954)
            )
            Text(
                videoTitle,
                fontSize = 14.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Extracting audio & video formats...", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            } else if (options.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No direct formats available for this video", color = Color.Red, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    val audioOptions = options.filter { it.formatType == "AUDIO" }
                    val videoOptions = options.filter { it.formatType == "VIDEO" }

                    if (audioOptions.isNotEmpty()) {
                        item {
                            Text(
                                "🎵 AUDIO FORMATS (MP3)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1DB954),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(audioOptions) { option ->
                            OptionRow(option = option) {
                                onDismiss()
                                Toast.makeText(context, "⚡ Starting 8-Thread Parallel Download...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    ParallelDownloader.downloadFile(
                                        context = context,
                                        urlStr = option.url,
                                        title = videoTitle,
                                        extension = option.extension
                                    ) { success, file ->
                                        // Completed via Notification & MediaScanner
                                    }
                                }
                            }
                        }
                    }

                    if (videoOptions.isNotEmpty()) {
                        item {
                            Text(
                                "🎬 VIDEO FORMATS (MP4)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF4081),
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(videoOptions) { option ->
                            OptionRow(option = option) {
                                onDismiss()
                                Toast.makeText(context, "⚡ Starting 8-Thread Parallel Download...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    ParallelDownloader.downloadFile(
                                        context = context,
                                        urlStr = option.url,
                                        title = videoTitle,
                                        extension = option.extension
                                    ) { success, file ->
                                        // Completed via Notification & MediaScanner
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: DownloadOption,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF282828))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (option.formatType == "AUDIO") Icons.Default.MusicNote else Icons.Default.Videocam,
                contentDescription = null,
                tint = if (option.formatType == "AUDIO") Color(0xFF1DB954) else Color(0xFFFF4081),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    option.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    if (option.formatType == "AUDIO") "Fast Multi-Threaded Audio" else "Full HD Video Download",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
