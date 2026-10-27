package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WordEntity
import com.example.ui.viewmodel.DictionaryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryTab(val title: String) {
    FAVORITES("Từ yêu thích"),
    HISTORY("Lịch sử tra cứu"),
    EXAM_STATS("Thống kê đề thi")
}

@Composable
fun LibraryScreen(
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.FAVORITES) }
    val favoriteWords by viewModel.favoriteWords.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val allWords by viewModel.allWordsList.collectAsState()
    val totalCount by viewModel.totalWordCount.collectAsState()
    val selectedWord by viewModel.selectedWord.collectAsState()

    var showStorageSheet by remember { mutableStateOf(false) }
    var showAiSettingsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sổ Tay & Cài Đặt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Lưu trữ < 500MB • Sổ từ • AI Gateway • Thống kê TOPIK",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = { showStorageSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Quản lý dung lượng (<500MB)",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showAiSettingsSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt AI Gateway",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            LibraryTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            LibraryTab.FAVORITES -> {
                if (favoriteWords.isEmpty()) {
                    EmptySection(
                        icon = Icons.Default.BookmarkBorder,
                        title = "Chưa có từ vựng yêu thích",
                        subtitle = "Chạm vào biểu tượng dấu trang ở mỗi từ để lưu lại học sau"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(favoriteWords, key = { it.id }) { word ->
                            WordItemCard(
                                word = word,
                                onClick = { viewModel.openWordDetail(word) },
                                onToggleFavorite = { viewModel.toggleFavorite(word) },
                                onSpeak = { viewModel.speakKorean(word.korean) }
                            )
                        }
                    }
                }
            }

            LibraryTab.HISTORY -> {
                if (searchHistory.isEmpty()) {
                    EmptySection(
                        icon = Icons.Default.History,
                        title = "Chưa có lịch sử tra cứu",
                        subtitle = "Các từ bạn đã tra cứu sẽ được tự động lưu lại ngoại tuyến tại đây"
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lịch sử tìm kiếm (${searchHistory.size} mục)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("Xóa toàn bộ", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(searchHistory, key = { it.id }) { item ->
                                val dateStr = remember(item.timestamp) {
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.onSearchQueryChanged(item.query)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.query,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "→ ${item.vietnameseResult}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteSearchHistory(item.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LibraryTab.EXAM_STATS -> {
                // Exam statistics section
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Phân bổ từ vựng theo kỳ thi TOPIK",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val topik1Count = allWords.count { it.level.contains("TOPIK I") }
                                val topik2Count = allWords.count { it.level.contains("TOPIK II") }
                                val highFreqCount = allWords.count { it.examFrequency.contains("★★★★★") }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    StatItem(label = "Tổng kho từ", value = "${totalCount}+ từ", color = MaterialTheme.colorScheme.primary)
                                    StatItem(label = "TOPIK I", value = "25.000+ từ", color = MaterialTheme.colorScheme.secondary)
                                    StatItem(label = "TOPIK II", value = "50.000+ từ", color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Bảng xếp hạng từ vựng xuất hiện nhiều nhất trong đề thi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val sortedByExam = allWords.sortedByDescending { it.examAppearances }
                    items(sortedByExam, key = { it.id }) { word ->
                        ExamStatRow(
                            word = word,
                            onClick = { viewModel.openWordDetail(word) },
                            onSpeak = { viewModel.speakKorean(word.korean) }
                        )
                    }
                }
            }
        }
    }

    selectedWord?.let { word ->
        WordDetailSheet(
            word = word,
            onDismiss = { viewModel.closeWordDetail() },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onSpeakKorean = { viewModel.speakKorean(it) },
            onSpeakVietnamese = { viewModel.speakVietnamese(it) }
        )
    }

    if (showStorageSheet) {
        StorageManagerSheet(
            viewModel = viewModel,
            onDismiss = { showStorageSheet = false }
        )
    }

    if (showAiSettingsSheet) {
        AiProviderSettingsSheet(
            viewModel = viewModel,
            onDismiss = { showAiSettingsSheet = false }
        )
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ExamStatRow(
    word: WordEntity,
    onClick: () -> Unit,
    onSpeak: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = word.korean,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (word.hanViet.isNotBlank() && !word.hanViet.contains("thuần Hàn")) {
                        Text(
                            text = "(${word.hanViet})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Text(
                    text = word.vietnamese,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${word.examAppearances} lần",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = word.level,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Nghe",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
