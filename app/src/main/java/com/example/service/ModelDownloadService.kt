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
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class ModelDownloadService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    private val channelId = "model_download_channel"
    private val NOTIFICATION_ID = 8888

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EduLocal:ModelDownloadWakeLock").apply {
            acquire()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra("model_id") ?: return START_NOT_STICKY
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

            val result = runCatching {
                val connection = URL(downloadUrl).openConnection()
                connection.connect()
                val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: sizeBytes
                
                connection.getInputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloadedBytes = 0L
                        var lastProgressUpdate = 0L

                        while (true) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 200) { // Limit updates to 5Hz
                                lastProgressUpdate = now
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                ModelDownloadManager.setProgress(modelId, progress)

                                val downloadedMb = downloadedBytes / (1024 * 1024)
                                val totalMb = totalBytes / (1024 * 1024)
                                val statusText = "Mengunduh $modelName: $downloadedMb/$totalMb MB"
                                
                                ModelDownloadManager.setStatus(statusText)
                                updateProgressNotification("Mengunduh $modelName", progress, "$downloadedMb/$totalMb MB")
                            }
                        }
                    }
                }
            }

            activeDownloads.remove(modelId)
            ModelDownloadManager.setDownloading(modelId, false)

            result.onSuccess {
                ModelDownloadManager.setStatus("$modelName siap digunakan.")
                showFinishedNotification("Unduhan Berhasil", "${modelName} telah selesai diunduh.")
            }.onFailure { e ->
                // Delete partial file on failure to prevent corrupt model
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                ModelDownloadManager.setStatus("Gagal mengunduh $modelName: ${e.localizedMessage ?: "koneksi terputus"}")
                showFinishedNotification("Unduhan Gagal", "Gagal mengunduh $modelName")
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
        serviceJob.cancel()
    }
}
