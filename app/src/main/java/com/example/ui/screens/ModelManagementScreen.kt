package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import android.net.Uri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.model.GpuAccelerationBackend
import com.example.data.model.LocalModelFile
import com.example.ui.viewmodel.EduLocalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: EduLocalViewModel,
    modifier: Modifier = Modifier
) {
    val models by viewModel.availableModels.collectAsState()
    val storageUsed by viewModel.totalStorageUsed.collectAsState()
    val downloadingModelIds by viewModel.downloadingModelIds.collectAsState()
    val modelDownloadStatus by viewModel.modelDownloadStatus.collectAsState()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingModelIdToDownload by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            pendingModelIdToDownload?.let { modelId ->
                viewModel.downloadModel(modelId)
                pendingModelIdToDownload = null
            }
        }
    )

    var showImportDialog by remember { mutableStateOf(false) }
    var importModelUri by remember { mutableStateOf<Uri?>(null) }
    var importModelName by remember { mutableStateOf("") }
    var importModelType by remember { mutableStateOf(LocalModelFile.ModelType.LLM) }
    var importModelFileName by remember { mutableStateOf("") }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importModelUri = uri
            var resolvedName = ""
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        resolvedName = it.getString(nameIndex)
                    }
                }
            }
            if (resolvedName.isNotBlank()) {
                importModelFileName = resolvedName
                importModelName = resolvedName.substringBeforeLast(".")
            } else {
                importModelFileName = "Model Kustom"
                importModelName = "Model Kustom"
            }
        }
    }

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

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Pengaturan & Model", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Impor Model Kustom",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabs = listOf("Model Obrolan (LLM)", "Model Gambar (SD)", "Model Lainnya")

        val filteredModels = remember(models, selectedTabIndex) {
            when (selectedTabIndex) {
                0 -> models.filter { it.type == LocalModelFile.ModelType.LLM }
                1 -> models.filter { it.type == LocalModelFile.ModelType.STABLE_DIFFUSION }
                else -> models.filter { it.type != LocalModelFile.ModelType.LLM && it.type != LocalModelFile.ModelType.STABLE_DIFFUSION }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Storage Health Monitor bar
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Penyimpanan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            val sizeFloat = storageUsed.substringBefore(" ").toFloatOrNull() ?: 0.0f
                            val isSafe = sizeFloat < 5.0f
                            val statusText = if (isSafe) "Optimal" else "Hampir Penuh"
                            val badgeBgColor = if (isSafe) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            val badgeTextColor = if (isSafe) Color(0xFF2E7D32) else Color(0xFFE65100)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kesehatan Penyimpanan AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .background(badgeBgColor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTextColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Model Terpasang: $storageUsed • Batas Aman: 6.0 GB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            val progress = (sizeFloat / 6.0f).coerceIn(0.0f, 1.0f)
                            val progressPercent = (progress * 100).toInt()
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isSafe) MaterialTheme.colorScheme.primary else Color(0xFFFF9800),
                                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "$progressPercent%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // GPU & Hardware Acceleration Configuration Panel
            item {
                var isExpanded by remember { mutableStateOf(false) }
                val activeBackend by viewModel.activeBackend.collectAsState()

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Closed state / Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = "Akselerasi",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Akselerasi Perangkat Keras",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Driver Aktif:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    val isCpu = activeBackend == GpuAccelerationBackend.CPU
                                    val activeBgColor = if (isCpu) Color(0xFFECEFF1) else Color(0xFFE0F2F1)
                                    val activeTextColor = if (isCpu) Color(0xFF455A64) else Color(0xFF00796B)
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(activeBgColor, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = activeBackend.displayName.substringBefore(" ("),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = activeTextColor
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = { isExpanded = !isExpanded }
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Sembunyikan" else "Tampilkan",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Active Backend Details summary
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Kompilator:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeBackend.technicalName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimasi Kecepatan:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = activeBackend.speedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "Rekomendasi:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                                Text(
                                    text = activeBackend.hardwareRecommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        
                        if (isExpanded) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            
                            Text(
                                text = "Pilih Kompilasi & Driver Device:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GpuAccelerationBackend.values().forEach { backend ->
                                    val isSelected = backend == activeBackend
                                    Card(
                                        onClick = { viewModel.setAccelerationBackend(backend) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) 
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                            else 
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) 
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            else 
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.setAccelerationBackend(backend) },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = backend.displayName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (backend.id == "vulkan" || backend.id == "qnn") {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFFE3F2FD), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "REKOMENDASI",
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF1976D2)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = backend.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = "Kecepatan",
                                                        tint = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Gray,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Kompilasi: ${backend.speedText}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
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

            // Section Header
            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Model",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Model Pembelajaran Tersedia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Download Status Banner Card
            if (modelDownloadStatus.isNotBlank()) {
                item {
                    val isError = modelDownloadStatus.contains("gagal", ignoreCase = true) || 
                                  modelDownloadStatus.contains("error", ignoreCase = true) ||
                                  modelDownloadStatus.contains("tidak", ignoreCase = true)
                    
                    val statusColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val statusBgColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    val statusBorderColor = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = statusBgColor),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusBorderColor)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .padding(end = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isError) Icons.Default.Info else Icons.Default.CloudDownload,
                                        contentDescription = "Status",
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = modelDownloadStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                    val activeProgressList = modelDownloadProgress.values
                                    if (activeProgressList.isNotEmpty()) {
                                        val averageProgress = activeProgressList.average().toFloat()
                                        LinearProgressIndicator(
                                            progress = { averageProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = statusColor,
                                            trackColor = statusColor.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.clearDownloadStatus() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = statusColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Category Selection Pills
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTabIndex == index
                        
                        val icon = when (index) {
                            0 -> Icons.Default.ChatBubble
                            1 -> Icons.Default.Palette
                            else -> Icons.Default.AutoAwesome
                        }
                        
                        val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        val textCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val border = if (isSelected) {
                            null
                        } else {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }

                        Card(
                            onClick = { selectedTabIndex = index },
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = RoundedCornerShape(99.dp),
                            border = border,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = textCol,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (index) {
                                        0 -> "Obrolan LLM"
                                        1 -> "Gambar SD"
                                        else -> "Lainnya"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textCol,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Model item cards list
            if (filteredModels.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "Tidak Ada Model Bawaan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Silakan impor berkas model kustom Anda sendiri (.task atau .bundle) dari penyimpanan perangkat menggunakan tombol '+' di kanan atas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                items(filteredModels) { model ->
                    ModelItemCard(
                        model = model,
                        isDownloading = downloadingModelIds.contains(model.id),
                        downloadProgress = modelDownloadProgress[model.id] ?: 0f,
                        onDownloadClick = { requestNotificationPermissionAndDownload(model.id) },
                        onDeleteClick = { viewModel.deleteModel(model.id) },
                        onCancelClick = { viewModel.cancelDownload(model.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = {
                    showImportDialog = false
                    importModelUri = null
                    importModelName = ""
                    importModelFileName = ""
                },
                title = { Text("Impor Model Kustom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Impor model LiteRT (.task), MNN (.bundle), atau model format lain yang diunduh secara eksternal (misalnya dari HuggingFace).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Pick File Button / Info
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { importFileLauncher.launch("*/*") }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (importModelUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (importModelUri != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (importModelUri != null) "File Terpilih" else "Pilih File Model",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (importModelUri != null) importModelFileName else "Klik untuk memilih file .task atau .bundle",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Model Name Input
                        OutlinedTextField(
                            value = importModelName,
                            onValueChange = { importModelName = it },
                            label = { Text("Nama Model") },
                            placeholder = { Text("Misal: Qwen 2.5 0.5B Custom") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Model Type Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Tipe Model:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    LocalModelFile.ModelType.LLM to "LLM (Chat)",
                                    LocalModelFile.ModelType.STABLE_DIFFUSION to "SD (Gambar)"
                                ).forEach { (type, label) ->
                                    val isSelected = importModelType == type
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { importModelType = type },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    LocalModelFile.ModelType.VISION to "Visi (Gambar)",
                                    LocalModelFile.ModelType.EMBEDDING to "RAG Vector"
                                ).forEach { (type, label) ->
                                    val isSelected = importModelType == type
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { importModelType = type },
                                        label = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    val isEnabled = importModelUri != null && importModelName.isNotBlank()
                    Button(
                        onClick = {
                            val uri = importModelUri
                            if (uri != null) {
                                viewModel.importCustomModel(
                                    uri = uri,
                                    name = importModelName,
                                    type = importModelType,
                                    onSuccess = {
                                        showImportDialog = false
                                        importModelUri = null
                                        importModelName = ""
                                        importModelFileName = ""
                                        android.widget.Toast.makeText(context, "Model kustom berhasil diimpor!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        android.widget.Toast.makeText(context, "Gagal mengimpor: $err", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        enabled = isEnabled
                    ) {
                        Text("Impor")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showImportDialog = false
                            importModelUri = null
                            importModelName = ""
                            importModelFileName = ""
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

@Composable
fun ModelItemCard(
    model: LocalModelFile,
    isDownloading: Boolean,
    downloadProgress: Float,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSnapdragon = remember {
        val hardware = android.os.Build.HARDWARE.lowercase()
        val board = android.os.Build.BOARD.lowercase()
        val soc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.os.Build.SOC_MODEL.lowercase()
        } else {
            ""
        }
        hardware.contains("qcom") || board.contains("qcom") || soc.contains("sm") || soc.contains("sdm") || soc.contains("snapdragon")
    }

    val totalRamGb = remember(context) {
        try {
            val actManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024 * 1024.0)
        } catch (e: Exception) {
            8.0
        }
    }
    val isLowMemory = totalRamGb <= 8.5

    val modelIcon = when (model.type) {
        LocalModelFile.ModelType.LLM -> Icons.Default.ChatBubble
        LocalModelFile.ModelType.STABLE_DIFFUSION -> Icons.Default.Palette
        LocalModelFile.ModelType.VISION -> Icons.Default.AutoAwesome
        LocalModelFile.ModelType.EMBEDDING -> Icons.Default.Book
    }
    
    val iconContainerColor = when (model.type) {
        LocalModelFile.ModelType.LLM -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        LocalModelFile.ModelType.STABLE_DIFFUSION -> Color(0xFFFCE4EC)
        LocalModelFile.ModelType.VISION -> Color(0xFFE8EAF6)
        LocalModelFile.ModelType.EMBEDDING -> Color(0xFFE8F5E9)
    }
    
    val iconColor = when (model.type) {
        LocalModelFile.ModelType.LLM -> MaterialTheme.colorScheme.primary
        LocalModelFile.ModelType.STABLE_DIFFUSION -> Color(0xFFD81B60)
        LocalModelFile.ModelType.VISION -> Color(0xFF3F51B5)
        LocalModelFile.ModelType.EMBEDDING -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular icon representator
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = modelIcon,
                        contentDescription = model.type.name,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Title and badges
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (model.isDownloaded) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Siap",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "Siap Pakai",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.displaySize,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Text(
                            text = when (model.type) {
                                LocalModelFile.ModelType.LLM -> "Chat & Koding"
                                LocalModelFile.ModelType.STABLE_DIFFUSION -> "Generator Gambar"
                                LocalModelFile.ModelType.VISION -> "Analisa Visual"
                                LocalModelFile.ModelType.EMBEDDING -> "Database RAG"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            // Description of model usage (💡 Recommendation banner)
            val recommendationText = when (model.id) {
                "qwen-2.5-0.5b-it-q8" -> "💡 Kebutuhan: Asisten Chat Lokal Free Mobile"
                "deepseek-r1-qwen-1.5b" -> "🧠 Kebutuhan: Penalaran Logis Lokal, Matematika, dan Koding"
                "qwen-2.5-1.5b-it" -> "🇮🇩 Kebutuhan: Akurasi Tinggi Bahasa Indonesia & Koding"
                "phi-3.5-mini-it" -> "🧠 Kebutuhan: Penalaran Logika & Sains Kompleks"
                "granite-3.0-1b-a400m-instruct-q8" -> "⚡ Kebutuhan: Model MoE Ultra Ringan, Respon Cepat & Hemat Baterai"
                "granite-3.0-3b-a800m-instruct-q8" -> "🧠 Kebutuhan: Model MoE Efisien dengan Penalaran & Koding Seimbang"
                "bge-small-en" -> "⚡ Kebutuhan: Database RAG & Unggah Dokumen"
                "mediapipe-vision" -> "📷 Kebutuhan: Analisa Kamera, Gambar & Diagram"
                "moondream2-tiny" -> "🖼️ Kebutuhan: Analisa Gambar Multimodal & Visual Chat"
                "stable-diffusion-1.5-mnn-int8" -> "🎨 Kebutuhan: Model manual MNN Stable Diffusion"
                "sdxl-turbo-qnn-mobile" -> "⚡ Kebutuhan: Model manual QNN Snapdragon"
                "animagine-xl-mini" -> "🌸 Kebutuhan: Ink/Anime Studio & Lukisan Ilustrasi"
                "sd-v1.5-highres" -> "🔍 Kebutuhan: Gambar Presisi Tinggi & Struktur Detail"
                else -> "⚡ Kebutuhan: Asisten EduLocal"
            }

            val (compatibilityText, compatibilityBgColor, compatibilityTextColor) = remember(model.id, isSnapdragon, isLowMemory) {
                when (model.id) {
                    "qwen-2.5-0.5b-it-q8" -> Triple("✅ Sangat Lancar (Rekomendasi Utama RAM 8GB)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "deepseek-r1-qwen-1.5b" -> Triple("✅ Sangat Lancar (Rekomendasi Utama Penalaran)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "qwen-2.5-1.5b-it" -> Triple("✅ Berjalan Baik (Akurasi Tinggi & Koding)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "phi-3.5-mini-it" -> if (isLowMemory) {
                        Triple("⚠️ RAM 8GB Agak Berat (Bisa Menyebabkan Lag)", Color(0xFFFFF3E0), Color(0xFFE65100))
                    } else {
                        Triple("✅ Sangat Lancar", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    }
                    "bge-small-en" -> Triple("✅ Sangat Ringan (Aktif Otomatis)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "mediapipe-vision" -> Triple("✅ Berjalan Baik & Stabil", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "moondream2-tiny" -> Triple("✅ Berjalan Baik (Multimodal Visi)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "stable-diffusion-1.5-mnn-int8" -> Triple("✅ Rekomendasi Utama SD (MNN Vulkan GPU)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "sdxl-turbo-qnn-mobile" -> if (isSnapdragon) {
                        Triple("✅ Cocok (Snapdragon NPU)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    } else {
                        Triple("❌ Tidak Kompatibel (Hanya untuk Chipset Snapdragon)", Color(0xFFFFEBEE), Color(0xFFC62828))
                    }
                    "granite-3.0-1b-a400m-instruct-q8" -> Triple("✅ Ultra Ringan & Sangat Lancar (Hemat RAM)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "granite-3.0-3b-a800m-instruct-q8" -> Triple("✅ Berjalan Baik & Hemat Daya (800M Aktif)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    "animagine-xl-mini" -> if (isLowMemory) {
                        Triple("⚠️ Kurang Disarankan (Resiko Crash / Memori Penuh)", Color(0xFFFFEBEE), Color(0xFFC62828))
                    } else {
                        Triple("✅ Berjalan Baik (Anime)", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    }
                    "sd-v1.5-highres" -> if (isLowMemory) {
                        Triple("⚠️ Kurang Disarankan (Resiko Crash / Memori Penuh)", Color(0xFFFFEBEE), Color(0xFFC62828))
                    } else {
                        Triple("✅ Berjalan Baik", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                    }
                    else -> Triple("✅ Kompatibel", Color(0xFFE8F5E9), Color(0xFF2E7D32))
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = recommendationText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(compatibilityBgColor)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = compatibilityText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = compatibilityTextColor
                    )
                }
            }
            
            // Description text
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            // Progress bar if downloading
            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Mengunduh...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${(downloadProgress * 100).toInt().coerceIn(0, 100)}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onCancelClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Action Buttons
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ModelActionButton(
                        model = model,
                        isDownloading = isDownloading,
                        isSnapdragon = isSnapdragon,
                        onDownloadClick = onDownloadClick,
                        onDeleteClick = onDeleteClick,
                        onCancelClick = onCancelClick
                    )
                }
            }
        }
    }
}

@Composable
fun ModelActionButton(
    model: LocalModelFile,
    isDownloading: Boolean,
    isSnapdragon: Boolean,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    if (model.isDownloaded) {
        Button(
            onClick = onDeleteClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Hapus", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else if (isDownloading) {
        Button(
            onClick = onCancelClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Batal", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else if (model.downloadUrl.isBlank()) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Button(
            onClick = {
                android.widget.Toast.makeText(
                    context,
                    "Model ini belum memiliki URL unduhan publik. Silakan impor file .task/.bundle secara manual menggunakan tombol '+' di kanan atas.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Impor", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Impor Manual", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        if (model.isResumable) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDeleteClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDownloadClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lanjutkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = "Unduh", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Unduh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
