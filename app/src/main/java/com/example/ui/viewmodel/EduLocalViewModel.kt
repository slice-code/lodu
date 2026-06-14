package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.service.ModelDownloadService
import com.example.service.ModelDownloadManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
import com.example.data.model.GenerationHistory
import com.example.data.model.OpenClSupportInfo
import com.example.data.model.SdGenerationSettings
import com.example.data.model.OpenClCapability
import com.example.data.model.SdModelRepository
import com.example.data.model.SdMobileDefaults
import com.example.data.repository.EduLocalRepository
import com.example.service.LocalLLMEngine
import com.example.service.LocalRAGPipeline
import com.example.service.BackendService
import com.example.service.LocalStableDiffusionEngine
import com.example.service.SdBackgroundGenerationService
import com.example.service.SdDreamBridge
import com.example.service.SdRuntimeStatus
import com.example.service.checkSdBackendHealth
import com.example.service.LocalVisionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.UUID
import com.example.data.model.ChatSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class LastGenerationInfo(
    val prompt: String,
    val negativePrompt: String,
    val modelId: String,
    val loraModel: String,
    val aspectRatio: String,
    val customWidth: Int,
    val customHeight: Int,
    val cfgScale: Float,
    val steps: Int,
    val seed: Long,
    val usedNativeInference: Boolean,
    val backendName: String,
    val fallbackReason: String? = null
)

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

    private val _lastGenerationInfo = MutableStateFlow<LastGenerationInfo?>(null)
    val lastGenerationInfo: StateFlow<LastGenerationInfo?> = _lastGenerationInfo.asStateFlow()

    private val _sdRuntimeStatus = MutableStateFlow<SdRuntimeStatus?>(null)
    val sdRuntimeStatus: StateFlow<SdRuntimeStatus?> = _sdRuntimeStatus.asStateFlow()

    private val _isSdModelLoading = MutableStateFlow(false)
    val isSdModelLoading: StateFlow<Boolean> = _isSdModelLoading.asStateFlow()

    private val _isSdModelLoaded = MutableStateFlow(false)
    val isSdModelLoaded: StateFlow<Boolean> = _isSdModelLoaded.asStateFlow()

    private val _isCheckingSdBackend = MutableStateFlow(false)
    val isCheckingSdBackend: StateFlow<Boolean> = _isCheckingSdBackend.asStateFlow()

    val sdBackendState: StateFlow<BackendService.BackendState> = BackendService.backendState

    private val _sdLoadStatus = MutableStateFlow("")
    val sdLoadStatus: StateFlow<String> = _sdLoadStatus.asStateFlow()

    private val sdPrefs = application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)

    // Stable Diffusion History flow
    val generationHistory: StateFlow<List<GenerationHistory>> = database.generationHistoryDao().getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    private val _sdBackendAutoReason = MutableStateFlow("")
    val sdBackendAutoReason: StateFlow<String> = _sdBackendAutoReason.asStateFlow()

    private val _openClSupportInfo = MutableStateFlow<OpenClSupportInfo?>(null)
    val openClSupportInfo: StateFlow<OpenClSupportInfo?> = _openClSupportInfo.asStateFlow()

    private val _isSdBackendAuto = MutableStateFlow(isSdBackendAutoPreference())
    val isSdBackendAuto: StateFlow<Boolean> = _isSdBackendAuto.asStateFlow()

    private val _activeBackend = MutableStateFlow(loadSavedBackend())
    val activeBackend: StateFlow<GpuAccelerationBackend> = _activeBackend.asStateFlow()

    private fun isSdBackendAutoPreference(): Boolean {
        return SdMobileDefaults.isAutoBackendPreference(
            sdPrefs.getString("acceleration_backend", SdMobileDefaults.BACKEND_AUTO)
        )
    }

    private fun loadSavedBackend(): GpuAccelerationBackend {
        val savedId = sdPrefs.getString("acceleration_backend", SdMobileDefaults.BACKEND_AUTO)
        val app = getApplication<Application>()
        val resolved = SdMobileDefaults.resolveMnnBackend(app, savedId)
        _sdBackendAutoReason.value = if (SdMobileDefaults.isAutoBackendPreference(savedId)) {
            SdMobileDefaults.describeAutoBackendChoice(app, resolved)
        } else {
            ""
        }
        _openClSupportInfo.value = SdMobileDefaults.getOpenClSupportInfo(app)
        return resolved
    }

    fun refreshOpenClProbe() {
        OpenClCapability.clearCache()
        _openClSupportInfo.value = SdMobileDefaults.getOpenClSupportInfo(getApplication())
    }

    /** Terapkan deteksi otomatis CPU/OpenCL sebelum load SD (jika mode auto). */
    private fun resolveAndApplySdBackend(reloadIfChanged: Boolean = false): GpuAccelerationBackend {
        val app = getApplication<Application>()
        val savedId = sdPrefs.getString("acceleration_backend", SdMobileDefaults.BACKEND_AUTO)
        val auto = SdMobileDefaults.isAutoBackendPreference(savedId)
        _isSdBackendAuto.value = auto

        val resolved = SdMobileDefaults.resolveMnnBackend(app, savedId)
        _sdBackendAutoReason.value = if (auto) {
            SdMobileDefaults.describeAutoBackendChoice(app, resolved)
        } else {
            ""
        }
        _openClSupportInfo.value = SdMobileDefaults.getOpenClSupportInfo(app)

        val changed = _activeBackend.value != resolved
        if (changed) {
            _activeBackend.value = resolved
            if (reloadIfChanged) {
                clearSdSessionPrepared()
                _isSdModelLoaded.value = false
            }
        }
        return resolved
    }

    fun setSdBackendAuto() {
        sdPrefs.edit().putString("acceleration_backend", SdMobileDefaults.BACKEND_AUTO).apply()
        _isSdBackendAuto.value = true
        val resolved = resolveAndApplySdBackend(reloadIfChanged = false)
        _sdLoadStatus.value = "Mode otomatis: ${resolved.displayName.substringBefore(" (")}"
    }

    private val _sdGenerationSettings = MutableStateFlow(loadSdGenerationSettings())
    val sdGenerationSettings: StateFlow<SdGenerationSettings> = _sdGenerationSettings.asStateFlow()

    private fun loadSdGenerationSettings(): SdGenerationSettings {
        val profileName = sdPrefs.getString("sd_profile", SdMobileDefaults.PerformanceProfile.BALANCED.name)
        val profile = SdMobileDefaults.PerformanceProfile.entries.find { it.name == profileName }
            ?: SdMobileDefaults.recommendedProfileForDevice(getApplication())
        return SdGenerationSettings(
            steps = sdPrefs.getFloat("sd_steps", profile.steps.toFloat()),
            cfgScale = sdPrefs.getFloat("sd_cfg", profile.cfg),
            customWidth = sdPrefs.getFloat("sd_width", profile.width.toFloat()),
            customHeight = sdPrefs.getFloat("sd_height", profile.height.toFloat()),
            aspectRatio = sdPrefs.getString("sd_aspect_ratio", SdMobileDefaults.DEFAULT_ASPECT_RATIO)
                ?: SdMobileDefaults.DEFAULT_ASPECT_RATIO,
            performanceProfile = profile
        )
    }

    fun saveSdGenerationSettings(settings: SdGenerationSettings) {
        _sdGenerationSettings.value = settings
        sdPrefs.edit()
            .putFloat("sd_steps", settings.steps)
            .putFloat("sd_cfg", settings.cfgScale)
            .putFloat("sd_width", settings.customWidth)
            .putFloat("sd_height", settings.customHeight)
            .putString("sd_aspect_ratio", settings.aspectRatio)
            .putString("sd_profile", settings.performanceProfile.name)
            .apply()
    }

    fun applySdPerformanceProfile(profile: SdMobileDefaults.PerformanceProfile) {
        val preset = SdMobileDefaults.aspectPresets.first()
        saveSdGenerationSettings(
            SdGenerationSettings(
                steps = profile.steps.toFloat(),
                cfgScale = profile.cfg,
                customWidth = profile.width.toFloat(),
                customHeight = profile.height.toFloat(),
                aspectRatio = preset.label,
                performanceProfile = profile
            )
        )
    }

    fun setAccelerationBackend(backend: GpuAccelerationBackend) {
        if (!isSdBackendAutoPreference() && _activeBackend.value == backend) return
        sdPrefs.edit().putString("acceleration_backend", backend.id).apply()
        _isSdBackendAuto.value = false
        _sdBackendAutoReason.value = ""
        _activeBackend.value = SdMobileDefaults.resolveMnnBackend(getApplication(), backend.id)
    }

    fun refreshSdRuntimeStatus(modelId: String) {
        _sdRuntimeStatus.value = stableDiffusionEngine.inspectRuntimeStatus(modelId)
    }

    private fun validateSelectedSdRuntime(modelId: String): Boolean {
        val status = stableDiffusionEngine.inspectRuntimeStatus(modelId)
        _sdRuntimeStatus.value = status
        if (!status.nativeCoreAvailable) {
            _isSdModelLoaded.value = false
            _sdLoadStatus.value = "Native runtime Stable Diffusion tidak tersedia"
            return false
        }
        if (!status.modelReady) {
            _isSdModelLoaded.value = false
            _sdLoadStatus.value = "Model belum lengkap: ${status.missingModelFiles.joinToString(", ")}"
            return false
        }
        return true
    }

    private val _selectedSdModelId = MutableStateFlow(loadSavedSdModelId())
    val selectedSdModelId: StateFlow<String> = _selectedSdModelId.asStateFlow()

    private fun loadSavedSdModelId(): String {
        val saved = sdPrefs.getString("selected_sd_model", "anythingv5cpu") ?: "anythingv5cpu"
        return saved.takeIf { isSupportedSdModelId(it) } ?: "anythingv5cpu"
    }

    private fun isSupportedSdModelId(modelId: String): Boolean {
        return SdModelRepository(getApplication()).findById(modelId) != null
    }

    private fun normalizeSelectedSdModel(models: List<LocalModelFile>) {
        val supportedSdModels = models.filter {
            it.type == LocalModelFile.ModelType.STABLE_DIFFUSION && isSupportedSdModelId(it.id)
        }
        val current = _selectedSdModelId.value
        if (supportedSdModels.none { it.id == current }) {
            val fallback = supportedSdModels.firstOrNull()?.id ?: "anythingv5cpu"
            _selectedSdModelId.value = fallback
            sdPrefs.edit().putString("selected_sd_model", fallback).apply()
            clearSdSessionPrepared()
            _isSdModelLoaded.value = false
        }
    }

    private val sdLoadMutex = Mutex()
    private var sdPreparedModelId: String? = null
    private var sdPrepareJob: Job? = null
    private var sdGenerationProgressJob: Job? = null

    private fun markSdSessionPrepared(modelId: String) {
        sdPreparedModelId = modelId
    }

    private fun clearSdSessionPrepared() {
        sdPreparedModelId = null
    }

    fun isSdBackendSessionPrepared(): Boolean {
        return sdPreparedModelId == _selectedSdModelId.value && SdDreamBridge.isBackendReady()
    }

    /** Fast UI sync when backend was already prepared this session (no service restart). */
    fun syncSdBackendUiState() {
        if (isSdBackendSessionPrepared()) {
            _isSdModelLoaded.value = true
            _isSdModelLoading.value = false
            _isCheckingSdBackend.value = false
            if (_sdLoadStatus.value.isBlank()) {
                _sdLoadStatus.value = "Model sudah siap"
            }
        }
    }

    fun cleanupSdDream() {
        sdGenerationProgressJob?.cancel()
        SdDreamBridge.cleanup(getApplication())
        clearSdSessionPrepared()
        _isSdModelLoaded.value = false
        _isSdModelLoading.value = false
        _isCheckingSdBackend.value = false
    }

    fun selectSdModel(modelId: String) {
        if (!isSupportedSdModelId(modelId)) return
        val model = _availableModels.value.find {
            it.id == modelId && it.type == LocalModelFile.ModelType.STABLE_DIFFUSION
        } ?: return
        val modelChanged = _selectedSdModelId.value != modelId
        if (!modelChanged) return
        _selectedSdModelId.value = modelId
        sdPrefs.edit().putString("selected_sd_model", modelId).apply()
        clearSdSessionPrepared()
        _isSdModelLoaded.value = false
        loadActiveSdModel(force = true)
    }

    /**
     * Ensures the SD backend is running once per model session (local-dream ModelRunScreen pattern).
     */
    fun ensureSdBackendReady() {
        resolveAndApplySdBackend(reloadIfChanged = false)
        if (isSdBackendSessionPrepared()) {
            syncSdBackendUiState()
            return
        }
        sdPrepareJob?.cancel()
        sdPrepareJob = viewModelScope.launch {
            loadActiveSdModel()
        }
    }

    private fun resolveGenerationDimensions(
        aspectRatio: String,
        customWidth: Int,
        customHeight: Int
    ): Pair<Int, Int> {
        val settings = _sdGenerationSettings.value
        val width = if (customWidth > 0) {
            SdMobileDefaults.clampDimension(customWidth)
        } else {
            SdMobileDefaults.clampDimension(settings.customWidth.toInt())
                .takeIf { it > 0 } ?: SdMobileDefaults.widthForAspectRatio(aspectRatio)
        }
        val height = if (customHeight > 0) {
            SdMobileDefaults.clampDimension(customHeight)
        } else {
            SdMobileDefaults.clampDimension(settings.customHeight.toInt())
                .takeIf { it > 0 } ?: SdMobileDefaults.heightForAspectRatio(aspectRatio)
        }
        return width to height
    }

    fun loadActiveSdModel(force: Boolean = false) {
        viewModelScope.launch {
            prepareSdBackendLocked(force)
        }
    }

    private suspend fun prepareSdBackendLocked(force: Boolean = false) {
        resolveAndApplySdBackend(reloadIfChanged = false)
        sdLoadMutex.withLock {
            val modelId = _selectedSdModelId.value
            val model = _availableModels.value.find {
                it.id == modelId && it.type == LocalModelFile.ModelType.STABLE_DIFFUSION
            }
            refreshSdRuntimeStatus(modelId)

            if (model == null || !model.isDownloaded) {
                _isSdModelLoaded.value = false
                _sdLoadStatus.value = if (model != null) {
                    "Model belum diunduh"
                } else {
                    "Model tidak ditemukan"
                }
                return@withLock
            }

            if (!validateSelectedSdRuntime(modelId)) {
                return@withLock
            }

            val (width, height) = resolveGenerationDimensions(
                _sdGenerationSettings.value.aspectRatio,
                _sdGenerationSettings.value.customWidth.toInt(),
                _sdGenerationSettings.value.customHeight.toInt()
            )

            if (!force && isSdBackendSessionPrepared()) {
                _isSdModelLoaded.value = true
                _sdLoadStatus.value = "Model sudah siap"
                return@withLock
            }

            if (force) {
                SdDreamBridge.stopBackend(getApplication())
                clearSdSessionPrepared()
                _isSdModelLoaded.value = false
            }

            if (_isSdModelLoading.value || _isCheckingSdBackend.value) {
                // Mutex serializes concurrent prepare calls.
            }

            val backendRunning = BackendService.backendState.value is BackendService.BackendState.Running
            val sameModel = sdPreparedModelId == modelId

            if (backendRunning && sameModel && !force) {
                _isCheckingSdBackend.value = true
                _sdLoadStatus.value = "Menghubungkan ke backend..."
            } else {
                if (backendRunning) {
                    SdDreamBridge.stopBackend(getApplication())
                }
                _isSdModelLoading.value = true
                _sdLoadStatus.value = "Memuat model ${model.name}..."
                SdDreamBridge.startBackend(getApplication(), modelId, width, height)
            }

            var healthy = false
            var unhealthy = false
            checkSdBackendHealth(
                BackendService.backendState,
                onHealthy = { healthy = true },
                onUnhealthy = { unhealthy = true }
            )

            _isSdModelLoading.value = false
            _isCheckingSdBackend.value = false

            if (healthy) {
                _isSdModelLoaded.value = true
                _sdLoadStatus.value = "Model siap — backend native berjalan"
                markSdSessionPrepared(modelId)
            } else {
                _isSdModelLoaded.value = false
                val backendErr = BackendService.backendState.value
                _sdLoadStatus.value = when {
                    backendErr is BackendService.BackendState.Error -> backendErr.message
                    unhealthy -> "Backend tidak merespons — coba lagi"
                    else -> "Gagal memulai backend native Stable Diffusion"
                }
            }
            refreshSdRuntimeStatus(modelId)
        }
    }

    fun unloadSdModel() {
        SdDreamBridge.stopBackend(getApplication())
        clearSdSessionPrepared()
        _isSdModelLoaded.value = false
        _isSdModelLoading.value = false
        _isCheckingSdBackend.value = false
        _sdLoadStatus.value = "Model tidak aktif"
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
            ModelDownloadManager.downloadingModelIds.collect { downloadingIds ->
                loadModelList()
            }
        }
        viewModelScope.launch {
            _availableModels.collect { models ->
                val activeLlm = models.find { it.id == _selectedLlmModelId.value && it.type == LocalModelFile.ModelType.LLM }
                if (activeLlm?.isDownloaded == true && !_isModelLoaded.value && !_isModelLoading.value) {
                    loadActiveLlmModel()
                }
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
                id = "anythingv5cpu",
                name = "Anything V5.0 (Anime CPU/GPU)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1200000000L,
                isDownloaded = isFileExists("anythingv5cpu"),
                downloadUrl = "https://huggingface.co/xororz/sd-mnn/resolve/main/AnythingV5.zip",
                localFileName = "anythingv5cpu.zip",
                description = "Model Anything V5.0 dioptimalkan untuk CPU/GPU ponsel menggunakan runtime MNN.",
                isResumable = isTempFileExists("anythingv5cpu.zip")
            ),
            LocalModelFile(
                id = "qteamixcpu",
                name = "QteaMix (Chibi CPU/GPU)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1200000000L,
                isDownloaded = isFileExists("qteamixcpu"),
                downloadUrl = "https://huggingface.co/xororz/sd-mnn/resolve/main/QteaMix.zip",
                localFileName = "qteamixcpu.zip",
                description = "Model QteaMix dioptimalkan untuk CPU/GPU ponsel menggunakan runtime MNN.",
                isResumable = isTempFileExists("qteamixcpu.zip")
            ),
            LocalModelFile(
                id = "absoluterealitycpu",
                name = "Absolute Reality (Photo CPU/GPU)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1200000000L,
                isDownloaded = isFileExists("absoluterealitycpu"),
                downloadUrl = "https://huggingface.co/xororz/sd-mnn/resolve/main/AbsoluteReality.zip",
                localFileName = "absoluterealitycpu.zip",
                description = "Model Absolute Reality dioptimalkan untuk CPU/GPU ponsel menggunakan runtime MNN.",
                isResumable = isTempFileExists("absoluterealitycpu.zip")
            ),
            LocalModelFile(
                id = "chilloutmixcpu",
                name = "ChilloutMix (Realistic CPU/GPU)",
                type = LocalModelFile.ModelType.STABLE_DIFFUSION,
                sizeBytes = 1200000000L,
                isDownloaded = isFileExists("chilloutmixcpu"),
                downloadUrl = "https://huggingface.co/xororz/sd-mnn/resolve/main/ChilloutMix.zip",
                localFileName = "chilloutmixcpu.zip",
                description = "Model ChilloutMix dioptimalkan untuk CPU/GPU ponsel menggunakan runtime MNN.",
                isResumable = isTempFileExists("chilloutmixcpu.zip")
            ),
            LocalModelFile(
                id = "lora-detail-tweaker",
                name = "Detail Tweaker LoRA (v1.5)",
                type = LocalModelFile.ModelType.LORA,
                sizeBytes = 37861176L,
                isDownloaded = isFileExists("detail_tweaker.safetensors", 37861176L),
                downloadUrl = "https://huggingface.co/ffxvs/lora-effects/resolve/main/detail_tweaker.safetensors",
                localFileName = "detail_tweaker.safetensors",
                description = "Model LoRA (Low-Rank Adaptation) detail tweaker untuk meningkatkan detail visual sketsa sains atau diagram dengan presisi tinggi.",
                isResumable = isTempFileExists("detail_tweaker.safetensors")
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
        val supportedSdIds = SdModelRepository(getApplication()).models.map { it.id }.toSet()
        val models = (hardcodedModels + customModels).filterNot {
            it.type == LocalModelFile.ModelType.STABLE_DIFFUSION && it.id !in supportedSdIds
        }
        _availableModels.value = models
        normalizeSelectedSdModel(models)
        calculateStorage(models)
    }

    private fun isFileExists(fileName: String, expectedSize: Long = 0L): Boolean {
        val file = File(File(getApplication<Application>().filesDir, "models"), fileName)
        if (!file.exists()) return false
        if (file.isDirectory) {
            return (file.list()?.isNotEmpty() == true)
        }
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
            val modelsDir = File(getApplication<Application>().filesDir, "models")
            val file = File(modelsDir, model.localFileName)
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            val dir = File(modelsDir, modelId)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            val tempFile = File(modelsDir, "${model.localFileName}.download")
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
        if (text.isBlank() && attachedUri == null) return

        viewModelScope.launch {
            val sessionId = _activeSessionId.value ?: return@launch
            
            val messageText = text.ifBlank {
                if (attachedType == MessageType.IMAGE) "[Gambar]" else "[Dokumen]"
            }

            val userMsg = ChatMessage(
                sessionId = sessionId,
                text = messageText,
                sender = MessageSender.USER,
                type = attachedType ?: MessageType.TEXT,
                attachmentPath = attachedUri?.toString()
            )
            repository.insertMessage(userMsg)
            _isGenerating.value = true

            var retrievedContext: String? = null
            if (attachedType == MessageType.FILE) {
                retrievedContext = ragPipeline.retrieveRelevantContext(text)
            } else if (attachedType == MessageType.IMAGE && attachedUri != null) {
                try {
                    val contentResolver = getApplication<Application>().contentResolver
                    contentResolver.openInputStream(attachedUri).use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            val analysis = visionEngine.analyzeImage(bitmap)
                            retrievedContext = "[Vision Analysis Result]\n" +
                                    "Detected Items: ${analysis.detectedItems.joinToString(", ")}\n" +
                                    "OCR/Text in Image: ${analysis.ocrText}\n" +
                                    "Summary: ${analysis.educationalSummary}"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EduLocalViewModel", "Failed to analyze image", e)
                }
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

            val queryText = text.ifBlank {
                if (attachedType == MessageType.IMAGE) {
                    "Tolong analisis gambar yang saya lampirkan."
                } else {
                    "Tolong bantu saya memahami dokumen yang dilampirkan ini."
                }
            }

            llmEngine.generateResponseStream(queryText, retrievedContext).collect { partialText ->
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
        seed: Long,
        customWidth: Int = 0,
        customHeight: Int = 0
    ) {
        if (prompt.isBlank()) return

        val (genWidth, genHeight) = resolveGenerationDimensions(aspectRatio, customWidth, customHeight)
        val safeSteps = steps.coerceIn(SdMobileDefaults.MIN_STEPS, SdMobileDefaults.MAX_STEPS)
        val safeCfg = cfgScale.coerceIn(SdMobileDefaults.MIN_CFG, SdMobileDefaults.MAX_CFG)

        viewModelScope.launch {
            if (_selectedSdModelId.value != modelId) {
                _diffusionStatus.value = "Model tidak cocok — pilih model di tab Prompt"
                return@launch
            }

            _diffusionProgress.value = true
            _generatedDiagram.value = null
            _totalDiffusionSteps.value = safeSteps
            _diffusionStep.value = 0
            _diffusionStatus.value = "Menyiapkan backend..."
            val actualSeed = if (seed == -1L) Random.nextLong(0, Long.MAX_VALUE) else seed
            resolveAndApplySdBackend(reloadIfChanged = false)
            val backend = activeBackend.value
            refreshSdRuntimeStatus(modelId)

            if (!validateSelectedSdRuntime(modelId)) {
                _diffusionProgress.value = false
                _diffusionStatus.value = _sdLoadStatus.value.ifBlank { "Model Stable Diffusion belum lengkap" }
                return@launch
            }

            if (!SdDreamBridge.isBackendReady()) {
                prepareSdBackendLocked(force = false)
            }
            if (!SdDreamBridge.isBackendReady()) {
                _diffusionProgress.value = false
                _diffusionStatus.value = _sdLoadStatus.value.ifBlank { "Backend tidak siap" }
                return@launch
            }
            _isSdModelLoaded.value = true

            val apiAspectRatio = when {
                aspectRatio == "Kustom" || !aspectRatio.contains(":") -> {
                    when {
                        genWidth == genHeight -> "1:1"
                        else -> "$genWidth:$genHeight"
                    }
                }
                else -> aspectRatio
            }

            SdBackgroundGenerationService.resetState()
            _diffusionStatus.value = "Mengirim request generate..."
            sdGenerationProgressJob?.cancel()
            sdGenerationProgressJob = launch {
                SdBackgroundGenerationService.generationState.collect { state ->
                    if (state is SdBackgroundGenerationService.GenerationState.Progress) {
                        val step = (state.progress * safeSteps).toInt().coerceIn(0, safeSteps)
                        _diffusionStep.value = step
                        _diffusionStatus.value = "Langkah $step dari $safeSteps..."
                        state.intermediateImage?.let { _generatedDiagram.value = it }
                    }
                }
            }

            val genIntent = Intent(getApplication(), SdBackgroundGenerationService::class.java).apply {
                putExtra("prompt", prompt)
                putExtra("negative_prompt", negativePrompt)
                putExtra("steps", safeSteps)
                putExtra("cfg", safeCfg)
                putExtra("seed", actualSeed)
                putExtra("width", genWidth)
                putExtra("height", genHeight)
                putExtra("effective_width", genWidth)
                putExtra("effective_height", genHeight)
                putExtra("use_opencl", backend == GpuAccelerationBackend.OPENCL)
                putExtra("scheduler", "dpm")
                putExtra("aspect_ratio", apiAspectRatio)
            }
            getApplication<Application>().startForegroundService(genIntent)

            val finalState = withTimeoutOrNull(30 * 60 * 1000L) {
                SdBackgroundGenerationService.generationState.first {
                    it is SdBackgroundGenerationService.GenerationState.Complete ||
                        it is SdBackgroundGenerationService.GenerationState.Error
                }
            }
            sdGenerationProgressJob?.cancel()

            if (finalState == null) {
                _diffusionProgress.value = false
                _diffusionStatus.value = "Generate timeout — backend tidak merespons"
                SdDreamBridge.stopGeneration(getApplication())
                return@launch
            }

            when (finalState) {
                is SdBackgroundGenerationService.GenerationState.Complete -> {
                    val bitmap = finalState.bitmap
                    val actualWidth = bitmap.width
                    val actualHeight = bitmap.height

                    _generatedDiagram.value = bitmap
                    _diffusionProgress.value = false
                    _diffusionStatus.value = "Selesai! (render native)"
                    SdBackgroundGenerationService.markBitmapConsumed()

                    _lastGenerationInfo.value = LastGenerationInfo(
                        prompt = prompt,
                        negativePrompt = negativePrompt,
                        modelId = modelId,
                        loraModel = loraModel,
                        aspectRatio = aspectRatio,
                        customWidth = actualWidth,
                        customHeight = actualHeight,
                        cfgScale = safeCfg,
                        steps = safeSteps,
                        seed = actualSeed,
                        usedNativeInference = true,
                        backendName = backend.displayName,
                        fallbackReason = null
                    )

                    try {
                        val context = getApplication<Application>().applicationContext
                        val historyDir = File(context.filesDir, "history_images")
                        if (!historyDir.exists()) historyDir.mkdirs()
                        val imageFile = File(historyDir, "sd_history_${System.currentTimeMillis()}.png")

                        java.io.FileOutputStream(imageFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }

                        val historyItem = GenerationHistory(
                            prompt = prompt,
                            negativePrompt = negativePrompt,
                            modelId = modelId,
                            loraModel = loraModel,
                            aspectRatio = aspectRatio,
                            customWidth = actualWidth,
                            customHeight = actualHeight,
                            cfgScale = safeCfg,
                            steps = safeSteps,
                            seed = actualSeed,
                            imagePath = imageFile.absolutePath
                        )
                        database.generationHistoryDao().insertHistory(historyItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                is SdBackgroundGenerationService.GenerationState.Error -> {
                    _diffusionProgress.value = false
                    _diffusionStatus.value = finalState.message
                }
                else -> {
                    _diffusionProgress.value = false
                    _diffusionStatus.value = "Generate gagal"
                }
            }
        }
    }

    fun deleteHistoryItem(item: GenerationHistory) {
        viewModelScope.launch {
            try {
                val file = File(item.imagePath)
                if (file.exists()) file.delete()
                database.generationHistoryDao().deleteHistoryById(item.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
                val historyDir = File(context.filesDir, "history_images")
                if (historyDir.exists()) {
                    historyDir.deleteRecursively()
                }
                database.generationHistoryDao().deleteAllHistory()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
