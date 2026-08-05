package com.example.musicplayer.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "YoutubeDownloader"

data class YoutubeVideo(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnail: String
)

class AppDownloader private constructor() : Downloader() {
    companion object {
        private var instance: AppDownloader? = null
        fun getInstance(): AppDownloader {
            if (instance == null) {
                instance = AppDownloader()
            }
            return instance!!
        }
    }

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = httpMethod
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

        for ((headerName, headerValueList) in headers) {
            if (headerName != null && headerValueList != null) {
                for (valItem in headerValueList) {
                    conn.addRequestProperty(headerName, valItem)
                }
            }
        }

        if (dataToSend != null && (httpMethod == "POST" || httpMethod == "PUT")) {
            conn.doOutput = true
            conn.outputStream.write(dataToSend)
        }

        val responseCode = conn.responseCode
        val responseMessage = conn.responseMessage ?: ""
        val responseHeaders = conn.headerFields ?: emptyMap()
        val cleanHeaders = mutableMapOf<String, List<String>>()
        for ((k, v) in responseHeaders) {
            if (k != null && v != null) {
                cleanHeaders[k] = v
            }
        }

        val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseBody = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

        return Response(responseCode, responseMessage, cleanHeaders, responseBody, request.url())
    }
}

object YoutubeDownloader {
    @Volatile
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        NewPipe.init(AppDownloader.getInstance())
                        isInitialized = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing NewPipeExtractor", e)
                    }
                }
            }
        }
    }

    private val invidiousInstances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be",
        "https://invidious.flokinet.to"
    )

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de"
    )

    private fun getServerUrl(path: String, query: String): String {
        return "https://music-player-backend-2-9yqo.onrender.com/$path?$query"
    }

    fun searchVideos(query: String): List<YoutubeVideo> {
        // Attempt 1: Direct Client-Side NewPipeExtractor Search (Fast & Reliable on Phone IP)
        val newPipeResults = searchVideosNewPipe(query)
        if (newPipeResults.isNotEmpty()) {
            Log.d(TAG, "NewPipeExtractor search SUCCESS for: $query (count=${newPipeResults.size})")
            return newPipeResults
        }

        // Attempt 2: Server Search
        Log.w(TAG, "NewPipe search empty, trying server search for: $query")
        val serverResults = searchVideosServer(query)
        if (serverResults.isNotEmpty()) {
            return serverResults
        }

        // Attempt 3: Invidious Fallback Search
        Log.w(TAG, "Server search empty, using fallback Invidious search for: $query")
        return searchVideosFallback(query)
    }

    private fun searchVideosNewPipe(query: String): List<YoutubeVideo> {
        ensureInitialized()
        val results = mutableListOf<YoutubeVideo>()
        try {
            val searchExtractor: SearchExtractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                ""
            )
            searchExtractor.fetchPage()
            val page = searchExtractor.initialPage
            for (item in page.items) {
                val streamItem = item as? org.schabi.newpipe.extractor.stream.StreamInfoItem
                val url = item.url ?: ""
                val videoId = if (url.contains("v=")) {
                    url.substringAfter("v=").substringBefore("&")
                } else {
                    url.substringAfterLast("/")
                }
                
                if (videoId.isNotBlank() && videoId.length in 10..12) {
                    val durationSeconds = streamItem?.duration ?: 0L
                    val minutes = durationSeconds / 60
                    val seconds = durationSeconds % 60
                    val durationText = String.format("%02d:%02d", minutes, seconds)

                    results.add(
                        YoutubeVideo(
                            id = videoId,
                            title = streamItem?.name ?: item.name ?: "Unknown Title",
                            artist = streamItem?.uploaderName ?: "Unknown Artist",
                            durationText = durationText,
                            thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe search failed for query=$query", e)
        }
        return results
    }

    private fun searchVideosServer(query: String): List<YoutubeVideo> {
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
        return results
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

    fun resolveAudioUrl(videoId: String, callback: (String?) -> Unit) {
        Thread {
            // Attempt 1: Direct Client-Side Extraction via NewPipeExtractor (Fastest & No IP Locking issues!)
            Log.d(TAG, "Trying direct client-side NewPipeExtractor for videoId=$videoId")
            val newPipeResult = resolveAudioUrlNewPipe(videoId)
            if (newPipeResult != null) {
                callback(newPipeResult)
                return@Thread
            }

            // Attempt 2: Server-side resolving
            Log.d(TAG, "NewPipe extractor failed, trying server for videoId=$videoId")
            val serverResult = tryResolveFromServer(videoId)
            if (serverResult != null) {
                callback(serverResult)
                return@Thread
            }

            // Attempt 3: Piped API
            Log.d(TAG, "Server failed, trying Piped API for videoId=$videoId")
            val pipedResult = tryResolveFromPiped(videoId)
            if (pipedResult != null) {
                callback(pipedResult)
                return@Thread
            }

            // Attempt 4: Invidious API
            Log.d(TAG, "Piped failed, trying Invidious fallback for videoId=$videoId")
            val invidiousResult = tryResolveFromInvidious(videoId)
            if (invidiousResult != null) {
                callback(invidiousResult)
                return@Thread
            }

            Log.w(TAG, "All resolve methods failed for videoId=$videoId")
            callback(null)
        }.start()
    }

    private fun resolveAudioUrlNewPipe(videoId: String): String? {
        ensureInitialized()
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val audioStreams = info.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val bestAudio = audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams[0]
                var streamUrl: String? = bestAudio.url
                if (!streamUrl.isNullOrBlank()) {
                    if (streamUrl.startsWith("http://")) {
                        streamUrl = streamUrl.replaceFirst("http://", "https://")
                    }
                    Log.d(TAG, "NewPipeExtractor SUCCESS for videoId=$videoId, bitrate=${bestAudio.averageBitrate}")
                    return streamUrl
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "NewPipeExtractor resolve error for videoId=$videoId", e)
            null
        }
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
                        streamUrl = streamUrl.replaceFirst(Regex("^http://"), "https://")
                    }
                    Log.d(TAG, "Server SUCCESS for videoId=$videoId, url=$streamUrl")
                    return streamUrl
                }
                Log.w(TAG, "Server returned 200 but no valid URL")
                null
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server EXCEPTION for videoId=$videoId", e)
            null
        }
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
                                bestUrl = bestUrl.replaceFirst(Regex("^http://"), "https://")
                            }
                            Log.d(TAG, "Piped SUCCESS from $instance (bitrate=$bestBitrate)")
                            return bestUrl
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Piped $instance failed: ${e.message}")
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
                                        streamUrl = streamUrl.replaceFirst(Regex("^http://"), "https://")
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
