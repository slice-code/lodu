package com.example.data.model

data class LocalModelFile(
    val id: String,
    val name: String,
    val type: ModelType,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val downloadUrl: String,
    val localFileName: String,
    val description: String,
    val isResumable: Boolean = false
) {
    enum class ModelType {
        LLM, VISION, EMBEDDING, STABLE_DIFFUSION, LORA
    }

    val displaySize: String
        get() = if (sizeBytes < 1024 * 1024 * 1024L) {
            String.format("%.0f MB", sizeBytes / (1024 * 1024.0))
        } else {
            String.format("%.2f GB", sizeBytes / (1024 * 1024 * 1024.0))
        }
}
