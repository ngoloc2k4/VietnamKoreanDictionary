package com.example.ai.tier1

import com.example.ai.gateway.UnifiedAiMessage
import com.example.ai.gateway.UnifiedAiRequest
import com.example.data.model.WordEntity
import com.example.util.KoreanVietnameseSearchMatcher

enum class UserIntent {
    OFFLINE_DICTIONARY_LOOKUP,
    HANJA_ETYMOLOGY,
    GRAMMAR_EXPLANATION,
    NUANCE_COMPARISON,
    TRANSLATION,
    GENERAL_KOREAN_TUTOR
}

data class Tier1Decision(
    val intent: UserIntent,
    val canHandleOffline: Boolean,
    val offlineAnswer: String? = null,
    val optimizedRequest: UnifiedAiRequest? = null,
    val localRagContext: String = ""
)

object LocalAiMiddleman {

    /**
     * Intent Classification: checks if question is a direct dictionary lookup or requires Cloud AI
     */
    fun classifyIntent(query: String, localCandidates: List<WordEntity>): UserIntent {
        val q = query.trim().lowercase()

        // 1. Hanja specific queries
        if (q.contains("hán tự") || q.contains("chữ hán") || q.contains("hán việt") || q.contains("hanja") || q.contains("gốc hán")) {
            return UserIntent.HANJA_ETYMOLOGY
        }

        // 2. Grammar comparisons or explanations
        if (q.contains("ngữ pháp") || q.contains("phân biệt") || q.contains("khác nhau") || q.contains("cấu trúc") ||
            q.contains("kính ngữ") || q.contains("chia đuôi") || q.contains("chia thì")) {
            return UserIntent.GRAMMAR_EXPLANATION
        }

        // 3. Translation of long text
        if (q.contains("dịch") || query.length > 50 || query.split(" ").size > 8) {
            return UserIntent.TRANSLATION
        }

        // 4. Exact word lookup that exists in local DB
        if (localCandidates.isNotEmpty() && query.length <= 25 && query.split(" ").size <= 3) {
            return UserIntent.OFFLINE_DICTIONARY_LOOKUP
        }

        return UserIntent.GENERAL_KOREAN_TUTOR
    }

    /**
     * Local RAG: Retrieves relevant entries from local DB and formats a compact summary (saves cloud tokens)
     */
    fun buildLocalRagContext(query: String, localWords: List<WordEntity>): String {
        if (localWords.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("[DỮ LIỆU TỪ ĐIỂN NỘI BỘ (LOCAL RAG - 78.000 TỪ)]\n")

        val top3 = localWords.take(3)
        for ((idx, word) in top3.withIndex()) {
            sb.append("${idx + 1}. Từ: ${word.korean}")
            if (word.hanja.isNotBlank()) sb.append(" [Hán: ${word.hanja}]")
            if (word.hanViet.isNotBlank()) sb.append(" (Hán-Việt: ${word.hanViet})")
            sb.append(" | Loại: ${word.partOfSpeech} | Nghĩa: ${word.vietnamese}\n")

            if (word.presentTense.isNotBlank()) {
                sb.append("   - Chia thì: Hiện tại [${word.presentTense}], Quá khứ [${word.pastTense}]\n")
            }
            if (word.grammarNotes.isNotBlank()) {
                sb.append("   - Ghi chú: ${word.grammarNotes.take(100)}\n")
            }
        }
        return sb.toString().trim()
    }

    /**
     * Prompt Optimizer: Formulates a structured, token-efficient prompt for Tier 2 Cloud AI
     */
    fun processQuery(
        userQuery: String,
        localWords: List<WordEntity>,
        chatHistory: List<UnifiedAiMessage> = emptyList()
    ): Tier1Decision {
        val intent = classifyIntent(userQuery, localWords)

        // If it's a simple 1-2 word lookup and we have exact local data, provide instant offline answer
        if (intent == UserIntent.OFFLINE_DICTIONARY_LOOKUP && localWords.isNotEmpty()) {
            val qTrim = userQuery.trim()
            val match = localWords.firstOrNull {
                it.korean.equals(qTrim, ignoreCase = true) ||
                it.vietnamese.split(",")[0].trim().equals(qTrim, ignoreCase = true)
            } ?: localWords.firstOrNull()

            val isDirectMatch = match != null && (
                match.korean.equals(qTrim, ignoreCase = true) ||
                match.vietnamese.contains(qTrim, ignoreCase = true)
            )

            if (isDirectMatch && match != null && qTrim.split(" ").size <= 2) {
                val best = match
                val answer = buildString {
                    append("📖 **Tra cứu ngoại tuyến tức thì:**\n\n")
                    append("• **Tiếng Hàn:** ${best.korean} ${best.pronunciation}\n")
                    append("• **Nghĩa:** ${best.vietnamese}\n")
                    append("• **Từ loại:** ${best.partOfSpeech}\n")
                    if (best.hanja.isNotBlank() || best.hanViet.isNotBlank()) {
                        append("• **Chữ Hán & Hán-Việt:** ${best.hanja} (${best.hanViet})\n")
                    }
                    if (best.presentTense.isNotBlank()) {
                        append("• **Dạng chia thì:** ${best.presentTense} | ${best.pastTense}\n")
                    }
                    if (best.definition.isNotBlank()) {
                        append("• **Định nghĩa:** ${best.definition}\n")
                    }
                    append("\n*(Dữ liệu từ kho từ điển ngoại tuyến 78.000 từ - Không tốn token AI)*")
                }
                return Tier1Decision(
                    intent = intent,
                    canHandleOffline = true,
                    offlineAnswer = answer
                )
            }
        }

        // Build Local RAG context to supply to Cloud AI
        val ragContext = buildLocalRagContext(userQuery, localWords)

        val systemPrompt = buildString {
            append("Bạn là 'VietKor AI Tutor' - chuyên gia giảng dạy tiếng Hàn và tiếng Việt cho người học.\n")
            append("Triết lý giảng dạy: Ngắn gọn, chuẩn xác ngữ pháp, nhấn mạnh mối liên hệ Hán-Việt và Hán-Hàn (Hanja) để giúp người học ghi nhớ sâu bản chất từ vựng.\n")
            append("Quy tắc định dạng:\n")
            append("1. Trả lời bằng tiếng Việt thân thiện, bố cục rõ ràng với gạch đầu dòng Markdown.\n")
            append("2. Luôn phiên âm kèm Hanja và Hán-Việt khi nhắc đến các từ gốc Hán.\n")
            append("3. Cung cấp ví dụ thực tế song ngữ Hàn - Việt.\n")
            if (ragContext.isNotBlank()) {
                append("\nThông tin kiểm chứng từ Cơ sở dữ liệu Từ điển nội bộ (hãy ưu tiên sử dụng để tránh ảo giác):\n")
                append(ragContext)
                append("\n")
            }
        }

        val messages = mutableListOf<UnifiedAiMessage>()
        messages.add(UnifiedAiMessage("system", systemPrompt))

        // Append recent chat history (limit to last 4 messages to keep token usage lean)
        val recentHistory = chatHistory.takeLast(4)
        messages.addAll(recentHistory)

        // Optimized user message
        messages.add(UnifiedAiMessage("user", userQuery.trim()))

        val request = UnifiedAiRequest(
            messages = messages,
            temperature = 0.6,
            maxTokens = 850
        )

        return Tier1Decision(
            intent = intent,
            canHandleOffline = false,
            optimizedRequest = request,
            localRagContext = ragContext
        )
    }
}
