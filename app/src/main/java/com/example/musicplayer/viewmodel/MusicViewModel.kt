package com.example.musicplayer.viewmodel

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color
import android.content.SharedPreferences
import com.example.musicplayer.data.Song
import com.example.musicplayer.data.SongScanner
import com.example.musicplayer.data.FavoritesManager
import com.example.musicplayer.data.PlaylistsManager
import com.example.musicplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.result.IntentSenderRequest
import android.app.RecoverableSecurityException
import android.provider.MediaStore
import android.content.ContentValues
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    val deleteRequestFlow = MutableSharedFlow<IntentSenderRequest>(extraBufferCapacity = 1)
    private lateinit var renamePrefs: SharedPreferences

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    private var appContext: Context? = null

    private lateinit var favoritesManager: FavoritesManager
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private lateinit var playlistsManager: PlaylistsManager
    private val _playlists = MutableStateFlow<List<String>>(emptyList())
    val playlists: StateFlow<List<String>> = _playlists.asStateFlow()

    private lateinit var eqPrefs: SharedPreferences
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqBands = MutableStateFlow(listOf(0, 0, 0, 0, 0))
    val eqBands: StateFlow<List<Int>> = _eqBands.asStateFlow()

    fun toggleFavorite(songId: Long) {
        val currentFavs = _favoriteIds.value.toMutableSet()
        if (currentFavs.contains(songId)) {
            favoritesManager.removeFavorite(songId)
            currentFavs.remove(songId)
        } else {
            favoritesManager.addFavorite(songId)
            currentFavs.add(songId)
        }
        _favoriteIds.value = currentFavs
    }

    // Playlist controls
    fun createPlaylist(name: String) {
        playlistsManager.createPlaylist(name)
        refreshPlaylists()
    }

    fun deletePlaylist(name: String) {
        playlistsManager.deletePlaylist(name)
        refreshPlaylists()
    }

    fun addSongToPlaylist(playlistName: String, songId: Long) {
        playlistsManager.addSongToPlaylist(playlistName, songId)
    }

    fun removeSongFromPlaylist(playlistName: String, songId: Long) {
        playlistsManager.removeSongFromPlaylist(playlistName, songId)
    }

    fun getSongsInPlaylist(playlistName: String): List<Song> {
        val songIds = playlistsManager.getSongsInPlaylist(playlistName)
        return _songs.value.filter { songIds.contains(it.id) }
    }

    private fun refreshPlaylists() {
        _playlists.value = playlistsManager.getPlaylists()
    }

    // Equalizer controls
    fun toggleEqualizer(enabled: Boolean) {
        _eqEnabled.value = enabled
        eqPrefs.edit().putBoolean("eq_enabled", enabled).apply()
    }

    fun setEqualizerBand(bandIndex: Int, level: Int) {
        val current = _eqBands.value.toMutableList()
        if (bandIndex in 0..4) {
            current[bandIndex] = level
            _eqBands.value = current
            // Save millibel level (db * 100)
            eqPrefs.edit().putInt("eq_band_$bandIndex", level * 100).apply()
        }
    }

    fun initialize(context: Context) {
        if (mediaController != null) return // Already initialized

        appContext = context.applicationContext
        
        favoritesManager = FavoritesManager(context.applicationContext)
        _favoriteIds.value = favoritesManager.getFavoriteIds()

        playlistsManager = PlaylistsManager(context.applicationContext)
        refreshPlaylists()

        renamePrefs = context.getSharedPreferences("music_player_renames", Context.MODE_PRIVATE)

        // Equalizer initialization
        eqPrefs = context.getSharedPreferences("music_player_eq", Context.MODE_PRIVATE)
        _eqEnabled.value = eqPrefs.getBoolean("eq_enabled", false)
        val savedBands = mutableListOf<Int>()
        for (i in 0..4) {
            // Read millibel level and convert back to dB (level / 100)
            val millibels = eqPrefs.getInt("eq_band_$i", 0)
            savedBands.add(millibels / 100)
        }
        _eqBands.value = savedBands

        // Scan songs
        viewModelScope.launch {
            _songs.value = SongScanner.scanLocalSongs(context)
        }

        // Initialize MediaController
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return
        
        // Sync initial state
        _isPlaying.value = controller.isPlaying
        _duration.value = controller.duration.coerceAtLeast(0L)
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        updateCurrentSongFromMediaItem(controller.currentMediaItem)

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongFromMediaItem(mediaItem)
                _duration.value = controller.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _duration.value = controller.duration.coerceAtLeast(0L)
            }
        })

        if (controller.isPlaying) {
            startProgressTracker()
        }
    }

    private fun updateCurrentSongFromMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentSong.value = null
            return
        }
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        _currentSong.value = _currentQueue.value.find { it.id == songId } ?: _songs.value.find { it.id == songId }
    }

    private fun createDefaultArtwork(context: Context): Bitmap {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        
        val shader = LinearGradient(
            0f, 0f, 500f, 500f,
            Color.parseColor("#1C1B1F"), // Dark grey
            Color.parseColor("#0F0E13"), // Deep dark grey
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, 500f, 500f, paint)
        
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = 180f
        paint.textAlign = Paint.Align.CENTER
        
        val y = 250f - ((paint.descent() + paint.ascent()) / 2)
        canvas.drawText("🎵", 250f, y, paint)
        
        return bitmap
    }

    private fun getAlbumArtBytes(context: Context, uri: Uri?): ByteArray? {
        if (uri == null) return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun playSong(song: Song, customQueue: List<Song>? = null) {
        val controller = mediaController ?: return
        val context = appContext
        
        val queue = customQueue ?: _songs.value
        _currentQueue.value = queue
        val currentIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        
        if (context == null) {
            val mediaItems = queue.map { item ->
                MediaItem.Builder()
                    .setMediaId(item.id.toString())
                    .setUri(item.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setAlbumTitle(item.album)
                            .setArtworkUri(item.albumArtUri)
                            .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems)
            controller.seekTo(currentIndex, 0L)
            controller.prepare()
            controller.play()
            return
        }

        viewModelScope.launch {
            val mediaItems = queue.mapIndexed { idx, item ->
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .setArtworkUri(item.albumArtUri)
                
                // Fetch artwork bytes only for the starting song to keep UI fast
                if (idx == currentIndex) {
                    var artworkBytes = withContext(Dispatchers.IO) {
                        getAlbumArtBytes(context, item.albumArtUri)
                    }
                    if (artworkBytes == null) {
                        artworkBytes = withContext(Dispatchers.Default) {
                            val bitmap = createDefaultArtwork(context)
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            stream.toByteArray()
                        }
                    }
                    if (artworkBytes != null) {
                        metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
                
                MediaItem.Builder()
                    .setMediaId(item.id.toString())
                    .setUri(item.uri)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
            }

            controller.setMediaItems(mediaItems)
            controller.seekTo(currentIndex, 0L)
            controller.prepare()
            controller.play()
        }
    }

    fun playOnlineStream(videoTitle: String, videoArtist: String, videoThumbnail: String, streamUrl: String) {
        val song = Song(
            id = streamUrl.hashCode().toLong(),
            title = videoTitle,
            artist = videoArtist,
            album = "YouTube Live",
            uri = Uri.parse(streamUrl),
            path = "",
            duration = 0,
            albumArtUri = Uri.parse(videoThumbnail)
        )
        // Set the current song value so the Now Playing sheet updates immediately
        _currentSong.value = song
        playSong(song, listOf(song))
    }

    fun refreshSongs(context: Context) {
        viewModelScope.launch {
            _songs.value = SongScanner.scanLocalSongs(context)
            _currentSong.value?.let { current ->
                _currentSong.value = _songs.value.find { it.id == current.id } ?: current
            }
        }
    }

    fun renameSong(context: Context, song: Song, newTitle: String) {
        renamePrefs.edit().putString("song_${song.id}", newTitle).apply()
        refreshSongs(context)
    }

    fun getSongTitle(song: Song): String {
        if (!::renamePrefs.isInitialized) return song.title
        return renamePrefs.getString("song_${song.id}", song.title) ?: song.title
    }

    fun deleteSong(context: Context, song: Song) {
        viewModelScope.launch {
            try {
                if (_currentSong.value?.id == song.id) {
                    mediaController?.stop()
                    _currentSong.value = null
                }
                
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                        deleteRequestFlow.tryEmit(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } else {
                        try {
                            context.contentResolver.delete(song.uri, null, null)
                            refreshSongs(context)
                        } catch (securityException: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val recoverableSecurityException = securityException as? RecoverableSecurityException
                                    ?: throw securityException
                                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                                deleteRequestFlow.tryEmit(IntentSenderRequest.Builder(intentSender).build())
                            } else {
                                throw securityException
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNext()
        }
    }
 
    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                mediaController?.let {
                    _currentPosition.value = it.currentPosition.coerceAtLeast(0L)
                    _duration.value = it.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
    }

    override fun onCleared() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        stopProgressTracker()
        super.onCleared()
    }
}
