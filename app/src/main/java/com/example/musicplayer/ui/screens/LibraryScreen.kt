package com.example.musicplayer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.data.Song
import com.example.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onMiniPlayerClick: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    
    val context = LocalContext.current
    val view = LocalView.current

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val selectedTab = pagerState.currentPage
    var selectedAlbumName by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistName by remember { mutableStateOf<String?>(null) }
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    var songForPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSelector by remember { mutableStateOf<Song?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<Song?>(null) }
    var showRenameDialog by remember { mutableStateOf<Song?>(null) }

    var searchQuery by remember { mutableStateOf("") }

    // Group songs by album
    val albums = remember(songs) {
        songs.groupBy { it.album }
    }

    Scaffold(
        topBar = {
            if (selectedTab != 4) {
                val tabHeading = when (selectedTab) {
                    0 -> "My Tracks"
                    1 -> "Albums"
                    2 -> "Favorites"
                    3 -> "YT Search"
                    else -> "Music Library"
                }
                TopAppBar(
                    title = {
                        Text(
                            tabHeading,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Segmented Tabs with Beautiful Icons & Labels (Hidden on YouTube Web Tab for 100% Fullscreen)
                if (selectedTab != 4) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Black,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 12.dp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Tracks") },
                        text = { Text("Tracks", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        icon = { Icon(Icons.Default.Album, contentDescription = "Albums") },
                        text = { Text("Albums", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        text = { Text("Favorites", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = "YT Music") },
                        text = { Text("YT Music", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(4) }
                        },
                        icon = { Icon(Icons.Default.OndemandVideo, contentDescription = "YouTube") },
                        text = { Text("YouTube", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = selectedTab != 3 && selectedTab != 4,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> { // Tracks Tab
                            val filteredSongs = remember(songs, searchQuery) {
                                if (searchQuery.isBlank()) {
                                    songs
                                } else {
                                    songs.filter {
                                        it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.artist.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                            }
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    placeholder = { Text("Search local songs...", color = Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.DarkGray
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (filteredSongs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No matching songs found.",
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 100.dp)
                                    ) {
                                        items(filteredSongs) { song ->
                                            SongRow(
                                                song = song,
                                                songTitle = viewModel.getSongTitle(song),
                                                isSelected = currentSong?.id == song.id,
                                                onClick = {
                                                    triggerHaptic(view)
                                                    viewModel.playSong(song, filteredSongs)
                                                },
                                                onMoreClick = {
                                                    triggerHaptic(view)
                                                    songForPlaylistDialog = song
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // Albums Tab (Folders + Playlists)
                            val albumItems = remember(playlists, albums) {
                                listOf("create_btn") + playlists.map { PlaylistRef(it) } + albums.keys.map { AlbumRef(it) }
                            }
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(albumItems) { ref ->
                                    when (ref) {
                                        is String -> {
                                            // Create Playlist Button
                                            CreatePlaylistCard(onClick = {
                                                triggerHaptic(view)
                                                showCreatePlaylistDialog = true
                                            })
                                        }
                                        is PlaylistRef -> {
                                            val playlistSongs = viewModel.getSongsInPlaylist(ref.name)
                                            AlbumCard(
                                                albumName = ref.name,
                                                songCount = playlistSongs.size,
                                                representativeSong = playlistSongs.firstOrNull(),
                                                isCustomPlaylist = true,
                                                onClick = {
                                                    triggerHaptic(view)
                                                    selectedPlaylistName = ref.name
                                                }
                                            )
                                        }
                                        is AlbumRef -> {
                                            val albumSongs = albums[ref.name] ?: emptyList()
                                            AlbumCard(
                                                albumName = ref.name,
                                                songCount = albumSongs.size,
                                                representativeSong = albumSongs.firstOrNull(),
                                                isCustomPlaylist = false,
                                                onClick = {
                                                    triggerHaptic(view)
                                                    selectedAlbumName = ref.name
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> { // Favorites Tab
                            val favoriteSongs = songs.filter { favoriteIds.contains(it.id) }
                            if (favoriteSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No favorites marked yet.",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 100.dp)
                                ) {
                                    items(favoriteSongs) { song ->
                                        SongRow(
                                            song = song,
                                            songTitle = viewModel.getSongTitle(song),
                                            isSelected = currentSong?.id == song.id,
                                            onClick = {
                                                triggerHaptic(view)
                                                viewModel.playSong(song, favoriteSongs)
                                            },
                                            onMoreClick = {
                                                triggerHaptic(view)
                                                songForPlaylistDialog = song
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        3 -> { // YouTube Search Tab
                            YoutubeScreen(
                                viewModel = viewModel,
                                onBack = { 
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onSongPlay = onMiniPlayerClick
                            )
                        }
                        4 -> { // YouTube Web Tab
                            YoutubeWebScreen(
                                viewModel = viewModel,
                                onBackToLibrary = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                }
                            )
                        }
                    }
                }
            }

            // Glassy Mini Player (Hidden on YouTube Web Tab for 100% Fullscreen)
            AnimatedVisibility(
                visible = currentSong != null && selectedTab != 4,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        songTitle = viewModel.getSongTitle(song),
                        isPlaying = isPlaying,
                        onTogglePlay = {
                            triggerHaptic(view)
                            viewModel.togglePlayPause()
                        },
                        onNext = {
                            triggerHaptic(view)
                            viewModel.skipToNext()
                        },
                        onClick = onMiniPlayerClick
                    )
                }
            }

             // Album Details Overlay
             AnimatedVisibility(
                 visible = selectedAlbumName != null,
                 enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                 exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                 modifier = Modifier.fillMaxSize()
             ) {
                 selectedAlbumName?.let { albumName ->
                     val albumSongs = albums[albumName] ?: emptyList()
                     AlbumDetailsView(
                         albumName = albumName,
                         songs = albumSongs,
                         currentSong = currentSong,
                         isCustomPlaylist = false,
                         viewModel = viewModel,
                         onSongClick = { song ->
                             triggerHaptic(view)
                             viewModel.playSong(song, albumSongs)
                         },
                         onSongMoreClick = { song ->
                             triggerHaptic(view)
                             songForPlaylistDialog = song
                         },
                         onClose = {
                             triggerHaptic(view)
                             selectedAlbumName = null
                         }
                     )
                 }
             }
 
             // Custom Playlist Details Overlay
             AnimatedVisibility(
                 visible = selectedPlaylistName != null,
                 enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                 exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                 modifier = Modifier.fillMaxSize()
             ) {
                 selectedPlaylistName?.let { playlistName ->
                     val playlistSongs = viewModel.getSongsInPlaylist(playlistName)
                     AlbumDetailsView(
                         albumName = playlistName,
                         songs = playlistSongs,
                         currentSong = currentSong,
                         isCustomPlaylist = true,
                         viewModel = viewModel,
                         onDeletePlaylist = {
                             viewModel.deletePlaylist(playlistName)
                             selectedPlaylistName = null
                         },
                         onRemoveSong = { song ->
                             viewModel.removeSongFromPlaylist(playlistName, song.id)
                         },
                         onSongClick = { song ->
                             triggerHaptic(view)
                             viewModel.playSong(song, playlistSongs)
                         },
                         onClose = {
                             triggerHaptic(view)
                             selectedPlaylistName = null
                         }
                     )
                 }
             }

            // Dialog: Create Playlist
            if (showCreatePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("New Custom Album/Playlist", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Enter album/playlist name...", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.createPlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    showCreatePlaylistDialog = false
                                }
                            }
                        ) {
                            Text("Create", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1C1C1E)
                )
            }

            // Dialog: Song Options (Add to Album, Rename Track, Delete Track)
            songForPlaylistDialog?.let { song ->
                AlertDialog(
                    onDismissRequest = { songForPlaylistDialog = null },
                    title = { Text(viewModel.getSongTitle(song), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Add to Custom Album",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showPlaylistSelector = song
                                        songForPlaylistDialog = null
                                    }
                                    .padding(vertical = 12.dp)
                            )
                            HorizontalDivider(color = Color.DarkGray)
                            Text(
                                "Rename Track",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showRenameDialog = song
                                        songForPlaylistDialog = null
                                    }
                                    .padding(vertical = 12.dp)
                            )
                            HorizontalDivider(color = Color.DarkGray)
                            Text(
                                "Delete from Device",
                                color = Color.Red,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showDeleteConfirmation = song
                                        songForPlaylistDialog = null
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { songForPlaylistDialog = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1C1C1E)
                )
            }

            // Dialog: Rename Track Dialog
            showRenameDialog?.let { song ->
                var renameText by remember { mutableStateOf(viewModel.getSongTitle(song)) }
                AlertDialog(
                    onDismissRequest = { showRenameDialog = null },
                    title = { Text("Rename Track", color = Color.White) },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (renameText.isNotBlank()) {
                                viewModel.renameSong(context, song, renameText.trim())
                                showRenameDialog = null
                            }
                        }) {
                            Text("Rename", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1C1C1E)
                )
            }

            // Dialog: Delete Track Confirmation
            showDeleteConfirmation?.let { song ->
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = null },
                    title = { Text("Delete Track?", color = Color.White) },
                    text = { Text("Are you sure you want to permanently delete \"${viewModel.getSongTitle(song)}\" from your device?", color = Color.LightGray) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteSong(context, song)
                            showDeleteConfirmation = null
                        }) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1C1C1E)
                )
            }

            // Dialog: Select Custom Album
            showPlaylistSelector?.let { song ->
                AlertDialog(
                    onDismissRequest = { showPlaylistSelector = null },
                    title = { Text("Select Custom Album", color = Color.White) },
                    text = {
                        if (playlists.isEmpty()) {
                            Text("You haven't created any custom albums/playlists yet.", color = Color.LightGray)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(playlists) { playlistName ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addSongToPlaylist(playlistName, song.id)
                                                showPlaylistSelector = null
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp)
                                    ) {
                                        Text(playlistName, color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPlaylistSelector = null
                            showCreatePlaylistDialog = true
                        }) {
                            Text("Create New", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPlaylistSelector = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1C1C1E)
                )
            }
        }
    }
}

// Sealed interfaces for layout refs
sealed interface LibraryRef
data class PlaylistRef(val name: String) : LibraryRef
data class AlbumRef(val name: String) : LibraryRef

@Composable
fun CreatePlaylistCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Create Album",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AlbumCard(
    albumName: String,
    songCount: Int,
    representativeSong: Song?,
    isCustomPlaylist: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(representativeSong?.albumArtUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(representativeSong?.albumArtUri) {
        thumbnail = withContext(Dispatchers.IO) {
            loadAlbumArtThumbnail(context, representativeSong?.albumArtUri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isCustomPlaylist) "💿" else "📁", fontSize = 36.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = albumName,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "$songCount Tracks",
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

@Composable
fun AlbumDetailsView(
    albumName: String,
    songs: List<Song>,
    currentSong: Song?,
    isCustomPlaylist: Boolean,
    viewModel: MusicViewModel,
    onDeletePlaylist: (() -> Unit)? = null,
    onRemoveSong: ((Song) -> Unit)? = null,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: ((Song) -> Unit)? = null,
    onClose: () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = albumName,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isCustomPlaylist && onDeletePlaylist != null) {
                    IconButton(onClick = {
                        triggerHaptic(view)
                        onDeletePlaylist()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete Album",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tracks added yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(songs) { song ->
                        SongRow(
                            song = song,
                            songTitle = viewModel.getSongTitle(song),
                            isSelected = currentSong?.id == song.id,
                            onClick = { onSongClick(song) },
                            onMoreClick = {
                                triggerHaptic(view)
                                if (isCustomPlaylist && onRemoveSong != null) {
                                    onRemoveSong(song)
                                } else if (onSongMoreClick != null) {
                                    onSongMoreClick(song)
                                }
                            },
                            showRemoveIcon = isCustomPlaylist
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    songTitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showRemoveIcon: Boolean = false
) {
    val context = LocalContext.current
    var thumbnail by remember(song.albumArtUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.albumArtUri) {
        thumbnail = withContext(Dispatchers.IO) {
            loadAlbumArtThumbnail(context, song.albumArtUri)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2C2C2E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🎵",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = songTitle,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // More context button
        IconButton(onClick = onMoreClick) {
            Icon(
                imageVector = if (showRemoveIcon) Icons.Filled.Close else Icons.Filled.MoreVert,
                contentDescription = if (showRemoveIcon) "Remove" else "Options",
                tint = if (showRemoveIcon) Color.Red.copy(alpha = 0.7f) else Color.Gray
            )
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    songTitle: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(song.albumArtUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(song.albumArtUri) {
        thumbnail = withContext(Dispatchers.IO) {
            loadAlbumArtThumbnail(context, song.albumArtUri)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C2C2E).copy(alpha = 0.85f),
                        Color(0xFF1C1C1E).copy(alpha = 0.95f)
                    )
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎵", fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = songTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onTogglePlay) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

fun loadAlbumArtThumbnail(context: Context, uri: Uri?): Bitmap? {
    if (uri == null) return null
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(120, 120), null)
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val bitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
            pfd.close()
            Bitmap.createScaledBitmap(bitmap, 120, 120, true)
        }
    } catch (e: Exception) {
        null
    }
}

private fun triggerHaptic(view: View) {
    try {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    } catch (e: Exception) {
        // Fallback silently
    }
}
