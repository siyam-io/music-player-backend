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
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

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
        "https://invidious.flokinet.to",
        "https://invidious.privacydev.net",
        "https://iv.melmac.space"
    )

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.privacydev.net"
    )

    fun searchVideos(query: String): List<YoutubeVideo> {
        // Attempt 1: 100% Native Client-Side YouTube Web Search Scraper (0.4s Sub-second search!)
        val directWebResults = searchVideosDirectWeb(query)
        if (directWebResults.isNotEmpty()) {
            Log.d(TAG, "Direct web search SUCCESS for: $query (count=${directWebResults.size})")
            return directWebResults
        }

        // Attempt 2: Client-Side NewPipeExtractor Search
        Log.w(TAG, "Direct web search empty, trying NewPipe search for: $query")
        val newPipeResults = searchVideosNewPipe(query)
        if (newPipeResults.isNotEmpty()) {
            Log.d(TAG, "NewPipe search SUCCESS for: $query (count=${newPipeResults.size})")
            return newPipeResults
        }

        // Attempt 3: Invidious Fallback Search
        Log.w(TAG, "NewPipe search empty, using fallback Invidious search for: $query")
        return searchVideosFallback(query)
    }

    private fun searchVideosDirectWeb(query: String): List<YoutubeVideo> {
        val results = mutableListOf<YoutubeVideo>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encoded"
            val conn = URL(searchUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val match = Regex("""var ytInitialData = (\{.*?});</script>""").find(html)
                if (match != null) {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    val contents = json.optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (contents != null && contents.length() > 0) {
                        for (i in 0 until contents.length()) {
                            val section = contents.getJSONObject(i)
                            val itemSection = section.optJSONObject("itemSectionRenderer")
                            val items = itemSection?.optJSONArray("contents") ?: continue

                            for (j in 0 until items.length()) {
                                val item = items.getJSONObject(j)
                                val vr = item.optJSONObject("videoRenderer") ?: continue

                                val videoId = vr.optString("videoId", "")
                                if (videoId.isBlank()) continue

                                val title = vr.optJSONObject("title")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text", "Unknown Title") ?: "Unknown Title"

                                val artist = vr.optJSONObject("ownerText")
                                    ?.optJSONArray("runs")
                                    ?.optJSONObject(0)
                                    ?.optString("text", "Unknown Artist") ?: "Unknown Artist"

                                val durationText = vr.optJSONObject("lengthText")
                                    ?.optString("simpleText", "00:00") ?: "00:00"

                                val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                                results.add(
                                    YoutubeVideo(
                                        id = videoId,
                                        title = title,
                                        artist = artist,
                                        durationText = durationText,
                                        thumbnail = thumbnail
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct web search error for query=$query", e)
        }
        return results
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
            // Attempt 1: Direct Client-Side Extraction via NewPipeExtractor
            Log.d(TAG, "Trying direct client-side NewPipeExtractor for videoId=$videoId")
            val newPipeResult = resolveAudioUrlNewPipe(videoId)
            if (newPipeResult != null) {
                callback(newPipeResult)
                return@Thread
            }

            // Attempt 2: Direct Web Scraper of ytInitialPlayerResponse
            Log.d(TAG, "Trying direct web player response for videoId=$videoId")
            val directWebResult = resolveAudioUrlDirectWeb(videoId)
            if (directWebResult != null) {
                callback(directWebResult)
                return@Thread
            }

            // Attempt 3: Public Piped API
            Log.d(TAG, "Trying Piped API for videoId=$videoId")
            val pipedResult = tryResolveFromPiped(videoId)
            if (pipedResult != null) {
                callback(pipedResult)
                return@Thread
            }

            // Attempt 4: Public Invidious API
            Log.d(TAG, "Trying Invidious API for videoId=$videoId")
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
                        streamUrl = streamUrl.replaceFirst(Regex("^http://"), "https://")
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

    private fun resolveAudioUrlDirectWeb(videoId: String): String? {
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val match = Regex("""var ytInitialPlayerResponse = (\{.*?});</script>""").find(html)
                if (match != null) {
                    val jsonStr = match.groupValues[1]
                    val json = JSONObject(jsonStr)
                    val streamingData = json.optJSONObject("streamingData")
                    val adaptiveFormats = streamingData?.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val fmt = adaptiveFormats.getJSONObject(i)
                            val mimeType = fmt.optString("mimeType", "")
                            if (mimeType.startsWith("audio/")) {
                                var streamUrl = fmt.optString("url", "")
                                if (streamUrl.isNotBlank()) {
                                    if (streamUrl.startsWith("http://")) {
                                        streamUrl = streamUrl.replaceFirst(Regex("^http://"), "https://")
                                    }
                                    Log.d(TAG, "Direct web player response SUCCESS for videoId=$videoId")
                                    return streamUrl
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Direct web resolve error for videoId=$videoId", e)
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
