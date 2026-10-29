package com.example.ai.tier2

import com.example.util.AppLogManager
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufModelMetadata(
    val isValidGguf: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val metadataKvCount: Long = 0L,
    val architecture: String = "unknown",
    val modelName: String = "",
    val contextLength: Int = 0,
    val embeddingLength: Int = 0,
    val blockCount: Int = 0,
    val quantization: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeMb: Double = 0.0,
    val estimatedRamNeededMb: Double = 0.0,
    val deviceAvailRamMb: Long = 0L,
    val canFitInDeviceRam: Boolean = true,
    val errorMessage: String? = null
)

object GgufModelInspector {

    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little-endian

    /**
     * Inspect a local model file and extract authentic GGUF binary metadata
     */
    fun inspect(modelFile: File?): GgufModelMetadata {
        if (modelFile == null || !modelFile.exists() || !modelFile.isFile) {
            return GgufModelMetadata(
                isValidGguf = false,
                errorMessage = "Tệp mô hình không tồn tại trên bộ nhớ thiết bị"
            )
        }

        val fileLength = modelFile.length()
        val fileSizeMb = fileLength.toDouble() / (1024.0 * 1024.0)
        val availRam = getAvailableDeviceRamMb()

        return try {
            FileInputStream(modelFile).use { fis ->
                val headerBuf = ByteArray(24)
                val readBytes = fis.read(headerBuf)
                if (readBytes < 24) {
                    return GgufModelMetadata(
                        isValidGguf = false,
                        fileSizeBytes = fileLength,
                        fileSizeMb = fileSizeMb,
                        errorMessage = "Tệp quá nhỏ để là định dạng GGUF hợp lệ"
                    )
                }

                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                val magic = bb.int
                if (magic != GGUF_MAGIC) {
                    AppLogManager.log("GgufInspector", "Magic byte không khớp: 0x${Integer.toHexString(magic)}, kỳ vọng GGUF", "WARN")
                    return GgufModelMetadata(
                        isValidGguf = false,
                        fileSizeBytes = fileLength,
                        fileSizeMb = fileSizeMb,
                        errorMessage = "Không phải tệp GGUF hợp lệ (magic header không khớp)"
                    )
                }

                val version = bb.int
                val tensorCount = bb.long
                val kvCount = bb.long

                var architecture = "unknown"
                var modelName = modelFile.nameWithoutExtension
                var contextLength = 0
                var embeddingLength = 0
                var blockCount = 0
                var quantization = extractQuantizationFromFileName(modelFile.name)

                // Read up to first 64 metadata key-value pairs safely
                val maxPairsToRead = minOf(kvCount, 64L)
                var pairsRead = 0
                val strLenBuf = ByteArray(8)

                while (pairsRead < maxPairsToRead) {
                    val keyLenRead = fis.read(strLenBuf)
                    if (keyLenRead < 8) break
                    val keyLen = ByteBuffer.wrap(strLenBuf).order(ByteOrder.LITTLE_ENDIAN).long
                    if (keyLen <= 0 || keyLen > 256) break

                    val keyBytes = ByteArray(keyLen.toInt())
                    val kRead = fis.read(keyBytes)
                    if (kRead < keyLen.toInt()) break
                    val key = String(keyBytes, Charsets.UTF_8)

                    // Read value type (uint32)
                    val typeBuf = ByteArray(4)
                    if (fis.read(typeBuf) < 4) break
                    val valType = ByteBuffer.wrap(typeBuf).order(ByteOrder.LITTLE_ENDIAN).int

                    // Type 8: String (uint64 len + bytes)
                    if (valType == 8) {
                        if (fis.read(strLenBuf) < 8) break
                        val sLen = ByteBuffer.wrap(strLenBuf).order(ByteOrder.LITTLE_ENDIAN).long
                        if (sLen > 0 && sLen < 1024) {
                            val valBytes = ByteArray(sLen.toInt())
                            if (fis.read(valBytes) == sLen.toInt()) {
                                val sVal = String(valBytes, Charsets.UTF_8)
                                if (key == "general.architecture") architecture = sVal
                                if (key == "general.name") modelName = sVal
                            }
                        }
                    } else if (valType == 4 || valType == 5) {
                        // UINT32 / INT32 (4 bytes)
                        val numBuf = ByteArray(4)
                        if (fis.read(numBuf) == 4) {
                            val num = ByteBuffer.wrap(numBuf).order(ByteOrder.LITTLE_ENDIAN).int
                            if (key.endsWith(".context_length")) contextLength = num
                            if (key.endsWith(".embedding_length")) embeddingLength = num
                            if (key.endsWith(".block_count")) blockCount = num
                        }
                    } else if (valType == 10 || valType == 11) {
                        // UINT64 / INT64 (8 bytes)
                        val numBuf = ByteArray(8)
                        if (fis.read(numBuf) == 8) {
                            val num = ByteBuffer.wrap(numBuf).order(ByteOrder.LITTLE_ENDIAN).long.toInt()
                            if (key.endsWith(".context_length")) contextLength = num
                            if (key.endsWith(".embedding_length")) embeddingLength = num
                            if (key.endsWith(".block_count")) blockCount = num
                        }
                    } else {
                        // Skip unhandled metadata values safely
                        break
                    }
                    pairsRead++
                }

                // Fallbacks if architecture wasn't explicitly extracted
                if (architecture == "unknown") {
                    val fn = modelFile.name.lowercase()
                    architecture = when {
                        fn.contains("qwen") -> "qwen2"
                        fn.contains("smollm") -> "smollm"
                        fn.contains("llama") -> "llama"
                        fn.contains("gemma") -> "gemma"
                        fn.contains("phi") -> "phi3"
                        fn.contains("mistral") -> "mistral"
                        else -> "transformer"
                    }
                }

                val estimatedRamMb = fileSizeMb * 1.25
                val canFit = availRam >= estimatedRamMb.toLong()

                AppLogManager.log("GgufInspector", "Đọc thành công GGUF v$version [$architecture], Tensors: $tensorCount, Size: ${fileSizeMb.toInt()}MB, RAM khả dụng: ${availRam}MB", "INFO")

                GgufModelMetadata(
                    isValidGguf = true,
                    version = version,
                    tensorCount = tensorCount,
                    metadataKvCount = kvCount,
                    architecture = architecture,
                    modelName = modelName,
                    contextLength = if (contextLength > 0) contextLength else 4096,
                    embeddingLength = embeddingLength,
                    blockCount = if (blockCount > 0) blockCount else 24,
                    quantization = quantization,
                    fileSizeBytes = fileLength,
                    fileSizeMb = fileSizeMb,
                    estimatedRamNeededMb = estimatedRamMb,
                    deviceAvailRamMb = availRam,
                    canFitInDeviceRam = canFit
                )
            }
        } catch (e: Exception) {
            AppLogManager.log("GgufInspector", "Lỗi đọc tệp GGUF: ${e.message}", "WARN")
            GgufModelMetadata(
                isValidGguf = false,
                fileSizeBytes = fileLength,
                fileSizeMb = fileSizeMb,
                errorMessage = "Lỗi đọc GGUF: ${e.message}"
            )
        }
    }

    private fun extractQuantizationFromFileName(fileName: String): String {
        val upper = fileName.uppercase()
        val regex = Regex("Q[0-9]_[KMA0-9_]+|BF16|F16|F32|Q4_0|Q4_1|Q5_0|Q5_1|Q8_0")
        return regex.find(upper)?.value ?: "Q4_K_M"
    }

    private fun getAvailableDeviceRamMb(): Long {
        return try {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists()) {
                val lines = memInfoFile.readLines()
                val memAvailLine = lines.firstOrNull { it.startsWith("MemAvailable:") }
                if (memAvailLine != null) {
                    val kb = memAvailLine.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                    return kb / 1024L
                }
            }
            val maxJvm = Runtime.getRuntime().maxMemory() / (1024L * 1024L)
            maxJvm * 4L
        } catch (e: Exception) {
            2048L
        }
    }
}
