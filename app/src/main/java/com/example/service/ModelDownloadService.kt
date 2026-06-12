package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private val channelId = "model_download_channel"
    private val NOTIFICATION_ID = 8888

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EduLocal:ModelDownloadWakeLock").apply {
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
        wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EduLocal:WifiLock")
        } else {
            wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL, "EduLocal:WifiLock")
        }.apply {
            acquire()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra("model_id") ?: return START_NOT_STICKY
        val cancel = intent.getBooleanExtra("cancel", false)
        if (cancel) {
            activeDownloads[modelId]?.cancel()
            activeDownloads.remove(modelId)
            ModelDownloadManager.setDownloading(modelId, false)
            ModelDownloadManager.setStatus("Unduhan dibatalkan.")
            if (activeDownloads.isEmpty()) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val modelName = intent.getStringExtra("model_name") ?: "Model AI"
        val downloadUrl = intent.getStringExtra("download_url") ?: ""
        val fileName = intent.getStringExtra("file_name") ?: ""
        val sizeBytes = intent.getLongExtra("size_bytes", 0L)

        if (downloadUrl.isBlank() || fileName.isBlank()) {
            return START_NOT_STICKY
        }

        // Avoid starting duplicate download for the same model
        if (activeDownloads.containsKey(modelId)) {
            return START_NOT_STICKY
        }

        // Initialize foreground notification
        startServiceForeground("Mengunduh $modelName", "Memulai unduhan...")

        // Launch background download task
        val downloadJob = serviceScope.launch {
            ModelDownloadManager.setDownloading(modelId, true)
            ModelDownloadManager.setStatus("Menghubungkan ke server...")

            val modelDir = File(filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()
            val destinationFile = File(modelDir, fileName)
            val tempFile = File(modelDir, "$fileName.download")

            var retryCount = 0
            val maxRetries = 5
            var downloadResult: Result<Unit>? = null

            while (retryCount < maxRetries && this@launch.isActive) {
                if (retryCount > 0) {
                    ModelDownloadManager.setStatus("Koneksi terputus. Mencoba kembali ($retryCount/$maxRetries)...")
                    kotlinx.coroutines.delay(2000L * retryCount)
                }

                val runResult = runCatching {
                    var currentUrl = downloadUrl
                    var connection = URL(currentUrl).openConnection() as java.net.HttpURLConnection
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 60000
                    connection.readTimeout = 60000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    
                    // Add Range header if temp file exists
                    val existingLength = if (tempFile.exists()) tempFile.length() else 0L
                    if (existingLength > 0L) {
                        connection.setRequestProperty("Range", "bytes=$existingLength-")
                    }

                    connection.connect()

                    var responseCode = connection.responseCode
                    var redirectCount = 0

                    while ((responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                            responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == java.net.HttpURLConnection.HTTP_SEE_OTHER ||
                            responseCode == 307 || responseCode == 308) && redirectCount < 10) {
                        
                        var newUrl = connection.getHeaderField("Location") ?: break
                        connection.disconnect()
                        
                        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                            val base = URL(currentUrl)
                            newUrl = URL(base, newUrl).toString()
                        }
                        
                        currentUrl = newUrl
                        connection = URL(currentUrl).openConnection() as java.net.HttpURLConnection
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 60000
                        connection.readTimeout = 60000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        
                        // Re-apply Range header after redirect
                        if (existingLength > 0L) {
                            connection.setRequestProperty("Range", "bytes=$existingLength-")
                        }

                        connection.connect()
                        responseCode = connection.responseCode
                        redirectCount++
                    }

                    // If 416 (Range Not Satisfiable), delete temp file and request clean download
                    if (responseCode == 416) {
                        connection.disconnect()
                        if (tempFile.exists()) tempFile.delete()
                        currentUrl = downloadUrl
                        connection = URL(currentUrl).openConnection() as java.net.HttpURLConnection
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 60000
                        connection.readTimeout = 60000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        connection.connect()
                        
                        responseCode = connection.responseCode
                        redirectCount = 0
                        while ((responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                                responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                                responseCode == java.net.HttpURLConnection.HTTP_SEE_OTHER ||
                                responseCode == 307 || responseCode == 308) && redirectCount < 10) {
                            var newUrl = connection.getHeaderField("Location") ?: break
                            connection.disconnect()
                            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                                val base = URL(currentUrl)
                                newUrl = URL(base, newUrl).toString()
                            }
                            currentUrl = newUrl
                            connection = URL(currentUrl).openConnection() as java.net.HttpURLConnection
                            connection.instanceFollowRedirects = false
                            connection.connectTimeout = 60000
                            connection.readTimeout = 60000
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            connection.connect()
                            responseCode = connection.responseCode
                            redirectCount++
                        }
                    }

                    if (responseCode != java.net.HttpURLConnection.HTTP_OK && responseCode != 206) {
                        throw Exception("Server returned HTTP response code: $responseCode untuk URL: $currentUrl")
                    }

                    val isAppend = responseCode == 206
                    val bytesToDownload = connection.contentLengthLong
                    val totalBytes = if (isAppend) {
                        existingLength + bytesToDownload
                    } else {
                        bytesToDownload.takeIf { it > 0L } ?: sizeBytes
                    }

                    var downloadedBytes = if (isAppend) existingLength else 0L

                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile, isAppend).use { output ->
                            val buffer = ByteArray(1024 * 1024) // 1 MB buffer to avoid JNI/IO bottlenecks
                            var lastProgressUpdate = 0L
                            var lastNotificationUpdate = 0L

                            while (true) {
                                if (!this@launch.isActive) {
                                    throw kotlinx.coroutines.CancellationException("User cancelled download")
                                }
                                val bytesRead = input.read(buffer)
                                if (bytesRead <= 0) break
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val now = System.currentTimeMillis()
                                // Update UI state Flow every 500ms to keep it smooth but performant
                                if (now - lastProgressUpdate > 500) {
                                    lastProgressUpdate = now
                                    val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    ModelDownloadManager.setProgress(modelId, progress)

                                    val downloadedMb = downloadedBytes / (1024 * 1024)
                                    val totalMb = totalBytes / (1024 * 1024)
                                    val statusText = "Mengunduh $modelName: $downloadedMb/$totalMb MB"
                                    ModelDownloadManager.setStatus(statusText)
                                }

                                // Update system notification every 2000ms to reduce Binder IPC overhead
                                if (now - lastNotificationUpdate > 2000) {
                                    lastNotificationUpdate = now
                                    val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    val downloadedMb = downloadedBytes / (1024 * 1024)
                                    val totalMb = totalBytes / (1024 * 1024)
                                    updateProgressNotification("Mengunduh $modelName", progress, "$downloadedMb/$totalMb MB")
                                }
                            }

                            // Send final progress update to UI before completion
                            val finalProgress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            ModelDownloadManager.setProgress(modelId, finalProgress)
                            val finalDownloadedMb = downloadedBytes / (1024 * 1024)
                            val finalTotalMb = totalBytes / (1024 * 1024)
                            ModelDownloadManager.setStatus("Mengunduh $modelName: $finalDownloadedMb/$finalTotalMb MB")
                        }
                    }

                    // If complete, rename temp file to target destination file
                    if (downloadedBytes >= (totalBytes * 0.98).toLong()) {
                        if (destinationFile.exists()) destinationFile.delete()
                        if (!tempFile.renameTo(destinationFile)) {
                            throw Exception("Gagal menyelesaikan file model (rename failed)")
                        }
                    } else {
                        throw Exception("Unduhan terputus sebelum selesai.")
                    }
                }

                downloadResult = runResult
                if (runResult.isSuccess) {
                    break
                } else {
                    val ex = runResult.exceptionOrNull()
                    if (ex is kotlinx.coroutines.CancellationException) {
                        break
                    }
                    retryCount++
                }
            }

            activeDownloads.remove(modelId)
            ModelDownloadManager.setDownloading(modelId, false)

            val finalResult = downloadResult ?: Result.failure(Exception("Unknown download error"))

            finalResult.onSuccess {
                ModelDownloadManager.setStatus("$modelName siap digunakan.")
                showFinishedNotification("Unduhan Berhasil", "${modelName} telah selesai diunduh.")
            }.onFailure { e ->
                // Do NOT delete the tempFile on timeout/failure so it can be resumed.
                // Only delete it if the user cancelled the download explicitly.
                if (e is kotlinx.coroutines.CancellationException) {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    ModelDownloadManager.setStatus("Unduhan $modelName dibatalkan.")
                } else {
                    ModelDownloadManager.setStatus("Gagal mengunduh $modelName: ${e.localizedMessage ?: "koneksi terputus"}")
                    showFinishedNotification("Unduhan Gagal", "Gagal mengunduh $modelName")
                }
            }

            // Stop service if no active downloads left
            if (activeDownloads.isEmpty()) {
                stopSelf()
            }
        }

        activeDownloads[modelId] = downloadJob
        return START_NOT_STICKY
    }

    private fun startServiceForeground(title: String, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.example.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateProgressNotification(title: String, progress: Float, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.example.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt(), false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showFinishedNotification(title: String, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, Class.forName("com.example.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        // Use a different notification ID so the finished notification doesn't conflict with progress
        notificationManager.notify(NOTIFICATION_ID + 1 + System.currentTimeMillis().toInt() % 1000, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan progress unduhan model AI lokal"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
        serviceJob.cancel()
    }
}
