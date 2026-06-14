package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import com.example.R
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * HTTP generate via localhost:8081 — ported from xororz/local-dream BackgroundGenerationService.
 */
class SdBackgroundGenerationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        private const val TAG = "SdBgGeneration"
        private const val CHANNEL_ID = "sd_image_generation_channel"
        private const val NOTIFICATION_ID = 3
        const val ACTION_STOP = "com.example.SD_GENERATION_STOP"

        private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
        val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

        private val _bitmapConsumed = MutableStateFlow(false)

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun resetState() {
            _generationState.value = GenerationState.Idle
            _bitmapConsumed.value = false
        }

        fun clearCompleteState() {
            if (_generationState.value is GenerationState.Complete) {
                _generationState.value = GenerationState.Idle
            }
        }

        fun markBitmapConsumed() {
            _bitmapConsumed.value = true
        }
    }

    sealed class GenerationState {
        object Idle : GenerationState()
        data class Progress(val progress: Float, val intermediateImage: Bitmap? = null) : GenerationState()
        data class Complete(val bitmap: Bitmap, val seed: Long?) : GenerationState()
        data class Error(val message: String) : GenerationState()
    }

    private fun updateState(newState: GenerationState) {
        _generationState.value = newState
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        _isServiceRunning.value = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action} extras=${intent?.extras}")
        startForeground(NOTIFICATION_ID, createNotification(0f))

        when (intent?.action) {
            ACTION_STOP -> {
                updateState(GenerationState.Idle)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val prompt = intent?.getStringExtra("prompt")
        if (prompt == null) {
            Log.e(TAG, "prompt extra missing")
            stopSelf()
            return START_NOT_STICKY
        }

        val negativePrompt = intent.getStringExtra("negative_prompt") ?: ""
        val steps = intent.getIntExtra("steps", 28)
        val cfg = intent.getFloatExtra("cfg", 7f)
        val seed = if (intent.hasExtra("seed")) intent.getLongExtra("seed", 0) else null
        val width = intent.getIntExtra("width", 512)
        val height = intent.getIntExtra("height", 512)
        val effectiveWidth = intent.getIntExtra("effective_width", width)
        val effectiveHeight = intent.getIntExtra("effective_height", height)
        val denoiseStrength = intent.getFloatExtra("denoise_strength", 0.6f)
        val useOpenCL = intent.getBooleanExtra("use_opencl", false)
        val scheduler = intent.getStringExtra("scheduler") ?: "dpm"
        val aspectRatio = intent.getStringExtra("aspect_ratio") ?: "1:1"

        if (_generationState.value is GenerationState.Complete) {
            updateState(GenerationState.Idle)
        }
        _bitmapConsumed.value = false
        updateState(GenerationState.Progress(0f))

        serviceScope.launch {
            Log.d(TAG, "start generation promptLength=${prompt.length} steps=$steps size=${width}x$height")
            runGeneration(
                prompt,
                negativePrompt,
                steps,
                cfg,
                seed,
                width,
                height,
                effectiveWidth,
                effectiveHeight,
                denoiseStrength,
                useOpenCL,
                scheduler,
                aspectRatio
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun runGeneration(
        prompt: String,
        negativePrompt: String,
        steps: Int,
        cfg: Float,
        seed: Long?,
        width: Int,
        height: Int,
        effectiveWidth: Int,
        effectiveHeight: Int,
        denoiseStrength: Float,
        useOpenCL: Boolean,
        scheduler: String,
        aspectRatio: String
    ) = withContext(Dispatchers.IO) {
        try {
            updateState(GenerationState.Progress(0f))

            val prefs = applicationContext.getSharedPreferences("app_prefs", MODE_PRIVATE)
            val showProcess = prefs.getBoolean("show_diffusion_process", true)
            val showStride = prefs.getInt("show_diffusion_stride", 1)

            val jsonObject = JSONObject().apply {
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("steps", steps)
                put("cfg", cfg)
                put("use_cfg", true)
                put("width", width)
                put("height", height)
                put("denoise_strength", denoiseStrength)
                put("use_opencl", useOpenCL)
                put("scheduler", scheduler)
                put("show_diffusion_process", showProcess)
                put("show_diffusion_stride", showStride)
                put("aspect_ratio", aspectRatio)
                seed?.let { put("seed", it) }
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(3600, TimeUnit.SECONDS)
                .readTimeout(3600, TimeUnit.SECONDS)
                .writeTimeout(3600, TimeUnit.SECONDS)
                .callTimeout(3600, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val request = Request.Builder()
                .url("http://localhost:8081/generate")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d(TAG, "POST /generate payload=${jsonObject}")
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "generate response code=${response.code}")
                if (!response.isSuccessful) {
                    throw IOException(
                        getString(R.string.sd_error_request_failed, response.code.toString())
                    )
                }

                response.body?.let { responseBody ->
                    val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data == "[DONE]") break

                            val message = JSONObject(data)
                            when (message.optString("type")) {
                                "progress" -> {
                                    val step = message.optInt("step")
                                    val totalSteps = message.optInt("total_steps")
                                    val progress = step.toFloat() / totalSteps

                                    val b64Img = message.optString("image")
                                    var bitmap: Bitmap? = null
                                    if (b64Img.isNotEmpty()) {
                                        try {
                                            val imageBytes = Base64.getDecoder().decode(b64Img)
                                            val pw = effectiveWidth
                                            val ph = effectiveHeight
                                            val pixels = IntArray(pw * ph)
                                            for (i in 0 until pw * ph) {
                                                val index = i * 3
                                                if (index + 2 < imageBytes.size) {
                                                    val r = imageBytes[index].toInt() and 0xFF
                                                    val g = imageBytes[index + 1].toInt() and 0xFF
                                                    val b = imageBytes[index + 2].toInt() and 0xFF
                                                    pixels[i] =
                                                        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                                }
                                            }
                                            bitmap = createBitmap(pw, ph)
                                            bitmap.setPixels(pixels, 0, pw, 0, 0, pw, ph)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "decode preview failed", e)
                                        }
                                    }

                                    updateState(GenerationState.Progress(progress, bitmap))
                                    updateNotification(progress)
                                }

                                "complete" -> {
                                    val base64Image = message.optString("image")
                                    val returnedSeed = message.optLong("seed", -1).takeIf { it != -1L }
                                    val resultWidth = message.optInt("width", 512)
                                    val resultHeight = message.optInt("height", 512)

                                    if (base64Image.isEmpty()) {
                                        throw IOException("no image data")
                                    }

                                    val imageBytes = Base64.getDecoder().decode(base64Image)
                                    val bitmap = createBitmap(resultWidth, resultHeight)
                                    val pixels = IntArray(resultWidth * resultHeight)
                                    for (i in 0 until resultWidth * resultHeight) {
                                        val index = i * 3
                                        val r = imageBytes[index].toInt() and 0xFF
                                        val g = imageBytes[index + 1].toInt() and 0xFF
                                        val b = imageBytes[index + 2].toInt() and 0xFF
                                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                                    }
                                    bitmap.setPixels(
                                        pixels,
                                        0,
                                        resultWidth,
                                        0,
                                        0,
                                        resultWidth,
                                        resultHeight
                                    )

                                    updateState(GenerationState.Complete(bitmap, returnedSeed))

                                    val waitStartTime = System.currentTimeMillis()
                                    val timeoutMs = 5000L
                                    while (!_bitmapConsumed.value && isActive) {
                                        if (System.currentTimeMillis() - waitStartTime > timeoutMs) break
                                        delay(100)
                                    }
                                    stopSelf()
                                }

                                "error" -> {
                                    throw IOException(
                                        message.optString("message", getString(R.string.sd_unknown_error))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generation error", e)
            updateState(
                GenerationState.Error(
                    e.message ?: getString(R.string.sd_unknown_error)
                )
            )
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sd_generation_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.sd_generation_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(progress: Float): Notification {
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sd_generating_notify))
            .setContentText("${(progress * 100).toInt()}%")
            .setProgress(100, (progress * 100).toInt(), false)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Float) {
        notificationManager.notify(NOTIFICATION_ID, createNotification(progress))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        handleTimeout(0)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        handleTimeout(fgsType)
    }

    private fun handleTimeout(fgsType: Int) {
        Log.e(TAG, "Foreground service timeout (fgsType=$fgsType)")
        updateState(GenerationState.Error("Service timeout"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[Job]?.cancel()
        _isServiceRunning.value = false
    }
}
