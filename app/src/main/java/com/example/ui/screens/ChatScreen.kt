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
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.MessageType
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
    
    // Character selection state variables
    var selectedCharacter by remember { mutableStateOf(AI_CHARACTERS[0]) }
    var showCharacterSelector by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
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
                    IconButton(
                        onClick = { onNavigateToCreative?.invoke() },
                        modifier = Modifier.testTag("shortcut_stable_diffusion_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Buka Stable Diffusion",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { isFavorited = !isFavorited }) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorit",
                            tint = if (isFavorited) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear History",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        // Active configuration pills state matching Screen 3 of screenshot
        var activeTone by remember { mutableStateOf("Tutor Indonesia") }
        var activeStyle by remember { mutableStateOf("Optimistic") }
        var activeOutput by remember { mutableStateOf("Default") }

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

                        var tempSelected by remember { mutableStateOf(selectedCharacter) }
                        
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
                                selectedCharacter = tempSelected
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
            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

            // Input Row Panel (Telegram/WhatsApp professional style)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Quick Option Pills on top of Text Input bar as seen in Screen 3 of screenshot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pill 1: Mode/Language selection
                        Surface(
                            onClick = {
                                activeTone = when (activeTone) {
                                    "Tutor Indonesia" -> "English Mode"
                                    "English Mode" -> "Bilingual (Mix)"
                                    else -> "Tutor Indonesia"
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeTone,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        // Pill 2: Style Selector
                        Surface(
                            onClick = {
                                activeStyle = when (activeStyle) {
                                    "Optimistic" -> "Academic Format"
                                    "Academic Format" -> "Ringkas & Padat"
                                    else -> "Optimistic"
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeStyle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }

                        // Pill 3: General Modifier Options
                        Surface(
                            onClick = {
                                activeOutput = when (activeOutput) {
                                    "Default" -> "Lengkap + Rumus"
                                    "Lengkap + Rumus" -> "Sederhana"
                                    else -> "Default"
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeOutput,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 10.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    // Divider dividing pills and typing bar
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    // Attachment option capsule
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { docPicker.launch("*/*") },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("attach_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach Study Document",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("attach_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Attach Homework Photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { onNavigateToCreative?.invoke() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("attach_stable_diffusion_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Buka Stable Diffusion",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Text input field inside custom styled pill
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { 
                            Text(
                                "Tanya EduLocal...", 
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ) 
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 120.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .testTag("chat_input_field"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    // Floating action style send button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (textInput.isNotBlank() || attachedUri != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = textInput.isNotBlank() || attachedUri != null) {
                                viewModel.sendMessage(textInput, attachedUri, attachedType)
                                textInput = ""
                                attachedUri = null
                                attachedType = null
                                attachedFileName = ""
                            }
                            .testTag("send_message_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (textInput.isNotBlank() || attachedUri != null)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
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
                    modifier = Modifier.widthIn(max = 290.dp)
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
