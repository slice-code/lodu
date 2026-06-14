package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.SdDreamModel
import com.example.data.model.SdMobileDefaults
import com.example.data.model.SdModelRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native SD backend process — ported from xororz/local-dream BackendService.
 */
class BackendService : Service() {

    private var process: Process? = null
    private lateinit var runtimeDir: File
    private var isStoppingBackend = false

    companion object {
        private const val TAG = "BackendService"
        private const val EXECUTABLE_NAME = "libstable_diffusion_core.so"
        private const val RUNTIME_DIR = "runtime_libs"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "backend_service_channel"

        const val EXTRA_MODEL_ID = "modelId"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"

        const val ACTION_STOP = "com.example.BACKEND_STOP"
        const val ACTION_RESTART = "com.example.BACKEND_RESTART"

        private object StateHolder {
            val _backendState = MutableStateFlow<BackendState>(BackendState.Idle)
        }

        val backendState: StateFlow<BackendState> = StateHolder._backendState.asStateFlow()

        private fun updateState(state: BackendState) {
            StateHolder._backendState.value = state
        }
    }

    sealed class BackendState {
        object Idle : BackendState()
        object Starting : BackendState()
        object Running : BackendState()
        data class Error(val message: String) : BackendState()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        prepareRuntimeDir()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        startForeground(NOTIFICATION_ID, createNotification(getString(R.string.sd_backend_notify)))

        when (intent?.action) {
            ACTION_STOP -> {
                stopBackend()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> stopBackend()
        }

        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val width = intent?.getIntExtra(EXTRA_WIDTH, SdMobileDefaults.DEFAULT_WIDTH)
            ?: SdMobileDefaults.DEFAULT_WIDTH
        val height = intent?.getIntExtra(EXTRA_HEIGHT, SdMobileDefaults.DEFAULT_HEIGHT)
            ?: SdMobileDefaults.DEFAULT_HEIGHT

        if (modelId != null) {
            if (process?.isAlive == true && isSdBackendHealthy()) {
                Log.i(TAG, "Backend already running and healthy, skip restart")
                updateState(BackendState.Running)
                return START_NOT_STICKY
            }
            if (process?.isAlive == true) {
                stopBackend()
            }
            val model = SdModelRepository(this).findById(modelId)
            if (model != null) {
                if (startBackend(model, width, height)) {
                    updateState(BackendState.Running)
                } else {
                    updateState(BackendState.Error("Backend start failed"))
                }
            } else {
                updateState(BackendState.Error("Model not found"))
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopBackend()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sd_backend_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.sd_backend_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sd_backend_notify_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun prepareRuntimeDir() {
        runtimeDir = File(filesDir, RUNTIME_DIR).apply { if (!exists()) mkdirs() }
        try {
            val assetNames = assets.list("qnnlibs") ?: emptyArray()
            for (fileName in assetNames) {
                val targetLib = File(runtimeDir, fileName)
                val needsCopy = !targetLib.exists() || run {
                    assets.open("qnnlibs/$fileName").use { input ->
                        targetLib.length() != input.available().toLong()
                    }
                }
                if (needsCopy) {
                    assets.open("qnnlibs/$fileName").use { input ->
                        targetLib.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                targetLib.setReadable(true, true)
                targetLib.setExecutable(true, true)
            }
        } catch (e: IOException) {
            Log.w(TAG, "qnnlibs prepare: ${e.message}")
        }
    }

    private fun startBackend(model: SdDreamModel, width: Int, height: Int): Boolean {
        Log.i(TAG, "startBackend ${model.id} ${width}x$height")
        updateState(BackendState.Starting)

        try {
            val nativeDir = applicationInfo.nativeLibraryDir
            val modelsDir = File(SdDreamModel.getModelsDir(this), model.id)
            val executableFile = File(nativeDir, EXECUTABLE_NAME)
            if (!executableFile.exists()) {
                Log.e(TAG, "Missing executable: ${executableFile.absolutePath}")
                return false
            }

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val useImg2img = prefs.getBoolean("use_img2img", true)
            val listenOnAll = prefs.getBoolean("listen_on_all_addresses", false)

            val command: List<String> = if (model.runOnCpu) {
                if (!File(modelsDir, "clip.mnn").exists() &&
                    !File(modelsDir, "clip_v2.mnn").exists()
                ) {
                    Log.e(TAG, "Missing clip model in ${modelsDir.absolutePath}")
                    return false
                }
                if (!File(modelsDir, "unet.mnn").exists() ||
                    !File(modelsDir, "vae_decoder.mnn").exists() ||
                    !File(modelsDir, "tokenizer.json").exists()
                ) {
                    Log.e(TAG, "MNN model files incomplete in ${modelsDir.absolutePath}")
                    return false
                }
                val cmd = mutableListOf(
                    executableFile.absolutePath,
                    "--type", "sd15cpu",
                    "--model_dir", modelsDir.absolutePath,
                    "--port", "8081"
                )
                if (!useImg2img) {
                    cmd += "--no_img2img"
                }
                if (File(modelsDir, "V_PRED").exists()) cmd += "--use_v_pred"
                if (listenOnAll) cmd += "--listen_all"
                cmd
            } else {
                val modelType = if (model.isSdxl) "sdxl" else "sd15npu"
                val cmd = mutableListOf(
                    executableFile.absolutePath,
                    "--type", modelType,
                    "--model_dir", modelsDir.absolutePath,
                    "--lib_dir", runtimeDir.absolutePath,
                    "--port", "8081",
                )
                if (!model.isSdxl && (width != 512 || height != 512)) {
                    val patch = if (width == height) {
                        File(modelsDir, "$width.patch").takeIf { it.exists() }
                            ?: File(modelsDir, "${width}x$height.patch")
                    } else {
                        File(modelsDir, "${width}x$height.patch")
                    }
                    if (patch.exists()) cmd += listOf("--patch", patch.absolutePath)
                }
                if (!useImg2img) {
                    cmd += "--no_img2img"
                }
                if (File(modelsDir, "V_PRED").exists()) cmd += "--use_v_pred"
                if (model.isSdxl) {
                    if (prefs.getBoolean("sdxl_lowram", true)) cmd += "--lowram"
                }
                if (listenOnAll) cmd += "--listen_all"
                cmd
            }

            val env = mutableMapOf<String, String>()
            val systemLibPaths = mutableListOf(
                runtimeDir.absolutePath,
                "/system/lib64",
                "/vendor/lib64",
                "/vendor/lib64/egl"
            )
            try {
                val maliSymlink = File("/system/vendor/lib64/egl/libGLES_mali.so")
                if (maliSymlink.exists()) {
                    val realPath = maliSymlink.canonicalPath
                    val soc = realPath.split("/").getOrNull(realPath.split("/").size - 2)
                    if (soc != null) {
                        systemLibPaths.add("/vendor/lib64/$soc")
                        systemLibPaths.add("/vendor/lib64/egl/$soc")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mali path: ${e.message}")
            }
            env["LD_LIBRARY_PATH"] = systemLibPaths.joinToString(":")
            env["DSP_LIBRARY_PATH"] = runtimeDir.absolutePath

            Log.i(TAG, "COMMAND: ${command.joinToString(" ")}")

            process = ProcessBuilder(command).apply {
                directory(File(nativeDir))
                redirectErrorStream(true)
                environment().putAll(env)
            }.start()

            startMonitorThread()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "startBackend failed", e)
            updateState(BackendState.Error("backend start failed: ${e.message}"))
            return false
        }
    }

    private fun startMonitorThread() {
        Thread {
            try {
                process?.let { proc ->
                    proc.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            Log.i(TAG, "Backend: $line")
                        }
                    }
                    val exitCode = proc.waitFor()
                    Log.i(TAG, "Backend exited $exitCode")
                    if (isStoppingBackend) {
                        updateState(BackendState.Idle)
                    } else {
                        updateState(BackendState.Error("Backend exited ($exitCode)"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "monitor error", e)
                updateState(BackendState.Error("monitor error: ${e.message}"))
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

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
        updateState(BackendState.Error("Service timeout"))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Thread {
            try {
                stopBackend()
            } catch (e: Exception) {
                Log.e(TAG, "stopBackend on timeout failed", e)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun stopBackend() {
        isStoppingBackend = true
        process?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroyForcibly()
                updateState(BackendState.Idle)
            } catch (e: Exception) {
                Log.e(TAG, "stopBackend error", e)
                updateState(BackendState.Error(e.message ?: "stop error"))
            } finally {
                process = null
            }
        }
        isStoppingBackend = false
    }
}
