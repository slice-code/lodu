package com.example.ui.viewmodel

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
    val stableDiffusionEngine = LocalStableDiffusionEngine(application)

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

    private val downloadManager = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1

    fun setAccelerationBackend(backend: GpuAccelerationBackend) {
        _activeBackend.value = backend
    }

    init {
        loadModelList()
        loadSavedSketches()
        registerDownloadReceiver()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    loadModelList()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            getApplication<Application>().registerReceiver(receiver, filter)
        }
    }

    private fun loadModelList() {
        val models = listOf(
            LocalModelFile(
                id = "gemma-3-1b-it-int4",
                name = "Gemma 3 1B IT INT4 (MediaPipe)",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 879000000L,
                isDownloaded = llmEngine.isModelReady(),
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
                localFileName = "gemma3-1b-it-int4.task",
                description = "Model Gemma 3 1B IT format .task resmi LiteRT Community untuk MediaPipe LLM Inference Android."
            ),
            LocalModelFile(
                id = "bge-small-en",
                name = "BGE Small Vector Embeddings",
                type = LocalModelFile.ModelType.EMBEDDING,
                sizeBytes = 120000000L,
                isDownloaded = true,
                downloadUrl = "",
                localFileName = "bge-small-en-v1.5.onnx",
                description = "Mekanisme RAG untuk memahami dokumen Anda secara lokal."
            ),
            LocalModelFile(
                id = "stable-diffusion-1.5-mnn-int8",
                name = "Stable Diffusion 1.5 MNN INT8",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 2100000000L,
                isDownloaded = isFileExists("sd15_mnn_int8.bundle"),
                downloadUrl = "",
                localFileName = "sd15_mnn_int8.bundle",
                description = "Paket model SD 1.5 mobile untuk Alibaba MNN CPU/GPU. Tambahkan file bundle manual karena URL publik belum dikonfirmasi."
            ),
            LocalModelFile(
                id = "sdxl-turbo-qnn-mobile",
                name = "SDXL Turbo Qualcomm QNN",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 3600000000L,
                isDownloaded = isFileExists("sdxl_turbo_qnn.bundle"),
                downloadUrl = "",
                localFileName = "sdxl_turbo_qnn.bundle",
                description = "Paket SDXL Turbo mobile untuk Qualcomm QNN SDK. Tambahkan file bundle manual karena URL publik belum dikonfirmasi."
            )
        )
        _availableModels.value = models
        calculateStorage(models)
    }

    private fun isFileExists(fileName: String): Boolean {
        return File(File(getApplication<Application>().filesDir, "models"), fileName).exists()
    }

    private fun calculateStorage(models: List<LocalModelFile>) {
        val totalBytes = models.filter { it.isDownloaded }.sumOf { it.sizeBytes }
        _totalStorageUsed.value = String.format("%.2f GB", totalBytes / (1024 * 1024 * 1024.0))
    }

    fun downloadModel(modelId: String) {
        val model = _availableModels.value.find { it.id == modelId } ?: return
        if (model.downloadUrl.isBlank()) return
        val modelDir = File(getApplication<Application>().filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        val destinationFile = File(modelDir, model.localFileName)

        val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
            .setTitle("Mengunduh AI Model: ${model.name}")
            .setDescription("Mengunduh data model AI lokal...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))

        downloadId = downloadManager.enqueue(request)
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = _availableModels.value.find { it.id == modelId } ?: return@launch
            val file = File(File(getApplication<Application>().filesDir, "models"), model.localFileName)
            if (file.exists()) file.delete()
            loadModelList()
        }
    }

    fun sendMessage(text: String, attachedUri: Uri? = null, attachedType: MessageType? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val userMsg = ChatMessage(
                text = text,
                sender = MessageSender.USER,
                type = attachedType ?: MessageType.TEXT,
                attachmentPath = attachedUri?.toString()
            )
            repository.insertMessage(userMsg)
            _isGenerating.value = true

            var retrievedContext: String? = null
            if (attachedType == MessageType.FILE) {
                retrievedContext = ragPipeline.retrieveRelevantContext(text)
            }

            val assistantMsgId = UUID.randomUUID().toString()
            var assistantMsg = ChatMessage(
                id = assistantMsgId,
                text = "...",
                sender = MessageSender.ASSISTANT,
                rContext = retrievedContext
            )
            repository.insertMessage(assistantMsg)

            llmEngine.generateResponseStream(text, retrievedContext).collect { partialText ->
                if (partialText.isNotBlank()) {
                    assistantMsg = assistantMsg.copy(text = partialText)
                    repository.insertMessage(assistantMsg)
                }
            }

            _isGenerating.value = false
        }
    }

    fun indexStudyMaterial(uri: Uri, fileName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = ragPipeline.processDocument(uri, fileName)
            _isIndexing.value = false
            result.onSuccess { onSuccess() }
            result.onFailure { onError(it.localizedMessage ?: "Gagal memproses dokumen") }
        }
    }

    fun removeStudyMaterial(doc: StudyDocument) {
        viewModelScope.launch { repository.deleteDocument(doc) }
    }

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
            val actualSeed = if (seed == -1L) (1000..9999).random().toLong() else seed

            for (currStep in 1..steps) {
                _diffusionStep.value = currStep
                _diffusionStatus.value = "Generating step $currStep..."
                kotlinx.coroutines.delay(50)
            }

            val bitmap = stableDiffusionEngine.generateDiagramDetailed(
                prompt, negativePrompt, modelId, loraModel, aspectRatio, cfgScale, steps, actualSeed, activeBackend.value.displayName
            )
            _generatedDiagram.value = bitmap
            _diffusionProgress.value = false
            _diffusionStatus.value = "Selesai!"
        }
    }

    fun insertImageMessage(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val file = File(context.cacheDir, "sd_gen_${System.currentTimeMillis()}.png")
            try {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                repository.insertMessage(ChatMessage(
                    text = "Sketsa hasil: \"$prompt\"",
                    sender = MessageSender.USER,
                    type = MessageType.DIAGRAM,
                    attachmentPath = file.absolutePath
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearChatHistory() }
    }

    fun loadSavedSketches() {
        val dir = File(getApplication<Application>().filesDir, "saved_sketches")
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
        _savedSketches.value = files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun saveSketch(bitmap: Bitmap, prompt: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val dir = File(getApplication<Application>().filesDir, "saved_sketches")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "sketch_${System.currentTimeMillis()}.png")
            try {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                loadSavedSketches()
                onSuccess()
            } catch (e: Exception) { onError(e.localizedMessage ?: "Error") }
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
