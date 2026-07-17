package com.example.musicplayer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YoutubeVideo(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnail: String
)

object YoutubeDownloader {
    private val invidiousInstances = listOf(
        "https://yewtu.be",
        "https://invidious.flokinet.to",
        "https://vid.priv.au",
        "https://inv.tux.im"
    )

    private fun getServerUrl(path: String, query: String): String {
        val isEmulator = android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.BOARD.contains("emulator")
                || android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
                || "google_sdk" == android.os.Build.PRODUCT
        val host = if (isEmulator) "10.0.2.2" else "192.168.0.103"
        return "http://$host:5082/$path?$query"
    }

    fun searchVideos(query: String): List<YoutubeVideo> {
        val results = mutableListOf<YoutubeVideo>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val serverUrl = getServerUrl("search", "q=$encodedQuery")
        
        try {
            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            
            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val jsonArray = JSONArray(response.toString())
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val id = item.getString("id")
                    val title = item.optString("title", "Unknown Title")
                    val author = item.optString("artist", "Unknown Artist")
                    val durationText = item.optString("durationText", "00:00")
                    val thumbnail = item.optString("thumbnail", "")
                    
                    results.add(
                        YoutubeVideo(
                            id = id,
                            title = title,
                            artist = author,
                            durationText = durationText,
                            thumbnail = thumbnail
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private val cobaltInstances = listOf(
        "https://api.cobalt.liubquanti.click/",
        "https://subito-c.meowing.de/",
        "https://nuko-c.meowing.de/",
        "https://api.qwkuns.me/",
        "https://melon.clxxped.lol/"
    )

    fun resolveAudioUrl(videoId: String, callback: (String?) -> Unit) {
        val serverUrl = getServerUrl("resolve", "id=$videoId")
        
        Thread {
            try {
                val conn = URL(serverUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()
                    
                    val responseJson = JSONObject(response.toString())
                    if (responseJson.has("url")) {
                        val streamUrl = responseJson.getString("url")
                        if (streamUrl.isNotBlank()) {
                            callback(streamUrl)
                            return@Thread
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            callback(null) // Return null if failed
        }.start()
    }

    fun startAudioDownload(context: Context, video: YoutubeVideo, onLinkGenerated: (Boolean) -> Unit) {
        resolveAudioUrl(video.id) { downloadLink ->
            if (downloadLink == null) {
                onLinkGenerated(false)
                return@resolveAudioUrl
            }
            
            try {
                // Enqueue system download request
                val request = DownloadManager.Request(Uri.parse(downloadLink)).apply {
                    setTitle(video.title)
                    setDescription("Downloading audio from YouTube")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    
                    val cleanTitle = video.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val cleanArtist = video.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MUSIC,
                        "$cleanTitle - $cleanArtist.mp3"
                    )
                }
                
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                onLinkGenerated(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onLinkGenerated(false)
            }
        }
    }
}
