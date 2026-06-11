package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.GpuAccelerationBackend
import com.example.data.model.LocalModelFile
import com.example.data.model.MessageSender
import com.example.data.model.MessageType
import com.example.data.model.StudyDocument
import com.example.data.repository.EduLocalRepository
import com.example.service.LocalLLMEngine
import com.example.service.LocalRAGPipeline
import com.example.service.LocalStableDiffusionEngine
import com.example.service.LocalVisionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EduLocalViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = EduLocalRepository(database.chatDao(), database.documentDao())

    // AI Engines
    val llmEngine = LocalLLMEngine(application)
    val ragPipeline = LocalRAGPipeline(application, repository)
    val visionEngine = LocalVisionEngine(application)
    val stableDiffusionEngine = LocalStableDiffusionEngine()

    // UI States
    val chatMessages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val indexedDocuments: StateFlow<List<StudyDocument>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Interactive States
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    // Stable Diffusion Outputs
    private val _generatedDiagram = MutableStateFlow<Bitmap?>(null)
    val generatedDiagram: StateFlow<Bitmap?> = _generatedDiagram.asStateFlow()

    private val _diffusionProgress = MutableStateFlow(false)
    val diffusionProgress: StateFlow<Boolean> = _diffusionProgress.asStateFlow()

    private val _diffusionStep = MutableStateFlow(0)
    val diffusionStep: StateFlow<Int> = _diffusionStep.asStateFlow()

    private val _totalDiffusionSteps = MutableStateFlow(20)
    val totalDiffusionSteps: StateFlow<Int> = _totalDiffusionSteps.asStateFlow()

    private val _diffusionStatus = MutableStateFlow("Ready")
    val diffusionStatus: StateFlow<String> = _diffusionStatus.asStateFlow()

    // Model Management downloads states
    private val _availableModels = MutableStateFlow<List<LocalModelFile>>(emptyList())
    val availableModels: StateFlow<List<LocalModelFile>> = _availableModels.asStateFlow()

    private val _totalStorageUsed = MutableStateFlow("0.0 GB")
    val totalStorageUsed: StateFlow<String> = _totalStorageUsed.asStateFlow()

    // Gallery Sketches states
    private val _savedSketches = MutableStateFlow<List<File>>(emptyList())
    val savedSketches: StateFlow<List<File>> = _savedSketches.asStateFlow()

    // Active local GPU / hardware acceleration backend
    private val _activeBackend = MutableStateFlow(GpuAccelerationBackend.VULKAN)
    val activeBackend: StateFlow<GpuAccelerationBackend> = _activeBackend.asStateFlow()

    fun setAccelerationBackend(backend: GpuAccelerationBackend) {
        _activeBackend.value = backend
    }

    init {
        loadModelList()
        loadSavedSketches()
    }

    private fun loadModelList() {
        val models = listOf(
            LocalModelFile(
                id = "gemma-2b-it",
                name = "Gemma 2B IT (Lightweight LLM)",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 1430000000L, // 1.43 GB
                isDownloaded = llmEngine.isModelReady(),
                downloadUrl = "https://huggingface.co/google/gemma-2b-it",
                localFileName = "gemma-2b-it-cpu-int4.bin",
                description = "Google's ultra-efficient 2B parameter model optimized for local Android CPU devices."
            ),
            LocalModelFile(
                id = "llama-3.2-1b-it",
                name = "Meta Llama 3.2 1B Instruct",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 1250000000L, // 1.25 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct",
                localFileName = "llama-3.2-1b-it-q4.bin",
                description = "Generasi baru LLM andalan Meta yang dirancang khusus untuk berjalan super-ringan pada Android CPU berdaya mikro."
            ),
            LocalModelFile(
                id = "qwen-2.5-1.5b-it",
                name = "Qwen 2.5 1.5B Instruct (Indonesian)",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 1620000000L, // 1.62 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct",
                localFileName = "qwen-2.5-1.5b-it-q4.bin",
                description = "Sangat fasih dalam percakapan Bahasa Indonesia, penalaran problem-solving koding, dan rumus matematika offline."
            ),
            LocalModelFile(
                id = "phi-3.5-mini-it",
                name = "Microsoft Phi-3.5 Mini Instruct",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 2200000000L, // 2.20 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct",
                localFileName = "phi-3.5-mini-it-q4.bin",
                description = "Model super-efisien buatan Microsoft dengan akurasi penalaran tingkat lanjut (Advanced Reasoning) setara model besar."
            ),
            LocalModelFile(
                id = "bge-small-en",
                name = "BGE Small Vector Embeddings",
                type = LocalModelFile.ModelType.EMBEDDING,
                sizeBytes = 120000000L, // 120 MB
                isDownloaded = true, // Embedded initially
                downloadUrl = "https://huggingface.co/BAAI/bge-small-en-v1.5",
                localFileName = "bge-small-en-v1.5.onnx",
                description = "Lightweight dense vector embedder mapping textbook sentences to 384 dimensions."
            ),
            LocalModelFile(
                id = "mediapipe-vision",
                name = "MediaPipe Multi-Vision Tasks Model",
                type = LocalModelFile.ModelType.VISION,
                sizeBytes = 450000000L, // 450 MB
                isDownloaded = true,
                downloadUrl = "https://developers.google.com/mediapipe",
                localFileName = "mobile_vision_tasks.task",
                description = "Includes MobileNet-v3 image classifier and text OCR scanner for material diagrams."
            ),
            LocalModelFile(
                id = "moondream2-tiny",
                name = "Moondream2 Multimodal (Tiny Vision)",
                type = LocalModelFile.ModelType.VISION,
                sizeBytes = 860000000L, // 860 MB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/vikhyat/moondream2",
                localFileName = "moondream2-q4.bin",
                description = "Model visual-language compact berukuran kecil (1.6B parameter) yang tangguh untuk menganalisa visual chat & OCR kompleks."
            ),
            LocalModelFile(
                id = "stable-diffusion-int4",
                name = "MLC Mobile Stable Diffusion (Creative)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 2100000000L, // 2.1 GB
                isDownloaded = false,
                downloadUrl = "https://mlc.ai",
                localFileName = "stable-diffusion-v1-5-int4.bin",
                description = "Highly quantized text-to-image generator generating 512x512 science doodles."
            ),
            LocalModelFile(
                id = "sdxl-turbo-mobile-lcm",
                name = "SDXL Turbo Mobile (1-Step LCM)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1420000000L, // 1.42 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/stabilityai/sdxl-turbo",
                localFileName = "sdxl_turbo_lcm_q4.bin",
                description = "Ultra-fast Realtime image synthesis that runs in just 1 single inference step on mobile."
            ),
            LocalModelFile(
                id = "animagine-xl-mini",
                name = "Animagine XL Mini (Anime Studio)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1580000000L, // 1.58 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/Linaqruf/animagine-xl-3.0",
                localFileName = "animagine_mini_q4.bin",
                description = "Lightweight anime-styled illustration checkpoint optimized for modern smartphone GPUs."
            ),
            LocalModelFile(
                id = "sd-v1.5-highres",
                name = "Stable Diffusion v1.5 (High Fidelity)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 2450000000L, // 2.45 GB
                isDownloaded = false,
                downloadUrl = "https://huggingface.co/runwayml/stable-diffusion-v1-5",
                localFileName = "sd_v1_5_fp16.bin",
                description = "Sharp, maximum resolution drawings featuring complex technical labels and science designs."
            )
        )
        _availableModels.value = models
        calculateStorage(models)
    }

    private fun calculateStorage(models: List<LocalModelFile>) {
        val totalBytes = models.filter { it.isDownloaded }.sumOf { it.sizeBytes }
        _totalStorageUsed.value = String.format("%.2f GB", totalBytes / (1024 * 1024 * 1024.0))
    }

    // Trigger fake download flow for demonstration
    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            val updated = _availableModels.value.map {
                if (it.id == modelId) it.copy(isDownloaded = true) else it
            }
            _availableModels.value = updated
            calculateStorage(updated)
        }
    }

    // Delete downloads to free space
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val updated = _availableModels.value.map {
                if (it.id == modelId && it.id != "bge-small-en" && it.id != "mediapipe-vision") {
                    it.copy(isDownloaded = false)
                } else it
            }
            _availableModels.value = updated
            calculateStorage(updated)
        }
    }

    /**
     * Sends user message and schedules local LLM stream response
     */
    fun sendMessage(text: String, attachedUri: Uri? = null, attachedType: MessageType? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save User Message
            val userMsg = ChatMessage(
                text = text,
                sender = MessageSender.USER,
                type = attachedType ?: MessageType.TEXT,
                attachmentPath = attachedUri?.toString()
            )
            repository.insertMessage(userMsg)
            _isGenerating.value = true

            // Formulate LLM call
            var retrievedContext: String? = null
            var processedImageContext: String? = null

            // 1. Check if we need to retrieve local RAG document chunks
            if (attachedType == MessageType.FILE) {
                // Query RAG context matches
                retrievedContext = ragPipeline.retrieveRelevantContext(text)
            }

            // 2. Check if we have a vision component
            if (attachedType == MessageType.IMAGE && attachedUri != null) {
                // Simulate local image loading and analyze
                // Let's create an placeholder explanation
                processedImageContext = "[Vision Task: Ditemukan Diagram Sel dengan tulisan tangan f(x) = 2x^2 + 4x - 6]"
            }

            // Compile absolute content injection
            val absolutePrompt = when {
                retrievedContext != null -> text
                processedImageContext != null -> "Analyze this image content: $processedImageContext and answer: $text"
                else -> text
            }

            // Generate Assistant response bubble, empty first
            val assistantMsgId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessage(
                id = assistantMsgId,
                text = "...",
                sender = MessageSender.ASSISTANT,
                rContext = retrievedContext
            )
            repository.insertMessage(assistantMsg)

            // Collect local LLM text generation stream
            llmEngine.generateResponseStream(absolutePrompt, retrievedContext).collect { partialText ->
                if (partialText.isNotBlank()) {
                    assistantMsg = assistantMsg.copy(text = partialText)
                    repository.insertMessage(assistantMsg)
                }
            }

            _isGenerating.value = false
        }
    }

    /**
     * RAG File Upload
     */
    fun indexStudyMaterial(uri: Uri, fileName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = ragPipeline.processDocument(uri, fileName)
            _isIndexing.value = false
            result.onSuccess {
                onSuccess()
            }
            result.onFailure {
                onError(it.localizedMessage ?: "Gagal memproses dokumen")
            }
        }
    }

    /**
     * Delete indexed study materials
     */
    fun removeStudyMaterial(doc: StudyDocument) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
        }
    }

    /**
     * Local Creative Stable Diffusion Generator (Standard version for backward compatibility)
     */
    fun generateDiffusionSketch(prompt: String) {
        generateDiffusionSketchDetailed(
            prompt = prompt,
            negativePrompt = "",
            modelId = "stable-diffusion-int4",
            loraModel = "None",
            aspectRatio = "1:1",
            cfgScale = 7.5f,
            steps = 20,
            seed = -1L
        )
    }

    /**
     * Local Creative Stable Diffusion Generator with advanced parameters
     */
    fun generateDiffusionSketchDetailed(
        prompt: String,
        negativePrompt: String,
        modelId: String,
        loraModel: String,
        aspectRatio: String,
        cfgScale: Float,
        steps: Int,
        seed: Long
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _diffusionProgress.value = true
            _generatedDiagram.value = null
            _totalDiffusionSteps.value = steps
            _diffusionStep.value = 0
            
            // Generate seed if selected random
            val actualSeed = if (seed == -1L) (1000..9999).random().toLong() else seed

            val backend = _activeBackend.value
            // Iteratively simulate diffusion steps to give a realistic interactive UI experience
            for (currStep in 1..steps) {
                _diffusionStep.value = currStep
                _diffusionStatus.value = when {
                    currStep == 1 -> "Inisialisasi latent noise dengan seed $actualSeed..."
                    currStep == steps / 4 -> "Menganalisa layer negative prompt: \"$negativePrompt\"..."
                    currStep == steps / 2 -> "Menerapkan adaptasi LoRA: $loraModel (${backend.technicalName})..."
                    currStep == (steps * 3) / 4 -> "Melakukan iterasi de-noising (CFG Scale: $cfgScale)..."
                    currStep == steps -> "Decoding latents menggunakan VAE Decoder..."
                    else -> "Mengevaluasi denoising step $currStep dari $steps (${backend.displayName})..."
                }
                // Delay based on steps and adjusted by hardware backend multiplier
                val baseDelay = (3000L / steps).coerceIn(40L, 250L)
                val adjustedDelay = (baseDelay * backend.speedMultiplier).toLong()
                kotlinx.coroutines.delay(adjustedDelay)
            }

            // Draw the canvas diagram based on inputs
            val bitmap = stableDiffusionEngine.generateDiagramDetailed(
                prompt = prompt,
                negativePrompt = negativePrompt,
                modelId = modelId,
                loraModel = loraModel,
                aspectRatio = aspectRatio,
                cfgScale = cfgScale,
                steps = steps,
                seed = actualSeed,
                backendName = backend.displayName
            )

            _generatedDiagram.value = bitmap
            _diffusionProgress.value = false
            _diffusionStatus.value = "Selesai menggambar!"
        }
    }

    fun addAssistantGreeting(text: String) {
        viewModelScope.launch {
            repository.insertMessage(
                ChatMessage(
                    text = text,
                    sender = MessageSender.ASSISTANT
                )
            )
        }
    }

    fun insertImageMessage(bitmap: android.graphics.Bitmap, prompt: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val file = java.io.File(context.cacheDir, "sd_gen_${System.currentTimeMillis()}.png")
            try {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                repository.insertMessage(
                    ChatMessage(
                        text = "Sketsa hasil generate local: \"$prompt\"",
                        sender = MessageSender.USER,
                        type = MessageType.DIAGRAM,
                        attachmentPath = file.absolutePath
                    )
                )
                repository.insertMessage(
                    ChatMessage(
                        text = "Ini adalah diagram/sketsa sains terkait \"$prompt\" yang baru saja kita buat di halaman Sketsa Kreatif. " +
                               "Semua label dan format teknis digambarkan secara komparatif dengan model Stable Diffusion lokal. Apakah ada bagian dari model ini yang ingin Anda tanyakan lebih detail?",
                        sender = MessageSender.ASSISTANT,
                        type = MessageType.TEXT
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
        }
    }

    fun loadSavedSketches() {
        val context = getApplication<Application>().applicationContext
        val dir = File(context.filesDir, "saved_sketches")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") }
        if (files != null) {
            _savedSketches.value = files.sortedByDescending { it.lastModified() }
        } else {
            _savedSketches.value = emptyList()
        }
    }

    fun saveSketch(bitmap: Bitmap, prompt: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val dir = File(context.filesDir, "saved_sketches")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val cleanPrompt = prompt.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)
            val fileName = "sketch_${System.currentTimeMillis()}_$cleanPrompt.png"
            val file = File(dir, fileName)
            try {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                loadSavedSketches()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun deleteSketch(file: File) {
        viewModelScope.launch {
            if (file.exists()) {
                file.delete()
                loadSavedSketches()
            }
        }
    }
}

class EduLocalViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EduLocalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EduLocalViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
