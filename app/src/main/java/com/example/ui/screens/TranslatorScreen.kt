package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.RichMarkdownText
import com.example.ui.viewmodel.DictionaryViewModel
import com.example.util.TranslationEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sourceText by viewModel.translationSource.collectAsState()
    val resultText by viewModel.translationResult.collectAsState()
    val isFromKorean by viewModel.isFromKorean.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val translationHistory by viewModel.translationHistory.collectAsState()
    val selectedEngine by viewModel.selectedTranslationEngine.collectAsState()
    val aiProviders by viewModel.aiProviders.collectAsState()

    var showClearTranslationHistoryDialog by remember { mutableStateOf(false) }
    var showAiSettingsSheet by remember { mutableStateOf(false) }
    var showCloudAiRequirementDialog by remember { mutableStateOf(false) }

    val hasValidCloudKey = remember(aiProviders) {
        viewModel.hasValidCloudAiProvider()
    }

    val fromLangLabel = if (isFromKorean) "Tiếng Hàn" else "Tiếng Việt"
    val toLangLabel = if (isFromKorean) "Tiếng Việt" else "Tiếng Hàn"

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("translator_screen")
    ) {
        // Fullscreen Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng màn hình dịch",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dịch Thuật Song Ngữ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Translate • DeepL • Local Tầng 2 • Cloud AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showAiSettingsSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt AI",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            // Engine selection chips row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Công cụ dịch thuật:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TranslationEngine.values().forEach { engine ->
                            val isSelected = selectedEngine == engine
                            val isCloudAi = engine == TranslationEngine.CLOUD_AI

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isCloudAi && !hasValidCloudKey) {
                                        showCloudAiRequirementDialog = true
                                    } else {
                                        viewModel.setTranslationEngine(engine)
                                    }
                                },
                                leadingIcon = {
                                    when (engine) {
                                        TranslationEngine.GOOGLE_TRANSLATE -> Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                        TranslationEngine.DEEPL -> Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        TranslationEngine.LOCAL_MODEL -> Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                        TranslationEngine.CLOUD_AI -> {
                                            if (hasValidCloudKey) {
                                                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                                            } else {
                                                Icon(Icons.Default.Lock, contentDescription = "Chưa có key", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(engine.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (isCloudAi && !hasValidCloudKey) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("(Cần Key)", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    // Engine description banner
                    Text(
                        text = selectedEngine.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Language Switcher Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fromLangLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        IconButton(
                            onClick = { viewModel.swapLanguages() },
                            modifier = Modifier.testTag("swap_language_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Đổi chiều dịch",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = toLangLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Source Input Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nhập nội dung ($fromLangLabel)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (sourceText.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearTranslation() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Xóa nội dung",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = sourceText,
                            onValueChange = { viewModel.setTranslationSource(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp)
                                .testTag("translation_source_input"),
                            placeholder = {
                                Text(
                                    if (isFromKorean) "Ví dụ: 안녕하세요, 한국어를 공부하고 있어요..."
                                    else "Ví dụ: Xin chào, tôi đang học tiếng Hàn..."
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                if (sourceText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            if (isFromKorean) viewModel.speakKorean(sourceText)
                                            else viewModel.speakVietnamese(sourceText)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Phát âm văn bản gốc",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.performTranslation() },
                                enabled = sourceText.isNotBlank() && !isTranslating,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("translate_button")
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Dịch ngay")
                            }
                        }
                    }
                }
            }

            // Translation Result Card
            if (resultText.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Kết quả ($toLangLabel)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = selectedEngine.title,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            if (isFromKorean) viewModel.speakVietnamese(resultText)
                                            else viewModel.speakKorean(resultText)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Phát âm kết quả",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Dịch thuật", resultText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Sao chép kết quả",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Hiển thị định dạng Markdown chuẩn phong cách Word
                            RichMarkdownText(
                                markdown = resultText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("translation_result_text")
                            )
                        }
                    }
                }
            }

            // Recent Translation History
            if (translationHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lịch sử dịch gần đây",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TextButton(onClick = { showClearTranslationHistoryDialog = true }) {
                            Text("Xóa lịch sử", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                items(translationHistory.take(10), key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setTranslationSource(item.sourceText)
                                viewModel.performTranslation()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.sourceText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.translatedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteTranslationHistory(item.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa mục này",
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

    // Confirmation Dialog for Clearing History
    if (showClearTranslationHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearTranslationHistoryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Xóa lịch sử dịch thuật?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Toàn bộ các câu và đoạn văn bản bạn đã dịch gần đây sẽ bị xóa.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearTranslationHistory()
                        showClearTranslationHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa lịch sử")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearTranslationHistoryDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Requirement Dialog for Cloud AI Model
    if (showCloudAiRequirementDialog) {
        AlertDialog(
            onDismissRequest = { showCloudAiRequirementDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Yêu cầu cấu hình API Key", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tính năng dịch bằng 'AI Model lớn hơn (Online)' yêu cầu ít nhất một nhà cung cấp AI " +
                            "(như Groq, OpenRouter, DeepSeek, Gemini...) đã được bật và nhập API Key hợp lệ.\n\n" +
                            "Bạn có muốn mở màn hình Cài đặt AI để cấu hình ngay bây giờ không?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloudAiRequirementDialog = false
                        showAiSettingsSheet = true
                    }
                ) {
                    Text("Mở Cài Đặt AI")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloudAiRequirementDialog = false }) {
                    Text("Để sau")
                }
            }
        )
    }

    // AI Provider Settings Sheet
    if (showAiSettingsSheet) {
        AiProviderSettingsSheet(
            viewModel = viewModel,
            onDismiss = { showAiSettingsSheet = false }
        )
    }
}
