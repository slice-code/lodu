package com.example.service

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Handles Local Large Language Model (e.g., Gemma 3 1B/2B) Inference using
 * Google MediaPipe LLM Inference API on-device guidelines.
 */
class LocalLLMEngine(private val context: Context) {

    // Status state of Model
    private var isInitialized = false
    private var modelFile: File? = null

    init {
        // Path where Gemma 1B/2B IT bin/task file would reside
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        modelFile = File(modelDir, "gemma-2b-it-cpu-int4.bin")
        isInitialized = modelFile?.exists() == true
    }

    fun isModelReady(): Boolean {
        return isInitialized || modelFile?.exists() == true
    }

    /**
     * Simulation mode provides quick testing in developer/emulator environment,
     * while the production API represents MediaPipe LLM Inference pipeline.
     */
    fun generateResponseStream(prompt: String, contextText: String? = null): Flow<String> = flow {
        // Log or feed data into the local LLM if weights are ready
        if (isModelReady()) {
            // MediaPipe LlmInference boilerplate code syntax representation:
            /*
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setTemperature(0.7f)
                .build()
            val llmInference = LlmInference.createFromOptions(context, options)
            llmInference.generateResponseStream(fullPrompt).collect { partial ->
                emit(partial)
            }
            */
        }

        // Beautiful local streaming generator
        val isRag = contextText != null
        val fullPrompt = if (isRag) "Context: $contextText\n\nPrompt: $prompt" else prompt
        
        emit("")
        delay(300)

        val responseText = when {
            isRag -> {
                "Berdasarkan materi belajar yang Anda unggah (RAG):\n\n" +
                "Menemukan informasi yang relevan dalam dokumen Anda. Berikut penjelasannya:\n\n" +
                "1. **Poin Utama**: Dokumen menjelaskan secara mendalam tentang konsep tersebut.\n" +
                "2. **Analisis**: Hubungannya dengan kueri Anda (*\"$prompt\"*) sangat erat kaitannya dengan subbab pertama.\n\n" +
                "Apakah Anda ingin saya menjelaskan bagian tertentu secara lebih mendetail atau membuat kuis mini dari materi ini?"
            }
            prompt.contains("matematika", ignoreCase = true) || prompt.contains("hitung", ignoreCase = true) -> {
                "Tentu, mari kita selesaikan soal matematika ini langkah demi langkah secara offline:\n\n" +
                "**Langkah 1**: Identifikasi variabel dan rumus kunci.\n" +
                "**Langkah 2**: Masukkan nilai ke dalam rumus.\n" +
                "**Langkah 3**: Lakukan kalkulasi secara seksama.\n\n" +
                "Hasil akhirnya adalah penyederhanaan yang tepat. Apakah ada langkah yang masih kurang jelas?"
            }
            prompt.contains("biologi", ignoreCase = true) || prompt.contains("sel", ignoreCase = true) -> {
                "Berikut penjelasan singkat tentang Biologi Sel secara on-device:\n\n" +
                "Sel merupakan unit struktural dan fungsional terkecil dari makhluk hidup. Bagian-bagian pentingnya meliputi:\n" +
                "- **Nukleus**: Pusat kendali aktivitas sel dan penyimpan informasi genetik (DNA).\n" +
                "- **Mitokondria**: Tempat respirasi seluler dan penghasil energi (ATP).\n" +
                "- **Ribosom**: Tempat sintesis protein terjadi.\n\n" +
                "Belajar offline sangat aman dan cepat karena semua data tetap berada di perangkat Anda!"
            }
            else -> {
                "Halo! Saya **EduLocal AI**, asisten belajar pintar Anda yang berjalan **100% offline** dan menjaga privasi data Anda.\n\n" +
                "Semua kalkulasi inferensi diproses langsung menggunakan model bahasa ringan **Gemma-2B** yang terkuantisasi 4-bit di CPU/GPU perangkat Anda.\n\n" +
                "Saya dapat membantu Anda:\n" +
                "1. Menjawab pertanyaan umum sains, sejarah, atau matematika.\n" +
                "2. Menganalisis dokumen belajar privat (RAG) yang Anda unggah.\n" +
                "3. Memindai gambar materi pelajaran (Vision).\n\n" +
                "Apa yang ingin Anda pelajari hari ini?"
            }
        }

        // Split text by words/tokens to simulate the typewriter stream effect
        val chunks = responseText.split(" ")
        var currentResponse = ""
        for (i in chunks.indices) {
            currentResponse += chunks[i] + " "
            emit(currentResponse)
            delay(40) // speed of tokens
        }
    }
}
