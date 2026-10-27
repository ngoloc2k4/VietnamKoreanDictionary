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

data class ModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val approximateSizeMb: Double,
    val isRecommended: Boolean = false,
    val tag: String = "LLM" // "LLM" or "EMBEDDING"
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
    val isActive: Boolean,
    val tag: String
)

object AiModelManager {

    private const val PREFS_NAME = "ai_models_prefs"
    private const val KEY_ACTIVE_MODEL = "active_model_file"
    private const val KEY_CUSTOM_NAMES = "custom_model_names"

    val PRESET_MODELS = listOf(
        ModelPreset(
            id = "smollm2_135m_q4",
            name = "SmolLM2-135M-Instruct (Quantized Q4_K_M)",
            description = "Khuyên dùng - Siêu nhẹ (~100MB), tối ưu hóa prompt & intent on-device cho CPU di động",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
            fileName = "smollm2-135m-instruct-q4_k_m.gguf",
            approximateSizeMb = 100.0,
            isRecommended = true,
            tag = "LLM"
        ),
        ModelPreset(
            id = "smollm2_135m_q8",
            name = "SmolLM2-135M-Instruct (Quantized Q8_0)",
            description = "Chất lượng 8-bit cao hơn (~140MB), thỏa mãn ngân sách <150MB của Tầng 1",
            downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf",
            fileName = "smollm2-135m-instruct-q8_0.gguf",
            approximateSizeMb = 140.0,
            isRecommended = false,
            tag = "LLM"
        ),
        ModelPreset(
            id = "minilm_l6_v2_embedding",
            name = "All-MiniLM-L6-v2 Embedding (Q8_0)",
            description = "Mô hình Vector Embedding 384 chiều siêu nhẹ (~30MB) cho tìm kiếm tương đồng ngữ nghĩa",
            downloadUrl = "https://huggingface.co/second-state/All-MiniLM-L6-v2-Embedding-GGUF/resolve/main/all-MiniLM-L6-v2-Q8_0.gguf",
            fileName = "all-minilm-l6-v2-q8_0.gguf",
            approximateSizeMb = 30.0,
            isRecommended = false,
            tag = "EMBEDDING"
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
        val activeFile = getPrefs(context).getString(KEY_ACTIVE_MODEL, null)
        val files = modelsDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") } ?: emptyArray()

        return files.map { file ->
            val preset = PRESET_MODELS.firstOrNull { it.fileName == file.name }
            val isCustom = preset == null
            val displayName = preset?.name ?: file.name
            val sizeMb = (file.length().toDouble() / (1024 * 1024) * 10).toInt() / 10.0
            val tag = preset?.tag ?: if (file.name.contains("embed", ignoreCase = true)) "EMBEDDING" else "CUSTOM"

            LocalModelInfo(
                id = preset?.id ?: file.name,
                name = displayName,
                fileName = file.name,
                file = file,
                sizeMb = sizeMb,
                isCustom = isCustom,
                isActive = file.name == activeFile,
                tag = tag
            )
        }.sortedByDescending { it.isActive }
    }

    fun getActiveModel(context: Context): LocalModelInfo? {
        val activeFile = getPrefs(context).getString(KEY_ACTIVE_MODEL, null) ?: return null
        val models = getDownloadedModels(context)
        return models.firstOrNull { it.fileName == activeFile }
    }

    fun setActiveModel(context: Context, fileName: String?) {
        getPrefs(context).edit().apply {
            if (fileName == null) {
                remove(KEY_ACTIVE_MODEL)
            } else {
                putString(KEY_ACTIVE_MODEL, fileName)
            }
            apply()
        }
        AppLogManager.log("AiModelManager", "Chuyển mô hình hoạt động: ${fileName ?: "Mặc định (Heuristic Mode)"}")
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
            approximateSizeMb = preset.approximateSizeMb
        )
    }

    suspend fun downloadCustomUrl(
        context: Context,
        url: String,
        targetFileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanFileName = if (targetFileName.endsWith(".gguf") || targetFileName.endsWith(".bin")) {
            targetFileName
        } else {
            "$targetFileName.gguf"
        }
        downloadFile(
            context = context,
            modelId = cleanFileName,
            url = url,
            targetFileName = cleanFileName,
            approximateSizeMb = 100.0
        )
    }

    private fun downloadFile(
        context: Context,
        modelId: String,
        url: String,
        targetFileName: String,
        approximateSizeMb: Double
    ): Result<File> {
        val modelsDir = getModelsDir(context)
        val targetFile = File(modelsDir, targetFileName)
        val tempFile = File(modelsDir, "$targetFileName.tmp")

        try {
            AppLogManager.log("AiModelManager", "Bắt đầu tải mô hình từ: $url -> $targetFileName")
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
                    _downloadState.value = DownloadState.Completed(modelId, targetFileName)
                    // If no active model is currently selected, set this newly downloaded model as active
                    if (getActiveModel(context) == null) {
                        setActiveModel(context, targetFileName)
                    }
                    AppLogManager.log("AiModelManager", "Tải hoàn tất và sẵn sàng: $targetFileName")
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
                AppLogManager.log("AiModelManager", "Đã hủy tải mô hình: $targetFileName")
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
        originalName: String
    ): Result<LocalModelInfo> = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDir(context)
        val targetFileName = originalName.ifBlank { "custom_model_${System.currentTimeMillis()}.gguf" }
        val targetFile = File(modelsDir, targetFileName)

        try {
            AppLogManager.log("AiModelManager", "Nhập mô hình từ bộ nhớ máy: $targetFileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IOException("Không thể đọc tệp từ URI đã chọn"))

            // Auto-activate imported model
            setActiveModel(context, targetFileName)

            val sizeMb = (targetFile.length().toDouble() / (1024 * 1024) * 10).toInt() / 10.0
            val info = LocalModelInfo(
                id = targetFileName,
                name = targetFileName,
                fileName = targetFileName,
                file = targetFile,
                sizeMb = sizeMb,
                isCustom = true,
                isActive = true,
                tag = if (targetFileName.contains("embed", ignoreCase = true)) "EMBEDDING" else "LOCAL_CUSTOM"
            )
            AppLogManager.log("AiModelManager", "Nhập thành công: $targetFileName ($sizeMb MB)")
            Result.success(info)
        } catch (e: Exception) {
            AppLogManager.log("AiModelManager", "Nhập thất bại: ${e.message}", "ERROR")
            Result.failure(e)
        }
    }

    fun deleteModel(context: Context, fileName: String): Boolean {
        val file = File(getModelsDir(context), fileName)
        val active = getPrefs(context).getString(KEY_ACTIVE_MODEL, null)
        if (active == fileName) {
            setActiveModel(context, null)
        }
        val deleted = file.delete()
        AppLogManager.log("AiModelManager", "Xóa mô hình $fileName: $deleted")
        return deleted
    }
}
