package com.example.musicplayer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class PlaylistsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_player_playlists", Context.MODE_PRIVATE)

    fun createPlaylist(name: String) {
        if (!prefs.contains(name)) {
            prefs.edit().putString(name, "[]").apply()
        }
    }

    fun deletePlaylist(name: String) {
        prefs.edit().remove(name).apply()
    }

    fun getPlaylists(): List<String> {
        return prefs.all.keys.toList()
    }

    fun addSongToPlaylist(playlistName: String, songId: Long) {
        val songsJson = prefs.getString(playlistName, "[]") ?: "[]"
        val jsonArray = JSONArray(songsJson)
        
        // Avoid duplicate additions
        for (i in 0 until jsonArray.length()) {
            if (jsonArray.getLong(i) == songId) return
        }
        
        jsonArray.put(songId)
        prefs.edit().putString(playlistName, jsonArray.toString()).apply()
    }

    fun removeSongFromPlaylist(playlistName: String, songId: Long) {
        val songsJson = prefs.getString(playlistName, "[]") ?: "[]"
        val jsonArray = JSONArray(songsJson)
        val newArray = JSONArray()
        
        for (i in 0 until jsonArray.length()) {
            val id = jsonArray.getLong(i)
            if (id != songId) {
                newArray.put(id)
            }
        }
        
        prefs.edit().putString(playlistName, newArray.toString()).apply()
    }

    fun getSongsInPlaylist(playlistName: String): List<Long> {
        val songsJson = prefs.getString(playlistName, "[]") ?: "[]"
        val jsonArray = JSONArray(songsJson)
        val list = mutableListOf<Long>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getLong(i))
        }
        return list
    }
}
