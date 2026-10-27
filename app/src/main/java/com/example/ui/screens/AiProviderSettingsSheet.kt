package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.gateway.AiProviderConfig
import com.example.ai.gateway.ProviderProtocol
import com.example.ui.viewmodel.DictionaryViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsSheet(
    viewModel: DictionaryViewModel,
    onDismiss: () -> Unit
) {
    val providers by viewModel.aiProviders.collectAsState()
    var editingProvider by remember { mutableStateOf<AiProviderConfig?>(null) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.testTag("ai_provider_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cấu Hình AI Gateway (Tầng 2)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Multi-Provider: Groq, DeepSeek, Gemini, Claude, Ollama...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { viewModel.resetProvidersToDefaults() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Đặt lại mặc định",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tier 2 Online Cloud architecture note
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tầng 2 hoạt động 100% Trực Tuyến (Online Cloud) với các mô hình cấp cao (Llama-3.3-70B, Claude, GPT, Gemini). Các mô hình nhúng cục bộ được quản lý độc lập tại Tầng 1 (Cài đặt bộ nhớ).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Test status if any
            testResultText?.let { res ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (res.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = res,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (res.startsWith("✅")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderItemCard(
                        provider = provider,
                        onSetDefault = { viewModel.setDefaultProvider(provider.id) },
                        onEdit = { editingProvider = provider },
                        onTest = {
                            isTesting = true
                            testResultText = "Đang kiểm tra kết nối tới ${provider.name}..."
                            viewModel.testProviderConnection(provider) { response ->
                                isTesting = false
                                testResultText = if (response.isSuccess) {
                                    "✅ Kết nối thành công tới ${provider.name}! Độ trễ: ${response.latencyMs}ms"
                                } else {
                                    "❌ Lỗi: ${response.errorMessage}"
                                }
                            }
                        },
                        onToggle = {
                            viewModel.saveProvider(provider.copy(isEnabled = !provider.isEnabled))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Provider Button
            Button(
                onClick = {
                    editingProvider = AiProviderConfig(
                        id = UUID.randomUUID().toString(),
                        name = "Custom Provider",
                        protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                        baseUrl = "https://api.openai.com/v1",
                        apiKey = "",
                        modelName = "gpt-4o-mini",
                        priority = providers.size + 1
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thêm AI Provider mới")
            }
        }
    }

    // Edit Provider Dialog
    editingProvider?.let { config ->
        EditProviderDialog(
            initialConfig = config,
            onDismiss = { editingProvider = null },
            onSave = { updated ->
                viewModel.saveProvider(updated)
                editingProvider = null
            },
            onDelete = {
                viewModel.deleteProvider(config.id)
                editingProvider = null
            }
        )
    }
}

@Composable
fun ProviderItemCard(
    provider: AiProviderConfig,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.isDefault) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (provider.isDefault) 2.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (provider.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Mặc định",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Details
            Text(
                text = "Model: ${provider.modelName} • Giao thức: ${provider.protocol.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Text(
                text = "Key: ${provider.maskedApiKey}",
                style = MaterialTheme.typography.labelSmall,
                color = if (provider.apiKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!provider.isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("Đặt mặc định", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProviderDialog(
    initialConfig: AiProviderConfig,
    onDismiss: () -> Unit,
    onSave: (AiProviderConfig) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initialConfig.name) }
    var protocol by remember { mutableStateOf(initialConfig.protocol) }
    var baseUrl by remember { mutableStateOf(initialConfig.baseUrl) }
    var apiKey by remember { mutableStateOf(initialConfig.apiKey) }
    var modelName by remember { mutableStateOf(initialConfig.modelName) }
    var showApiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa Provider") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Protocol selector
                Text("Giao thức API:", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderProtocol.values().forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = {
                                protocol = p
                                baseUrl = p.defaultBaseUrl
                                modelName = p.defaultModel
                            },
                            label = { Text(if (p == ProviderProtocol.OPENAI_COMPATIBLE) "OpenAI/Groq" else if (p == ProviderProtocol.ANTHROPIC_COMPATIBLE) "Claude" else "Gemini", fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL Endpoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    initialConfig.copy(
                        name = name.trim(),
                        protocol = protocol,
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        modelName = modelName.trim()
                    )
                )
            }) {
                Text("Lưu")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Xóa")
                }
                TextButton(onClick = onDismiss) {
                    Text("Hủy")
                }
            }
        }
    )
}
