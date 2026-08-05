package com.example.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object SongScanner {
    fun scanLocalSongs(context: Context): List<Song> {
        val songList = mutableListOf<Song>()
        
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        
        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Title"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    var album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    
                    val file = File(path)
                    if (file.exists() && file.parentFile != null && file.parentFile?.name != "Music" && file.parentFile?.name != "Movies") {
                        album = file.parentFile?.name ?: album
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                    
                    songList.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            uri = contentUri,
                            path = path,
                            duration = duration,
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct Filesystem Recursive Scanner for Music & Movies Subfolders
        try {
            val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val moviesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val rootsToScan = listOf(musicRoot, moviesRoot)

            for (root in rootsToScan) {
                if (root.exists() && root.isDirectory) {
                    val allFiles = root.walkTopDown().filter { file ->
                        file.isFile && (file.extension.lowercase() in listOf("mp3", "m4a", "wav", "mp4", "aac"))
                    }

                    for (file in allFiles) {
                        if (songList.none { it.path == file.absolutePath }) {
                            val songId = file.absolutePath.hashCode().toLong()
                            val cleanTitle = file.nameWithoutExtension
                            val folderAlbumName = if (file.parentFile != null && file.parentFile?.name != "Music" && file.parentFile?.name != "Movies") {
                                file.parentFile?.name ?: "Internal Download"
                            } else {
                                "Internal Download"
                            }

                            songList.add(
                                Song(
                                    id = songId,
                                    title = cleanTitle,
                                    artist = folderAlbumName,
                                    album = folderAlbumName,
                                    uri = Uri.fromFile(file),
                                    path = file.absolutePath,
                                    duration = 0L,
                                    albumArtUri = null
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return songList
    }
}
