package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.example.data.model.GpuAccelerationBackend
import com.example.data.model.GenerationHistory
import com.example.data.model.LocalModelFile
import com.example.data.model.SdGenerationSettings
import com.example.data.model.SdMobileDefaults
import com.example.service.BackendService
import com.example.service.SdBackgroundGenerationService
import com.example.ui.components.BlockingProgressOverlay
import com.example.ui.components.OverlayIconButton
import com.example.ui.components.SmoothLinearWavyProgressIndicator
import com.example.ui.components.ZoomableImageOverlay
import com.example.ui.theme.Motion
import com.example.ui.viewmodel.EduLocalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreativeModeScreen(
    viewModel: EduLocalViewModel,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // View Model states
    val generatedDiagram by viewModel.generatedDiagram.collectAsState()
    val isDiffusing by viewModel.diffusionProgress.collectAsState()
    val diffusionStep by viewModel.diffusionStep.collectAsState()
    val totalDiffusionSteps by viewModel.totalDiffusionSteps.collectAsState()
    val diffusionStatus by viewModel.diffusionStatus.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val activeBackend by viewModel.activeBackend.collectAsState()
    val isSdBackendAuto by viewModel.isSdBackendAuto.collectAsState()
    val sdBackendAutoReason by viewModel.sdBackendAutoReason.collectAsState()
    val lastGenerationInfo by viewModel.lastGenerationInfo.collectAsState()
    val sdRuntimeStatus by viewModel.sdRuntimeStatus.collectAsState()
    val isSdModelLoading by viewModel.isSdModelLoading.collectAsState()
    val isCheckingSdBackend by viewModel.isCheckingSdBackend.collectAsState()
    val isSdModelLoaded by viewModel.isSdModelLoaded.collectAsState()
    val sdLoadStatus by viewModel.sdLoadStatus.collectAsState()
    val selectedModelId by viewModel.selectedSdModelId.collectAsState()
    val sdGenSettings by viewModel.sdGenerationSettings.collectAsState()
    val downloadingModelIds by viewModel.downloadingModelIds.collectAsState()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsState()
    val historyItems by viewModel.generationHistory.collectAsState()
    val sdBackendState by viewModel.sdBackendState.collectAsState()
    val genServiceState by SdBackgroundGenerationService.generationState.collectAsState()

    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val pagerState = rememberPagerState(pageCount = { 3 })
    val tabLabels = listOf(
        stringResource(R.string.sd_prompt_tab),
        stringResource(R.string.sd_result_tab),
        stringResource(R.string.sd_history_tab),
    )

    // Active Display states
    var activeDisplayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeDisplayHistoryItem by remember { mutableStateOf<GenerationHistory?>(null) }

    // Sync generated image to display when finished
    LaunchedEffect(generatedDiagram) {
        if (generatedDiagram != null) {
            activeDisplayBitmap = generatedDiagram
            activeDisplayHistoryItem = null
        }
    }

    // Local UI parameter states
    var positivePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("worst quality, low quality, normal quality, poorly drawn, blurry, deformed, watermark, signature") }
    var selectedLora by remember { mutableStateOf("None") }
    var selectedAspectRatio by remember { mutableStateOf(SdMobileDefaults.DEFAULT_ASPECT_RATIO) }

    var customWidth by remember { mutableFloatStateOf(SdMobileDefaults.DEFAULT_WIDTH.toFloat()) }
    var customHeight by remember { mutableFloatStateOf(SdMobileDefaults.DEFAULT_HEIGHT.toFloat()) }
    var inferenceSteps by remember { mutableFloatStateOf(SdMobileDefaults.DEFAULT_STEPS.toFloat()) }
    var cfgScale by remember { mutableFloatStateOf(SdMobileDefaults.DEFAULT_CFG) }
    var isRandomSeed by remember { mutableStateOf(true) }
    var customSeedInput by remember { mutableStateOf("1337") }
    
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showOpenClWarning by remember { mutableStateOf(false) }
    var useOpenCl by remember { mutableStateOf(activeBackend == GpuAccelerationBackend.OPENCL) }
    var intermediateBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeBackend) {
        useOpenCl = activeBackend == GpuAccelerationBackend.OPENCL
    }

    LaunchedEffect(genServiceState) {
        when (val state = genServiceState) {
            is SdBackgroundGenerationService.GenerationState.Progress -> {
                state.intermediateImage?.let { intermediateBitmap = it }
            }
            is SdBackgroundGenerationService.GenerationState.Complete -> {
                intermediateBitmap = null
            }
            is SdBackgroundGenerationService.GenerationState.Error -> {
                intermediateBitmap = null
                errorMessage = state.message
            }
            else -> Unit
        }
    }

    val isGeneratingService = genServiceState is SdBackgroundGenerationService.GenerationState.Progress
    val isRunning = isDiffusing || isGeneratingService
    val genProgress = when (val state = genServiceState) {
        is SdBackgroundGenerationService.GenerationState.Progress -> state.progress
        else -> if (totalDiffusionSteps > 0) diffusionStep.toFloat() / totalDiffusionSteps else 0f
    }
    val hasKnownGenerationProgress = genProgress > 0f
    val isBackendStarting = sdBackendState is BackendService.BackendState.Starting
    val isCheckingBackend = isBackendStarting || isSdModelLoading || isCheckingSdBackend
    val isBackendReady = sdBackendState is BackendService.BackendState.Running && isSdModelLoaded
    val backendErrorMessage = (sdBackendState as? BackendService.BackendState.Error)?.message

    var pendingModelIdToDownload by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            pendingModelIdToDownload?.let { modelId ->
                viewModel.downloadModel(modelId)
                pendingModelIdToDownload = null
            }
        }
    )

    val requestNotificationPermissionAndDownload = { modelId: String ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                viewModel.downloadModel(modelId)
            } else {
                pendingModelIdToDownload = modelId
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.downloadModel(modelId)
        }
    }

    val aspectRatioPresets = SdMobileDefaults.aspectPresets

    LaunchedEffect(sdGenSettings) {
        inferenceSteps = sdGenSettings.steps
        cfgScale = sdGenSettings.cfgScale
        customWidth = sdGenSettings.customWidth
        customHeight = sdGenSettings.customHeight
        selectedAspectRatio = sdGenSettings.aspectRatio
    }

    fun persistSdSettings(profile: SdMobileDefaults.PerformanceProfile = sdGenSettings.performanceProfile) {
        viewModel.saveSdGenerationSettings(
            SdGenerationSettings(
                steps = inferenceSteps,
                cfgScale = cfgScale,
                customWidth = customWidth,
                customHeight = customHeight,
                aspectRatio = selectedAspectRatio,
                performanceProfile = profile
            )
        )
    }

    LaunchedEffect(selectedModelId, activeBackend) {
        viewModel.refreshSdRuntimeStatus(selectedModelId)
    }

    // Match local-dream's screen lifecycle: start SD only while the Sketsa tab is active,
    // and release the native backend when leaving so chat/LLM memory stays available.
    LaunchedEffect(isActive) {
        if (!isActive) {
            viewModel.cleanupSdDream()
            return@LaunchedEffect
        }
        if (viewModel.isSdBackendSessionPrepared()) {
            viewModel.syncSdBackendUiState()
        } else {
            viewModel.ensureSdBackendReady()
        }
    }

    // Zoom and details states
    var showZoomDialog by remember { mutableStateOf(false) }
    var zoomBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedHistoryItem by remember { mutableStateOf<GenerationHistory?>(null) }
    var showHistoryDetailDialog by remember { mutableStateOf(false) }

    // LoRA options
    val loraOptions = remember {
        listOf(
            Triple("None", "🎓 Standard Style", "Gaya dasar minimalis standard mendidik"),
            Triple("Pencil Sketch & Blueprint", "📐 Blueprint Sketch", "Gaya cetak biru ilmiah dengan garis putih presisi"),
            Triple("Watercolor Vector", "🎨 Watercolor Wash", "Visualisasi cat air halus artistik"),
            Triple("3D Render Concept", "🔮 3D Tech Glass", "Model kaca siber neon 3D berkilau"),
            Triple("Cyberpunk Neon Glow", "⚡ Cyberpunk Glow", "Grafis berpendar tajam dengan warna ungu dan hijau radioaktif")
        )
    }

    // Presets
    val promptPresets = remember {
        listOf(
            Triple(
                "🌸 Anime Girl",
                "masterpiece, best quality, 1girl, solo, cute girl, smiling, school uniform, long hair, anime style, highly detailed",
                "worst quality, low quality, normal quality, poorly drawn, signature, watermark, blurry, deformed"
            ),
            Triple(
                "🎨 Chibi Anime",
                "masterpiece, best quality, 1girl, cute chibi, big eyes, colorful background, anime style, highly detailed",
                "worst quality, low quality, normal quality, poorly drawn, blurry, realistic, photo, 3d"
            ),
            Triple(
                "📷 Photo Realist",
                "masterpiece, best quality, portrait of a majestic lion, close up, photography, realistic, 8k resolution, highly detailed",
                "paintings, cartoon, anime, lowres, bad anatomy, bad hands, text, error, signature, watermark, blurry"
            ),
            Triple(
                "📐 Skema Sains",
                "detailed structure of an animal cell, nucleus organelle, scientific concept diagram, watercolor scientific illustration, labeled",
                "blurry, low quality, photo, realistic, 3d render, messy, low resolution"
            ),
            Triple(
                "⚙️ Dinamo Mesin",
                "detailed schematic of a robotic engine, mechanical tech blueprint, engineering drawing, technical labels, highly precise",
                "blurry, photo, anime, low quality, hand drawn, messy"
            )
        )
    }

    val sdModels = availableModels.filter { it.type == LocalModelFile.ModelType.STABLE_DIFFUSION }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            pagerState.animateScrollToPage(1)
        }
    }

    val selectedModel = sdModels.find { it.id == selectedModelId } ?: sdModels.firstOrNull()
    val effectiveSelectedModelId = selectedModel?.id ?: selectedModelId

    LaunchedEffect(selectedModel?.id) {
        if (positivePrompt.isBlank()) {
            positivePrompt = when (selectedModel?.id) {
                "anythingv5cpu" -> "masterpiece, best quality, 1girl, solo, cute, white hair,"
                "qteamixcpu" -> "chibi, best quality, 1girl, solo, cute, pink hair,"
                "absoluterealitycpu" -> "masterpiece, best quality, portrait, photorealistic,"
                "chilloutmixcpu" -> "masterpiece, best quality, 1girl, solo,"
                else -> ""
            }
        }
        if (negativePrompt.isBlank()) {
            negativePrompt = when (selectedModel?.id) {
                "anythingv5cpu" -> "lowres, bad anatomy, bad hands, missing fingers, extra fingers"
                "qteamixcpu" -> "lowres, bad anatomy, bad hands, missing fingers"
                "absoluterealitycpu" -> "lowres, bad anatomy, blurry, watermark"
                "chilloutmixcpu" -> "lowres, bad anatomy, bad hands, missing fingers"
                else -> negativePrompt
            }
        }
    }

    Box(modifier = modifier) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (scrollBehavior.state.collapsedFraction < 0.5f) {
                        Column {
                            Text(
                                text = selectedModel?.name ?: stringResource(R.string.sd_prompt_tab),
                                maxLines = 1,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                            Text(
                                text = selectedModel?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    Row {
                        tabLabels.forEachIndexed { index, label ->
                            val selected = pagerState.currentPage == index
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        focusManager.clearFocus()
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                            ) {
                                Text(label)
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.sd_prompt_settings),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    TextButton(
                                        onClick = { showAdvancedSettings = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            stringResource(R.string.sd_advanced_settings),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    promptPresets.forEach { (label, pos, neg) ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                positivePrompt = pos
                                                negativePrompt = neg
                                                Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                                            },
                                            label = { Text(label, fontSize = 11.sp) },
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = positivePrompt,
                                    onValueChange = { positivePrompt = it },
                                    label = { Text("Prompt") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    trailingIcon = {
                                        if (positivePrompt.isNotEmpty()) {
                                            IconButton(onClick = { positivePrompt = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "clear")
                                            }
                                        }
                                    },
                                )

                                OutlinedTextField(
                                    value = negativePrompt,
                                    onValueChange = { negativePrompt = it },
                                    label = { Text("Negative prompt") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    trailingIcon = {
                                        if (negativePrompt.isNotEmpty()) {
                                            IconButton(onClick = { negativePrompt = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "clear")
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        // Mode Akselerasi CPU/GPU
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Mode Akselerasi",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        if (useOpenCl) "GPU (Sangat cepat, membutuhkan OpenCL)" else "CPU (Standard, lebih lambat)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = !useOpenCl,
                                        onClick = {
                                            useOpenCl = false
                                            viewModel.setAccelerationBackend(GpuAccelerationBackend.CPU)
                                        },
                                        label = { Text("CPU", style = MaterialTheme.typography.bodyMedium) }
                                    )
                                    FilterChip(
                                        selected = useOpenCl,
                                        onClick = {
                                            showOpenClWarning = true
                                        },
                                        label = { Text("GPU", style = MaterialTheme.typography.bodyMedium) }
                                    )
                                }
                            }
                        }

                        // Model Selector
                        Text(
                            text = "Model Lukis",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (sdModels.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("Memuat daftar model...", fontSize = 11.sp)
                                }
                            }
                        } else {
                            sdModels.forEach { model ->
                                val isSelected = selectedModelId == model.id
                                val isDownloaded = model.isDownloaded
                                val isDownloading = downloadingModelIds.contains(model.id)
                                val progress = modelDownloadProgress[model.id] ?: 0f

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isSelected) {
                                            viewModel.selectSdModel(model.id)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    if (!isSelected) viewModel.selectSdModel(model.id)
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${model.description} (${model.displaySize})", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            
                                            // Action/Download info
                                            if (isDownloaded) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Ready",
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else if (isDownloading) {
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            } else {
                                                IconButton(
                                                    onClick = { requestNotificationPermissionAndDownload(model.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CloudDownload,
                                                        contentDescription = "Download model",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (isDownloading) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Status muat model (seperti Chat LLM)
                        if (selectedModel?.isDownloaded == true) {
                            when {
                                isCheckingBackend -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = sdLoadStatus.ifBlank { "Memuat model Stable Diffusion..." },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                isBackendReady -> {
                                    Surface(
                                        color = Color(0xFFE8F5E9),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = sdLoadStatus.ifBlank { "Model siap digunakan" },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                                backendErrorMessage != null || sdLoadStatus.isNotBlank() -> {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = backendErrorMessage ?: sdLoadStatus,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Runtime status
                        sdRuntimeStatus?.let { status ->
                            val statusColor = when {
                                status.nativeCoreAvailable && status.modelReady -> Color(0xFF2E7D32)
                                status.modelReady -> Color(0xFFF57C00)
                                else -> MaterialTheme.colorScheme.error
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = statusColor.copy(alpha = 0.08f)
                                ),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (status.nativeCoreAvailable && status.modelReady) {
                                                Icons.Default.CheckCircle
                                            } else {
                                                Icons.Default.Info
                                            },
                                            contentDescription = null,
                                            tint = statusColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = when {
                                                status.nativeCoreAvailable && status.modelReady -> {
                                                    val backendLabel = if (isSdBackendAuto) {
                                                        "Otomatis → ${activeBackend.displayName.substringBefore(" (")}"
                                                    } else {
                                                        activeBackend.displayName.substringBefore(" (")
                                                    }
                                                    "Runtime native siap — $backendLabel"
                                                }
                                                !status.nativeCoreAvailable ->
                                                    "Runtime native tidak tersedia (libstable_diffusion_core.so tidak ditemukan)"
                                                !status.modelReady ->
                                                    "Model belum lengkap — file hilang: ${status.missingModelFiles.take(3).joinToString(", ")}"
                                                else -> "Status runtime tidak diketahui"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColor
                                        )
                                    }
                                    if (isSdBackendAuto && sdBackendAutoReason.isNotBlank()) {
                                        Text(
                                            text = sdBackendAutoReason,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!status.nativeCoreAvailable) {
                                        Text(
                                            text = "Hubungkan/kemas library native Stable Diffusion untuk dapat menggunakan fitur ini.",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // LoRA Style Selector
                        Text(
                            text = "Gaya Visual (LoRA Preset)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            loraOptions.forEach { (loraId, label, _) ->
                                FilterChip(
                                    selected = selectedLora == loraId,
                                    onClick = { selectedLora = loraId },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        // Profil performa mobile
                        Text(
                            text = "Profil Performa (Mobile)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SdMobileDefaults.PerformanceProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = sdGenSettings.performanceProfile == profile,
                                    onClick = { viewModel.applySdPerformanceProfile(profile) },
                                    label = { Text(profile.label, fontSize = 10.sp) }
                                )
                            }
                        }
                        Text(
                            text = "Rekomendasi: ${SdMobileDefaults.MAX_DIMENSION}px max, ${SdMobileDefaults.DEFAULT_STEPS} langkah, CFG ${SdMobileDefaults.DEFAULT_CFG}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        // Aspect Ratio Selector
                        Text(
                            text = "Rasio Gambar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            aspectRatioPresets.forEach { preset ->
                                FilterChip(
                                    selected = selectedAspectRatio == preset.label,
                                    onClick = {
                                        selectedAspectRatio = preset.label
                                        customWidth = preset.width.toFloat()
                                        customHeight = preset.height.toFloat()
                                        persistSdSettings()
                                    },
                                    label = { Text(preset.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                            FilterChip(
                                selected = selectedAspectRatio == "Kustom",
                                onClick = { selectedAspectRatio = "Kustom" },
                                label = { Text("Kustom", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }

                        val isDownloaded = selectedModel?.isDownloaded == true
                        val isDownloading = downloadingModelIds.contains(effectiveSelectedModelId)
                        val isBtnEnabled = when {
                            selectedModel == null -> false
                            sdRuntimeStatus?.nativeCoreAvailable != true -> false
                            isRunning || isCheckingBackend -> false
                            !isDownloaded -> !isDownloading
                            !isBackendReady -> true
                            else -> true
                        }

                        Button(
                            onClick = {
                                if (!isDownloaded) {
                                    if (!isDownloading) {
                                        requestNotificationPermissionAndDownload(effectiveSelectedModelId)
                                    }
                                } else if (!isBackendReady) {
                                    Log.i("CreativeModeScreen", "Generate button preparing backend model=$effectiveSelectedModelId")
                                    viewModel.ensureSdBackendReady()
                                } else {
                                    errorMessage = null
                                    val effectivePrompt = positivePrompt.ifBlank {
                                        when (effectiveSelectedModelId) {
                                            "anythingv5cpu" -> "masterpiece, best quality, 1girl, solo, cute, white hair,"
                                            "qteamixcpu" -> "chibi, best quality, 1girl, solo, cute, pink hair,"
                                            "absoluterealitycpu" -> "masterpiece, best quality, portrait, photorealistic,"
                                            "chilloutmixcpu" -> "masterpiece, best quality, 1girl, solo,"
                                            else -> ""
                                        }
                                    }
                                    if (effectivePrompt.isBlank()) {
                                        Toast.makeText(context, "Prompt masih kosong", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val parsedSeed = if (isRandomSeed) -1L else customSeedInput.toLongOrNull() ?: 1337L
                                    val genWidth = if (selectedAspectRatio == "Kustom") customWidth.toInt() else 0
                                    val genHeight = if (selectedAspectRatio == "Kustom") customHeight.toInt() else 0
                                    persistSdSettings()
                                    Log.i("CreativeModeScreen", "Generate button start model=$effectiveSelectedModelId promptLength=${effectivePrompt.length}")
                                    viewModel.generateDiffusionSketchDetailed(
                                        prompt = effectivePrompt,
                                        negativePrompt = negativePrompt,
                                        modelId = effectiveSelectedModelId,
                                        loraModel = selectedLora,
                                        aspectRatio = selectedAspectRatio,
                                        cfgScale = cfgScale,
                                        steps = inferenceSteps.toInt(),
                                        seed = parsedSeed,
                                        customWidth = genWidth,
                                        customHeight = genHeight,
                                    )
                                }
                            },
                            enabled = isBtnEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            AnimatedContent(
                                targetState = isRunning,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(Motion.DurationShort)) +
                                        scaleIn(initialScale = 0.8f, animationSpec = tween(Motion.DurationShort)))
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(Motion.DurationShort)) +
                                                scaleOut(targetScale = 0.8f, animationSpec = tween(Motion.DurationShort)),
                                        )
                                },
                                label = "GenerateButtonContent",
                            ) { loading ->
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        when {
                                            selectedModel == null -> "Model tidak tersedia"
                                            sdRuntimeStatus?.nativeCoreAvailable != true -> "Runtime Tidak Tersedia"
                                            !isDownloaded -> if (isDownloading) "Mengunduh…" else "Unduh model"
                                            !isBackendReady -> stringResource(R.string.sd_loading_model)
                                            else -> stringResource(R.string.sd_generate_image)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Crossfade(
                            targetState = isRunning to (activeDisplayBitmap != null),
                            label = "result_crossfade",
                        ) { (running, hasResult) ->
                            if (running) {
                                ElevatedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sd_generating),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        SmoothLinearWavyProgressIndicator(
                                            progress = genProgress,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text(
                                            text = "${(genProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (hasKnownGenerationProgress && totalDiffusionSteps > 0) {
                                            Text(
                                                text = "Langkah $diffusionStep / $totalDiffusionSteps",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        } else {
                                            Text(
                                                text = diffusionStatus.ifBlank { "Menunggu progress dari backend..." },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        val preview = intermediateBitmap ?: generatedDiagram
                                        if (preview != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Card(
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f),
                                            ) {
                                            Image(
                                                bitmap = preview.asImageBitmap(),
                                                    contentDescription = "Generation Preview",
                                                    modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit,
                                            )
                                            }
                                        }
                                    }
                                }
                            } else if (!hasResult) {
                                ElevatedCard(modifier = Modifier.padding(16.dp)) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        val iconScale = remember { Animatable(1f) }
                                        LaunchedEffect(Unit) {
                                            iconScale.animateTo(
                                                targetValue = 1.1f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1500),
                                                    repeatMode = RepeatMode.Reverse,
                                                ),
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .graphicsLayer(
                                                    scaleX = iconScale.value,
                                                    scaleY = iconScale.value,
                                                ),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            stringResource(R.string.sd_no_results),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        Text(
                                            stringResource(R.string.sd_no_results_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(
                                            onClick = {
                                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                            },
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            Text(stringResource(R.string.sd_go_to_generate))
                                        }
                                    }
                                }
                            } else {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                stringResource(R.string.sd_result_title),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            activeDisplayBitmap?.let { bitmap ->
                                                FilledTonalIconButton(
                                                    onClick = {
                                                        viewModel.saveSketch(
                                                            bitmap = bitmap,
                                                            prompt = positivePrompt,
                                                            onSuccess = {
                                                                Toast.makeText(context, "Disimpan", Toast.LENGTH_SHORT).show()
                                                            },
                                                            onError = { e ->
                                                                Toast.makeText(context, e, Toast.LENGTH_SHORT).show()
                                                            },
                                                        )
                                                    },
                                                ) {
                                                    Icon(Icons.Default.Save, contentDescription = "save")
                                                }
                                            }
                                        }

                                        val displayBmp = activeDisplayBitmap
                                        if (displayBmp != null) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clickable {
                                                        zoomBitmap = displayBmp
                                                        showZoomDialog = true
                                                    },
                                                shape = MaterialTheme.shapes.medium,
                                                shadowElevation = 4.dp,
                                            ) {
                                                Image(
                                                    bitmap = displayBmp.asImageBitmap(),
                                                    contentDescription = "result",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (historyItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hasil Pembuatan Terkini",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(historyItems.take(10)) { item ->
                                    val bitmap = remember(item.imagePath) {
                                        try {
                                            BitmapFactory.decodeFile(item.imagePath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (bitmap != null) {
                                        Card(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clickable {
                                                    activeDisplayBitmap = bitmap
                                                    activeDisplayHistoryItem = item
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(
                                                width = if (activeDisplayHistoryItem?.id == item.id) 2.dp else 1.dp,
                                                color = if (activeDisplayHistoryItem?.id == item.id) MaterialTheme.colorScheme.primary else Color.Transparent
                                            )
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // PAGE 2: RIWAYAT (HISTORY GRID)
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Riwayat Gambar (${historyItems.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (historyItems.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        viewModel.clearAllHistory()
                                        activeDisplayBitmap = null
                                        activeDisplayHistoryItem = null
                                        Toast.makeText(context, "Semua riwayat dihapus!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus Semua", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (historyItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Belum Ada Riwayat", fontWeight = FontWeight.Bold, color = Color.Gray)
                                    Text("Gambar yang sukses di-render akan otomatis masuk ke riwayat.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(historyItems) { item ->
                                    val bitmap = remember(item.imagePath) {
                                        try {
                                            BitmapFactory.decodeFile(item.imagePath)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    Card(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable {
                                                selectedHistoryItem = item
                                                showHistoryDetailDialog = true
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = item.prompt,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .align(Alignment.BottomCenter)
                                                    .background(Color.Black.copy(alpha = 0.6f))
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    text = item.prompt,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            if (isActive) {
            BlockingProgressOverlay(visible = isCheckingBackend && !isRunning) {
                CircularProgressIndicator()
                Text(
                    text = sdLoadStatus.ifBlank { stringResource(R.string.sd_loading_model) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            }
        }
    }

    if (isActive && showOpenClWarning) {
        AlertDialog(
            onDismissRequest = { showOpenClWarning = false },
            title = { Text(stringResource(R.string.sd_runtime_gpu)) },
            text = { Text(stringResource(R.string.sd_opencl_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        useOpenCl = true
                        viewModel.setAccelerationBackend(GpuAccelerationBackend.OPENCL)
                        showOpenClWarning = false
                    },
                ) { Text(stringResource(R.string.sd_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showOpenClWarning = false }) {
                    Text(stringResource(R.string.sd_cancel))
                }
            },
        )
    }

    if (isActive && showAdvancedSettings) {
        AlertDialog(
            onDismissRequest = { showAdvancedSettings = false },
            title = { Text(stringResource(R.string.sd_advanced_settings_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.sd_steps_label, inferenceSteps.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = inferenceSteps,
                        onValueChange = {
                            inferenceSteps = it
                            persistSdSettings()
                        },
                        valueRange = SdMobileDefaults.MIN_STEPS.toFloat()..SdMobileDefaults.MAX_STEPS.toFloat(),
                        steps = SdMobileDefaults.MAX_STEPS - SdMobileDefaults.MIN_STEPS - 1,
                    )
                    Text("CFG: ${String.format(Locale.US, "%.1f", cfgScale)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = cfgScale,
                        onValueChange = {
                            cfgScale = it
                            persistSdSettings()
                        },
                        valueRange = SdMobileDefaults.MIN_CFG..SdMobileDefaults.MAX_CFG,
                    )
                    Text(
                        stringResource(
                            R.string.sd_image_size_label,
                            customWidth.toInt(),
                            customHeight.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = customWidth,
                        onValueChange = { value ->
                            customWidth = Math.round(value / SdMobileDefaults.DIMENSION_STEP.toFloat())
                                .toFloat() * SdMobileDefaults.DIMENSION_STEP
                            selectedAspectRatio = "Kustom"
                            persistSdSettings()
                        },
                        valueRange = SdMobileDefaults.MIN_DIMENSION.toFloat()
                            ..SdMobileDefaults.MAX_DIMENSION.toFloat(),
                        steps = (SdMobileDefaults.MAX_DIMENSION - SdMobileDefaults.MIN_DIMENSION) /
                            SdMobileDefaults.DIMENSION_STEP - 1,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.sd_runtime_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilterChip(
                            selected = !useOpenCl,
                            onClick = {
                                useOpenCl = false
                                viewModel.setAccelerationBackend(GpuAccelerationBackend.CPU)
                            },
                            label = { Text(stringResource(R.string.sd_runtime_cpu)) },
                        )
                        FilterChip(
                            selected = useOpenCl,
                            onClick = { showOpenClWarning = true },
                            label = { Text(stringResource(R.string.sd_runtime_gpu)) },
                        )
                    }
                    OutlinedTextField(
                        value = if (isRandomSeed) "" else customSeedInput,
                        onValueChange = {
                            isRandomSeed = it.isBlank()
                            customSeedInput = it.filter { c -> c.isDigit() }
                        },
                        label = { Text(stringResource(R.string.sd_random_seed)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdvancedSettings = false }) {
                    Text(stringResource(R.string.sd_confirm))
                }
            },
        )
    }

    if (isActive) {
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
        errorMessage?.let { msg ->
            Card(
                onClick = { errorMessage = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
    }

    if (isActive && showZoomDialog && zoomBitmap != null) {
        ZoomableImageOverlay(
            bitmap = zoomBitmap,
            onDismiss = { showZoomDialog = false },
            showScaleIndicator = true,
            topEndContent = {
                OverlayIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "close",
                    onClick = { showZoomDialog = false },
                )
            },
        )
    }

    if (isActive && showHistoryDetailDialog && selectedHistoryItem != null) {
        val item = selectedHistoryItem!!
        val histBitmap = remember(item.imagePath) {
            try { BitmapFactory.decodeFile(item.imagePath) } catch (_: Exception) { null }
        }
        ZoomableImageOverlay(
            bitmap = histBitmap,
            onDismiss = {
                showHistoryDetailDialog = false
                selectedHistoryItem = null
            },
            topEndContent = {
                OverlayIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "delete",
                    onClick = {
                        viewModel.deleteHistoryItem(item)
                        showHistoryDetailDialog = false
                        selectedHistoryItem = null
                    },
                )
            },
        )
    }
    }
}
