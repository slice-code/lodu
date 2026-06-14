package com.example.service

import android.content.Context
import com.example.data.model.SdDreamModel
import java.io.File

data class SdRuntimeStatus(
    val nativeCoreAvailable: Boolean,
    val modelReady: Boolean,
    val missingModelFiles: List<String> = emptyList(),
    val qnnLibsReady: Boolean = false
)

/**
 * Runtime inspection for SD model files on disk (no backend/process management).
 * Backend + generate follow local-dream via [BackendService] and [SdBackgroundGenerationService].
 */
class LocalStableDiffusionEngine(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models")
    }

    fun isNativeCoreAvailable(): Boolean {
        return File(context.applicationInfo.nativeLibraryDir, "libstable_diffusion_core.so").exists()
    }

    fun resolveModelDirPath(modelId: String): String? {
        val modelDir = File(modelsDir, modelId)
        return if (modelDir.isDirectory) modelDir.absolutePath else null
    }

    fun inspectRuntimeStatus(modelId: String): SdRuntimeStatus {
        val nativeCoreAvailable = isNativeCoreAvailable()
        val modelDir = File(SdDreamModel.getModelsDir(context), modelId)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return SdRuntimeStatus(
                nativeCoreAvailable = nativeCoreAvailable,
                modelReady = false,
                missingModelFiles = listOf("model directory")
            )
        }
        val missing = requiredModelFiles(modelDir).filterNot { it.exists() }.map { it.name }
        val runtimeDir = File(context.filesDir, "runtime_libs")
        val qnnReady = File(runtimeDir, "libQnnHtp.so").exists() &&
            File(runtimeDir, "libQnnSystem.so").exists()
        return SdRuntimeStatus(
            nativeCoreAvailable = nativeCoreAvailable,
            modelReady = missing.isEmpty(),
            missingModelFiles = missing,
            qnnLibsReady = qnnReady
        )
    }

    private fun requiredModelFiles(modelDir: File): List<File> {
        val clipV2 = File(modelDir, "clip_v2.mnn")
        val clip = File(modelDir, "clip.mnn")
        val required = mutableListOf(
            File(modelDir, "tokenizer.json"),
            if (clipV2.exists()) clipV2 else clip,
            File(modelDir, "unet.mnn"),
            File(modelDir, "vae_decoder.mnn")
        )
        // The native backend only needs these embeddings for clip_v2.mnn.
        if (clipV2.exists()) {
            required += File(modelDir, "pos_emb.bin")
            required += File(modelDir, "token_emb.bin")
        }
        return required
    }
}
