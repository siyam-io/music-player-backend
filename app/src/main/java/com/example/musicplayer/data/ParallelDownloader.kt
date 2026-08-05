package com.example.musicplayer.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "ParallelDownloader"
private const val CHANNEL_ID = "high_speed_downloads"
private const val THREAD_COUNT = 8

object ParallelDownloader {

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fast Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "High-speed multi-threaded downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun downloadFile(
        context: Context,
        urlStr: String,
        title: String,
        extension: String,
        onProgress: (Int, String) -> Unit = { _, _ -> },
        onComplete: (Boolean, File?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fileName = "$cleanTitle.$extension"
        val targetDir = if (extension == "mp4") {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val outputFile = File(targetDir, fileName)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("⚡ Downloading: $cleanTitle")
            .setContentText("Connecting to 8 parallel streams...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)

        notificationManager.notify(notificationId, builder.build())

        try {
            val url = URL(urlStr)
            val headConn = url.openConnection() as HttpURLConnection
            headConn.requestMethod = "HEAD"
            headConn.connectTimeout = 8000
            headConn.readTimeout = 8000
            headConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            headConn.connect()

            val contentLength = headConn.contentLengthLong
            val acceptRanges = headConn.getHeaderField("Accept-Ranges")
            val supportsRanges = contentLength > 0 && (acceptRanges == "bytes" || headConn.responseCode in 200..206)

            Log.d(TAG, "Content-Length: $contentLength bytes, Supports Ranges: $supportsRanges")

            if (!supportsRanges || contentLength < 500_000) {
                // Fallback to single stream fast download
                downloadSingleThread(urlStr, outputFile, builder, notificationManager, notificationId, contentLength, onProgress)
            } else {
                // High-Speed 8-Parallel Threads Chunk Downloader
                downloadParallel(urlStr, outputFile, contentLength, builder, notificationManager, notificationId, onProgress)
            }

            // Success Notification
            val doneBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("✅ Download Complete!")
                .setContentText("$cleanTitle saved to ${if (extension == "mp4") "Videos" else "Music"}")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setProgress(0, 0, false)

            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            notificationManager.notify(notificationId, doneBuilder.build())
            mainHandler.post {
                onComplete(true, outputFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Parallel download failed for $title", e)
            val failBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("❌ Download Failed")
                .setContentText(e.message ?: "Network error")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setOngoing(false)
                .setProgress(0, 0, false)

            notificationManager.notify(notificationId, failBuilder.build())
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                onComplete(false, null)
            }
        }
    }

    private suspend fun downloadParallel(
        urlStr: String,
        outputFile: File,
        totalBytes: Long,
        builder: NotificationCompat.Builder,
        manager: NotificationManager,
        notificationId: Int,
        onProgress: (Int, String) -> Unit
    ) = coroutineScope {
        val raf = RandomAccessFile(outputFile, "rw")
        raf.setLength(totalBytes)
        raf.close()

        val downloadedBytes = AtomicLong(0)
        val chunkSize = totalBytes / THREAD_COUNT
        val startTime = System.currentTimeMillis()

        val jobs = (0 until THREAD_COUNT).map { index ->
            val startByte = index * chunkSize
            val endByte = if (index == THREAD_COUNT - 1) totalBytes - 1 else (startByte + chunkSize - 1)

            async(Dispatchers.IO) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL(urlStr)
                    conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Range", "bytes=$startByte-$endByte")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000

                    val inputStream = conn.inputStream
                    val partRaf = RandomAccessFile(outputFile, "rw")
                    partRaf.seek(startByte)

                    val buffer = ByteArray(32 * 1024) // 32KB buffer for speed
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        partRaf.write(buffer, 0, bytesRead)
                        val total = downloadedBytes.addAndGet(bytesRead.toLong())

                        val percent = ((total * 100) / totalBytes).toInt().coerceIn(0, 100)
                        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
                        val speedMBs = if (elapsedTime > 0) (total / (1024.0 * 1024.0 * elapsedTime)) else 0.0
                        val speedStr = String.format("%.1f MB/s", speedMBs)

                        builder.setContentText("⚡ $speedStr | $percent%")
                            .setProgress(100, percent, false)
                        manager.notify(notificationId, builder.build())
                        onProgress(percent, speedStr)
                    }

                    partRaf.close()
                    inputStream.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Chunk $index error", e)
                    throw e
                } finally {
                    conn?.disconnect()
                }
            }
        }

        jobs.awaitAll()
    }

    private fun downloadSingleThread(
        urlStr: String,
        outputFile: File,
        builder: NotificationCompat.Builder,
        manager: NotificationManager,
        notificationId: Int,
        totalBytes: Long,
        onProgress: (Int, String) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val inputStream = conn.inputStream
        val outputStream = outputFile.outputStream()
        val buffer = ByteArray(32 * 1024)
        var bytesRead: Int
        var downloaded = 0L
        val startTime = System.currentTimeMillis()

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            downloaded += bytesRead

            if (totalBytes > 0) {
                val percent = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
                val speedMBs = if (elapsedTime > 0) (downloaded / (1024.0 * 1024.0 * elapsedTime)) else 0.0
                val speedStr = String.format("%.1f MB/s", speedMBs)

                builder.setContentText("⚡ $speedStr | $percent%")
                    .setProgress(100, percent, false)
                manager.notify(notificationId, builder.build())
                onProgress(percent, speedStr)
            }
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
        conn.disconnect()
    }
}
