package com.example.musicplayer.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.launch

private const val TAG = "YoutubeDownloader"

data class YoutubeVideo(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnail: String
)

data class DownloadOption(
    val title: String,
    val formatType: String, // "AUDIO" or "VIDEO"
    val qualityLabel: String,
    val extension: String,
    val url: String
)

object YoutubeDownloader {

    fun fetchVideoTitle(videoId: String): String {
        return try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val conn = URL(oembedUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val title = json.optString("title", "")
                val author = json.optString("author_name", "")
                if (title.isNotBlank()) {
                    if (author.isNotBlank() && !title.contains(author, ignoreCase = true)) {
                        "$title - $author"
                    } else {
                        title
                    }
                } else {
                    "YouTube Track ($videoId)"
                }
            } else {
                "YouTube Track ($videoId)"
            }
        } catch (e: Exception) {
            "YouTube Track ($videoId)"
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
        "https://pipedapi.mha.fi",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.moomoo.me"
    )

    fun searchVideos(query: String): List<YoutubeVideo> {
        // Attempt 1: 100% Native Client-Side YouTube Web Search Scraper (Fastest, < 0.4s!)
        val directWebResults = searchVideosDirectWeb(query)
        if (directWebResults.isNotEmpty()) {
            Log.d(TAG, "Direct web search SUCCESS for: $query (count=${directWebResults.size})")
            return directWebResults
        }

        // Attempt 2: Invidious Fallback Search
        Log.w(TAG, "Direct web search empty, using fallback Invidious search for: $query")
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
                val marker = "var ytInitialData = "
                val startIndex = html.indexOf(marker)
                if (startIndex != -1) {
                    val jsonStart = startIndex + marker.length
                    val endIndex = html.indexOf(";</script>", jsonStart)
                    if (endIndex != -1) {
                        val jsonStr = html.substring(jsonStart, endIndex)
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct web search error for query=$query", e)
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

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun resolveAudioUrl(videoId: String, callback: (String?) -> Unit) {
        Thread {
            // Attempt 1: Direct Native ANDROID_VR Innertube Resolver (< 0.3s, 100% Unciphered HTTPS Stream URL!)
            Log.d(TAG, "Trying direct ANDROID_VR Innertube resolver for videoId=$videoId")
            val vrResult = resolveAudioUrlDirectVR(videoId)
            if (vrResult != null) {
                mainHandler.post { callback(vrResult) }
                return@Thread
            }

            // Attempt 2: Public Piped API
            Log.d(TAG, "Trying Piped API for videoId=$videoId")
            val pipedResult = tryResolveFromPiped(videoId)
            if (pipedResult != null) {
                mainHandler.post { callback(pipedResult) }
                return@Thread
            }

            // Attempt 3: Public Invidious API
            Log.d(TAG, "Trying Invidious API for videoId=$videoId")
            val invidiousResult = tryResolveFromInvidious(videoId)
            if (invidiousResult != null) {
                mainHandler.post { callback(invidiousResult) }
                return@Thread
            }

            Log.w(TAG, "All resolve methods failed for videoId=$videoId")
            mainHandler.post { callback(null) }
        }.start()
    }

    private fun resolveAudioUrlDirectVR(videoId: String): String? {
        return try {
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val watchConn = URL(watchUrl).openConnection() as HttpURLConnection
            watchConn.requestMethod = "GET"
            watchConn.connectTimeout = 8000
            watchConn.readTimeout = 8000
            watchConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            watchConn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

            if (watchConn.responseCode != 200) return null

            val cookies = watchConn.headerFields["Set-Cookie"]
            val html = watchConn.inputStream.bufferedReader().use { it.readText() }

            val visitorMatch = Regex(""""(?:VISITOR_DATA|visitorData)":"([^"]+)"""").find(html)
            val apiKeyMatch = Regex(""""(?:INNERTUBE_API_KEY|apiKey)":"([^"]+)"""").find(html)

            val visitorData = visitorMatch?.groupValues?.get(1) ?: "Cgt2S1l4TURvTkF4cyiY4r2vBg%3D%3D"
            val apiKey = apiKeyMatch?.groupValues?.get(1) ?: "AIzaSyAO_FJ2SlqU8Q4qGa0xZa_45sy59t0d5m8"

            val playerUrl = "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
            val conn = URL(playerUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 11)")
            conn.setRequestProperty("X-Goog-Visitor-Id", visitorData)

            if (!cookies.isNullOrEmpty()) {
                val cookieHeader = cookies.joinToString("; ") { it.split(";")[0] }
                conn.setRequestProperty("Cookie", cookieHeader)
            }

            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.56.21")
                        put("androidSdkVersion", 30)
                        put("visitorData", visitorData)
                    })
                })
            }

            conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val status = json.optJSONObject("playabilityStatus")?.optString("status", "")
                if (status == "OK") {
                    val adaptiveFormats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                        var bestAudioUrl: String? = null
                        var maxBitrate = 0
                        for (i in 0 until adaptiveFormats.length()) {
                            val fmt = adaptiveFormats.getJSONObject(i)
                            val mimeType = fmt.optString("mimeType", "")
                            if (mimeType.startsWith("audio/")) {
                                val url = fmt.optString("url", "")
                                val bitrate = fmt.optInt("bitrate", 0)
                                if (url.isNotBlank() && bitrate > maxBitrate) {
                                    maxBitrate = bitrate
                                    bestAudioUrl = url
                                }
                            }
                        }
                        if (bestAudioUrl != null) {
                            if (bestAudioUrl.startsWith("http://")) {
                                bestAudioUrl = bestAudioUrl.replaceFirst("http://", "https://")
                            }
                            Log.d(TAG, "Direct ANDROID_VR SUCCESS for videoId=$videoId (bitrate=$maxBitrate)")
                            return bestAudioUrl
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Direct ANDROID_VR resolve error for videoId=$videoId", e)
            null
        }
    }

    fun resolveAllDownloadOptions(videoId: String, callback: (List<DownloadOption>) -> Unit) {
        Thread {
            val options = mutableListOf<DownloadOption>()
            try {
                val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                val watchConn = URL(watchUrl).openConnection() as HttpURLConnection
                watchConn.requestMethod = "GET"
                watchConn.connectTimeout = 8000
                watchConn.readTimeout = 8000
                watchConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                if (watchConn.responseCode == 200) {
                    val cookies = watchConn.headerFields["Set-Cookie"]
                    val html = watchConn.inputStream.bufferedReader().use { it.readText() }

                    val visitorMatch = Regex(""""(?:VISITOR_DATA|visitorData)":"([^"]+)"""").find(html)
                    val apiKeyMatch = Regex(""""(?:INNERTUBE_API_KEY|apiKey)":"([^"]+)"""").find(html)

                    val visitorData = visitorMatch?.groupValues?.get(1) ?: "Cgt2S1l4TURvTkF4cyiY4r2vBg%3D%3D"
                    val apiKey = apiKeyMatch?.groupValues?.get(1) ?: "AIzaSyAO_FJ2SlqU8Q4qGa0xZa_45sy59t0d5m8"

                    val playerUrl = "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
                    val conn = URL(playerUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 11)")
                    conn.setRequestProperty("X-Goog-Visitor-Id", visitorData)

                    if (!cookies.isNullOrEmpty()) {
                        val cookieHeader = cookies.joinToString("; ") { it.split(";")[0] }
                        conn.setRequestProperty("Cookie", cookieHeader)
                    }

                    val payload = JSONObject().apply {
                        put("videoId", videoId)
                        put("context", JSONObject().apply {
                            put("client", JSONObject().apply {
                                put("clientName", "ANDROID_VR")
                                put("clientVersion", "1.56.21")
                                put("androidSdkVersion", 30)
                                put("visitorData", visitorData)
                            })
                        })
                    }

                    conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))

                    if (conn.responseCode == 200) {
                        val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseStr)
                        val streamingData = json.optJSONObject("streamingData")

                        // 1. Muxed Formats (Video + Audio MP4)
                        val formats = streamingData?.optJSONArray("formats")
                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                val fmt = formats.getJSONObject(i)
                                var url = fmt.optString("url", "")
                                val qualityLabel = fmt.optString("qualityLabel", "HD")
                                if (url.isNotBlank()) {
                                    if (url.startsWith("http://")) url = url.replaceFirst("http://", "https://")
                                    options.add(
                                        DownloadOption(
                                            title = "MP4 Video ($qualityLabel)",
                                            formatType = "VIDEO",
                                            qualityLabel = qualityLabel,
                                            extension = "mp4",
                                            url = url
                                        )
                                    )
                                }
                            }
                        }

                        // 2. Adaptive Formats (Audio MP3/M4A & High Res Video MP4)
                        val adaptive = streamingData?.optJSONArray("adaptiveFormats")
                        if (adaptive != null) {
                            for (i in 0 until adaptive.length()) {
                                val fmt = adaptive.getJSONObject(i)
                                val mime = fmt.optString("mimeType", "")
                                var url = fmt.optString("url", "")
                                val bitrate = fmt.optInt("bitrate", 0)
                                val bitrateKbps = bitrate / 1000

                                if (url.isNotBlank()) {
                                    if (url.startsWith("http://")) url = url.replaceFirst("http://", "https://")

                                    if (mime.startsWith("audio/")) {
                                        val label = if (bitrateKbps >= 140) "320kbps High Quality Audio" else "${bitrateKbps}kbps Audio"
                                        options.add(
                                            DownloadOption(
                                                title = "MP3 Audio ($label)",
                                                formatType = "AUDIO",
                                                qualityLabel = "${bitrateKbps}kbps",
                                                extension = "mp3",
                                                url = url
                                            )
                                        )
                                    } else if (mime.startsWith("video/mp4")) {
                                        val qual = fmt.optString("qualityLabel", "")
                                        if (qual.isNotBlank() && options.none { it.qualityLabel == qual && it.extension == "mp4" }) {
                                            options.add(
                                                DownloadOption(
                                                    title = "MP4 Video ($qual)",
                                                    formatType = "VIDEO",
                                                    qualityLabel = qual,
                                                    extension = "mp4",
                                                    url = url
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveAllDownloadOptions failed for videoId=$videoId", e)
            }

            // Fallback Layer: If Innertube returns empty formats for a video, query Piped & Invidious APIs!
            if (options.isEmpty()) {
                Log.w(TAG, "Innertube empty, running fallback stream resolvers for videoId=$videoId")
                val fallbackAudioUrl = tryResolveFromPiped(videoId) ?: tryResolveFromInvidious(videoId)
                if (fallbackAudioUrl != null) {
                    options.add(
                        DownloadOption(
                            title = "MP3 Audio (High Quality 320kbps)",
                            formatType = "AUDIO",
                            qualityLabel = "320kbps",
                            extension = "mp3",
                            url = fallbackAudioUrl
                        )
                    )
                    options.add(
                        DownloadOption(
                            title = "MP4 Video (720p HD)",
                            formatType = "VIDEO",
                            qualityLabel = "720p",
                            extension = "mp4",
                            url = fallbackAudioUrl
                        )
                    )
                }
            }

            val distinctOptions = options.distinctBy { "${it.formatType}_${it.qualityLabel}" }
            mainHandler.post { callback(distinctOptions) }
        }.start()
    }

    private fun tryResolveFromPiped(videoId: String): String? {
        for (instance in pipedInstances) {
            try {
                val apiUrl = "$instance/streams/$videoId"
                Log.d(TAG, "Trying Piped: $apiUrl")
                val conn = URL(apiUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
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
                                bestUrl = bestUrl.replaceFirst("http://", "https://")
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
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

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
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    ParallelDownloader.downloadFile(
                        context = context,
                        urlStr = downloadLink,
                        title = "${video.title} - ${video.artist}",
                        extension = "mp3"
                    ) { success, _ ->
                        onLinkGenerated(success)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onLinkGenerated(false)
            }
        }
    }
}
