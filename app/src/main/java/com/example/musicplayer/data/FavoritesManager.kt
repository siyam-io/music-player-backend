package com.example.musicplayer.data

import android.content.Context
import android.content.SharedPreferences

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("music_player_favs", Context.MODE_PRIVATE)

    fun addFavorite(songId: Long) {
        prefs.edit().putBoolean(songId.toString(), true).apply()
    }

    fun removeFavorite(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }

    fun isFavorite(songId: Long): Boolean {
        return prefs.getBoolean(songId.toString(), false)
    }

    fun getFavoriteIds(): Set<Long> {
        return prefs.all.keys.mapNotNull { it.toLongOrNull() }.toSet()
    }
}
