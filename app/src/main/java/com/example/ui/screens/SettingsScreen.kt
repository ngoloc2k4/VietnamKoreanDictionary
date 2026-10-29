package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.gateway.AiProviderConfig
import com.example.ai.gateway.ProviderConnectionStatus
import com.example.ai.tier1.AiModelManager
import com.example.ai.tier1.DownloadState
import com.example.ai.tier1.LocalModelInfo
import com.example.ai.tier1.ModelPreset
import com.example.ai.tier1.Tier2ExecutionMode
import com.example.ui.viewmodel.DictionaryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DictionaryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf(
        "🤖 Mô hình AI",
        "☁️ Cloud AI",
        "🔊 Giọng đọc",
        "🛠️ LAN & Bộ nhớ"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Top Header
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Cài đặt & Quản lý Hệ thống",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Kiến trúc AI 2 Tầng • Cổng Cloud • TTS • LAN Debug",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v2.5",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> AiModelsSettingsTab(viewModel = viewModel)
                1 -> CloudAiSettingsTab(viewModel = viewModel)
                2 -> TtsSettingsTab(viewModel = viewModel)
                3 -> LanAndStorageSettingsTab(viewModel = viewModel)
            }
        }
    }
}

/**
 * TAB 1: AI MODELS (TIER 1 & TIER 2)
 * Full control over On-device models, downloaded models, custom imports, tier switching.
 */
@Composable
private fun AiModelsSettingsTab(viewModel: DictionaryViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val activeLocalModel by viewModel.activeLocalModel.collectAsState()
    val activeTier2Model by viewModel.activeTier2Model.collectAsState()
    val tier2Mode by viewModel.tier2ExecutionMode.collectAsState()
    val downloadState by viewModel.modelDownloadState.collectAsState()

    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var targetImportTier by remember { mutableStateOf(1) }
    var customUrlText by remember { mutableStateOf("") }
    var customFileNameText by remember { mutableStateOf("") }
    var customTierForUrl by remember { mutableStateOf(1) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "imported_model_${System.currentTimeMillis()}.gguf"
            viewModel.importLocalModel(uri, fileName, targetImportTier)
            Toast.makeText(context, "Đang nạp mô hình vào Tầng $targetImportTier...", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Concept Overview Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kiến Trúc AI 2 Tầng Cục Bộ (Zero-Token)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "• Tầng 1 (<150MB): Phân loại câu lệnh, trích xuất Local RAG từ 78.000 từ, tra cứu tức thì trên CPU.\n" +
                               "• Tầng 2 (0.5B-0.8B): Dịch câu đa mệnh đề, phân tích liên từ (-고 나서...), chiết tự Hán-Hàn sâu sắc ngoại tuyến.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Tier 2 Mode Switcher
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Chế độ xử lý cho Tầng 2:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = tier2Mode == Tier2ExecutionMode.OFFLINE_LOCAL,
                            onClick = { viewModel.setTier2ExecutionMode(Tier2ExecutionMode.OFFLINE_LOCAL) },
                            label = { Text("📱 Ngoại tuyến trên máy", fontSize = 12.sp) },
                            leadingIcon = if (tier2Mode == Tier2ExecutionMode.OFFLINE_LOCAL) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = tier2Mode == Tier2ExecutionMode.ONLINE_CLOUD,
                            onClick = { viewModel.setTier2ExecutionMode(Tier2ExecutionMode.ONLINE_CLOUD) },
                            label = { Text("☁️ Đám mây (Cloud)", fontSize = 12.sp) },
                            leadingIcon = if (tier2Mode == Tier2ExecutionMode.ONLINE_CLOUD) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Download Progress State Banner
        if (downloadState !is DownloadState.Idle) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (val state = downloadState) {
                            is DownloadState.Downloading -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Đang tải: ${state.modelId}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("${state.progress}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Đã nhận: ${state.downloadedMb} MB / ${state.totalMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                OutlinedButton(
                                    onClick = { viewModel.cancelModelDownload() },
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Hủy tải", fontSize = 11.sp)
                                }
                            }
                            is DownloadState.Success -> {
                                Text("✅ Đã tải thành công: ${state.fileName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { viewModel.resetModelDownloadState() }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Đóng")
                                }
                            }
                            is DownloadState.Failed -> {
                                Text("❌ Lỗi tải: ${state.errorMessage}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.resetModelDownloadState() }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Đóng")
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // ==========================================
        // SECTION 1: TẦNG 1 (LOCAL FAST RAG & INTENT)
        // ==========================================
        item {
            SectionHeader(
                icon = Icons.Filled.Bolt,
                title = "Tầng 1: Prompt Optimizer & Local RAG (<150MB)",
                subtitle = "Chạy siêu nhẹ trên CPU, kích thước tối ưu cho di động"
            )
        }

        // Currently Active Tier 1
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mô hình Tầng 1 đang kích hoạt:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = activeLocalModel?.name ?: "Heuristic Tầng 1 (0MB, Tối ưu RAM)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (activeLocalModel != null) {
                        TextButton(
                            onClick = { viewModel.setActiveLocalModel(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Dùng Heuristic (0MB)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Downloaded & Imported Models for Tier 1
        val tier1Downloaded = downloadedModels.filter { it.tier == 1 }
        if (tier1Downloaded.isNotEmpty()) {
            items(tier1Downloaded) { model ->
                ModelItemCard(
                    model = model,
                    isActive = model.isActiveTier1,
                    isPreset = !model.isCustom,
                    onActivate = { viewModel.setActiveLocalModel(model.fileName) },
                    onDeactivate = { viewModel.setActiveLocalModel(null) },
                    onSwitchTier = { viewModel.setCustomModelTier(model.fileName, 2) },
                    onDelete = { viewModel.deleteLocalModel(model.fileName) }
                )
            }
        } else {
            item {
                Text(
                    text = "Chưa có mô hình Tầng 1 nào được tải hoặc nhập. Hệ thống đang dùng thuật toán Heuristic 0MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Presets available to download for Tier 1
        val tier1PresetsToDownload = AiModelManager.PRESET_MODELS.filter { preset ->
            preset.tier == 1 && downloadedModels.none { it.fileName == preset.fileName }
        }
        if (tier1PresetsToDownload.isNotEmpty()) {
            item {
                Text(
                    text = "Mô hình đề xuất cho Tầng 1 (Hugging Face Hub):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(tier1PresetsToDownload) { preset ->
                PresetDownloadCard(
                    preset = preset,
                    isDownloading = (downloadState as? DownloadState.Downloading)?.modelId == preset.id,
                    onDownload = { viewModel.downloadModelPreset(preset) }
                )
            }
        }

        // Import Button for Tier 1
        item {
            OutlinedButton(
                onClick = {
                    targetImportTier = 1
                    filePickerLauncher.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("📥 Nhập mô hình cho Tầng 1 từ bộ nhớ (.gguf)")
            }
        }

        // ==========================================
        // SECTION 2: TẦNG 2 (HEAVY REASONING & TRANSLATION)
        // ==========================================
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(
                icon = Icons.Filled.Psychology,
                title = "Tầng 2: Heavy Reasoning & Translation (0.5B - 0.8B)",
                subtitle = "Chạy suy luận sâu, dịch câu phức và giải thích ngữ pháp ngoại tuyến"
            )
        }

        // Currently Active Tier 2
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mô hình Tầng 2 đang kích hoạt:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = activeTier2Model?.name ?: "Chưa chọn (Qwen2.5-0.5B mặc định)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (activeTier2Model != null) {
                        TextButton(
                            onClick = { viewModel.setActiveTier2Model(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Bỏ chọn", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Downloaded & Imported Models for Tier 2
        val tier2Downloaded = downloadedModels.filter { it.tier == 2 }
        if (tier2Downloaded.isNotEmpty()) {
            items(tier2Downloaded) { model ->
                ModelItemCard(
                    model = model,
                    isActive = model.isActiveTier2,
                    isPreset = !model.isCustom,
                    onActivate = { viewModel.setActiveTier2Model(model.fileName) },
                    onDeactivate = { viewModel.setActiveTier2Model(null) },
                    onSwitchTier = { viewModel.setCustomModelTier(model.fileName, 1) },
                    onDelete = { viewModel.deleteLocalModel(model.fileName) }
                )
            }
        } else {
            item {
                Text(
                    text = "Chưa có mô hình Tầng 2 ngoại tuyến nào. Bạn có thể tải mô hình Qwen2.5-0.5B (~390MB) bên dưới hoặc nhập tệp GGUF từ máy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Presets available to download for Tier 2
        val tier2PresetsToDownload = AiModelManager.PRESET_MODELS.filter { preset ->
            preset.tier == 2 && downloadedModels.none { it.fileName == preset.fileName }
        }
        if (tier2PresetsToDownload.isNotEmpty()) {
            item {
                Text(
                    text = "Mô hình đề xuất cho Tầng 2 (Hugging Face Hub):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(tier2PresetsToDownload) { preset ->
                PresetDownloadCard(
                    preset = preset,
                    isDownloading = (downloadState as? DownloadState.Downloading)?.modelId == preset.id,
                    onDownload = { viewModel.downloadModelPreset(preset) }
                )
            }
        }

        // Import Button for Tier 2 & Custom URL Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        targetImportTier = 2
                        filePickerLauncher.launch("*/*")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("📥 Nhập Tầng 2 từ máy", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showCustomUrlDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("🌐 Tải từ URL HuggingFace", fontSize = 11.sp)
                }
            }
        }
    }

    // Custom URL Download Dialog
    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Tải Mô Hình Từ URL Trực Tiếp") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nhập URL tải tệp GGUF trực tiếp (ví dụ từ Hugging Face resolve/main):", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        label = { Text("Download URL") },
                        placeholder = { Text("https://huggingface.co/.../model.gguf") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customFileNameText,
                        onValueChange = { customFileNameText = it },
                        label = { Text("Tên tệp lưu (.gguf)") },
                        placeholder = { Text("custom_model.gguf") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Phân tầng:", style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = customTierForUrl == 1,
                            onClick = { customTierForUrl = 1 },
                            label = { Text("Tầng 1") }
                        )
                        FilterChip(
                            selected = customTierForUrl == 2,
                            onClick = { customTierForUrl = 2 },
                            label = { Text("Tầng 2") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = customUrlText.trim()
                        val fileName = customFileNameText.trim().ifBlank { url.substringAfterLast('/').substringBefore('?') }
                        if (url.isNotBlank()) {
                            viewModel.downloadCustomModel(url, fileName, customTierForUrl)
                            showCustomUrlDialog = false
                        }
                    },
                    enabled = customUrlText.isNotBlank()
                ) {
                    Text("Bắt đầu tải")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

/**
 * TAB 2: CLOUD AI GATEWAY
 * Manage API keys for Groq, Gemini, Claude, OpenRouter, OpenAI, and test connections.
 */
@Composable
private fun CloudAiSettingsTab(viewModel: DictionaryViewModel) {
    val context = LocalContext.current
    val providers by viewModel.aiProviders.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val connectionStatuses by viewModel.connectionStatuses.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cổng AI Đa Nhà Cung Cấp (Cloud AI Gateway)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Cấu hình API Key của bạn để sử dụng khi kết nối mạng. Hệ thống tự động chuyển tiếp giữa các nhà cung cấp nếu có sự cố.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(providers) { provider ->
            ProviderConfigCard(
                provider = provider,
                isDefault = provider.isDefault,
                connectionStatus = connectionStatuses[provider.id] ?: ProviderConnectionStatus.Untested,
                isTesting = isTestingConnection,
                onSaveKey = { newKey -> viewModel.updateProviderApiKey(provider.id, newKey) },
                onSetDefault = { viewModel.setDefaultProvider(provider.id) },
                onToggleEnabled = { enabled -> viewModel.toggleProviderEnabled(provider.id, enabled) },
                onTestConnection = { viewModel.testProviderConnection(provider.id) }
            )
        }
    }
}

/**
 * TAB 3: TTS VOICE & PRONUNCIATION
 */
@Composable
private fun TtsSettingsTab(viewModel: DictionaryViewModel) {
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tốc độ đọc phát âm (Text-To-Speech)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Điều chỉnh tốc độ phát âm tiếng Hàn và tiếng Việt khi bấm vào biểu tượng loa trong từ điển.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tốc độ đọc: ${"%.2f".format(ttsSpeed)}x", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = when {
                            ttsSpeed < 0.85f -> "Chậm (Người mới)"
                            ttsSpeed > 1.15f -> "Nhanh (Thành thạo)"
                            else -> "Chuẩn (Tự nhiên)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Slider(
                    value = ttsSpeed,
                    onValueChange = { viewModel.setTtsSpeed(it) },
                    valueRange = 0.5f..1.5f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.75f to "Chậm (0.75x)", 0.9f to "Chuẩn (0.9x)", 1.0f to "1.0x", 1.2f to "Nhanh (1.2x)").forEach { (speed, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(ttsSpeed - speed) < 0.05f,
                            onClick = { viewModel.setTtsSpeed(speed) },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Kiểm tra giọng đọc mẫu:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedButton(
                    onClick = { viewModel.speakKorean("안녕하세요! 한국어 학습을 진심으로 응원합니다.") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nghe thử tiếng Hàn (안녕하세요!)")
                }

                OutlinedButton(
                    onClick = { viewModel.speakVietnamese("Chào mừng bạn đến với ứng dụng Từ điển Hàn Việt.") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nghe thử tiếng Việt")
                }
            }
        }
    }
}

/**
 * TAB 4: LAN DEBUG HTTP SERVER & STORAGE BUDGET
 */
@Composable
private fun LanAndStorageSettingsTab(viewModel: DictionaryViewModel) {
    val context = LocalContext.current
    val isDebugRunning by viewModel.isDebugServerRunning.collectAsState()
    val debugUrl by viewModel.debugServerUrl.collectAsState()
    val storageReport by viewModel.storageReport.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LAN Debug Server Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Máy Chủ Debug LAN (Cổng 8888)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = isDebugRunning,
                        onCheckedChange = { viewModel.toggleDebugServer() }
                    )
                }

                Text(
                    text = "Cho phép PC hoặc Agent nhà phát triển kết nối qua cùng mạng WiFi để xem Logcat và JSON API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isDebugRunning) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = debugUrl,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Debug URL", debugUrl))
                                    Toast.makeText(context, "Đã sao chép URL", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("💻 Lệnh cURL đọc Log dành cho Agent / PC:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("curl ${debugUrl}api/logs?limit=50", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Storage Budget (<500MB)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dung Lượng Bộ Nhớ (<500MB)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${storageReport.totalSizeMb} MB", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                LinearProgressIndicator(
                    progress = { (storageReport.totalSizeMb / 500.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("• Cơ sở dữ liệu: ${storageReport.databaseSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                    Text("• Mô hình AI: ${storageReport.modelsSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                    Text("• Bộ đệm: ${storageReport.cacheSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        viewModel.cleanStorageCache()
                        Toast.makeText(context, "Đã dọn dẹp bộ nhớ đệm!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dọn dẹp bộ nhớ đệm (Clear Cache)")
                }
            }
        }
    }
}

// ==========================================
// REUSABLE HELPER COMPONENTS
// ==========================================

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ModelItemCard(
    model: LocalModelInfo,
    isActive: Boolean,
    isPreset: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onSwitchTier: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (model.tier == 1) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Tầng ${model.tier}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (model.isCustom) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Tự nạp",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${model.fileName} • ${model.sizeMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tier Switcher Button for custom flexibility
                TextButton(
                    onClick = onSwitchTier,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (model.tier == 1) "Chuyển sang T2" else "Chuyển sang T1", fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Button(
                            onClick = onDeactivate,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("✓ Đang dùng", fontSize = 11.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onActivate,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Kích hoạt", fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetDownloadCard(
    preset: ModelPreset,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(preset.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        if (preset.isRecommended) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text("Khuyên dùng", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dung lượng: ~${preset.approximateSizeMb.toInt()} MB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isDownloading) "Đang tải..." else "Tải về", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigCard(
    provider: AiProviderConfig,
    isDefault: Boolean,
    connectionStatus: ProviderConnectionStatus,
    isTesting: Boolean,
    onSaveKey: (String) -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTestConnection: () -> Unit
) {
    var apiKeyInput by remember(provider.apiKey) { mutableStateOf(provider.apiKey) }
    var showKey by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDefault) 2.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isDefault,
                        onClick = onSetDefault
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(provider.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    onSaveKey(it)
                },
                label = { Text("API Key") },
                placeholder = { Text("Nhập API Key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (connectionStatus) {
                    is ProviderConnectionStatus.Success -> {
                        Text("🟢 Kết nối tốt (${connectionStatus.latencyMs}ms)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    is ProviderConnectionStatus.Failed -> {
                        Text("🔴 Lỗi: ${connectionStatus.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    is ProviderConnectionStatus.Testing -> {
                        Text("🟡 Đang kiểm tra...", style = MaterialTheme.typography.labelSmall)
                    }
                    else -> {
                        Text("⚪ Chưa kiểm tra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                OutlinedButton(
                    onClick = onTestConnection,
                    enabled = !isTesting && apiKeyInput.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Kiểm tra kết nối", fontSize = 11.sp)
                }
            }
        }
    }
}
