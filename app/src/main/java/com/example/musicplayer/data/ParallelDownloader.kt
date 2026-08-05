package com.example.musicplayer.data

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.musicplayer.MainActivity
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
                NotificationManager.IMPORTANCE_DEFAULT
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
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
            .setProgress(100, 0, false)

        notificationManager.notify(notificationId, builder.build())

        try {
            val url = URL(urlStr)
            val probeConn = url.openConnection() as HttpURLConnection
            probeConn.requestMethod = "GET"
            probeConn.setRequestProperty("Range", "bytes=0-1")
            probeConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            probeConn.connectTimeout = 10000
            probeConn.readTimeout = 10000
            probeConn.connect()

            var contentLength = -1L
            val contentRange = probeConn.getHeaderField("Content-Range")
            if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                contentLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
            }
            if (contentLength <= 0) {
                contentLength = probeConn.contentLengthLong
            }
            probeConn.disconnect()

            Log.d(TAG, "Probe Content-Length: $contentLength bytes")

            if (contentLength < 500_000) {
                downloadSingleThread(urlStr, outputFile, builder, notificationManager, notificationId, contentLength, onProgress)
            } else {
                downloadParallel(urlStr, outputFile, contentLength, builder, notificationManager, notificationId, onProgress)
            }

            // Scan file so Android System MediaStore indexes it immediately
            val mimeType = if (extension == "mp4") "video/mp4" else "audio/mpeg"
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                arrayOf(mimeType),
                null
            )

            // Build PendingIntent for Notification Click -> Play Downloaded Music
            val playIntent = Intent(context, MainActivity::class.java).apply {
                action = "PLAY_DOWNLOADED_SONG"
                putExtra("FILE_PATH", outputFile.absolutePath)
                putExtra("TITLE", cleanTitle)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Success Notification
            val doneBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("✅ Download Complete!")
                .setContentText("Tap to play $cleanTitle 🎵")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false)

            notificationManager.notify(notificationId, doneBuilder.build())
            mainHandler.post {
                onComplete(true, outputFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Parallel download failed, falling back to System DownloadManager", e)
            try {
                // Fallback to System DownloadManager
                val dmRequest = DownloadManager.Request(Uri.parse(urlStr)).apply {
                    setTitle(cleanTitle)
                    setDescription("Downloading from YouTube")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    setDestinationInExternalPublicDir(
                        if (extension == "mp4") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC,
                        fileName
                    )
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(dmRequest)

                notificationManager.cancel(notificationId)
                mainHandler.post {
                    onComplete(true, outputFile)
                }
            } catch (fallbackErr: Exception) {
                Log.e(TAG, "System DownloadManager fallback failed", fallbackErr)
                val failBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("❌ Download Failed")
                    .setContentText(e.message ?: "Network error")
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setOngoing(false)
                    .setProgress(0, 0, false)

                notificationManager.notify(notificationId, failBuilder.build())
                mainHandler.post {
                    onComplete(false, null)
                }
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
        val lastNotifyTime = AtomicLong(0)
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
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    conn.connectTimeout = 12000
                    conn.readTimeout = 12000

                    val inputStream = conn.inputStream
                    val partRaf = RandomAccessFile(outputFile, "rw")
                    partRaf.seek(startByte)

                    val buffer = ByteArray(64 * 1024) // 64KB buffer
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        partRaf.write(buffer, 0, bytesRead)
                        val total = downloadedBytes.addAndGet(bytesRead.toLong())

                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime.get() > 500 || total >= totalBytes) {
                            lastNotifyTime.set(now)
                            val percent = if (totalBytes > 0) ((total * 100) / totalBytes).toInt().coerceIn(0, 100) else 50
                            val elapsedTime = (now - startTime) / 1000.0
                            val speedMBs = if (elapsedTime > 0) (total / (1024.0 * 1024.0 * elapsedTime)) else 0.0
                            val speedStr = String.format("%.1f MB/s", speedMBs)

                            builder.setContentText("⚡ $speedStr | $percent%")
                                .setProgress(100, percent, false)
                            manager.notify(notificationId, builder.build())
                            onProgress(percent, speedStr)
                        }
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
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        conn.connectTimeout = 12000
        conn.readTimeout = 12000

        val inputStream = conn.inputStream
        val outputStream = outputFile.outputStream()
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var downloaded = 0L
        val startTime = System.currentTimeMillis()
        var lastNotifyTime = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            downloaded += bytesRead

            val now = System.currentTimeMillis()
            if (now - lastNotifyTime > 500) {
                lastNotifyTime = now
                val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 50
                val elapsedTime = (now - startTime) / 1000.0
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
