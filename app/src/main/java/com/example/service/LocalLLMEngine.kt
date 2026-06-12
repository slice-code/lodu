package com.example.service

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles Local Large Language Model (Gemma 2B) Inference using
 * Google MediaPipe LLM Inference API.
 */
class LocalLLMEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var activeModelFileName: String = "qwen2.5-0.5b-instruct-q8.task"

    fun selectModel(fileName: String) {
        if (activeModelFileName != fileName) {
            activeModelFileName = fileName
            closeEngine()
        }
    }

    suspend fun selectAndLoadModel(fileName: String) = withContext(Dispatchers.IO) {
        if (activeModelFileName != fileName || llmInference == null) {
            activeModelFileName = fileName
            closeEngine()
            if (isModelReady()) {
                initializeInference()
            }
        }
    }

    fun isEngineInitialized(): Boolean = llmInference != null

    private fun getModelFile(): File {
        return File(File(context.filesDir, "models"), activeModelFileName)
    }

    private fun initializeInference() {
        if (llmInference != null) return
        try {
            // MediaPipe LlmInferenceOptions for version 0.10.x
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(getModelFile().absolutePath)
                .setMaxTokens(1024)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isModelReady(): Boolean = getModelFile().exists()

    /**
     * Menghasilkan respon streaming menggunakan MediaPipe LLM Inference API.
     */
    fun generateResponseStream(prompt: String, contextText: String? = null): Flow<String> = callbackFlow {
        if (!isModelReady()) {
            trySend("Model $activeModelFileName belum ditemukan. Silakan unduh di menu Kelola Model.")
            close()
            return@callbackFlow
        }

        if (llmInference == null) {
            initializeInference()
        }

        val fullPrompt = if (contextText != null) {
            "<start_of_turn>user\nKonteks: $contextText\n\nPertanyaan: $prompt<end_of_turn>\n<start_of_turn>model\n"
        } else {
            "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
        }

        var currentResponse = ""

        try {
            // Native MediaPipe streaming generation using ProgressListener
            llmInference?.generateResponseAsync(fullPrompt, ProgressListener { partialResult, done ->
                currentResponse += partialResult
                trySend(currentResponse)
                if (done) {
                    close()
                }
            })
        } catch (e: Exception) {
            trySend("Error AI: ${e.localizedMessage}")
            close()
        }

        awaitClose {
            // No resources need to be freed per-flow in this SDK version
        }
    }
    
    fun closeEngine() {
        llmInference?.close()
        llmInference = null
    }
}
