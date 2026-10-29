package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AiTutorScreen
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.FlashcardScreen
import com.example.ui.screens.HanjaHanVietScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DictionaryViewModel

enum class NavigationTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DICTIONARY(
        "Từ điển",
        Icons.Filled.MenuBook,
        Icons.Outlined.MenuBook,
        "nav_dictionary_tab"
    ),
    HANJA(
        "Hán tự",
        Icons.Filled.AccountTree,
        Icons.Outlined.AccountTree,
        "nav_hanja_tab"
    ),
    AI_TUTOR(
        "AI Tutor",
        Icons.Filled.SmartToy,
        Icons.Outlined.SmartToy,
        "nav_ai_tab"
    ),
    FLASHCARD(
        "Flashcard",
        Icons.Filled.Style,
        Icons.Outlined.Style,
        "nav_flashcard_tab"
    ),
    LIBRARY(
        "Sổ tay",
        Icons.Filled.Bookmark,
        Icons.Outlined.BookmarkBorder,
        "nav_library_tab"
    ),
    SETTINGS(
        "Cài đặt",
        Icons.Filled.Settings,
        Icons.Outlined.Settings,
        "nav_settings_tab"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: DictionaryViewModel = viewModel()
                var currentTab by remember { mutableStateOf(NavigationTab.DICTIONARY) }
                val selectedWord by viewModel.selectedWord.collectAsState()

                // If word detail dialog is open and user presses back, close it
                BackHandler(enabled = selectedWord != null) {
                    viewModel.closeWordDetail()
                }

                // If on another tab and back is pressed, return to Dictionary
                BackHandler(enabled = selectedWord == null && currentTab != NavigationTab.DICTIONARY) {
                    currentTab = NavigationTab.DICTIONARY
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("main_navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp
                        ) {
                            NavigationTab.values().forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    modifier = Modifier.testTag(tab.testTag),
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title, fontSize = 11.sp, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        NavigationTab.DICTIONARY -> DictionaryScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavigationTab.HANJA -> HanjaHanVietScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavigationTab.AI_TUTOR -> AiTutorScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { currentTab = NavigationTab.SETTINGS },
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavigationTab.FLASHCARD -> FlashcardScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavigationTab.LIBRARY -> LibraryScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        NavigationTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
