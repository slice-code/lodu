package com.example.service

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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
    private val modelFile: File by lazy {
        File(File(context.filesDir, "models"), "gemma-2b-it-cpu-int4.bin")
    }

    init {
        if (isModelReady()) {
            initializeInference()
        }
    }

    private fun initializeInference() {
        if (llmInference != null) return
        try {
            // MediaPipe LlmInferenceOptions for version 0.10.x
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                // Beberapa versi MediaPipe mungkin menggunakan nama property yang berbeda 
                // atau menyembunyikan temperature/topK di builder utama.
                // Kita gunakan parameter minimal yang pasti ada.
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isModelReady(): Boolean = modelFile.exists()

    /**
     * Menghasilkan respon streaming menggunakan MediaPipe LLM Inference API.
     */
    fun generateResponseStream(prompt: String, contextText: String? = null): Flow<String> = callbackFlow {
        if (!isModelReady()) {
            trySend("Model gemma-2b belum ditemukan. Silakan unduh di menu Kelola Model.")
            close()
            return@callbackFlow
        }

        withContext(Dispatchers.IO) {
            if (llmInference == null) {
                initializeInference()
            }

            val fullPrompt = if (contextText != null) {
                "<start_of_turn>user\nKonteks: $contextText\n\nPertanyaan: $prompt<end_of_turn>\n<start_of_turn>model\n"
            } else {
                "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
            }

            try {
                // MediaPipe 0.10.x generateResponse runs on calling thread
                val result = llmInference?.generateResponse(fullPrompt)
                if (result != null) {
                    // Simulate streaming character by character for smooth UI
                    var currentText = ""
                    val words = result.split(" ")
                    for (word in words) {
                        currentText += "$word "
                        trySend(currentText)
                        kotlinx.coroutines.delay(30)
                    }
                }
            } catch (e: Exception) {
                trySend("Error AI: ${e.localizedMessage}")
            } finally {
                close()
            }
        }
        
        awaitClose { }
    }
    
    fun closeEngine() {
        llmInference?.close()
        llmInference = null
    }
}
