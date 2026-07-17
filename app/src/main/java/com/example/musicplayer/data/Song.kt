package com.example.musicplayer.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val path: String,
    val duration: Long,
    val albumArtUri: Uri? = null
)
