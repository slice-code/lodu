package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.EduLocalViewModel
import com.example.data.model.LocalModelFile
import kotlinx.coroutines.delay

// Data structure for elegant scientific shortcuts
data class ScienceTemplate(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val loraId: String,
    val category: String,
    val categoryColor: Color,
    val icon: ImageVector
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreativeModeScreen(
    viewModel: EduLocalViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainScrollState = rememberScrollState()
    val templatesHorizontalScrollState = rememberScrollState()

    // ViewModel State bindings
    val generatedDiagram by viewModel.generatedDiagram.collectAsState()
    val isDiffusing by viewModel.diffusionProgress.collectAsState()
    val diffusionStep by viewModel.diffusionStep.collectAsState()
    val totalDiffusionSteps by viewModel.totalDiffusionSteps.collectAsState()
    val diffusionStatus by viewModel.diffusionStatus.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val activeBackend by viewModel.activeBackend.collectAsState()

    // Local UI form states
    var positivePrompt by remember { mutableStateOf("") }
    var negativePrompt by remember { mutableStateOf("blurry, low quality, pixelated, distorted, bad proportions") }
    var selectedModelId by remember { mutableStateOf("stable-diffusion-int4") }
    var selectedLora by remember { mutableStateOf("None") }
    var selectedAspectRatio by remember { mutableStateOf("1:1 (Square)") }
    var inferenceSteps by remember { mutableStateOf(20f) }
    var cfgScale by remember { mutableStateOf(7.5f) }
    var isRandomSeed by remember { mutableStateOf(true) }
    var customSeedInput by remember { mutableStateOf("1337") }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    // Educational facts carousel state for rendering screen
    val loaderFacts = remember {
        listOf(
            "AI Stable Diffusion menyusun gambar pixel demi pixel dengan mengurangi noise acak hingga tersisa struktur visual tajam.",
            "Semua proses render ini berjalan 100% offline dan privat menggunakan GPU lokal pada smartphone Anda.",
            "Style 'Blueprint Sketch' ideal digunakan untuk mencetak diagram fisika, sketsa teknik, dan struktur mekanik.",
            "Style 'Watercolor Wash' menyajikan diagram sains yang artistik dengan warna cat air lembut bergaris tipis pena.",
            "Tingkatkan nilai 'CFG Scale' jika Anda ingin AI menggambar objek yang sangat patuh pada susunan kata deskripsi Anda.",
            "Untuk hasil cetak tebal dan kontras retro tinggi, gunakan style 'Cyberpunk Glow' dengan latar belakang gelap."
        )
    }
    var currentFactIndex by remember { mutableStateOf(0) }

    // Cycle informative science tips every 4 seconds when rendering is active
    LaunchedEffect(isDiffusing) {
        if (isDiffusing) {
            while (true) {
                delay(4000)
                currentFactIndex = (currentFactIndex + 1) % loaderFacts.size
            }
        }
    }

    // List of LoRA Styles
    val loraOptions = remember {
        listOf(
            Triple("None", "🎓 Standard Teach", "Koleksi sketsa sains standard dengan warna dasar minimalis mendidik"),
            Triple("Pencil Sketch & Blueprint", "📐 Blueprint Sketch", "Gaya cetak biru ilmiah dengan garis putih presisi di atas kertas draf"),
            Triple("Watercolor Vector", "🎨 Watercolor Wash", "Visualisasi cat air halus artistik dengan garis sapuan tinta hitam"),
            Triple("3D Render Concept", "🔮 3D Tech Glass", "Model kaca siber neon 3D berkilau dengan efek reflektansi mutakhir"),
            Triple("Cyberpunk Neon Glow", "⚡ Cyberpunk Glow", "Grafis berpendar tajam dengan balutan warna ungu toksik dan hijau radioaktif")
        )
    }

    // Filter available Stable Diffusion compatible models
    val sdModels = availableModels.filter { it.type == LocalModelFile.ModelType.STABLE_DIFFUSION }

    // A collection of scientific prompt blueprints that load with one click
    val scienceTemplates = remember {
        listOf(
            ScienceTemplate(
                title = "Sel Hewan 3D",
                subtitle = "Organel Mitokondria & Nukleus",
                prompt = "Struktur visual 3D sel biologi hewan detail, memperlihatkan organel mitokondria, nukleus, membran sel, berlabel rapi, gaya gambar sains cat air edukatif",
                loraId = "Watercolor Vector",
                category = "BIOLOGI",
                categoryColor = Color(0xFF2E7D32),
                icon = Icons.Default.Science
            ),
            ScienceTemplate(
                title = "Gunung Berapi",
                subtitle = "Potongan Anatomi Magma",
                prompt = "Cross-section diagram anatomi dalam gunung berapi aktif, memperlihatkan kantung magma merah menyala, pipa kepundan, lapisan kerak bumi geologi, sketsa bergaya arsitektur teknis",
                loraId = "Pencil Sketch & Blueprint",
                category = "GEOLOGI",
                categoryColor = Color(0xFFD84315),
                icon = Icons.Default.Terrain
            ),
            ScienceTemplate(
                title = "Mesin Dinamo",
                subtitle = "Sistem Motor Listrik",
                prompt = "Cetak biru skematik motor traksi listrik fisika, memperlihatkan rotor kumparan tembaga, stator magnet, poros silinder berlabel alfabet presisi, grafis detail tinggi",
                loraId = "Pencil Sketch & Blueprint",
                category = "FISIKA",
                categoryColor = Color(0xFF1565C0),
                icon = Icons.Default.Settings
            ),
            ScienceTemplate(
                title = "Sistem Tata Surya",
                subtitle = "Lintasan Orbit Planet",
                prompt = "Model diagram tata surya astronomi, matahari berada di pusat dikelilingi orbit planet bumi, mars, yupiter melingkar rapi, bersinar neon di ruang hampa kosmik gelap",
                loraId = "Cyberpunk Neon Glow",
                category = "ASTRONOMI",
                categoryColor = Color(0xFF6A1B9A),
                icon = Icons.Default.WbSunny
            ),
            ScienceTemplate(
                title = "Siklus Air Bumi",
                subtitle = "Proses Kondensasi Hujan",
                prompt = "Diagram siklus hidrologi air bumi sederhana, awan hujan presipitasi, evaporasi permukaan laut, transpirasi hutan hijau, ilustrasi panah penunjuk arah rapi bersahabat",
                loraId = "None",
                category = "EKOLOGI",
                categoryColor = Color(0xFF00838F),
                icon = Icons.Default.WaterDrop
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Studio Desain Kreatif",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50)) // Glowing green online badge
                            )
                            Text(
                                text = "Stable Diffusion Lokal Offline (GPU Terjamin)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(mainScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Elegant Welcome Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x3E8000FF),
                                    Color(0x008000FF)
                                ),
                                center = Offset(size.width * 0.1f, size.height * 0.2f),
                                radius = size.minDimension * 0.8f
                            )
                        )
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Studio Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lab Visualisasi Sains AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Desain sketsa teoretis, diagram anatomi, dan gambar berwarna secara privat tanpa server luar. Sesuaikan parameter di bawah lalu saksikan visualisasi terwujud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // MODEL CHIP SELECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Engine AI Lokal Aktif",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text("Dukungan SD-INT4", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                    }
                }

                if (sdModels.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Menghubungkan jalur model local...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    sdModels.forEach { model ->
                        val isSelected = selectedModelId == model.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedModelId = model.id },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = model.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = model.displaySize,
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = model.description,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SCIENCE INSTANT CAROUSEL
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "2. Inspirasi Topik Sains Instan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ketuk salah satu kartu di bawah untuk memuat deskripsi ajar terlengkap secara otomatis:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(templatesHorizontalScrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    scienceTemplates.forEach { item ->
                        val isMatched = positivePrompt == item.prompt
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable {
                                    positivePrompt = item.prompt
                                    selectedLora = item.loraId
                                    Toast
                                        .makeText(
                                            context,
                                            "Prompt '${item.title}' terpasang dengan gaya '${item.loraId}'!",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMatched)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = if (isMatched) 1.5.dp else 1.dp,
                                color = if (isMatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(item.categoryColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = item.category,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = item.categoryColor
                                        )
                                    }
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = item.categoryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = item.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.subtitle,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PROMPTS AND WORKSPACE FIELDS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "3. Susun Deskripsi Visual (Prompt)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Positive Prompt
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prompt Positif (Objek Utama)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (positivePrompt.isNotEmpty()) {
                                    Text(
                                        text = "Reset Deskripsi",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { positivePrompt = "" }
                                    )
                                }
                            }
                            TextField(
                                value = positivePrompt,
                                onValueChange = { positivePrompt = it },
                                placeholder = { Text("Tulis ide visual sains seperti 'peta atom bohr terperinci'...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .testTag("sd_positive_prompt"),
                                leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                                shape = RoundedCornerShape(10.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Negative Prompt
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Prompt Negatif (Kelemahan untuk Dihindari)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextField(
                                value = negativePrompt,
                                onValueChange = { negativePrompt = it },
                                placeholder = { Text("blurry, bad proportions, distorted...", fontSize = 12.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .testTag("sd_negative_prompt"),
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(10.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // GAYA SENI LORA
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "4. Gaya Seni & Tekstur Gambar (LoRA)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        loraOptions.forEach { (id, name, desc) ->
                            val isLoraSelected = selectedLora == id

                            // Determine dynamic accent color for style cards
                            val styleBorderColor = when (id) {
                                "Pencil Sketch & Blueprint" -> Color(0xFF2196F3)
                                "Watercolor Vector" -> Color(0xFFE91E63)
                                "3D Render Concept" -> Color(0xFF9C27B0)
                                "Cyberpunk Neon Glow" -> Color(0xFF00FFCC)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isLoraSelected)
                                            styleBorderColor.copy(alpha = 0.12f)
                                        else
                                            Color.Transparent
                                    )
                                    .clickable { selectedLora = id }
                                    .border(
                                        width = if (isLoraSelected) 1.dp else 0.dp,
                                        color = if (isLoraSelected) styleBorderColor else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isLoraSelected) styleBorderColor else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconImage = when (id) {
                                        "Pencil Sketch & Blueprint" -> Icons.Default.Edit
                                        "Watercolor Vector" -> Icons.Default.Palette
                                        "3D Render Concept" -> Icons.Default.Layers
                                        "Cyberpunk Neon Glow" -> Icons.Default.Bolt
                                        else -> Icons.Default.School
                                    }
                                    Icon(
                                        imageVector = iconImage,
                                        contentDescription = null,
                                        tint = if (isLoraSelected) Color.Black else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLoraSelected) styleBorderColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        lineHeight = 13.sp
                                    )
                                }
                                RadioButton(
                                    selected = isLoraSelected,
                                    onClick = { selectedLora = id },
                                    colors = RadioButtonDefaults.colors(selectedColor = styleBorderColor)
                                )
                            }
                        }
                    }
                }
            }

            // EXPANDABLE ADVANCED SETTINGS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Konfigurasi Lanjut (Langkah, Skala, Seed)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand configs",
                            tint = Color.Gray
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 8.dp, end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                            // Sizing & Aspect Ratio
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Rasio Bidang Gambar (Aspect Ratio)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "1:1 (Square)" to Icons.Default.CropSquare,
                                        "16:9 (Landscape)" to Icons.Default.PanoramaHorizontal,
                                        "9:16 (Portrait)" to Icons.Default.VerticalDistribute,
                                        "4:3 (Academic)" to Icons.Default.DesktopMac
                                    ).forEach { (ratio, icon) ->
                                        val isRatioSelected = selectedAspectRatio == ratio
                                        FilterChip(
                                            selected = isRatioSelected,
                                            onClick = { selectedAspectRatio = ratio },
                                            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                            label = { Text(ratio, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            // Steps count
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Inference Steps (Iterasi Kejelasan)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Makin tinggi = makin tajam & memakan waktu GPU",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = "${inferenceSteps.toInt()} langkah",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Slider(
                                        value = inferenceSteps,
                                        onValueChange = { inferenceSteps = it },
                                        valueRange = 1f..50f,
                                        steps = 49
                                    )
                                }
                            }

                            // CFG Scale
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "CFG Scale (Kepatuhan Teks)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Makin tinggi = makin taat pada teks deskriptif",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = String.format("%.1f", cfgScale),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Slider(
                                        value = cfgScale,
                                        onValueChange = { cfgScale = it },
                                        valueRange = 1.0f..15.0f,
                                        steps = 28
                                    )
                                }
                            }

                            // Seed Configuration
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Gunakan Seed Acak (Acakan Baru)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Aktifkan agar setiap lukisan orisinil tak terulang",
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Switch(
                                        checked = isRandomSeed,
                                        onCheckedChange = { isRandomSeed = it }
                                    )
                                }
                                if (!isRandomSeed) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = customSeedInput,
                                        onValueChange = { customSeedInput = it.filter { char -> char.isDigit() } },
                                        label = { Text("Atur Manual Angka Kontrol (Seed)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PRIMARY GENERATE TRIGGER BUTTON
            val isButtonEnabled = positivePrompt.isNotBlank() && !isDiffusing
            Button(
                onClick = {
                    val parsedSeed = if (isRandomSeed) -1L else customSeedInput.toLongOrNull() ?: 1337L
                    viewModel.generateDiffusionSketchDetailed(
                        prompt = positivePrompt,
                        negativePrompt = negativePrompt,
                        modelId = selectedModelId,
                        loraModel = selectedLora,
                        aspectRatio = selectedAspectRatio,
                        cfgScale = cfgScale,
                        steps = inferenceSteps.toInt(),
                        seed = parsedSeed
                    )
                },
                enabled = isButtonEnabled,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("sd_generate_detailed_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Lukis",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDiffusing) "Mengevaluasi Model Lokal..." else "Lukis Sketsa Sekarang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            // RENDER WORKSPACE CANVAS DISPLAY
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "5. Monitor Kanvas Gambar Hasil",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 340.dp, max = 512.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F1014))
                        .border(
                            BorderStroke(
                                width = if (isDiffusing) 2.dp else 1.5.dp,
                                color = if (isDiffusing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDiffusing) {
                        val progressPercent = if (totalDiffusionSteps > 0) (diffusionStep * 100) / totalDiffusionSteps else 0
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(80.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 4.dp
                                )
                                Icon(
                                    imageVector = Icons.Default.Brush,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "RENDER LOKAL BERJALAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = if (totalDiffusionSteps > 0) diffusionStep.toFloat() / totalDiffusionSteps else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Iterasi evaluasi: $diffusionStep dari $totalDiffusionSteps ($progressPercent%)\nStatus: $diffusionStatus",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            // Cycling Science Facts Container
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Fakta Menarik Sains AI",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = loaderFacts[currentFactIndex],
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    } else if (generatedDiagram != null) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = generatedDiagram!!.asImageBitmap(),
                                    contentDescription = "Stable Diffusion Output Image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Action & specs description bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF07070A))
                                    .padding(vertical = 10.dp, horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sketsa sukses di-render!",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "${activeBackend.displayName} • $selectedAspectRatio",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        // Exquisite Empty Drafting Board UI
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = Color.Gray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "Kanvas Kosong (Siap Di-render)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Ketik deskripsi ilmiah, pilih salah satu inspirasi sains di atas, atau atur preset style LoRA Anda. Ketuk tombol 'Lukis Sketsa Sekarang' untuk memproses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }

            // FLOATING ACTION OPTIONS ACCORDING TO RESULTS
            val finalImage = generatedDiagram
            if (finalImage != null && !isDiffusing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Gunakan Hasil Karya Sketsa Sains Anda:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Simpan ke Galeri Lokal
                            Button(
                                onClick = {
                                    viewModel.saveSketch(
                                        bitmap = finalImage,
                                        prompt = positivePrompt,
                                        onSuccess = {
                                            Toast.makeText(
                                                context,
                                                "Gambar disimpan ke Galeri Lokal!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(
                                                context,
                                                "Gagal menyimpan: $err",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Simpan PNG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 2. Bawa Ke Chat Screen
                            Button(
                                onClick = {
                                    viewModel.insertImageMessage(finalImage, positivePrompt)
                                    Toast.makeText(
                                        context,
                                        "Asisten menerima gambar! Masuk ke tab CHAT untuk mendiskusikannya.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Guna di Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 3. Salin Prompt
                            IconButton(
                                onClick = {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("Copied Prompt", positivePrompt)
                                    clipboardManager.setPrimaryClip(clipData)
                                    Toast.makeText(context, "Deskripsi disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Salin deskripsi",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
