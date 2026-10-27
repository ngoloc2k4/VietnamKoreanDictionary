package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.tier1.*
import com.example.ui.viewmodel.DictionaryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerSheet(
    viewModel: DictionaryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val report by viewModel.storageReport.collectAsState()
    val ttsSpeed by viewModel.ttsSpeed.collectAsState()
    val isDebugRunning by viewModel.isDebugServerRunning.collectAsState()
    val debugUrl by viewModel.debugServerUrl.collectAsState()

    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val activeLocalModel by viewModel.activeLocalModel.collectAsState()
    val activeTier2Model by viewModel.activeTier2Model.collectAsState()
    val downloadState by viewModel.modelDownloadState.collectAsState()
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var customUrlText by remember { mutableStateOf("") }
    var customNameText by remember { mutableStateOf("") }
    var customTierSelected by remember { mutableStateOf(1) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "imported_model.gguf"
            viewModel.importLocalModel(uri, fileName)
            Toast.makeText(context, "Đang nạp mô hình từ bộ nhớ...", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("storage_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Cài Đặt Hệ Thống & Bộ Nhớ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tốc độ đọc TTS • Debug Server 8888 • Lưu trữ (<500MB)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==========================================
            // 1. TTS PLAYBACK SPEED CONFIGURATION
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tốc độ đọc phát âm (TTS)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${ttsSpeed}x",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Điều chỉnh tốc độ giọng đọc tiếng Hàn & tiếng Việt để phù hợp trình độ nghe:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val speeds = listOf(
                        0.75f to "0.75x (Chậm - Luyện nghe)",
                        0.9f to "0.9x (Tự nhiên)",
                        1.0f to "1.0x (Chuẩn)",
                        1.25f to "1.25x (Nhanh)"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speeds.forEach { (speed, label) ->
                            FilterChip(
                                selected = (ttsSpeed - speed).let { Math.abs(it) < 0.05f },
                                onClick = {
                                    viewModel.setTtsSpeed(speed)
                                    viewModel.speakKorean("안녕하세요")
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. IN-APP HTTP DEBUG SERVER (PORT 8888)
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDebugRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = if (isDebugRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HTTP Debug Console (Cổng 8888)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = isDebugRunning,
                            onCheckedChange = { viewModel.toggleDebugServer() }
                        )
                    }

                    Text(
                        text = "Mở máy chủ HTTP nội bộ trên cổng 8888 để xem log trực tiếp, theo dõi độ trễ AI, dung lượng DB và các sự kiện hệ thống qua trình duyệt web trên máy tính hoặc điện thoại cùng mạng Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isDebugRunning && debugUrl.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "● Máy chủ đang chạy tại:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = debugUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("DebugServerUrl", debugUrl)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Đã sao chép link debug!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sao chép link", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(debugUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Không thể mở trình duyệt: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mở Console", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. ON-DEVICE AI MODELS (TIER 1 EMBEDDED)
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mô Hình AI Nhúng Tầng 1 (On-Device)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Chọn hoặc tải mô hình GGUF từ Hugging Face Hub, hoặc nhập file từ bộ nhớ máy để chạy AI cục bộ tối ưu hóa câu lệnh & Local RAG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Active model status banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "● Tầng 1 (Local Router):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeLocalModel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = activeLocalModel?.name ?: "Heuristic Siêu Nhẹ (0MB, tiết kiệm RAM)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (activeLocalModel != null) {
                                    TextButton(onClick = { viewModel.setActiveLocalModel(null) }) {
                                        Text("Tắt", fontSize = 11.sp)
                                    }
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "● Tầng 2 (Heavy Model):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeTier2Model != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = activeTier2Model?.name ?: "Trực Tuyến (Cloud Multi-Provider)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (activeTier2Model != null) {
                                    TextButton(onClick = { viewModel.setActiveTier2Model(null) }) {
                                        Text("Dùng Cloud", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Download progress indicator if currently downloading
                    when (val state = downloadState) {
                        is DownloadState.Downloading -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Đang tải mô hình: ${state.downloadedMb} MB / ${state.totalMb} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${(state.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp)
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.cancelModelDownload() },
                                        modifier = Modifier.align(Alignment.End),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Hủy tải", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        is DownloadState.Failed -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tải thất bại: ${state.error}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.resetModelDownloadState() }) {
                                        Text("Đóng", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Text(
                        text = "Danh sách Model Presets (Hugging Face Hub):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Presets
                    AiModelManager.PRESET_MODELS.forEach { preset ->
                        val downloaded = downloadedModels.firstOrNull { it.fileName == preset.fileName }
                        val isDownloading = (downloadState as? DownloadState.Downloading)?.modelId == preset.id
                        val isActive = if (preset.tier == 1) downloaded?.isActiveTier1 == true else downloaded?.isActiveTier2 == true

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (preset.isRecommended) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = "Khuyên dùng",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = preset.description,
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
                                    Text(
                                        text = if (downloaded != null) "Đã lưu (${downloaded.sizeMb} MB)"
                                               else "~${preset.approximateSizeMb.toInt()} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (downloaded != null) {
                                            if (!isActive) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (preset.tier == 1) {
                                                            viewModel.setActiveLocalModel(downloaded.fileName)
                                                        } else {
                                                            viewModel.setActiveTier2Model(downloaded.fileName)
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text(if (preset.tier == 1) "Kích hoạt T1" else "Kích hoạt T2", fontSize = 11.sp)
                                                }
                                            } else {
                                                Button(
                                                    onClick = {},
                                                    enabled = false,
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Đang dùng", fontSize = 11.sp)
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteLocalModel(downloaded.fileName) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Xóa",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.downloadModelPreset(preset) },
                                                enabled = !isDownloading,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tải về", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Custom model actions (Import from phone storage or enter direct HuggingFace URL)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chọn từ máy (.gguf)", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { showCustomUrlDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nhập URL HuggingFace", fontSize = 11.sp)
                        }
                    }
                }
            }

            // ==========================================
            // 4. STORAGE BUDGET BREAKDOWN (<500MB)
            // ==========================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tổng dung lượng sử dụng:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${report.totalUsedMb} MB / 500 MB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (report.totalUsedMb / report.maxBudgetMb).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    StorageRowItem(
                        label = "Base App (APK & Code Logic)",
                        size = "${report.baseAppMb} MB",
                        note = "Mã nguồn Kotlin & Jetpack Compose siêu nhẹ"
                    )

                    StorageRowItem(
                        label = "Từ điển ngoại tuyến (78.000 từ)",
                        size = "${report.databaseMb} MB",
                        note = "SQLite nén tối ưu, chạy 100% không cần mạng"
                    )

                    StorageRowItem(
                        label = "Mô hình AI nhúng (Tầng 1)",
                        size = "${report.modelsMb} MB",
                        note = if (report.modelsMb > 0) "Mô hình on-device lưu trong files/models/"
                               else "Chưa tải (0 MB - Đang dùng Heuristic)"
                    )

                    StorageRowItem(
                        label = "Bộ nhớ đệm & Lịch sử tra cứu",
                        size = "${report.cacheMb} MB",
                        note = "Tự động dọn dẹp theo chính sách an toàn"
                    )
                }
            }

            // Clean Cache Button
            Button(
                onClick = {
                    viewModel.cleanStorageCache()
                    Toast.makeText(context, "Đã dọn dẹp bộ nhớ đệm thành công!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dọn dẹp bộ nhớ đệm (Clean Cache)")
            }

            // Privacy Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Quyền riêng tư: Dữ liệu và API Key được lưu an toàn cục bộ, không gửi dữ liệu người dùng tới bất kỳ máy chủ bên thứ ba nào.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text("Nhập URL Mô Hình Tùy Chỉnh") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Dán link tải trực tiếp file .gguf hoặc .bin (ví dụ từ Hugging Face resolve/main):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        label = { Text("URL tải về") },
                        placeholder = { Text("https://huggingface.co/.../model.gguf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customNameText,
                        onValueChange = { customNameText = it },
                        label = { Text("Tên tệp lưu trữ") },
                        placeholder = { Text("custom_model.gguf") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customUrlText.isNotBlank()) {
                            val fileName = customNameText.ifBlank { "custom_model_${System.currentTimeMillis()}.gguf" }
                            viewModel.downloadCustomModel(customUrlText.trim(), fileName.trim())
                            showCustomUrlDialog = false
                            customUrlText = ""
                            customNameText = ""
                            Toast.makeText(context, "Bắt đầu tải mô hình tùy chỉnh...", Toast.LENGTH_SHORT).show()
                        }
                    }
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

@Composable
fun StorageRowItem(
    label: String,
    size: String,
    note: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(text = note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text(text = size, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
