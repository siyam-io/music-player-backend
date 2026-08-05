package com.example.musicplayer.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val prefs = remember { context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE) }
    var savedFolder by remember { mutableStateOf(prefs.getString("saved_folder", "Internal Download") ?: "Internal Download") }

    var editableTitle by remember(videoTitle) { mutableStateOf(videoTitle) }
    var editableFolder by remember { mutableStateOf(savedFolder) }

    val presetFolders = listOf("Internal Download", "Favorites", "Pop Hits", "Rock & Classics", "Remix & Chill")

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

            Spacer(modifier = Modifier.height(12.dp))

            // Rename Song Title Field
            OutlinedTextField(
                value = editableTitle,
                onValueChange = { editableTitle = it },
                label = { Text("Song Title (Rename)", color = Color(0xFF1DB954), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Choose Save Folder / Album Field
            OutlinedTextField(
                value = editableFolder,
                onValueChange = {
                    editableFolder = it
                    prefs.edit().putString("saved_folder", it).apply()
                },
                label = { Text("Target Album / Folder Name", color = Color(0xFF1DB954), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            // Preset Folder Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(presetFolders) { folder ->
                    FilterChip(
                        selected = editableFolder == folder,
                        onClick = {
                            editableFolder = folder
                            prefs.edit().putString("saved_folder", folder).apply()
                        },
                        label = { Text(folder, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1DB954),
                            selectedLabelColor = Color.Black,
                            containerColor = Color(0xFF2C2C2C),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
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
                        .height(120.dp),
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
                                "🎵 AUDIO FORMATS (MP3 / M4A)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1DB954),
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(audioOptions) { option ->
                            OptionRow(option = option) {
                                onDismiss()
                                val targetTitle = editableTitle.ifBlank { videoTitle }
                                val targetFolder = editableFolder.ifBlank { "Internal Download" }
                                Toast.makeText(context, "⚡ Starting 8-Thread Parallel Download into $targetFolder...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    ParallelDownloader.downloadFile(
                                        context = context,
                                        urlStr = option.url,
                                        title = targetTitle,
                                        extension = option.extension,
                                        folderName = targetFolder
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
                                val targetTitle = editableTitle.ifBlank { videoTitle }
                                val targetFolder = editableFolder.ifBlank { "Internal Download" }
                                Toast.makeText(context, "⚡ Starting 8-Thread Parallel Download into $targetFolder...", Toast.LENGTH_SHORT).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    ParallelDownloader.downloadFile(
                                        context = context,
                                        urlStr = option.url,
                                        title = targetTitle,
                                        extension = option.extension,
                                        folderName = targetFolder
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
private fun OptionRow(option: DownloadOption, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = Color(0xFF2C2C2C)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        text = option.qualityLabel,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${option.extension.uppercase()} • High Speed Stream",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                color = Color(0xFF1DB954).copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Download",
                        color = Color(0xFF1DB954),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
