package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.EduLocalViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: EduLocalViewModel,
    modifier: Modifier = Modifier,
    onNavigateToChat: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val savedFiles by viewModel.savedSketches.collectAsState()
    var selectedFile by remember { mutableStateOf<File?>(null) }

    // Always refresh list when entering screen
    LaunchedEffect(Unit) {
        viewModel.loadSavedSketches()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Galeri Gambar AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Koleksi Denoiser Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            // Intro Explanatory Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Gallery Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "Koleksi Lukisan & Diagram Sains",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Daftar visualisasi kreatif yang telah Anda klik 'Simpan PNG'. Semua data berdimensi utuh ini disimpan secara privat di flash storage ponsel Anda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (savedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Empty Gallery",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Galeri Masih Kosong",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        Text(
                            text = "Buka tab 'Sketsa' di sebelah menu Chat untuk mulai melukis diagram sains pertamamu, lalu pilih 'Simpan PNG' agar tampil di sini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("gallery_grid"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedFiles) { file ->
                        GalleryItemCard(
                            file = file,
                            onClick = { selectedFile = file }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Full Screen Lightbox Dialog
    selectedFile?.let { file ->
        val bitmap = remember(file) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        }

        // Parse human-readable prompt from file name (sketch_timestamp_prompt.png)
        val promptText = remember(file) {
            val nameParts = file.nameWithoutExtension.split("_")
            if (nameParts.size > 2) {
                nameParts.drop(2).joinToString(" ")
            } else {
                "Sketsa Sains Terbuka"
            }
        }

        Dialog(
            onDismissRequest = { selectedFile = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp)
                ) {
                    // Dialog Top Row Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedFile = null },
                            modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }

                        Text(
                            text = "Kajian Detil Sketsa",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        IconButton(
                            onClick = {
                                viewModel.deleteSketch(file)
                                Toast.makeText(context, "Gambar terhapus!", Toast.LENGTH_SHORT).show()
                                selectedFile = null
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.White)
                        }
                    }

                    // Main Image Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Selected Large Canvas Image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                "Gagal mendekode berkas gambar PNG.",
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Metadata overlay card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F22)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Prompt Gambar:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val date = Date(file.lastModified())
                                val format = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                                Text(
                                    text = format.format(date),
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                            }

                            Text(
                                text = "\"$promptText\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )

                            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Kirim ke Chat
                                Button(
                                    onClick = {
                                        if (bitmap != null) {
                                            viewModel.insertImageMessage(bitmap, promptText)
                                            Toast.makeText(context, "Asisten menerima gambar ini! Periksa tab Chat.", Toast.LENGTH_LONG).show()
                                            selectedFile = null
                                            onNavigateToChat?.invoke()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bawa ke Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Salin Teks
                                IconButton(
                                    onClick = {
                                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clipData = android.content.ClipData.newPlainText("Copied prompt", promptText)
                                        clipboardManager.setPrimaryClip(clipData)
                                        Toast.makeText(context, "Deskripsi disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryItemCard(
    file: File,
    onClick: () -> Unit
) {
    val bitmap = remember(file) {
        try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    // Parse prompt
    val promptText = remember(file) {
        val nameParts = file.nameWithoutExtension.split("_")
        if (nameParts.size > 2) {
            nameParts.drop(2).joinToString(" ")
        } else {
            "Visual Sketsa"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("gallery_item_${file.nameWithoutExtension.take(12)}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = promptText,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = promptText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val date = Date(file.lastModified())
                val format = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                Text(
                    text = format.format(date),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
