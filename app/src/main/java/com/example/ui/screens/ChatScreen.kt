package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.MessageType
import com.example.data.model.LocalModelFile
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.EduLocalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// AI Character definitions to match the "Select Character" layout of the screenshot
data class AICharacter(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val iconEmoji: String,
    val avatarBg: Color,
    val startColor: Color,
    val welcomeMessage: String
)

val AI_CHARACTERS = listOf(
    AICharacter(
        id = "general-tutor",
        name = "General Tutor AI",
        role = "Dosen & Tutor Sains",
        description = "Membantu memecahkan hitungan matematika, merinci konsep fisika, biologi, kimia secara runut.",
        iconEmoji = "🎓",
        avatarBg = Color(0xFFE0F2F1),
        startColor = Color(0xFF3F51B5),
        welcomeMessage = "Halo! Saya adalah **General Tutor AI** privat Anda. Silahkan ketik pertanyaan, lampirkan diagram fisika (Vision), atau unggah file notes/buku RAG Anda untuk kita bahas secara offline!"
    ),
    AICharacter(
        id = "software-dev",
        name = "Software Developer",
        role = "Asisten Koding & Teknologi",
        description = "Ahli koding yang siap membantu Anda menulis sintaks Kotlin, memecahkan bug, serta merancang arsitektur aplikasi.",
        iconEmoji = "💻",
        avatarBg = Color(0xFFE8EAF6),
        startColor = Color(0xFF009688),
        welcomeMessage = "Halo! Saya adalah **Software Developer** offline Anda. Siap membantu membuat modul koding berkualitas tinggi. Ada program atau sintaks yang ingin kita bedah bersama?"
    ),
    AICharacter(
        id = "graphic-designer",
        name = "Graphic Designer",
        role = "Direktur Kreatif & Desain",
        description = "Membantu mendesain komposisi visual, tata letak UI, kritik desain, serta memandu proses rancangan kreatif.",
        iconEmoji = "🎨",
        avatarBg = Color(0xFFFCE4EC),
        startColor = Color(0xFFE91E63),
        welcomeMessage = "Halo! Saya adalah asisten **Graphic Designer** digital Anda. Siap mendiskusikan palet warna, tipografi, dan inspirasi layout yang menawan. Apa proyek desain kreatif kita kali ini?"
    ),
    AICharacter(
        id = "copywriter",
        name = "Copywriter Expert",
        role = "Spesialis Teks & Bahasa",
        description = "Asisten menulis kreatif yang menyusun rilis berita, artikel blog persuasif, esai akademik, serta pidato berkelas.",
        iconEmoji = "✍️",
        avatarBg = Color(0xFFFFF3E0),
        startColor = Color(0xFFFF9800),
        welcomeMessage = "Halo! Saya **Copywriter Expert** privat Anda. Mari kita salurkan ide pikiran Anda menjadi paragraf-paragraf tulisan yang memikat, persuasif, dan bertata bahasa sempurna!"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: EduLocalViewModel,
    modifier: Modifier = Modifier,
    onNavigateToCreative: (() -> Unit)? = null
) {
    val messages by viewModel.chatMessages.collectAsState()
    val filteredMessages = remember(messages) {
        messages.filter { it.sender != MessageSender.SYSTEM }
    }
    val isGenerating by viewModel.isGenerating.collectAsState()
    val localModels by viewModel.availableModels.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }
    
    val chatSessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()

    val activeSession = remember(chatSessions, activeSessionId) {
        chatSessions.find { it.id == activeSessionId }
    }
    val selectedCharacter = remember(activeSession) {
        AI_CHARACTERS.find { it.id == activeSession?.characterId } ?: AI_CHARACTERS[0]
    }

    var showCharacterSelector by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    var showModelSelector by remember { mutableStateOf(false) }
    val selectedLlmModelId by viewModel.selectedLlmModelId.collectAsState()
    val availableLlmModels = remember(localModels) {
        localModels.filter { it.type == LocalModelFile.ModelType.LLM }
    }

    // Attachment state management
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var attachedType by remember { mutableStateOf<MessageType?>(null) }
    var attachedFileName by remember { mutableStateOf("") }

    // File pickers
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedType = MessageType.IMAGE
            attachedFileName = "Gambar_Materi.jpg"
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedType = MessageType.FILE
            attachedFileName = "Catatan_Kuliah.txt"
        }
    }

    // Scroll to bottom when a new message breaks in
    LaunchedEffect(filteredMessages.size, isGenerating) {
        if (filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Riwayat Obrolan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.createNewSession("Sesi Tutor Baru", selectedCharacter.id)
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sesi Baru")
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatSessions) { session ->
                            val isSelected = session.id == activeSessionId
                            val char = AI_CHARACTERS.find { it.id == session.characterId } ?: AI_CHARACTERS[0]

                            Card(
                                onClick = {
                                    viewModel.selectSession(session.id)
                                    coroutineScope.launch { drawerState.close() }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(char.avatarBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(char.iconEmoji, fontSize = 16.sp)
                                        }
                                        Column {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val dateText = remember(session.timestamp) {
                                                SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
                                            }
                                            Text(
                                                text = "${char.name} • $dateText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.deleteSession(session.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (chatSessions.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearHistory()
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Bersihkan Semua History", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Buka Riwayat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCharacterSelector = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Avatar circle container matching screen 3 of the screenshot
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(selectedCharacter.avatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedCharacter.iconEmoji,
                                fontSize = 22.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "You are chatting with a",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = selectedCharacter.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Pilih Karakter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    var showTopMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Pilihan Lainnya",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pilih Model LLM") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    showModelSelector = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Buka Stable Diffusion") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToCreative?.invoke()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isFavorited) "Hapus dari Favorit" else "Tambah ke Favorit") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorited) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    isFavorited = !isFavorited
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bersihkan Chat") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.clearHistory()
                                }
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


        // Model Selector Dialog
        if (showModelSelector) {
            Dialog(onDismissRequest = { showModelSelector = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pilih Model Chat LLM",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Model yang aktif akan memproses pesan Anda. Pastikan model sudah diunduh terlebih dahulu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            availableLlmModels.forEach { model ->
                                val isSelected = selectedLlmModelId == model.id
                                val isDownloaded = model.isDownloaded
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isDownloaded) {
                                            viewModel.selectLlmModel(model.id)
                                            showModelSelector = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                if (isDownloaded) {
                                                    viewModel.selectLlmModel(model.id)
                                                    showModelSelector = false
                                                }
                                            },
                                            enabled = isDownloaded
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDownloaded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isDownloaded) "Siap digunakan • ${model.displaySize}" else "Belum diunduh (Penyimpanan: ${model.displaySize})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isDownloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { showModelSelector = false }) {
                            Text("Batal")
                        }
                    }
                }
            }
        }

        // Character Select Sheet/Dialog matching Screen 2 style
        if (showCharacterSelector) {
            Dialog(onDismissRequest = { showCharacterSelector = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle / aesthetic bar
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Select Character",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Choose your AI-character and embark on a learning journey with EduLocal AI, the offline mobile chat app that will take you on an adventure like no other.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        var tempSelected by remember(selectedCharacter) { mutableStateOf(selectedCharacter) }
                        
                        // Selectable Characters List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AI_CHARACTERS.forEach { char ->
                                val isCharSelected = tempSelected.id == char.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { tempSelected = char },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCharSelected) 
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else 
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isCharSelected) 1.5.dp else 1.dp,
                                        color = if (isCharSelected) 
                                            MaterialTheme.colorScheme.primary 
                                        else 
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(char.avatarBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(char.iconEmoji, fontSize = 22.sp)
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = char.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = char.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                lineHeight = 14.sp
                                            )
                                        }

                                        if (isCharSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Large accent Selection Button
                        Button(
                            onClick = {
                                viewModel.createNewSession("Sesi ${tempSelected.name}", tempSelected.id)
                                showCharacterSelector = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = "Select",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Active session title bar matching the mockup (Screen 3)
            var showEditTitleDialog by remember { mutableStateOf(false) }
            activeSession?.let { currentSession ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currentSession.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit button as a rounded-square outline container
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { showEditTitleDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Ubah Topik",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Plus button as a rounded-square outline container
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.createNewSession("Sesi Tutor Baru", selectedCharacter.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Sesi Baru",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (showEditTitleDialog) {
                    var tempTitle by remember { mutableStateOf(currentSession.title) }
                    AlertDialog(
                        onDismissRequest = { showEditTitleDialog = false },
                        title = { Text("Ubah Topik Belajar") },
                        text = {
                            OutlinedTextField(
                                value = tempTitle,
                                onValueChange = { tempTitle = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.updateSessionTitle(currentSession.id, tempTitle)
                                showEditTitleDialog = false
                            }) {
                                Text("Simpan")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditTitleDialog = false }) {
                                Text("Batal")
                            }
                        }
                    )
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredMessages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Centered visual character header
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(selectedCharacter.avatarBg)
                                    .border(2.dp, selectedCharacter.startColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedCharacter.iconEmoji,
                                    fontSize = 42.sp
                                )
                            }
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Chat dengan ${selectedCharacter.name}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = selectedCharacter.role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = selectedCharacter.startColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Asisten AI lokal, berjalan 100% offline dan privat menggunakan model hemat daya lokal Anda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Saran Pertanyaan:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Dynamic Quick Starter Suggestions
                            val samplePrompts = when (selectedCharacter.id) {
                                "general-tutor" -> listOf(
                                    "Jelaskan konsep fotosintesis secara singkat." to "🌿 Biologi",
                                    "Bagaimana rumus hukum kekekalan energi?" to "⚡ Fisika"
                                )
                                "software-dev" -> listOf(
                                    "Contoh penggunaan coroutines di Kotlin." to "💻 Kotlin",
                                    "Buat UI Jetpack Compose tombol melayang." to "🎨 Compose"
                                )
                                "graphic-designer" -> listOf(
                                    "Palet warna terbaik untuk aplikasi kesehatan." to "🎨 Desain",
                                    "Saran ukuran font untuk tipografi web." to "✍️ Font"
                                )
                                "copywriter" -> listOf(
                                    "Tulis slogan produk ramah lingkungan." to "✍️ Teks",
                                    "Ide paragraf pembuka esai tentang AI." to "📝 Esai"
                                )
                                else -> listOf(
                                    "Berikan saya tips belajar yang efektif." to "📚 Belajar",
                                    "Apa keuntungan menjalankan AI offline?" to "💡 Tips"
                                )
                            }
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                samplePrompts.forEach { (prompt, badge) ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { textInput = prompt },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(selectedCharacter.startColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = selectedCharacter.startColor,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = prompt,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Gunakan",
                                                tint = selectedCharacter.startColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredMessages) { message ->
                        ChatBubble(message = message)
                    }
                }
                if (isGenerating) {
                    item {
                        TypingIndicator()
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Attachment Panel indicator line
            AnimatedVisibility(
                visible = attachedUri != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (attachedType == MessageType.IMAGE) Icons.Default.Image else Icons.Default.AttachFile,
                        contentDescription = "Lampiran",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachedFileName,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Batal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable {
                                attachedUri = null
                                attachedType = null
                                attachedFileName = ""
                            }
                            .padding(4.dp)
                    )
                }
            }

            // Input Row Panel matching Screen 3 of the mockup
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // Typing Bar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var showAttachmentMenu by remember { mutableStateOf(false) }

                        TextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = {
                                Text(
                                    "Tanya EduLocal...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            leadingIcon = {
                                Box {
                                    IconButton(onClick = { showAttachmentMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Attachment",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAttachmentMenu,
                                        onDismissRequest = { showAttachmentMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Unggah Dokumen (.txt)", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                docPicker.launch("text/*")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Unggah Gambar", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                showAttachmentMenu = false
                                                imagePicker.launch("image/*")
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 112.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clip(RoundedCornerShape(24.dp))
                                .testTag("chat_input_field"),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            singleLine = false,
                            maxLines = 4
                        )

                        // SD Shortcut Button
                        IconButton(
                            onClick = { onNavigateToCreative?.invoke() },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0F2F1))
                                .testTag("shortcut_stable_diffusion_input_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Buka Stable Diffusion",
                                tint = Color(0xFF00796B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Send Button
                        val canSend = textInput.isNotBlank() || attachedUri != null
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    viewModel.sendMessage(textInput, attachedUri, attachedType)
                                    textInput = ""
                                    attachedUri = null
                                    attachedType = null
                                    attachedFileName = ""
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = when {
            isSystem -> Alignment.Center
            isUser -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        if (isSystem) {
            // Elegant Welcome / Disclaimer Alert Box
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Pemberitahuan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // Standard Chat Bubble Layout
            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                Surface(
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    ),
                    tonalElevation = if (isUser) 1.dp else 3.dp,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(0.85f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Display attachment notice inside chat bubble
                        if (message.attachmentPath != null) {
                            val attachName = if (message.type == MessageType.IMAGE) "📷 Gambar_Materi.jpg" else "📄 Catatan_RAG_Belajar.txt"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = attachName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Text content
                        if (isUser) {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            MarkdownText(
                                text = message.text,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Retrieve context watermark for RAG
                if (message.rContext != null) {
                    Text(
                        text = "⚡ Menggunakan Referensi RAG Lokal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                        fontSize = 10.sp
                    )
                }

                // Local timestamp
                val timeString = remember(message.timestamp) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "typing")
        
        @Composable
        fun animateDot(delayMillis: Int): Float {
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 800
                        0.4f at 0
                        1.0f at 300
                        0.4f at 600
                    },
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayMillis)
                ),
                label = "dot"
            )
            return scale
        }

        val dot1 = animateDot(0)
        val dot2 = animateDot(200)
        val dot3 = animateDot(400)

        Box(modifier = Modifier.size(6.dp * dot1).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.size(6.dp * dot2).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.size(6.dp * dot3).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Berpikir offline...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}
