package com.example.data.model

import android.content.Context
import java.io.File

/**
 * Model metadata aligned with xororz/local-dream [Model].
 */
data class SdDreamModel(
    val id: String,
    val name: String,
    val description: String,
    val generationSize: Int = 512,
    val textEmbeddingSize: Int = 768,
    val runOnCpu: Boolean = true,
    val useCpuClip: Boolean = false,
    val isSdxl: Boolean = false,
    val defaultPrompt: String = "",
    val defaultNegativePrompt: String = ""
) {
    companion object {
        private const val MODELS_DIR = "models"

        fun getModelsDir(context: Context): File = File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }

        fun isModelDownloaded(context: Context, modelId: String): Boolean {
            val modelDir = File(getModelsDir(context), modelId)
            if (!modelDir.exists() || !modelDir.isDirectory) return false
            val files = modelDir.listFiles()
            return files != null && files.isNotEmpty()
        }
    }
}
