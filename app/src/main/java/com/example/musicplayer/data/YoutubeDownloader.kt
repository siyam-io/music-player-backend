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
import android.util.Log

private const val TAG = "YoutubeDownloader"

data class YoutubeVideo(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnail: String
)

object YoutubeDownloader {
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.in.projectsegfault.com",
        "https://api.piped.projectsegfault.com"
    )

    private val invidiousInstances = listOf(
        "https://yewtu.be",
        "https://invidious.flokinet.to",
        "https://invidious.nerdvpn.de",
        "https://inv.nadeko.net"
    )

    private fun getServerUrl(path: String, query: String): String {
        return "https://music-player-backend-2-9yqo.onrender.com/$path?$query"
    }

    fun searchVideos(query: String): List<YoutubeVideo> {
        val results = mutableListOf<YoutubeVideo>()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val serverUrl = getServerUrl("search", "q=$encodedQuery")
        
        try {
            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            
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
                    
                    if (id.isNotBlank()) {
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
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (results.isNotEmpty()) {
            return results
        }

        // Fallback Search via Invidious / Piped instances if backend returns empty
        Log.w(TAG, "Backend search empty/failed, using fallback search API for: $query")
        return searchVideosFallback(query)
    }

    private fun searchVideosFallback(query: String): List<YoutubeVideo> {
        val results = mutableListOf<YoutubeVideo>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        for (instance in invidiousInstances) {
            try {
                val apiUrl = "$instance/api/v1/search?q=$encoded&type=video"
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val body = reader.readText()
                    reader.close()

                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val videoId = item.optString("videoId", "")
                        val title = item.optString("title", "Unknown Title")
                        val author = item.optString("author", "Unknown Artist")
                        val lengthSeconds = item.optInt("lengthSeconds", 0)
                        val minutes = lengthSeconds / 60
                        val seconds = lengthSeconds % 60
                        val durationText = String.format("%02d:%02d", minutes, seconds)
                        val thumbnail = "$instance/vi/$videoId/mqdefault.jpg"

                        if (videoId.isNotBlank()) {
                            results.add(
                                YoutubeVideo(
                                    id = videoId,
                                    title = title,
                                    artist = author,
                                    durationText = durationText,
                                    thumbnail = thumbnail
                                )
                            )
                        }
                    }
                    if (results.isNotEmpty()) {
                        Log.d(TAG, "Fallback search succeeded from $instance")
                        return results
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback search failed for $instance: ${e.message}")
            }
        }
        return results
    }

    private val cobaltInstances = listOf(
        "https://api.cobalt.tools",
        "https://cobalt.canine.tools"
    )

    fun resolveAudioUrl(videoId: String, callback: (String?) -> Unit) {
        Thread {
            // Attempt 1: Try our yt-dlp backend server (uses android/mweb innertube clients)
            Log.d(TAG, "Trying yt-dlp server for videoId=$videoId")
            val serverResult = tryResolveFromServer(videoId)
            if (serverResult != null) {
                callback(serverResult)
                return@Thread
            }

            // Attempt 2: Try Piped API
            Log.d(TAG, "Server failed, trying Piped API for videoId=$videoId")
            val pipedResult = tryResolveFromPiped(videoId)
            if (pipedResult != null) {
                callback(pipedResult)
                return@Thread
            }

            // Attempt 3: Try Invidious instances
            Log.d(TAG, "Piped failed, trying Invidious fallback for videoId=$videoId")
            val invidiousResult = tryResolveFromInvidious(videoId)
            if (invidiousResult != null) {
                callback(invidiousResult)
                return@Thread
            }

            // Attempt 4: Try Cobalt API
            Log.d(TAG, "Invidious failed, trying Cobalt fallback for videoId=$videoId")
            val cobaltResult = tryResolveFromCobalt(videoId)
            if (cobaltResult != null) {
                callback(cobaltResult)
                return@Thread
            }

            Log.w(TAG, "All resolve methods failed for videoId=$videoId")
            callback(null)
        }.start()
    }

    private fun tryResolveFromPiped(videoId: String): String? {
        for (instance in pipedInstances) {
            try {
                val apiUrl = "$instance/streams/$videoId"
                Log.d(TAG, "Trying Piped: $apiUrl")
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseBody = reader.readText()
                    reader.close()

                    val json = JSONObject(responseBody)
                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null && audioStreams.length() > 0) {
                        // Find highest quality audio stream
                        var bestUrl = ""
                        var bestBitrate = 0
                        for (i in 0 until audioStreams.length()) {
                            val stream = audioStreams.getJSONObject(i)
                            val bitrate = stream.optInt("bitrate", 0)
                            val url = stream.optString("url", "")
                            if (url.isNotBlank() && bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = url
                            }
                        }
                        if (bestUrl.isNotBlank()) {
                            if (bestUrl.startsWith("http://")) {
                                bestUrl = bestUrl.replaceFirst("http://", "https://")
                            }
                            Log.d(TAG, "Piped SUCCESS from $instance (bitrate=$bestBitrate)")
                            return bestUrl
                        }
                    }
                    Log.w(TAG, "Piped $instance returned 200 but no audio streams")
                } else {
                    Log.w(TAG, "Piped $instance returned ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped $instance failed: ${e.message}")
            }
        }
        return null
    }

    private fun tryResolveFromServer(videoId: String): String? {
        val serverUrl = getServerUrl("resolve", "id=$videoId")
        Log.d(TAG, "tryResolveFromServer videoId=$videoId, url=$serverUrl")
        return try {
            val conn = URL(serverUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("Connection", "close")

            val responseCode = conn.responseCode
            Log.d(TAG, "Server responseCode=$responseCode for videoId=$videoId")

            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val responseBody = reader.readText()
                reader.close()

                val responseJson = JSONObject(responseBody)
                var streamUrl = responseJson.optString("url", "")
                if (streamUrl.isBlank()) {
                    streamUrl = responseJson.optString("direct_url", "")
                }
                if (streamUrl.isNotBlank()) {
                    if (streamUrl.startsWith("http://")) {
                        streamUrl = streamUrl.replaceFirst("http://", "https://")
                    }
                    Log.d(TAG, "Server SUCCESS for videoId=$videoId, url=$streamUrl")
                    return streamUrl
                }
                Log.w(TAG, "Server returned 200 but no valid URL")
                null
            } else {
                try {
                    val errorStream = conn.errorStream
                    if (errorStream != null) {
                        val errorBody = BufferedReader(InputStreamReader(errorStream)).readText()
                        Log.e(TAG, "Server HTTP $responseCode: $errorBody")
                    }
                } catch (ignored: Exception) {}
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server EXCEPTION for videoId=$videoId", e)
            null
        }
    }

    private fun tryResolveFromCobalt(videoId: String): String? {
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
        for (instance in cobaltInstances) {
            try {
                Log.d(TAG, "Trying Cobalt instance: $instance")
                val conn = URL(instance).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("url", youtubeUrl)
                    put("audioFormat", "mp3")
                    put("isAudioOnly", true)
                }
                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                Log.d(TAG, "Cobalt $instance responseCode=$responseCode")

                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseBody = reader.readText()
                    reader.close()
                    Log.d(TAG, "Cobalt response: $responseBody")

                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "")
                    if (status == "redirect" || status == "stream" || status == "tunnel") {
                        var streamUrl = json.optString("url", "")
                        if (streamUrl.isNotBlank()) {
                            if (streamUrl.startsWith("http://")) {
                                streamUrl = streamUrl.replaceFirst("http://", "https://")
                            }
                            Log.d(TAG, "Cobalt SUCCESS from $instance")
                            return streamUrl
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cobalt $instance failed: ${e.message}")
            }
        }
        return null
    }

    private fun tryResolveFromInvidious(videoId: String): String? {
        for (instance in invidiousInstances) {
            try {
                val apiUrl = "$instance/api/v1/videos/$videoId"
                Log.d(TAG, "Trying Invidious: $apiUrl")
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseBody = reader.readText()
                    reader.close()

                    val json = JSONObject(responseBody)
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val fmt = adaptiveFormats.getJSONObject(i)
                            val type = fmt.optString("type", "")
                            if (type.startsWith("audio/")) {
                                var streamUrl = fmt.optString("url", "")
                                if (streamUrl.isNotBlank()) {
                                    if (streamUrl.startsWith("http://")) {
                                        streamUrl = streamUrl.replaceFirst("http://", "https://")
                                    }
                                    Log.d(TAG, "Invidious SUCCESS from $instance")
                                    return streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invidious $instance failed: ${e.message}")
            }
        }
        return null
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
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    
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
