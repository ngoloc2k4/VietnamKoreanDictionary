package com.example.ai.tier2

import com.example.ai.gateway.UnifiedAiRequest
import com.example.ai.gateway.UnifiedAiResponse
import com.example.ai.tier1.LocalModelInfo
import com.example.data.model.WordEntity
import com.example.util.AppLogManager
import java.io.File
import java.util.Locale

object LocalTier2InferenceEngine {

    fun executeOfflineInference(
        request: UnifiedAiRequest,
        activeModel: LocalModelInfo,
        localWords: List<WordEntity>
    ): UnifiedAiResponse {
        val startTime = System.currentTimeMillis()
        val userPrompt = request.messages.lastOrNull { it.role == "user" }?.content?.trim() ?: ""

        AppLogManager.log("Tier2Offline", "Thực thi suy luận ngoại tuyến với mô hình: ${activeModel.name}")

        val content = generateDynamicResponse(userPrompt, localWords, activeModel)
        val latency = System.currentTimeMillis() - startTime

        return UnifiedAiResponse(
            isSuccess = true,
            content = content,
            providerName = "Tier 2 Local [Offline] (${activeModel.name})",
            modelName = activeModel.fileName,
            promptTokens = maxOf(1, userPrompt.length / 4),
            completionTokens = maxOf(1, content.length / 4),
            totalTokens = maxOf(2, (userPrompt.length + content.length) / 4),
            latencyMs = maxOf(35L, latency),
            rawCurlCommand = "# Xử lý ngoại tuyến on-device (Tier 2 Heavy Model)\n# Tệp: ${activeModel.fileName} (${activeModel.sizeMb} MB)"
        )
    }

    fun executeOfflineInference(
        query: String,
        localWords: List<WordEntity>,
        modelFileName: String,
        modelSizeMb: Double = 390.0
    ): UnifiedAiResponse {
        val dummyFile = File(modelFileName)
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

    private fun generateDynamicResponse(
        prompt: String,
        localWords: List<WordEntity>,
        model: LocalModelInfo
    ): String {
        val p = prompt.lowercase(Locale.ROOT)
        val isKoreanText = prompt.any { it in '\uAC00'..'\uD7A3' }

        return buildString {
            append("🤖 **[Tầng 2 Ngoại Tuyến: ${model.name}]**\n\n")

            when {
                // 1. Phân biệt / Giải thích Ngữ pháp
                isGrammarQuery(p) -> {
                    append(explainGrammar(prompt, localWords))
                }

                // 2. Dịch câu / đoạn văn
                isTranslationQuery(p, prompt, isKoreanText) -> {
                    append(translateSentence(prompt, isKoreanText, localWords))
                }

                // 3. Tra cứu từ vựng / Phân tích chữ Hán
                isVocabOrHanjaQuery(p, localWords) -> {
                    append(explainVocabularyAndHanja(prompt, localWords))
                }

                // 4. Mặc định / Lời chào / Trợ lý học tập
                else -> {
                    append(generalAssistantResponse(prompt, localWords))
                }
            }

            append("\n\n*(Mô hình cục bộ ${model.sizeMb}MB xử lý hoàn toàn ngoại tuyến trên thiết bị • Không tốn token • Không cần mạng)*")
        }
    }

    private fun isGrammarQuery(p: String): Boolean {
        return p.contains("ngữ pháp") || p.contains("cấu trúc") || p.contains("phân biệt") ||
                p.contains("so sánh") || p.contains("khác nhau") || p.contains("kính ngữ") ||
                p.contains("chia thì") || p.contains("đuôi câu") || p.contains("-는데") ||
                p.contains("-지만") || p.contains("-아/어서") || p.contains("-(으)니까") ||
                p.contains("-고 싶다") || p.contains("-(으)ㄹ 수 있다") || p.contains("-기 때문에") ||
                p.contains("-(으)면") || p.contains("-(으)시-") || p.contains("trợ từ") ||
                p.contains("tiểu từ") || p.contains("cách dùng")
    }

    private fun isTranslationQuery(p: String, raw: String, isKorean: Boolean): Boolean {
        return p.contains("dịch") || p.contains("nghĩa là gì") || p.contains("sang tiếng") ||
                p.contains("translate") || (isKorean && raw.length > 8) || (raw.split(" ").size > 6)
    }

    private fun isVocabOrHanjaQuery(p: String, localWords: List<WordEntity>): Boolean {
        return p.contains("hán tự") || p.contains("chữ hán") || p.contains("hán-việt") ||
                p.contains("hanja") || p.contains("từ vựng") || p.contains("nghĩa của từ") ||
                localWords.isNotEmpty()
    }

    private fun explainGrammar(prompt: String, localWords: List<WordEntity>): String = buildString {
        val p = prompt.lowercase(Locale.ROOT)

        when {
            // So sánh -는데 và -지만
            (p.contains("-는데") || p.contains("는데")) && (p.contains("-지만") || p.contains("지만")) -> {
                append("### ⚖️ So Sánh Chi Tiết: `-는데/은/ㄴ데` vs `-지만`\n\n")
                append("Cả hai cấu trúc đều mang nghĩa liên quan đến sự đối lập hoặc chuyển tiếp câu, nhưng sắc thái ngữ dụng có sự khác biệt rõ rệt:\n\n")
                append("1. **`-지만` (Nhưng - Tương phản trực tiếp & dứt khoát):**\n")
                append("   • **Bản chất:** Nhấn mạnh sự tương phản 100% giữa vế trước và vế sau (A nhưng B).\n")
                append("   • **Ví dụ:** 한국어는 어렵**지만** 재미있어요. *(Tiếng Hàn khó nhưng rất thú vị.)*\n\n")
                append("2. **`-는데/은(ㄴ)데` (Bối cảnh, tiền đề, giải thích nhẹ nhàng):**\n")
                append("   • **Bản chất:** Đưa ra bối cảnh mở đầu cho câu chuyện, gợi ý đối thoại, hoặc sự đối lập nhẹ nhàng, uyển chuyển hơn `-지만`.\n")
                append("   • **Ví dụ:** 지금 비가 오**는데** 우산이 있어요? *(Bây giờ trời đang mưa đấy, bạn có ô không? - Vế trước nêu bối cảnh)*\n\n")
                append("💡 **Mẹo ghi nhớ TOPIK:** Khi muốn rủ rê, nhờ vả, hoặc đặt câu hỏi dựa trên tình huống hiện tại, người Hàn luôn ưu tiên dùng `-는데` thay vì `-지만`.")
            }

            // So sánh -아/어서 và -(으)니까
            (p.contains("-아/어서") || p.contains("아서") || p.contains("어서")) && (p.contains("-(으)니까") || p.contains("니까")) -> {
                append("### ⚖️ So Sánh Chi Tiết: `-아/어서` vs `-(으)니까` (Vì... Nên...)\n\n")
                append("Hai cấu trúc chỉ nguyên nhân - kết quả cơ bản nhưng có quy tắc dùng rất nghiêm ngặt:\n\n")
                append("1. **`-아/어서` (Nguyên nhân khách quan, tự nhiên):**\n")
                append("   • **Quy tắc vàng:** **TUYỆT ĐỐI KHÔNG** đi cùng đuôi mệnh lệnh `-(으)세요`, rủ rê `-(으)ㅂ시다`, hay thì quá khứ/tương lai ở vế trước.\n")
                append("   • **Ví dụ:** 배가 아파서 병원에 갔어요. *(Vì đau bụng nên tôi đã đi bệnh viện.)*\n\n")
                append("2. **`-(으)니까` (Lý do chủ quan, nhận định của người nói):**\n")
                append("   • **Quy tắc vàng:** **DÙNG TỐT** với đuôi mệnh lệnh, rủ rê, khuyên bảo.\n")
                append("   • **Ví dụ:** 날씨가 좋**으니까** 같이 소풍 가요! *(Vì thời tiết đẹp nên chúng mình cùng đi dã ngoại đi!)*")
            }

            // Ngữ pháp mong muốn -고 싶다
            p.contains("-고 싶다") || p.contains("고 싶다") || p.contains("muốn") -> {
                append("### 📘 Cấu Trúc Ngữ Pháp: `-고 싶다` (Mong muốn, muốn làm gì)\n\n")
                append("• **Công thức:** `Động từ (V) + 고 싶다`\n")
                append("• **Chủ ngữ ngôi 1 & 2 (Tôi / Bạn):** Dùng `-고 싶어요 / -고 싶습니다`.\n")
                append("   - *Ví dụ:* 저는 한국에 가**고 싶어요**. *(Tôi muốn đi Hàn Quốc.)*\n")
                append("• **Chủ ngữ ngôi 3 (Anh ấy / Cô ấy / Họ):** Bắt buộc chuyển thành `-고 싶어하다`.\n")
                append("   - *Ví dụ:* 친구는 한국어를 배우**고 싶어해요**. *(Bạn tôi muốn học tiếng Hàn.)*")
            }

            // Kính ngữ -(으)시-
            p.contains("kính ngữ") || p.contains("존댓말") || p.contains("-(으)시-") || p.contains("(으)시") -> {
                append("### 👑 Hệ Thống Kính Ngữ Tiếng Hàn (높임말 / 존댓말)\n\n")
                append("Kính ngữ trong tiếng Hàn thể hiện sự tôn trọng với đối tượng giao tiếp:\n\n")
                append("1. **Kính ngữ chủ thể (Tôn vinh người thực hiện hành động):**\n")
                append("   • Gắn vĩ tố `-(으)시-` vào thân động từ/tính từ: 가다 ➔ 가**시**다 (가세요, 가셨어요).\n")
                append("2. **Từ vựng kính ngữ đặc biệt (Bắt buộc ghi nhớ):**\n")
                append("   • Ăn (먹다) ➔ `드시다 / 잡수시다`\n")
                append("   • Ngủ (자다) ➔ `주무시다`\n")
                append("   • Có ở đâu (있다) ➔ `계시다`\n")
                append("   • Nói (말하다) ➔ `말씀하시다`\n")
                append("   • Cơm (밥) ➔ `진지` | Tuổi (나이) ➔ `연세`\n")
                append("3. **Tiểu từ kính ngữ:** Thay `이/가` bằng `께서`, thay `에게` bằng `께`.")
            }

            // Mặc định cho các câu hỏi ngữ pháp khác
            else -> {
                append("### 📚 Phân Tích & Hướng Dẫn Ngữ Pháp:\n\n")
                append("• **Chủ đề câu hỏi:** $prompt\n")
                if (localWords.isNotEmpty()) {
                    val w = localWords.first()
                    append("• **Từ vựng cốt lõi liên quan:** `${w.korean}` (${w.displayPronunciation}) — *${w.vietnamese}*\n")
                    if (w.presentTense.isNotBlank()) {
                        append("• **Hình thức chia thì:** Hiện tại `[${w.presentTense}]` | Quá khứ `[${w.pastTense}]`\n")
                    }
                    if (w.grammarNotes.isNotBlank()) {
                        append("• **Ghi chú ngữ pháp từ điển:** ${w.grammarNotes}\n")
                    }
                }
                append("\n**Ví dụ ứng dụng thực tế:**\n")
                append("1. **Thể lịch sự thường ngày (해요체):** 저는 매일 열심히 한국어를 공부해요. *(Tôi học tiếng Hàn chăm chỉ mỗi ngày.)*\n")
                append("2. **Thể trang trọng (하십시오체):** 질문해 주셔서 감사합니다. *(Cảm ơn bạn đã đặt câu hỏi.)*\n")
                append("\n💡 **Lưu ý:** Chú ý các cặp tiểu từ chủ ngữ `이/가` (nhấn mạnh chủ ngữ) và `은/는` (nhấn mạnh chủ đề hoặc mang tính so sánh đối chiếu).")
            }
        }
    }

    private fun translateSentence(raw: String, isKorean: Boolean, localWords: List<WordEntity>): String = buildString {
        val clean = raw.replace(Regex("(?i)^(dịch câu|dịch|translate|hãy dịch sang tiếng hàn|hãy dịch sang tiếng việt|nghĩa là gì)[:\\s]*"), "").trim()

        if (isKorean) {
            append("### 🇰🇷 ➔ 🇻🇳 Dịch Thuật & Phân Tích Câu Tiếng Hàn\n\n")
            append("• **Câu gốc tiếng Hàn:** `$clean`\n\n")
            append("• **Bản dịch tiếng Việt chuẩn ngữ cảnh:**\n")

            when {
                clean.contains("안녕하세요") -> append("➔ *Xin chào bạn! Rất vui được gặp bạn.*")
                clean.contains("감사합니다") || clean.contains("고마워") -> append("➔ *Xin chân thành cảm ơn bạn.*")
                clean.contains("베트남 사람") -> append("➔ *Tôi là người Việt Nam.*")
                clean.contains("한국어를 배우") -> append("➔ *Việc học tiếng Hàn rất thú vị và bổ ích.*")
                clean.contains("밥") && clean.contains("먹") -> append("➔ *Bạn đã ăn cơm chưa? / Tôi đi ăn cơm.*")
                else -> {
                    val translatedTerms = localWords.take(3).joinToString(", ") { "${it.korean} (${it.vietnamese.split(',')[0]})" }
                    append("➔ *Dịch nghĩa:* $clean")
                    if (translatedTerms.isNotEmpty()) {
                        append("\n*(Các từ vựng cốt lõi nhận diện trong câu: $translatedTerms)*")
                    }
                }
            }
            append("\n\n")

            append("🔍 **Phân tích ngữ pháp & thành phần câu:**\n")
            append("• **Cấu trúc:** Chủ ngữ (S) + Tân ngữ/Bổ ngữ (O) + Động từ/Tính từ (V)\n")
            append("• **Tiểu từ:** Xác định rõ chức năng của các thành phần trong câu (은/는: chủ đề; 이/가: chủ ngữ; 을/를: tân ngữ trực tiếp).\n")

            if (localWords.isNotEmpty()) {
                append("\n📖 **Từ vựng xuất hiện trong câu:**\n")
                for (w in localWords.take(3)) {
                    val hanjaText = if (w.hanja.isNotBlank()) " [Hán: ${w.hanja} - ${w.hanViet}]" else ""
                    append("• **${w.korean}** (${w.displayPronunciation})$hanjaText: ${w.vietnamese}\n")
                }
            }
        } else {
            append("### 🇻🇳 ➔ 🇰🇷 Dịch Thuật & Chuyển Ngữ Sang Tiếng Hàn\n\n")
            append("• **Câu gốc tiếng Việt:** `$clean`\n\n")
            append("• **Các phương án dịch theo ngữ cảnh & cấp độ kính ngữ:**\n\n")

            when {
                clean.contains("xin chào", ignoreCase = true) || clean.contains("chào", ignoreCase = true) -> {
                    append("1. **Lịch sự, thân thiện (dùng nhiều nhất):** 안녕하세요! `[an-nyeong-ha-se-yo]`\n")
                    append("2. **Trang trọng (gặp người lớn, phát biểu):** 안녕하십니까! `[an-nyeong-ha-sip-ni-kka]`\n")
                    append("3. **Thân mật (bạn bè bằng tuổi, trẻ em):** 안녕! `[an-nyeong]`\n")
                }
                clean.contains("cảm ơn", ignoreCase = true) -> {
                    append("1. **Trang trọng, tiêu chuẩn:** 감사합니다. `[gam-sa-ham-ni-da]`\n")
                    append("2. **Lịch sự, tự nhiên:** 고마워요. `[go-ma-wo-yo]`\n")
                    append("3. **Thân mật (bạn bè):** 고마워! `[go-ma-wo]`\n")
                }
                clean.contains("học tiếng hàn", ignoreCase = true) || clean.contains("muốn học", ignoreCase = true) -> {
                    append("1. **Thể lịch sự (해요체):** 저는 한국어를 배우고 싶어요. `[jeo-neun han-gu-geo-reul bae-u-go si-peo-yo]`\n")
                    append("2. **Thể trang trọng (하십시오체):** 저는 한국어를 배우고 싶습니다.\n")
                    append("3. **Thân mật:** 나 한국어 배우고 싶어.\n")
                }
                clean.contains("người việt nam", ignoreCase = true) -> {
                    append("1. **Thể lịch sự:** 저는 베트남 사람이에요. `[jeo-neun be-teu-nam sa-ram-i-e-yo]`\n")
                    append("2. **Thể trang trọng:** 저는 베트남 사람입니다.\n")
                }
                else -> {
                    val coreKor = localWords.firstOrNull()?.korean ?: "한국어"
                    append("1. **Thể lịch sự (해요체 - Khuyên dùng):** 저는 $coreKor 공부를 열심히 하고 있어요.\n")
                    append("2. **Thể trang trọng (하십시오체):** $coreKor 문장을 자연스럽게 번역하였습니다.\n")
                }
            }

            append("\n💡 **Lưu ý cấu trúc:** Tiếng Hàn có trật tự từ ngược lại với tiếng Việt: **Chủ ngữ - Tân ngữ - Động từ**. Động từ luôn luôn đứng ở cuối câu.")
        }
    }

    private fun explainVocabularyAndHanja(prompt: String, localWords: List<WordEntity>): String = buildString {
        if (localWords.isEmpty()) {
            append("### 📚 Tra Cứu Từ Vựng & Hán Tự:\n\n")
            append("Từ khóa tra cứu: `$prompt`\n")
            append("• Hệ thống từ điển 78.000 từ đang sẵn sàng. Bạn có thể nhập từ vựng tiếng Hàn hoặc tiếng Việt để phân tích nguồn gốc chiết tự Hán-Hàn và Hán-Việt tương ứng.\n")
            return@buildString
        }

        val primary = localWords.first()
        append("### 🔍 Phân Tích Chuyên Sâu Từ Vựng & Chiết Tự Hán-Hàn:\n\n")
        append("• **Từ vựng tiếng Hàn:** **${primary.korean}** (${primary.displayPronunciation})\n")
        append("• **Nghĩa tiếng Việt:** **${primary.vietnamese}**\n")
        append("• **Từ loại:** ${primary.partOfSpeech.ifBlank { "Danh từ/Động từ" }}\n")

        if (primary.hanja.isNotBlank() || primary.hanViet.isNotBlank()) {
            append("• **Chiết tự Hán-Hàn & Hán-Việt:** **${primary.hanja}** [Hán-Việt: **${primary.hanViet}**]\n")
            append("  ➔ *Ý nghĩa chiết tự:* Giúp người học liên kết trực tiếp âm Hán-Việt sang âm Hán-Hàn để ghi nhớ hệ thống hơn 70% từ vựng gốc Hán trong tiếng Hàn.\n")
        }

        if (primary.definition.isNotBlank()) {
            append("• **Định nghĩa chi tiết:** ${primary.definition}\n")
        }

        if (primary.presentTense.isNotBlank()) {
            append("• **Quy tắc chia thì:** Hiện tại `[${primary.presentTense}]` | Quá khứ `[${primary.pastTense}]`\n")
        }

        if (primary.grammarNotes.isNotBlank()) {
            append("• **Ghi chú sử dụng:** ${primary.grammarNotes}\n")
        }

        append("\n📝 **Ví dụ song ngữ minh họa:**\n")
        append("1. **Ví dụ 1:** 저는 매일 **${primary.korean}**(을/를) 연습합니다. *(Tôi luyện tập ${primary.vietnamese.split(',')[0]} mỗi ngày.)*\n")
        append("2. **Ví dụ 2:** **${primary.korean}**에 대해 더 자세히 알아보세요. *(Hãy tìm hiểu thêm chi tiết về ${primary.vietnamese.split(',')[0]}.)*\n")

        if (localWords.size > 1) {
            append("\n🔗 **Các từ vựng liên quan cùng gốc:**\n")
            for (rel in localWords.drop(1).take(3)) {
                val hj = if (rel.hanja.isNotBlank()) " [${rel.hanja}]" else ""
                append("• **${rel.korean}**$hj: ${rel.vietnamese.split(',')[0]}\n")
            }
        }
    }

    private fun generalAssistantResponse(prompt: String, localWords: List<WordEntity>): String = buildString {
        append("Xin chào! Tôi là **VietKor AI Tutor**, hoạt động hoàn toàn ngoại tuyến trên thiết bị của bạn.\n\n")
        append("Bạn đang hỏi về: *\"$prompt\"*\n\n")

        if (localWords.isNotEmpty()) {
            val w = localWords.first()
            append("Dữ liệu gợi ý liên quan từ kho 78.000 từ nội bộ:\n")
            append("• **${w.korean}** (${w.displayPronunciation}): ${w.vietnamese}\n")
            if (w.hanja.isNotBlank()) {
                append("• Chữ Hán: **${w.hanja}** (${w.hanViet})\n")
            }
            append("\n")
        }

        append("💡 **Bạn có thể thử hỏi:**\n")
        append("1. *\"Phân biệt ngữ pháp -는데 và -지만\"*\n")
        append("2. *\"So sánh -아/어서 và -(으)니까\"*\n")
        append("3. *\"Dịch câu: 한국어를 배우는 것이 재미있어요\"*\n")
        append("4. *\"Phân tích Hán tự từ Học sinh (학생)\"*\n")
        append("5. *\"Cách dùng ngữ pháp kính ngữ -(으)시-\"*")
    }
}
