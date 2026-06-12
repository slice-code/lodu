package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.service.ModelDownloadService
import com.example.service.ModelDownloadManager
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID
import com.example.data.model.ChatSession
import org.json.JSONArray
import org.json.JSONObject

class EduLocalViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = EduLocalRepository(database.chatDao(), database.documentDao())

    // AI Engines
    val llmEngine = LocalLLMEngine(application)
    val ragPipeline = LocalRAGPipeline(application, repository)
    val visionEngine = LocalVisionEngine(application)
    val stableDiffusionEngine = LocalStableDiffusionEngine(application)

    // UI States
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    val chatSessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessage>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                kotlinx.coroutines.flow.flowOf(emptyList())
            }
        }
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

    val downloadingModelIds: StateFlow<Set<String>> = ModelDownloadManager.downloadingModelIds
    val modelDownloadStatus: StateFlow<String> = ModelDownloadManager.modelDownloadStatus
    val modelDownloadProgress: StateFlow<Map<String, Float>> = ModelDownloadManager.modelDownloadProgress

    // Model loading states
    private val _isModelLoading = MutableStateFlow(false)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    // Gallery Sketches states
    private val _savedSketches = MutableStateFlow<List<File>>(emptyList())
    val savedSketches: StateFlow<List<File>> = _savedSketches.asStateFlow()

    // Active local GPU / hardware acceleration backend
    private val _activeBackend = MutableStateFlow(GpuAccelerationBackend.VULKAN)
    val activeBackend: StateFlow<GpuAccelerationBackend> = _activeBackend.asStateFlow()

    fun setAccelerationBackend(backend: GpuAccelerationBackend) {
        _activeBackend.value = backend
    }

    private val _selectedLlmModelId = MutableStateFlow("qwen-2.5-0.5b-it-q8")
    val selectedLlmModelId: StateFlow<String> = _selectedLlmModelId.asStateFlow()

    fun selectLlmModel(modelId: String) {
        val model = _availableModels.value.find { it.id == modelId && it.type == LocalModelFile.ModelType.LLM } ?: return
        _selectedLlmModelId.value = modelId
        loadActiveLlmModel()
    }

    fun loadActiveLlmModel() {
        viewModelScope.launch {
            val modelId = _selectedLlmModelId.value
            val model = _availableModels.value.find { it.id == modelId && it.type == LocalModelFile.ModelType.LLM } ?: return@launch
            if (!model.isDownloaded) {
                _isModelLoaded.value = false
                return@launch
            }
            
            _isModelLoading.value = true
            withContext(Dispatchers.IO) {
                llmEngine.selectAndLoadModel(model.localFileName)
            }
            _isModelLoaded.value = llmEngine.isEngineInitialized()
            _isModelLoading.value = false
        }
    }

    init {
        loadModelList()
        loadSavedSketches()
        loadActiveLlmModel()
        viewModelScope.launch {
            ModelDownloadManager.downloadingModelIds.collect {
                loadModelList()
                loadActiveLlmModel()
            }
        }
        viewModelScope.launch {
            repository.allSessions.collect { sessions ->
                if (_activeSessionId.value == null) {
                    if (sessions.isNotEmpty()) {
                        _activeSessionId.value = sessions.first().id
                    } else {
                        createNewSession("Sesi Tutor Baru", "general-tutor")
                    }
                }
            }
        }
    }

    private fun getCustomModels(): List<LocalModelFile> {
        val prefs = getApplication<Application>().getSharedPreferences("custom_models_prefs", android.content.Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_models_list", null) ?: return emptyList()
        val list = mutableListOf<LocalModelFile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val fileName = obj.getString("localFileName")
                val isDownloaded = isFileExists(fileName, obj.optLong("sizeBytes", 0L))
                list.add(
                    LocalModelFile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = LocalModelFile.ModelType.valueOf(obj.getString("type")),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        isDownloaded = isDownloaded,
                        downloadUrl = "",
                        localFileName = fileName,
                        description = obj.optString("description", "Model kustom eksternal."),
                        isResumable = isTempFileExists(fileName)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveCustomModel(model: LocalModelFile) {
        val prefs = getApplication<Application>().getSharedPreferences("custom_models_prefs", android.content.Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_models_list", null)
        val array = if (jsonStr != null) {
            try { JSONArray(jsonStr) } catch(e: Exception) { JSONArray() }
        } else {
            JSONArray()
        }
        
        val obj = JSONObject().apply {
            put("id", model.id)
            put("name", model.name)
            put("type", model.type.name)
            put("sizeBytes", model.sizeBytes)
            put("localFileName", model.localFileName)
            put("description", model.description)
        }
        array.put(obj)
        prefs.edit().putString("custom_models_list", array.toString()).apply()
    }

    private fun loadModelList() {
        val hardcodedModels = listOf(
            LocalModelFile(
                id = "qwen-2.5-0.5b-it-q8",
                name = "Qwen2.5 0.5B Instruct Q8 (MediaPipe)",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 546660344L,
                isDownloaded = isFileExists("qwen2.5-0.5b-instruct-q8.task", 546660344L),
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                localFileName = "qwen2.5-0.5b-instruct-q8.task",
                description = "Model Qwen2.5 0.5B Instruct format .task LiteRT Community yang bebas token dan ringan untuk perangkat Android.",
                isResumable = isTempFileExists("qwen2.5-0.5b-instruct-q8.task")
            ),
            LocalModelFile(
                id = "deepseek-r1-qwen-1.5b",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 1650000000L,
                isDownloaded = isFileExists("deepseek-r1-distill-qwen-1.5b-q8.task", 1650000000L),
                downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
                localFileName = "deepseek-r1-distill-qwen-1.5b-q8.task",
                description = "Model penalaran DeepSeek-R1 terdistilasi dari Qwen 1.5B, dioptimalkan untuk performa tinggi pada perangkat mobile menggunakan LiteRT.",
                isResumable = isTempFileExists("deepseek-r1-distill-qwen-1.5b-q8.task")
            ),
            LocalModelFile(
                id = "qwen-2.5-1.5b-it",
                name = "Qwen2.5 1.5B Instruct",
                type = LocalModelFile.ModelType.LLM,
                sizeBytes = 1650000000L,
                isDownloaded = isFileExists("qwen2.5-1.5b-instruct-q8.task", 1650000000L),
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                localFileName = "qwen2.5-1.5b-instruct-q8.task",
                description = "Model Qwen2.5 1.5B Instruct dengan akurasi tinggi untuk Bahasa Indonesia dan koding.",
                isResumable = isTempFileExists("qwen2.5-1.5b-instruct-q8.task")
            ),
            LocalModelFile(
                id = "stable-diffusion-1.5-mnn-int8",
                name = "Stable Diffusion 1.5 MNN INT8",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 860000000L,
                isDownloaded = isFileExists("sd15_mnn_int8.bundle", 860000000L),
                downloadUrl = "https://huggingface.co/litert-community/stable-diffusion-1.5-mnn/resolve/main/sd15_mnn_int8.bundle",
                localFileName = "sd15_mnn_int8.bundle",
                description = "Model Stable Diffusion 1.5 dioptimalkan untuk GPU ponsel menggunakan runtime MNN INT8.",
                isResumable = isTempFileExists("sd15_mnn_int8.bundle")
            ),
            LocalModelFile(
                id = "sdxl-turbo-qnn-mobile",
                name = "SDXL Turbo Mobile LCM (Snapdragon)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1650000000L,
                isDownloaded = isFileExists("sdxl_turbo_qnn.bundle", 1650000000L),
                downloadUrl = "https://huggingface.co/litert-community/sdxl-turbo-qnn/resolve/main/sdxl_turbo_qnn.bundle",
                localFileName = "sdxl_turbo_qnn.bundle",
                description = "Model SDXL Turbo cepat dioptimalkan untuk Qualcomm NPU Snapdragon.",
                isResumable = isTempFileExists("sdxl_turbo_qnn.bundle")
            ),
            LocalModelFile(
                id = "animagine-xl-mini",
                name = "Animagine XL Mini (Anime)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1450000000L,
                isDownloaded = isFileExists("animagine_xl_mini.bundle", 1450000000L),
                downloadUrl = "https://huggingface.co/litert-community/animagine-xl-mnn/resolve/main/animagine_xl_mini.bundle",
                localFileName = "animagine_xl_mini.bundle",
                description = "Model visual anime/manga offline berkualitas tinggi untuk ilustrasi kreatif Anda.",
                isResumable = isTempFileExists("animagine_xl_mini.bundle")
            ),
            LocalModelFile(
                id = "sd-v1.5-highres",
                name = "Stable Diffusion v1.5 High-Res",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1950000000L,
                isDownloaded = isFileExists("sd_v1.5_highres.bundle", 1950000000L),
                downloadUrl = "https://huggingface.co/litert-community/stable-diffusion-v1.5-highres/resolve/main/sd_v1.5_highres.bundle",
                localFileName = "sd_v1.5_highres.bundle",
                description = "Model gambar presisi tinggi dengan detail tajam untuk diagram sains.",
                isResumable = isTempFileExists("sd_v1.5_highres.bundle")
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
            )
        )
        val customModels = getCustomModels()
        val models = hardcodedModels + customModels
        _availableModels.value = models
        calculateStorage(models)
    }

    private fun isFileExists(fileName: String, expectedSize: Long = 0L): Boolean {
        val file = File(File(getApplication<Application>().filesDir, "models"), fileName)
        if (!file.exists()) return false
        if (expectedSize > 0L) {
            // Check if file size is at least 95% of expected size to verify completeness
            return file.length() >= (expectedSize * 0.95).toLong()
        }
        return file.length() > 0L
    }

    private fun isTempFileExists(fileName: String): Boolean {
        val file = File(File(getApplication<Application>().filesDir, "models"), "$fileName.download")
        return file.exists() && file.length() > 0L
    }

    private fun calculateStorage(models: List<LocalModelFile>) {
        val totalBytes = models.filter { it.isDownloaded }.sumOf { it.sizeBytes }
        _totalStorageUsed.value = String.format("%.2f GB", totalBytes / (1024 * 1024 * 1024.0))
    }

    fun downloadModel(modelId: String) {
        val model = _availableModels.value.find { it.id == modelId } ?: return
        if (model.downloadUrl.isBlank() || downloadingModelIds.value.contains(modelId)) return

        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra("model_id", modelId)
            putExtra("model_name", model.name)
            putExtra("download_url", model.downloadUrl)
            putExtra("file_name", model.localFileName)
            putExtra("size_bytes", model.sizeBytes)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelDownload(modelId: String) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra("model_id", modelId)
            putExtra("cancel", true)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearDownloadStatus() {
        ModelDownloadManager.setStatus("")
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            val model = _availableModels.value.find { it.id == modelId } ?: return@launch
            val file = File(File(getApplication<Application>().filesDir, "models"), model.localFileName)
            if (file.exists()) file.delete()
            val tempFile = File(File(getApplication<Application>().filesDir, "models"), "${model.localFileName}.download")
            if (tempFile.exists()) tempFile.delete()
            
            // Remove custom model metadata from SharedPreferences if it exists
            val prefs = getApplication<Application>().getSharedPreferences("custom_models_prefs", android.content.Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("custom_models_list", null)
            if (jsonStr != null) {
                try {
                    val array = JSONArray(jsonStr)
                    val newArray = JSONArray()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        if (obj.getString("id") != modelId) {
                            newArray.put(obj)
                        }
                    }
                    prefs.edit().putString("custom_models_list", newArray.toString()).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            loadModelList()
        }
    }

    fun importCustomModel(
        uri: Uri,
        name: String,
        type: LocalModelFile.ModelType,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val contentResolver = context.contentResolver
            
            var fileName = ""
            var fileSize = 0L
            
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = it.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = it.getLong(sizeIndex)
                }
            }
            
            if (fileName.isBlank()) {
                fileName = "custom_model_${System.currentTimeMillis()}"
                if (type == LocalModelFile.ModelType.STABLE_DIFFUSION) {
                    fileName += ".bundle"
                } else {
                    fileName += ".task"
                }
            }
            
            val modelDir = File(context.filesDir, "models")
            if (!modelDir.exists()) modelDir.mkdirs()
            val destinationFile = File(modelDir, fileName)
            
            _isGenerating.value = true
            
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        destinationFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            
            _isGenerating.value = false
            
            result.onSuccess {
                val size = if (fileSize > 0L) fileSize else destinationFile.length()
                val customModel = LocalModelFile(
                    id = "custom-${UUID.randomUUID()}",
                    name = name,
                    type = type,
                    sizeBytes = size,
                    isDownloaded = true,
                    downloadUrl = "",
                    localFileName = fileName,
                    description = "Model kustom eksternal diimpor dari perangkat."
                )
                saveCustomModel(customModel)
                loadModelList()
                onSuccess()
            }.onFailure { e ->
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                onError(e.localizedMessage ?: "Gagal menyalin file model")
            }
        }
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun createNewSession(title: String, characterId: String) {
        viewModelScope.launch {
            val newSession = ChatSession(title = title, characterId = characterId)
            repository.insertSession(newSession)
            _activeSessionId.value = newSession.id
        }
    }

    fun updateSessionTitle(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = null
            }
        }
    }

    fun sendMessage(text: String, attachedUri: Uri? = null, attachedType: MessageType? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val sessionId = _activeSessionId.value ?: return@launch
            val userMsg = ChatMessage(
                sessionId = sessionId,
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
                sessionId = sessionId,
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
                val sessionId = _activeSessionId.value ?: return@launch
                repository.insertMessage(ChatMessage(
                    sessionId = sessionId,
                    text = "Sketsa hasil: \"$prompt\"",
                    sender = MessageSender.USER,
                    type = MessageType.DIAGRAM,
                    attachmentPath = file.absolutePath
                ))
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val sessionId = _activeSessionId.value ?: return@launch
            repository.deleteSession(sessionId)
        }
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
