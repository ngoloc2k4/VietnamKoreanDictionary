package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.gateway.UnifiedAiResponse
import com.example.ai.tier1.Tier2ExecutionMode
import com.example.ui.components.RichMarkdownText
import com.example.ui.viewmodel.AiTutorExecutionTier
import com.example.ui.viewmodel.ChatMessageUi
import com.example.ui.viewmodel.DictionaryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTutorScreen(
    viewModel: DictionaryViewModel,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chatMessages by viewModel.aiChatMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    val providers by viewModel.aiProviders.collectAsState()
    val activeLocalModel by viewModel.activeLocalModel.collectAsState()
    val activeTier2Model by viewModel.activeTier2Model.collectAsState()
    val tier2Mode by viewModel.tier2ExecutionMode.collectAsState()
    val selectedTier by viewModel.selectedAiTutorTier.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val dynamicSuggestions by viewModel.dynamicSuggestions.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showStorageSheet by remember { mutableStateOf(false) }
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedDebugResponse by remember { mutableStateOf<UnifiedAiResponse?>(null) }

    val defaultProvider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()

    // Auto-scroll on new message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_tutor_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VietKor AI Tutor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "2-Tier AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val tierLabel = when (selectedTier) {
                    AiTutorExecutionTier.TIER1_LOCAL -> "⚡ Tầng 1 Local (${activeLocalModel?.name ?: "Heuristic 0MB"})"
                    AiTutorExecutionTier.TIER2_OFFLINE -> "📱 Tầng 2 Ngoại tuyến (${activeTier2Model?.name ?: "Qwen2.5-0.5B"})"
                    AiTutorExecutionTier.TIER2_CLOUD -> "☁️ Tầng 2 Đám mây (${defaultProvider?.name ?: "Cloud Gateway"})"
                }

                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Cài đặt & Quản lý Mô hình", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showClearHistoryDialog = true }) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Xóa lịch sử trò chuyện", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // In-Screen Tier & Active Model Selector Bar
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Tier Switcher Chips (Tier 1 vs Tier 2 Offline vs Tier 2 Cloud)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedTier == AiTutorExecutionTier.TIER1_LOCAL,
                        onClick = { viewModel.setAiTutorExecutionTier(AiTutorExecutionTier.TIER1_LOCAL) },
                        label = { Text("⚡ Tầng 1 Local", fontSize = 11.sp) },
                        leadingIcon = if (selectedTier == AiTutorExecutionTier.TIER1_LOCAL) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTier == AiTutorExecutionTier.TIER2_OFFLINE,
                        onClick = { viewModel.setAiTutorExecutionTier(AiTutorExecutionTier.TIER2_OFFLINE) },
                        label = { Text("📱 T2 Ngoại tuyến", fontSize = 11.sp) },
                        leadingIcon = if (selectedTier == AiTutorExecutionTier.TIER2_OFFLINE) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedTier == AiTutorExecutionTier.TIER2_CLOUD,
                        onClick = { viewModel.setAiTutorExecutionTier(AiTutorExecutionTier.TIER2_CLOUD) },
                        label = { Text("☁️ T2 Đám mây", fontSize = 11.sp) },
                        leadingIcon = if (selectedTier == AiTutorExecutionTier.TIER2_CLOUD) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Model Selector for Active Tier
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showModelPickerDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeModelDescription = when (selectedTier) {
                            AiTutorExecutionTier.TIER1_LOCAL -> "Mô hình T1: ${activeLocalModel?.name ?: "Heuristic 0MB (Mặc định)"}"
                            AiTutorExecutionTier.TIER2_OFFLINE -> "Mô hình T2: ${activeTier2Model?.name ?: "Qwen2.5-0.5B Ngoại tuyến"}"
                            AiTutorExecutionTier.TIER2_CLOUD -> "Nhà cung cấp: ${defaultProvider?.name ?: "Groq / Gemini"} (${defaultProvider?.modelName ?: ""})"
                        }
                        Text(
                            text = activeModelDescription,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Đổi", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Conversation Status Bar & Clear History Action
        if (chatMessages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch sử hội thoại (${chatMessages.size} tin nhắn)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                TextButton(
                    onClick = { showClearHistoryDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Xóa lịch sử",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(chatMessages) { message ->
                ChatMessageItem(
                    message = message,
                    onOpenDebug = { selectedDebugResponse = it }
                )
            }

            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tầng 1 đang kiểm chứng Local RAG & xử lý...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // Dynamic Suggestion Prompts with Shuffle Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { viewModel.refreshDynamicSuggestions() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎲 Đổi gợi ý",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            dynamicSuggestions.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            inputText = prompt
                            viewModel.sendAiPrompt(prompt)
                            inputText = ""
                        }
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Input Field Bar
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Hỏi về ngữ pháp, Hán tự, câu dịch...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input_field"),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendAiPrompt(toSend)
                    },
                    enabled = inputText.isNotBlank() && !isThinking,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Gửi")
                }
            }
        }
    }

    // AI Provider Settings Sheet (Tier 2 Cloud Online)
    if (showSettingsSheet) {
        AiProviderSettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Storage & On-Device Model Manager Sheet (Tier 1 Local)
    if (showStorageSheet) {
        StorageManagerSheet(
            viewModel = viewModel,
            onDismiss = { showStorageSheet = false }
        )
    }

    // Interactive In-Screen Model Picker Dialog
    if (showModelPickerDialog) {
        AlertDialog(
            onDismissRequest = { showModelPickerDialog = false },
            title = {
                Text(
                    text = when (selectedTier) {
                        AiTutorExecutionTier.TIER1_LOCAL -> "Chọn Mô hình Tầng 1 (Local)"
                        AiTutorExecutionTier.TIER2_OFFLINE -> "Chọn Mô hình Tầng 2 (Ngoại tuyến)"
                        AiTutorExecutionTier.TIER2_CLOUD -> "Chọn Nhà cung cấp Cloud (Tầng 2)"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTier) {
                        AiTutorExecutionTier.TIER1_LOCAL -> {
                            // Heuristic option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setActiveLocalModel(null)
                                        showModelPickerDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = activeLocalModel == null,
                                    onClick = {
                                        viewModel.setActiveLocalModel(null)
                                        showModelPickerDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Heuristic Siêu Nhẹ (0MB)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Tối ưu RAM, tra cứu trực tiếp kho 78.000 từ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            val t1Models = downloadedModels.filter { it.tier == 1 }
                            t1Models.forEach { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setActiveLocalModel(model.fileName)
                                            showModelPickerDialog = false
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = activeLocalModel?.fileName == model.fileName,
                                        onClick = {
                                            viewModel.setActiveLocalModel(model.fileName)
                                            showModelPickerDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${model.sizeMb} MB • ${if (model.isCustom) "Tự nạp" else "Preset"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        AiTutorExecutionTier.TIER2_OFFLINE -> {
                            val t2Models = downloadedModels.filter { it.tier == 2 }
                            if (t2Models.isEmpty()) {
                                Text(
                                    text = "Chưa có mô hình Tầng 2 nào được tải. Bạn có thể vào Cài đặt để tải Qwen2.5-0.5B (~390MB) hoặc nạp tệp .gguf từ máy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                t2Models.forEach { model ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setActiveTier2Model(model.fileName)
                                                showModelPickerDialog = false
                                            }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = activeTier2Model?.fileName == model.fileName,
                                            onClick = {
                                                viewModel.setActiveTier2Model(model.fileName)
                                                showModelPickerDialog = false
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${model.sizeMb} MB • ${if (model.isCustom) "Tự nạp" else "Preset"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }

                        AiTutorExecutionTier.TIER2_CLOUD -> {
                            providers.forEach { provider ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.setDefaultProvider(provider.id)
                                            showModelPickerDialog = false
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = provider.isDefault,
                                        onClick = {
                                            viewModel.setDefaultProvider(provider.id)
                                            showModelPickerDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(provider.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(provider.modelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            showModelPickerDialog = false
                            onNavigateToSettings()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚙️ Mở Cài đặt Quản lý Mô hình đầy đủ", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPickerDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    // Confirmation Dialog for Clearing Chat History
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("Xóa lịch sử trò chuyện?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Toàn bộ các câu hỏi và câu trả lời trong phiên hội thoại AI hiện tại sẽ được làm mới về trạng thái ban đầu.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAiChat()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "Đã xóa lịch sử trò chuyện", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa lịch sử")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // AI Debug Console Modal (FLOSS Feature)
    selectedDebugResponse?.let { debug ->
        AiDebugConsoleDialog(
            response = debug,
            onDismiss = { selectedDebugResponse = null }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessageUi,
    onOpenDebug: (UnifiedAiResponse) -> Unit
) {
    val context = LocalContext.current
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.96f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Badge for Offline / Local RAG handled
            if (message.isOfflineHandled) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "⚡ Trả lời ngoại tuyến on-device (0 Token)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Box(modifier = Modifier.padding(14.dp)) {
                    RichMarkdownText(
                        markdown = message.content,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        baseTextStyle = MaterialTheme.typography.bodyMedium,
                        isUser = isUser
                    )
                }
            }

            // Quick actions below assistant message (Copy & Debug)
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Copy action button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Tutor", message.content))
                                Toast.makeText(context, "Đã sao chép phản hồi", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Sao chép",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sao chép",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Debug Pill Button for Cloud AI
                    if (message.debugInfo != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenDebug(message.debugInfo) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${message.debugInfo.providerName} • ${message.debugInfo.latencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 10.sp
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
fun AiDebugConsoleDialog(
    response: UnifiedAiResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Debug Console (FLOSS)")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Nhà cung cấp: ${response.providerName} (${response.modelName})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Độ trễ phản hồi: ${response.latencyMs} ms",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Token tiêu thụ: Prompt: ${response.promptTokens} | Completion: ${response.completionTokens} | Tổng: ${response.totalTokens}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text("Lệnh cURL thực tế:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = response.rawCurlCommand.ifBlank { "Không có raw cURL" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("cURL", response.rawCurlCommand))
                        Toast.makeText(context, "Đã sao chép lệnh cURL vào clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sao chép cURL Command")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
