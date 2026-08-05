package com.example.musicplayer.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.YoutubeDownloader
import com.example.musicplayer.data.YoutubeVideo
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray

// SharedPreferences helper to store recent searches and plays
private const val YT_PREFS = "yt_search_prefs"
private const val KEY_HISTORY = "history_v1"
private const val KEY_RECENT_SONGS = "recent_songs_v1"

private fun getSearchHistory(context: Context): List<String> {
    val prefs = context.getSharedPreferences(YT_PREFS, Context.MODE_PRIVATE)
    val listStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
    return try {
        val arr = JSONArray(listStr)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveSearchHistory(context: Context, query: String) {
    if (query.isBlank()) return
    val prefs = context.getSharedPreferences(YT_PREFS, Context.MODE_PRIVATE)
    val history = getSearchHistory(context).toMutableList()
    history.remove(query)
    history.add(0, query)
    val limitedHistory = history.take(8)
    prefs.edit().putString(KEY_HISTORY, JSONArray(limitedHistory).toString()).apply()
}

private fun getRecentPlays(context: Context): List<YoutubeVideo> {
    val prefs = context.getSharedPreferences(YT_PREFS, Context.MODE_PRIVATE)
    val listStr = prefs.getString(KEY_RECENT_SONGS, "[]") ?: "[]"
    return try {
        val arr = JSONArray(listStr)
        List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            YoutubeVideo(
                id = obj.getString("id"),
                title = obj.getString("title"),
                artist = obj.getString("artist"),
                durationText = obj.getString("durationText"),
                thumbnail = obj.getString("thumbnail")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveRecentPlay(context: Context, video: YoutubeVideo) {
    val prefs = context.getSharedPreferences(YT_PREFS, Context.MODE_PRIVATE)
    val recents = getRecentPlays(context).toMutableList()
    recents.removeAll { it.id == video.id }
    recents.add(0, video)
    val limited = recents.take(10)
    
    val arr = JSONArray()
    for (item in limited) {
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("title", item.title)
        obj.put("artist", item.artist)
        obj.put("durationText", item.durationText)
        obj.put("thumbnail", item.thumbnail)
        arr.put(obj)
    }
    prefs.edit().putString(KEY_RECENT_SONGS, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onSongPlay: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showDownloadSheet by remember { mutableStateOf(false) }
    var selectedVideoForDownload by remember { mutableStateOf<YoutubeVideo?>(null) }
    var availableDownloadOptions by remember { mutableStateOf<List<com.example.musicplayer.data.DownloadOption>>(emptyList()) }
    var isFetchingOptions by remember { mutableStateOf(false) }

    // Search history and recent plays states
    var searchHistory by remember { mutableStateOf(getSearchHistory(context)) }
    var recentPlays by remember { mutableStateOf(getRecentPlays(context)) }

    val trendingSuggestions = listOf(
        "Lofi Hip Hop beats",
        "Pritam hits",
        "Arijit Singh Sad Songs",
        "Coke Studio Bangla",
        "Anuv Jain playlist",
        "Synthwave radio"
    )

    fun performSearch(query: String) {
        if (query.isBlank()) return
        searchQuery = query
        isLoading = true
        saveSearchHistory(context, query)
        searchHistory = getSearchHistory(context) // refresh history
        keyboardController?.hide()
        
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                YoutubeDownloader.searchVideos(query)
            }
            searchResults = results
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // Minimalist Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search songs, playlists, or artists...", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = "" 
                        searchResults = emptyList()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                performSearch(searchQuery)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF2C2C2E),
                focusedContainerColor = Color(0xFF1C1C1E),
                unfocusedContainerColor = Color(0xFF1C1C1E)
            ),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (searchResults.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Section 1: Recent plays (Previous Songs)
                if (recentPlays.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recently Played",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    items(recentPlays) { video ->
                        YoutubeVideoRow(
                            video = video,
                            onDownloadClick = {
                                triggerHaptic(view)
                                Toast.makeText(context, "Generating download link...", Toast.LENGTH_SHORT).show()
                                YoutubeDownloader.startAudioDownload(context, video) { success ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(context, "Download started! Swipe notification panel to view.", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onRowClick = {
                                triggerHaptic(view)
                                Toast.makeText(context, "Resolving stream link...", Toast.LENGTH_SHORT).show()
                                YoutubeDownloader.resolveAudioUrl(video.id) { streamUrl ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        if (streamUrl != null) {
                                            saveRecentPlay(context, video)
                                            recentPlays = getRecentPlays(context) // refresh list
                                            viewModel.playOnlineStream(video.title, video.artist, video.thumbnail, streamUrl)
                                            onSongPlay()
                                        } else {
                                            Toast.makeText(context, "Could not load streaming link. Try another song.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Section 2: Recent search terms
                if (searchHistory.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Searches",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(searchHistory) { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { performSearch(historyItem) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = historyItem, color = Color.LightGray, fontSize = 15.sp)
                        }
                    }
                }

                // Section 3: Trending recommendations / Suggestions
                item {
                    Text(
                        text = "Suggestions",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
                    )
                }

                items(trendingSuggestions) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { performSearch(suggestion) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = suggestion, color = Color.LightGray, fontSize = 15.sp)
                    }
                }
            }
        } else {
            // Search Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults) { video ->
                    YoutubeVideoRow(
                        video = video,
                        onDownloadClick = {
                            triggerHaptic(view)
                            Toast.makeText(context, "Generating download link...", Toast.LENGTH_SHORT).show()
                            YoutubeDownloader.startAudioDownload(context, video) { success ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, "Download started! Swipe notification panel to view.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Download failed. Please try another song.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        onRowClick = {
                            triggerHaptic(view)
                            Toast.makeText(context, "Resolving stream link...", Toast.LENGTH_SHORT).show()
                            YoutubeDownloader.resolveAudioUrl(video.id) { streamUrl ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    if (streamUrl != null) {
                                        saveRecentPlay(context, video)
                                        recentPlays = getRecentPlays(context) // update play list
                                        viewModel.playOnlineStream(video.title, video.artist, video.thumbnail, streamUrl)
                                        onSongPlay()
                                    } else {
                                        Toast.makeText(context, "Could not load streaming link. Try another song.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun YoutubeVideoRow(
    video: YoutubeVideo,
    onDownloadClick: () -> Unit,
    onRowClick: () -> Unit
) {
    var thumbnailBitmap by remember(video.thumbnail) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.thumbnail) {
        if (video.thumbnail.isNotBlank()) {
            thumbnailBitmap = withContext(Dispatchers.IO) {
                try {
                    val url = URL(video.thumbnail)
                    BitmapFactory.decodeStream(url.openConnection().getInputStream())
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F0F11))
            .clickable { onRowClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded Thumbnail Image
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 85.dp, height = 55.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 85.dp, height = 55.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E22)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.artist,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = video.durationText,
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Download Action
        IconButton(
            onClick = onDownloadClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xFF1E1E22),
                contentColor = Color.White
            ),
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download MP3",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun triggerHaptic(view: android.view.View) {
    try {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    } catch (e: Exception) {
        // Fallback
    }
}
