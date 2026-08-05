package com.example.musicplayer.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var sharedPreferences: SharedPreferences? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val eq = equalizer ?: return@OnSharedPreferenceChangeListener
        try {
            if (key == "eq_enabled") {
                eq.enabled = prefs.getBoolean("eq_enabled", false)
            } else if (key?.startsWith("eq_band_") == true) {
                val bandIndex = key.substringAfter("eq_band_").toIntOrNull()
                if (bandIndex != null) {
                    val level = prefs.getInt(key, 0)
                    eq.setBandLevel(bandIndex.toShort(), level.toShort())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        sharedPreferences = getSharedPreferences("music_player_eq", Context.MODE_PRIVATE)
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .build()

        // Initialize immediately if session ID is already set
        val initialSessionId = player.audioSessionId
        if (initialSessionId != 0 && initialSessionId != -1) {
            initEqualizer(initialSessionId)
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != -1) {
                    initEqualizer(audioSessionId)
                }
            }
        })
            
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            android.app.PendingIntent.getActivity(
                this, 0, launchIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val sessionBuilder = MediaSession.Builder(this, player)
        if (intent != null) {
            sessionBuilder.setSessionActivity(intent)
        }
        mediaSession = sessionBuilder.build()
    }

    private fun initEqualizer(audioSessionId: Int) {
        try {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId)
            
            val prefs = sharedPreferences ?: return
            eq.enabled = prefs.getBoolean("eq_enabled", false)
            
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val level = prefs.getInt("eq_band_$i", 0)
                eq.setBandLevel(i.toShort(), level.toShort())
            }
            equalizer = eq
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        equalizer?.release()
        equalizer = null
        mediaSession?.let {
            val player = it.player
            player.release()
            it.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
