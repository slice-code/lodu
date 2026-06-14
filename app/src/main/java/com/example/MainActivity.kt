package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CreativeModeScreen
import com.example.ui.screens.GalleryScreen
import com.example.ui.screens.ModelManagementScreen
import com.example.ui.screens.StudyModeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EduLocalViewModel
import com.example.ui.viewmodel.EduLocalViewModelFactory

enum class NavigationTab {
    CHAT, CREATIVE, STUDY, GALLERY, MODEL
}

class MainActivity : ComponentActivity() {
    
    private val viewModel: EduLocalViewModel by viewModels {
        EduLocalViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentTab by remember { mutableStateOf(NavigationTab.CHAT) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.CHAT,
                                onClick = { currentTab = NavigationTab.CHAT },
                                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                                label = { Text("Chat", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("tab_chat")
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.CREATIVE,
                                onClick = { currentTab = NavigationTab.CREATIVE },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Sketsa SD") },
                                label = { Text("Sketsa", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("tab_creative")
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.STUDY,
                                onClick = { currentTab = NavigationTab.STUDY },
                                icon = { Icon(Icons.Default.Book, contentDescription = "Belajar RAG") },
                                label = { Text("Belajar", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("tab_study")
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.GALLERY,
                                onClick = { currentTab = NavigationTab.GALLERY },
                                icon = { Icon(Icons.Default.Palette, contentDescription = "Galeri Sketsa") },
                                label = { Text("Galeri", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("tab_gallery")
                            )
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.MODEL,
                                onClick = { currentTab = NavigationTab.MODEL },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                                label = { Text("Pengaturan", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("tab_model")
                            )
                        }
                    }
                ) { innerPadding ->
                    val contentModifier = Modifier
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .fillMaxSize()

                    fun Modifier.tabLayer(visible: Boolean): Modifier = this.then(
                        if (visible) {
                            Modifier.zIndex(1f)
                        } else {
                            Modifier
                                .zIndex(-1f)
                                .graphicsLayer { alpha = 0f }
                        }
                    )

                    Box(modifier = contentModifier) {
                        ChatScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .tabLayer(currentTab == NavigationTab.CHAT),
                            onNavigateToCreative = { currentTab = NavigationTab.CREATIVE }
                        )
                        CreativeModeScreen(
                            viewModel = viewModel,
                            isActive = currentTab == NavigationTab.CREATIVE,
                            modifier = Modifier
                                .fillMaxSize()
                                .tabLayer(currentTab == NavigationTab.CREATIVE)
                        )
                        StudyModeScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .tabLayer(currentTab == NavigationTab.STUDY)
                        )
                        GalleryScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .tabLayer(currentTab == NavigationTab.GALLERY),
                            onNavigateToChat = { currentTab = NavigationTab.CHAT }
                        )
                        ModelManagementScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .tabLayer(currentTab == NavigationTab.MODEL)
                        )
                    }
                }
            }
        }
    }
}
