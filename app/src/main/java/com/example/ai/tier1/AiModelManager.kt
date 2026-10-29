package com.example.ai.tier1

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.util.AppLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Tier2ExecutionMode(val label: String) {
    ONLINE_CLOUD("Trực tuyến (Cloud Gateway)"),
    OFFLINE_LOCAL("Ngoại tuyến (Local Model 0.5B - 0.8B)")
}

data class ModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val approximateSizeMb: Double,
    val isRecommended: Boolean = false,
    val tag: String = "LLM_TIER1",
    val tier: Int = 1
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val modelId: String,
        val progress: Float,
        val downloadedMb: Double,
        val totalMb: Double
    ) : DownloadState()
    data class Completed(val modelId: String, val fileName: String) : DownloadState()
    data class Failed(val modelId: String, val error: String) : DownloadState()
}

data class LocalModelInfo(
    val id: String,
    val name: String,
    val fileName: String,
    val file: File,
    val sizeMb: Double,
    val isCustom: Boolean,
    val isActiveTier1: Boolean,
    val isActiveTier2: Boolean,
    val tag: String,
    val tier: Int
) {
    val isActive: Boolean get() = isActiveTier1 || isActiveTier2
}

object AiModelManager {

    private const val PREFS_NAME = "ai_models_prefs"
    private const val KEY_ACTIVE_TIER1 = "active_model_file"
    private const val KEY_ACTIVE_TIER2 = "active_tier2_model_file"
    private const val KEY_TIER2_MODE = "tier2_execution_mode"

    val PRESET_MODELS = listOf(
        // ==========================================
        // TIER 1: LIGHTWEIGHT PROMPT & INTENT (<150MB)
        // ==========================================
        ModelPreset(
            id = "smollm2_135m_q4",
            name = "SmolLM2-135M-Instruct (Quantized Q4_K_M)",
            description = "Tầng 1 [Khuyên dùng]: Siêu nhẹ (~100MB), tối ưu prompt & intent trên CPU di động",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
            fileName = "smollm2-135m-instruct-q4_k_m.gguf",
            approximateSizeMb = 100.0,
            isRecommended = true,
            tag = "LLM_TIER1",
            tier = 1
        ),
        ModelPreset(
            id = "smollm2_135m_q8",
            name = "SmolLM2-135M-Instruct (Quantized Q8_0)",
            description = "Tầng 1: Chất lượng 8-bit cao hơn (~140MB), thỏa mãn ngân sách <150MB",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf",
            fileName = "smollm2-135m-instruct-q8_0.gguf",
            approximateSizeMb = 140.0,
            isRecommended = false,
            tag = "LLM_TIER1",
            tier = 1
        ),
        ModelPreset(
            id = "minilm_l6_v2_embedding",
            name = "All-MiniLM-L6-v2 Embedding (Q8_0)",
            description = "Tầng 1: Vector Embedding 384 chiều (~30MB) cho tìm kiếm ngữ nghĩa tương đồng",
            downloadUrl = "https://huggingface.co/second-state/All-MiniLM-L6-v2-Embedding-GGUF/resolve/main/all-MiniLM-L6-v2-Q8_0.gguf",
            fileName = "all-minilm-l6-v2-q8_0.gguf",
            approximateSizeMb = 30.0,
            isRecommended = false,
            tag = "EMBEDDING",
            tier = 1
        ),

        // ==========================================
        // TIER 2: HEAVY REASONING & TRANSLATION (0.5B - 0.8B)
        // ==========================================
        ModelPreset(
            id = "qwen2_5_05b_q4",
            name = "Qwen2.5-0.5B-Instruct (Quantized Q4_K_M)",
            description = "Tầng 2 [Khuyên dùng]: Mô hình 0.5B đa ngôn ngữ Hàn - Việt xuất sắc, chạy offline suy luận sâu (~390MB)",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            approximateSizeMb = 390.0,
            isRecommended = true,
            tag = "LLM_TIER2",
            tier = 2
        ),
        ModelPreset(
            id = "smollm2_360m_q4",
            name = "SmolLM2-360M-Instruct (Quantized Q4_K_M)",
            description = "Tầng 2: Mô hình 360M tham số cân bằng giữa suy luận offline và dung lượng (~260MB)",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
            fileName = "smollm2-360m-instruct-q4_k_m.gguf",
            approximateSizeMb = 260.0,
            isRecommended = false,
            tag = "LLM_TIER2",
            tier = 2
        ),
        ModelPreset(
            id = "danube3_500m_q4",
            name = "H2O-Danube3-500M-Chat (Quantized Q4_K_M)",
            description = "Tầng 2: Mô hình 500M tham số tối ưu hóa cho tác vụ đàm thoại trên di động (~340MB)",
            downloadUrl = "https://huggingface.co/bartowski/h2o-danube3-500m-chat-GGUF/resolve/main/h2o-danube3-500m-chat-Q4_K_M.gguf",
            fileName = "h2o-danube3-500m-chat-q4_k_m.gguf",
            approximateSizeMb = 340.0,
            isRecommended = false,
            tag = "LLM_TIER2",
            tier = 2
        )
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private var activeDownloadCall: Call? = null

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getModelsDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDownloadedModels(context: Context): List<LocalModelInfo> {
        val modelsDir = getModelsDir(context)
        val activeTier1 = getPrefs(context).getString(KEY_ACTIVE_TIER1, null)
        val activeTier2 = getPrefs(context).getString(KEY_ACTIVE_TIER2, null)
        val files = modelsDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: emptyArray()

        return files.map { file ->
            val preset = PRESET_MODELS.firstOrNull { it.fileName == file.name }
            val isCustom = preset == null
            val displayName = preset?.name ?: file.name
            val sizeMb = (file.length().toDouble() / (1024 * 1024) * 10).toInt() / 10.0
            val storedTier = getPrefs(context).getInt("custom_tier_${file.name}", -1)
            val tier = preset?.tier ?: if (storedTier != -1) storedTier else (if (sizeMb > 200.0) 2 else 1)
            val tag = preset?.tag ?: if (file.name.contains("embed", ignoreCase = true)) "EMBEDDING" else "CUSTOM"

            LocalModelInfo(
                id = preset?.id ?: file.name,
                name = displayName,
                fileName = file.name,
                file = file,
                sizeMb = sizeMb,
                isCustom = isCustom,
                isActiveTier1 = file.name == activeTier1,
                isActiveTier2 = file.name == activeTier2,
                tag = tag,
                tier = tier
            )
        }.sortedWith(compareByDescending<LocalModelInfo> { it.isActiveTier1 || it.isActiveTier2 }.thenBy { it.tier })
    }

    fun getCustomModelTier(context: Context, fileName: String, defaultTier: Int = 1): Int {
        return getPrefs(context).getInt("custom_tier_$fileName", defaultTier)
    }

    fun setCustomModelTier(context: Context, fileName: String, tier: Int) {
        getPrefs(context).edit().putInt("custom_tier_$fileName", tier).apply()
        AppLogManager.log("AiModelManager", "Đã cập nhật tầng cho mô hình $fileName -> Tầng $tier")
    }

    // Tier 1 Active Model
    fun getActiveModel(context: Context): LocalModelInfo? = getActiveTier1Model(context)

    fun getActiveTier1Model(context: Context): LocalModelInfo? {
        val activeFile = getPrefs(context).getString(KEY_ACTIVE_TIER1, null) ?: return null
        val models = getDownloadedModels(context)
        return models.firstOrNull { it.fileName == activeFile }
    }

    fun setActiveModel(context: Context, fileName: String?) = setActiveTier1Model(context, fileName)

    fun setActiveTier1Model(context: Context, fileName: String?) {
        getPrefs(context).edit().apply {
            if (fileName == null) {
                remove(KEY_ACTIVE_TIER1)
            } else {
                putString(KEY_ACTIVE_TIER1, fileName)
            }
            apply()
        }
        AppLogManager.log("AiModelManager", "Chuyển mô hình Tầng 1: ${fileName ?: "Mặc định (Heuristic Mode)"}")
    }

    // Tier 2 Active Model & Execution Mode
    fun getActiveTier2Model(context: Context): LocalModelInfo? {
        val activeFile = getPrefs(context).getString(KEY_ACTIVE_TIER2, null) ?: return null
        val models = getDownloadedModels(context)
        return models.firstOrNull { it.fileName == activeFile }
    }

    fun setActiveTier2Model(context: Context, fileName: String?) {
        getPrefs(context).edit().apply {
            if (fileName == null) {
                remove(KEY_ACTIVE_TIER2)
            } else {
                putString(KEY_ACTIVE_TIER2, fileName)
            }
            apply()
        }
        AppLogManager.log("AiModelManager", "Chuyển mô hình Tầng 2: ${fileName ?: "Chưa chọn (Dùng Cloud)"}")
    }

    fun getTier2ExecutionMode(context: Context): Tier2ExecutionMode {
        val raw = getPrefs(context).getString(KEY_TIER2_MODE, Tier2ExecutionMode.ONLINE_CLOUD.name)
        return try {
            Tier2ExecutionMode.valueOf(raw ?: Tier2ExecutionMode.ONLINE_CLOUD.name)
        } catch (e: Exception) {
            Tier2ExecutionMode.ONLINE_CLOUD
        }
    }

    fun setTier2ExecutionMode(context: Context, mode: Tier2ExecutionMode) {
        getPrefs(context).edit().putString(KEY_TIER2_MODE, mode.name).apply()
        AppLogManager.log("AiModelManager", "Chuyển chế độ Tầng 2: ${mode.label}")
    }

    fun getTotalModelsSizeMb(context: Context): Double {
        val modelsDir = getModelsDir(context)
        val files = modelsDir.listFiles { f -> f.isFile } ?: emptyArray()
        val totalBytes = files.sumOf { it.length() }
        return (totalBytes.toDouble() / (1024 * 1024) * 10).toInt() / 10.0
    }

    fun isModelDownloaded(context: Context, fileName: String): Boolean {
        val file = File(getModelsDir(context), fileName)
        return file.exists() && file.length() > 1024 * 1024 // Greater than 1MB
    }

    suspend fun downloadPreset(
        context: Context,
        preset: ModelPreset
    ): Result<File> = withContext(Dispatchers.IO) {
        downloadFile(
            context = context,
            modelId = preset.id,
            url = preset.downloadUrl,
            targetFileName = preset.fileName,
            approximateSizeMb = preset.approximateSizeMb,
            tier = preset.tier
        )
    }

    suspend fun downloadCustomUrl(
        context: Context,
        url: String,
        targetFileName: String,
        tier: Int = 1
    ): Result<File> = withContext(Dispatchers.IO) {
        val sanitizedBaseName = File(targetFileName.trim()).name
        val cleanFileName = if (sanitizedBaseName.endsWith(".gguf") || sanitizedBaseName.endsWith(".bin")) {
            sanitizedBaseName
        } else {
            "$sanitizedBaseName.gguf"
        }
        downloadFile(
            context = context,
            modelId = cleanFileName,
            url = url,
            targetFileName = cleanFileName,
            approximateSizeMb = 150.0,
            tier = tier
        )
    }

    private fun downloadFile(
        context: Context,
        modelId: String,
        url: String,
        targetFileName: String,
        approximateSizeMb: Double,
        tier: Int
    ): Result<File> {
        val modelsDir = getModelsDir(context)
        val safeFileName = File(targetFileName).name
        val targetFile = File(modelsDir, safeFileName)
        val tempFile = File(modelsDir, "$safeFileName.tmp")

        try {
            AppLogManager.log("AiModelManager", "Bắt đầu tải mô hình Tầng $tier: $url -> $safeFileName")
            _downloadState.value = DownloadState.Downloading(modelId, 0f, 0.0, approximateSizeMb)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VietnamKoreanDictionary-Android/1.0")
                .build()

            val call = httpClient.newCall(request)
            activeDownloadCall = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "HTTP ${response.code}: ${response.message}"
                    _downloadState.value = DownloadState.Failed(modelId, errMsg)
                    AppLogManager.log("AiModelManager", "Tải thất bại: $errMsg", "ERROR")
                    return Result.failure(IOException(errMsg))
                }

                val body = response.body ?: throw IOException("Phản hồi rỗng từ máy chủ")
                val totalBytes = body.contentLength().takeIf { it > 0 } ?: (approximateSizeMb * 1024 * 1024).toLong()
                val totalMb = (totalBytes.toDouble() / (1024 * 1024) * 10).toInt() / 10.0

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReportTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastReportTime > 250) {
                                lastReportTime = now
                                val progress = (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                val downloadedMb = (totalRead.toDouble() / (1024 * 1024) * 10).toInt() / 10.0
                                _downloadState.value = DownloadState.Downloading(modelId, progress, downloadedMb, totalMb)
                            }
                        }
                        output.flush()
                    }
                }

                if (tempFile.renameTo(targetFile)) {
                    _downloadState.value = DownloadState.Completed(modelId, safeFileName)
                    // Auto-activate in appropriate tier if none active
                    if (tier == 1 && getActiveTier1Model(context) == null) {
                        setActiveTier1Model(context, safeFileName)
                    } else if (tier == 2 && getActiveTier2Model(context) == null) {
                        setActiveTier2Model(context, safeFileName)
                    }
                    AppLogManager.log("AiModelManager", "Tải hoàn tất và sẵn sàng: $safeFileName (Tầng $tier)")
                    return Result.success(targetFile)
                } else {
                    val errMsg = "Không thể ghi tệp mô hình vào bộ nhớ"
                    _downloadState.value = DownloadState.Failed(modelId, errMsg)
                    return Result.failure(IOException(errMsg))
                }
            }
        } catch (e: Exception) {
            tempFile.delete()
            if (activeDownloadCall?.isCanceled() == true) {
                _downloadState.value = DownloadState.Idle
                AppLogManager.log("AiModelManager", "Đã hủy tải mô hình: $safeFileName")
                return Result.failure(IOException("Đã hủy tải"))
            }
            val msg = e.message ?: "Lỗi không xác định khi tải"
            _downloadState.value = DownloadState.Failed(modelId, msg)
            AppLogManager.log("AiModelManager", "Lỗi tải mô hình: $msg", "ERROR")
            return Result.failure(e)
        } finally {
            activeDownloadCall = null
        }
    }

    fun cancelDownload() {
        activeDownloadCall?.cancel()
        _downloadState.value = DownloadState.Idle
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        originalName: String,
        tier: Int = 1
    ): Result<LocalModelInfo> = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDir(context)
        val targetFileName = originalName.ifBlank { "custom_model_${System.currentTimeMillis()}.gguf" }
        val targetFile = File(modelsDir, targetFileName)

        try {
            AppLogManager.log("AiModelManager", "Nhập mô hình từ bộ nhớ máy: $targetFileName (Tầng $tier)")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Không thể đọc tệp từ URI đã chọn"))

            val sizeMb = (targetFile.length().toDouble() / (1024 * 1024) * 10).toInt() / 10.0
            val determinedTier = if (tier == 2) 2 else if (tier == 1) 1 else (if (sizeMb > 200.0) 2 else 1)
            setCustomModelTier(context, targetFileName, determinedTier)

            if (determinedTier == 1) {
                setActiveTier1Model(context, targetFileName)
            } else {
                setActiveTier2Model(context, targetFileName)
            }

            val info = LocalModelInfo(
                id = targetFileName,
                name = targetFileName,
                fileName = targetFileName,
                file = targetFile,
                sizeMb = sizeMb,
                isCustom = true,
                isActiveTier1 = determinedTier == 1,
                isActiveTier2 = determinedTier == 2,
                tag = if (determinedTier == 2) "LLM_TIER2" else "LOCAL_CUSTOM",
                tier = determinedTier
            )
            AppLogManager.log("AiModelManager", "Nhập thành công: $targetFileName ($sizeMb MB, Tầng $determinedTier)")
            Result.success(info)
        } catch (e: Exception) {
            AppLogManager.log("AiModelManager", "Nhập thất bại: ${e.message}", "ERROR")
            Result.failure(e)
        }
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        val file = File(getModelsDir(context), fileName)
        val activeTier1 = getPrefs(context).getString(KEY_ACTIVE_TIER1, null)
        val activeTier2 = getPrefs(context).getString(KEY_ACTIVE_TIER2, null)
        if (activeTier1 == fileName) {
            setActiveTier1Model(context, null)
        }
        if (activeTier2 == fileName) {
            setActiveTier2Model(context, null)
        }
        val deleted = file.delete()
        getPrefs(context).edit().remove("custom_tier_$fileName").apply()
        AppLogManager.log("AiModelManager", "Xóa mô hình $fileName: $deleted")
        return deleted
    }
}
