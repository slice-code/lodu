package com.example.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Backend health polling — same logic as local-dream ModelRunScreen.checkBackendHealth.
 */
suspend fun checkSdBackendHealth(
    backendState: StateFlow<BackendService.BackendState>,
    onHealthy: () -> Unit,
    onUnhealthy: () -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .build()

        val startTime = System.currentTimeMillis()
        val timeoutDuration = 60000L

        while (currentCoroutineContext().isActive) {
            if (backendState.value is BackendService.BackendState.Error) {
                withContext(Dispatchers.Main) { onUnhealthy() }
                break
            }

            if (System.currentTimeMillis() - startTime > timeoutDuration) {
                withContext(Dispatchers.Main) { onUnhealthy() }
                break
            }

            try {
                val request = Request.Builder()
                    .url("http://localhost:8081/health")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) { onHealthy() }
                    break
                }
            } catch (_: Exception) {
                // backend still starting
            }

            delay(100)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onUnhealthy() }
    }
}

fun isSdBackendHealthy(): Boolean {
    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url("http://localhost:8081/health")
            .get()
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }
}

object SdDreamBridge {

    fun startBackend(context: Context, modelId: String, width: Int, height: Int) {
        val intent = Intent(context, BackendService::class.java).apply {
            putExtra(BackendService.EXTRA_MODEL_ID, modelId)
            putExtra(BackendService.EXTRA_WIDTH, width)
            putExtra(BackendService.EXTRA_HEIGHT, height)
        }
        context.startForegroundService(intent)
    }

    fun stopBackend(context: Context) {
        try {
            val stopIntent = Intent(context, BackendService::class.java).apply {
                action = BackendService.ACTION_STOP
            }
            context.startService(stopIntent)
            context.stopService(Intent(context, BackendService::class.java))
        } catch (_: Exception) {
            // ignore
        }
    }

    fun stopGeneration(context: Context) {
        try {
            val intent = Intent(context, SdBackgroundGenerationService::class.java).apply {
                action = SdBackgroundGenerationService.ACTION_STOP
            }
            context.startService(intent)
            SdBackgroundGenerationService.resetState()
        } catch (_: Exception) {
            // ignore
        }
    }

    fun cleanup(context: Context) {
        stopGeneration(context)
        stopBackend(context)
        SdBackgroundGenerationService.clearCompleteState()
    }

    fun isBackendReady(): Boolean {
        val state = BackendService.backendState.value
        if (state is BackendService.BackendState.Error) return false
        return isSdBackendHealthy()
    }
}
