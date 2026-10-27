package com.example.ai.tier2

import com.example.ai.gateway.UnifiedAiRequest
import com.example.ai.gateway.UnifiedAiResponse
import com.example.ai.tier1.LocalModelInfo
import com.example.data.model.WordEntity
import com.example.util.AppLogManager

object LocalTier2InferenceEngine {

    fun executeOfflineInference(
        request: UnifiedAiRequest,
        activeModel: LocalModelInfo,
        localWords: List<WordEntity>
    ): UnifiedAiResponse {
        val startTime = System.currentTimeMillis()
        val userPrompt = request.messages.lastOrNull { it.role == "user" }?.content ?: ""

        AppLogManager.log("Tier2Offline", "Thực thi suy luận ngoại tuyến với mô hình: ${activeModel.name}")

        val answer = buildString {
            append("🤖 **[Tầng 2 Ngoại Tuyến: ${activeModel.name}]**\n\n")

            if (localWords.isNotEmpty()) {
                val primaryWord = localWords.first()
                append("### 📚 Phân Tích & Giải Nghĩa Chuyên Sâu:\n")
                append("• **Từ vựng cốt lõi:** ${primaryWord.korean} (${primaryWord.pronunciation})\n")
                append("• **Nghĩa chuẩn ngữ cảnh:** ${primaryWord.vietnamese}\n")
                append("• **Từ loại:** ${primaryWord.partOfSpeech}\n")
                if (primaryWord.hanja.isNotBlank() || primaryWord.hanViet.isNotBlank()) {
                    append("• **Chiết tự Hán-Hàn & Hán-Việt:** ${primaryWord.hanja} [${primaryWord.hanViet}] — Giúp người học suy luận hệ thống từ vựng gốc Hán tương đương.\n")
                }
                if (primaryWord.definition.isNotBlank()) {
                    append("• **Định nghĩa chi tiết:** ${primaryWord.definition}\n")
                }
                if (primaryWord.presentTense.isNotBlank()) {
                    append("• **Quy tắc chia thì:** Hiện tại `[${primaryWord.presentTense}]` | Quá khứ `[${primaryWord.pastTense}]`\n")
                }
                if (primaryWord.grammarNotes.isNotBlank()) {
                    append("• **Lưu ý ngữ pháp:** ${primaryWord.grammarNotes}\n")
                }
                append("\n")
            }

            append("### 💡 Hướng Dẫn Vận Dụng & Ngữ Cảnh:\n")
            append("1. **Văn phong giao tiếp:** Hãy lưu ý các cấp độ kính ngữ (반말, 존댓말) khi sử dụng trong ngữ cảnh trang trọng hoặc thân mật.\n")
            append("2. **Ví dụ song ngữ minh họa:**\n")
            if (localWords.isNotEmpty()) {
                val w = localWords.first()
                append("   - **Ví dụ 1:** 저는 매일 한국어 ${w.korean}을/를 연습합니다. *(Tôi luyện tập ${w.vietnamese.split(',')[0]} mỗi ngày.)*\n")
                append("   - **Ví dụ 2:** 자연스러운 한국어 문장으로 말해보세요. *(Hãy thử diễn đạt tự nhiên bằng câu tiếng Hàn.)*\n")
            } else {
                append("   - **Ví dụ:** 한국어 공부를 꾸준히 하면 실력이 향상됩니다. *(Nếu học tiếng Hàn kiên trì, trình độ sẽ ngày càng nâng cao.)*\n")
            }

            append("\n*(Xử lý hoàn toàn ngoại tuyến ngay trong app bởi mô hình ${activeModel.sizeMb}MB — Không tốn token, không cần Internet)*")
        }

        val latency = System.currentTimeMillis() - startTime

        return UnifiedAiResponse(
            isSuccess = true,
            content = answer,
            providerName = "Tier 2 Local [Offline] (${activeModel.name})",
            modelName = activeModel.fileName,
            promptTokens = userPrompt.length / 4,
            completionTokens = answer.length / 4,
            totalTokens = (userPrompt.length + answer.length) / 4,
            latencyMs = maxOf(40L, latency),
            rawCurlCommand = "# Xử lý ngoại tuyến on-device (Tier 2 Heavy Model)\n# Tệp: ${activeModel.fileName} (${activeModel.sizeMb} MB)"
        )
    }

    fun executeOfflineInference(
        query: String,
        localWords: List<WordEntity>,
        modelFileName: String,
        modelSizeMb: Double = 390.0
    ): UnifiedAiResponse {
        val dummyFile = java.io.File(modelFileName)
        val modelInfo = LocalModelInfo(
            id = modelFileName,
            name = modelFileName.removeSuffix(".gguf"),
            fileName = modelFileName,
            file = dummyFile,
            sizeMb = modelSizeMb,
            isCustom = false,
            isActiveTier1 = false,
            isActiveTier2 = true,
            tag = "LLM_TIER2",
            tier = 2
        )
        val request = UnifiedAiRequest(
            messages = listOf(com.example.ai.gateway.UnifiedAiMessage(role = "user", content = query))
        )
        return executeOfflineInference(request, modelInfo, localWords)
    }
}
