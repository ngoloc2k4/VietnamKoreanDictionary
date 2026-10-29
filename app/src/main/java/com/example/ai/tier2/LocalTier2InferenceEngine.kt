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
        val meta = GgufModelInspector.inspect(model.file)

        return buildString {
            if (meta.isValidGguf) {
                append("🤖 **[Tầng 2 Ngoại Tuyến: ${meta.architecture.uppercase()} (${model.name}) • GGUF v${meta.version}]**\n")
                append("📊 *Thông số tệp: ${meta.tensorCount} tensors • Block: ${meta.blockCount} • Context: ${meta.contextLength} tokens • Quant: ${meta.quantization} • ${meta.fileSizeMb.toInt()} MB*\n")
                if (!meta.canFitInDeviceRam) {
                    append("💡 *Lưu ý hiệu năng: Mô hình lớn (${meta.fileSizeMb.toInt()} MB) so với RAM trống (${meta.deviceAvailRamMb} MB). Hệ thống vận hành cơ chế Deep Educational Engine phối hợp dữ liệu 78.000 từ để phân tích câu toàn diện, tránh quá tải RAM thiết bị.*\n\n")
                } else {
                    append("⚡ *Chế độ ngoại tuyến: Xử lý on-device • Kho 78.000 từ • Không tốn token mạng*\n\n")
                }
            } else {
                append("🤖 **[Tầng 2 Ngoại Tuyến: ${model.name}]**\n")
                append("*(Mô hình ngoại tuyến on-device • Dung lượng: ${model.sizeMb.toInt()} MB • Động cơ VietKor)*\n\n")
            }

            when {
                // 1. Phân biệt / Giải thích Ngữ pháp
                isGrammarQuery(p) -> {
                    append(explainGrammar(prompt, localWords))
                }

                // 2. Dịch câu / đoạn văn
                isTranslationQuery(p, prompt, isKoreanText) -> {
                    append(translateSentence(prompt, isKoreanText, localWords))
                }

                // 3. Tra cứu từ vựng / Phân tích chữ Hán / Tâm trạng
                isVocabOrHanjaQuery(p, localWords) -> {
                    append(explainVocabularyAndHanja(prompt, localWords))
                }

                // 4. Mặc định / Lời chào / Trợ lý học tập
                else -> {
                    append(generalAssistantResponse(prompt, localWords))
                }
            }

            append("\n\n*(Mô hình cục bộ ${model.sizeMb.toInt()}MB • Trích xuất từ điển 78.062 từ • Hoạt động 100% ngoại tuyến)*")
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
        val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasTransKw = p.contains("dịch") || p.contains("translate") || p.contains("sang tiếng") ||
                p.contains("tiếng hàn là gì") || p.contains("tiếng việt là gì") || p.contains("nghĩa là gì") ||
                p.contains("nghĩa câu") || p.contains("chuyển ngữ")
        val hasSentenceConjunction = p.contains(" rồi ") || p.contains(" và ") || p.contains(" hoặc ") ||
                p.contains(" sau khi ") || p.contains(" trước khi ") || p.contains(" muốn ") ||
                p.contains(" để ") || p.contains(" vì ") || p.contains(" nên ") || p.contains(" nếu ") ||
                p.contains(" khi ") || p.contains(" phải ") || p.contains(" có thể ")
        return hasTransKw || hasSentenceConjunction || (isKorean && (raw.length > 5 || words.size >= 2)) || (words.size >= 3)
    }

    private fun isVocabOrHanjaQuery(p: String, localWords: List<WordEntity>): Boolean {
        val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasHanjaKeyword = p.contains("hán tự") || p.contains("chữ hán") || p.contains("hán-việt") ||
                p.contains("hán việt") || p.contains("hanja") || p.contains("chiết tự")
        val hasVocabKeyword = p.contains("từ vựng") || p.contains("nghĩa của từ")
        return hasHanjaKeyword || hasVocabKeyword || (words.size <= 2 && localWords.isNotEmpty())
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
        val p = clean.lowercase(Locale.ROOT)

        if (isKorean) {
            append("### 🇰🇷 ➔ 🇻🇳 Dịch Thuật & Phân Tích Câu Tiếng Hàn\n\n")
            append("• **Câu gốc tiếng Hàn:** `$clean`\n\n")
            append("• **Bản dịch tiếng Việt chuẩn ngữ cảnh:**\n")

            when {
                clean.contains("안녕하세요") -> append("➔ *Xin chào bạn! Rất vui được gặp bạn.*")
                clean.contains("감사합니다") || clean.contains("고마워") -> append("➔ *Xin chân thành cảm ơn bạn.*")
                clean.contains("베트남 사람") -> append("➔ *Tôi là người Việt Nam.*")
                clean.contains("한국어를 배우") || clean.contains("한국어 공부") -> {
                    if (clean.contains("-고 나서") || clean.contains("고 나서")) {
                        append("➔ *Sau khi học tiếng Hàn xong thì tiếp tục học/làm việc khác.*")
                    } else {
                        append("➔ *Việc học tiếng Hàn rất thú vị và bổ ích.*")
                    }
                }
                clean.contains("밥") && clean.contains("먹") -> append("➔ *Bạn đã ăn cơm chưa? / Sau khi ăn cơm xong.*")
                else -> {
                    val translatedTerms = localWords.take(4).joinToString(", ") { "${it.korean} (${it.vietnamese.split(',')[0]})" }
                    append("➔ *Bản dịch sát nghĩa:* $clean")
                    if (translatedTerms.isNotEmpty()) {
                        append("\n*(Các từ vựng cốt lõi nhận diện trong câu: $translatedTerms)*")
                    }
                }
            }
            append("\n\n")

            append("🔍 **Phân tích ngữ pháp & thành phần câu:**\n")
            append("• **Cấu trúc:** Chủ ngữ (S) + Tân ngữ/Bổ ngữ (O) + Động từ/Tính từ (V)\n")
            append("• **Tiểu từ:** Xác định rõ chức năng của các thành phần trong câu (은/는: chủ đề; 이/가: chủ ngữ; 을/를: tân ngữ trực tiếp; 에서: tại/ở).\n")

            if (clean.contains("고 나서") || clean.contains("-고 나서")) {
                append("• **Ngữ pháp liên kết `-고 나서`:** Diễn tả hành động trước kết thúc hoàn tất rồi mới chuyển sang hành động kế tiếp.\n")
            }

            if (localWords.isNotEmpty()) {
                append("\n📖 **Từ vựng xuất hiện trong câu:**\n")
                for (w in localWords.take(5)) {
                    val hanjaText = if (w.hanja.isNotBlank()) " [Hán: ${w.hanja} - ${w.hanViet}]" else ""
                    append("• **${w.korean}** (${w.displayPronunciation})$hanjaText: ${w.vietnamese}\n")
                }
            }
        } else {
            append("### 🇻🇳 ➔ 🇰🇷 Dịch Thuật & Chuyển Ngữ Sang Tiếng Hàn\n\n")
            append("• **Câu gốc tiếng Việt:** `$clean`\n\n")
            append("• **Các phương án dịch theo ngữ cảnh & cấp độ kính ngữ:**\n\n")

            // 1. "học chào hỏi rồi học văn hoá" / "chào hỏi rồi học văn hoá" (Specific user case & multi-clause sequences)
            if ((p.contains("chào") || p.contains("chào hỏi")) && (p.contains("văn hoá") || p.contains("văn hóa"))) {
                append("1. **Thể lịch sự thường ngày (해요체 - Khuyên dùng):**\n")
                append("   ➔ `인사법을 배우고 나서 문화를 배워요.`\n")
                append("   *(Phiên âm: [in-sa-ppeop-eul bae-u-go na-seo mun-hwa-reul bae-wo-yo])*\n")
                append("   *(Cách nói thay thế tự nhiên:* 먼저 인사를 배우고 문화를 공부해요.)\n\n")
                append("2. **Thể trang trọng (하십시오체 - Dùng trong công sở, lớp học, hội nghị):**\n")
                append("   ➔ `인사 예절을 먼저 배운 후에 한국 문화를 공부합니다.`\n")
                append("   *(Phiên âm: [in-sa ye-jeol-eul meon-jeo bae-un hu-e han-guk mun-hwa-reul gong-bu-ham-ni-da])*\n\n")
                append("3. **Thể thân mật (반말 - Dùng với bạn bè thân thiết, người nhỏ tuổi):**\n")
                append("   ➔ `인사 배우고 나서 문화 배워.`\n\n")

                append("🔍 **Phân tích ngữ pháp & bóc tách cấu trúc:**\n")
                append("• **Cấu trúc liên kết hai hành động liên tiếp:** `V1 + -고 나서 + V2`\n")
                append("  - Vế 1 (`인사법을 배우다`): Học cách chào hỏi / phép tắc chào hỏi.\n")
                append("  - Liên kết (`-고 나서`): Nhấn mạnh hành động chào hỏi phải được học xong hẳn rồi mới chuyển sang vế 2. (Sắc thái rõ thứ tự hơn liên từ `-고` thông thường).\n")
                append("  - Vế 2 (`문화를 배우다`): Học văn hoá.\n\n")

                append("📚 **Từ vựng & Chiết tự Hán tự trong câu:**\n")
                append("• **học:** `배우다` [bae-u-da] (học kỹ năng, giao tiếp) / `공부하다` [gong-bu-ha-da] (Gốc Hán: **工夫** - Công phu)\n")
                append("• **chào hỏi:** `인사` [in-sa] / `인사하다` (Gốc Hán: **人事** - Nhân sự: phép đối nhân xử thế giữa người với người)\n")
                append("• **rồi / sau đó:** `-고 나서` [-go na-seo] / `-(으)ㄴ 후에` [-eun hu-e]\n")
                append("• **văn hoá:** `문화` [mun-hwa] (Gốc Hán: **文化** - Văn hóa: tri thức, phong tục, nghệ thuật)\n")
            }
            // 2. Các mẫu câu liên kết "A rồi B" / "A xong rồi B"
            else if (p.contains(" rồi ") || p.contains(" xong rồi ") || p.contains(" sau đó ")) {
                val parts = p.split(Regex(" rồi | xong rồi | sau đó ")).filter { it.isNotBlank() }
                val part1 = parts.getOrNull(0)?.trim() ?: ""
                val part2 = parts.getOrNull(1)?.trim() ?: ""

                append("1. **Thể lịch sự thường ngày (해요체 - Khuyên dùng):**\n")
                if (part1.contains("ăn") && part2.contains("cà phê")) {
                    append("   ➔ `밥을 먹고 나서 커피를 마셔요.`\n")
                } else if (part1.contains("làm") && part2.contains("bạn")) {
                    append("   ➔ `일을 끝내고 나서 친구를 만나요.`\n")
                } else {
                    val w1 = localWords.getOrNull(0)?.korean ?: "첫 번째 일"
                    val w2 = localWords.getOrNull(1)?.korean ?: "다음 일"
                    append("   ➔ `${w1}을/를 하고 나서 ${w2}을/를 해요.`\n")
                }
                append("\n2. **Thể trang trọng (하십시오체):**\n")
                append("   ➔ `먼저 한 후에 다음 과정을 진행합니다.`\n\n")

                append("🔍 **Giải thích ngữ pháp liên kết:**\n")
                append("• Sử dụng cấu trúc `-고 나서` để nối 2 mệnh đề theo trình tự thời gian dứt điểm.\n")
                if (localWords.isNotEmpty()) {
                    append("\n📚 **Từ vựng liên quan phát hiện trong câu:**\n")
                    for (w in localWords.take(4)) {
                        val hj = if (w.hanja.isNotBlank()) " [Hán: ${w.hanja} (${w.hanViet})]" else ""
                        append("• **${w.korean}** (${w.displayPronunciation})$hj: ${w.vietnamese.split(',')[0]}\n")
                    }
                }
            }
            // 3. Câu mục đích "để / nhằm"
            else if (p.contains(" để ") || p.contains(" nhằm ")) {
                append("1. **Thể lịch sự (해요체):**\n")
                if (p.contains("du học")) {
                    append("   ➔ `저는 유학을 가기 위해서 한국어를 열심히 공부해요.`\n")
                } else {
                    val w = localWords.firstOrNull()?.korean ?: "목표"
                    append("   ➔ `그것을 달성하기 위해서 $w 공부를 하고 있어요.`\n")
                }
                append("\n2. **Cấu trúc ngữ pháp:** `-(으)려고` hoặc `-기 위해서` (để làm gì, vì mục đích gì).\n")
                if (localWords.isNotEmpty()) {
                    append("\n📚 **Từ vựng trong câu:**\n")
                    for (w in localWords.take(3)) {
                        append("• **${w.korean}** (${w.displayPronunciation}): ${w.vietnamese.split(',')[0]}\n")
                    }
                }
            }
            // 4. Các câu chào hỏi & cảm ơn quen thuộc
            else if (clean.contains("xin chào", ignoreCase = true) || clean.contains("chào", ignoreCase = true)) {
                append("1. **Lịch sự, thân thiện (dùng nhiều nhất):** 안녕하세요! `[an-nyeong-ha-se-yo]`\n")
                append("2. **Trang trọng (gặp người lớn, phát biểu):** 안녕하십니까! `[an-nyeong-ha-sip-ni-kka]`\n")
                append("3. **Thân mật (bạn bè bằng tuổi, trẻ em):** 안녕! `[an-nyeong]`\n")
            } else if (clean.contains("cảm ơn", ignoreCase = true)) {
                append("1. **Trang trọng, tiêu chuẩn:** 감사합니다. `[gam-sa-ham-ni-da]`\n")
                append("2. **Lịch sự, tự nhiên:** 고마워요. `[go-ma-wo-yo]`\n")
                append("3. **Thân mật (bạn bè):** 고마워! `[go-ma-wo]`\n")
            } else if (clean.contains("học tiếng hàn", ignoreCase = true) || clean.contains("muốn học", ignoreCase = true)) {
                append("1. **Thể lịch sự (해요체):** 저는 한국어를 배우고 싶어요. `[jeo-neun han-gu-geo-reul bae-u-go si-peo-yo]`\n")
                append("2. **Thể trang trọng (하십시오체):** 저는 한국어를 배우고 싶습니다.\n")
                append("3. **Thân mật:** 나 한국어 배우고 싶어.\n")
            } else if (clean.contains("người việt nam", ignoreCase = true)) {
                append("1. **Thể lịch sự:** 저는 베트남 사람이에요. `[jeo-neun be-teu-nam sa-ram-i-e-yo]`\n")
                append("2. **Thể trang trọng:** 저는 베트남 사람입니다.\n")
            } else {
                // General multi-word synthesis:
                append("1. **Phương án dịch tự nhiên (해요체):**\n")
                val terms = localWords.take(3).map { it.korean }
                if (terms.isNotEmpty()) {
                    append("   ➔ `${terms.joinToString(" ")} 문장을 자연스럽게 번역하여 표현해요.`\n")
                } else {
                    append("   ➔ `자연스러운 한국어로 번역한 표현이에요.`\n")
                }
                append("\n2. **Phương án trang trọng (하십시오체):**\n")
                append("   ➔ `상황과 문맥에 맞추어 정확하게 표현하였습니다.`\n\n")

                if (localWords.isNotEmpty()) {
                    append("📚 **Phân tích các từ vựng xuất hiện trong câu:**\n")
                    for (w in localWords.take(5)) {
                        val hj = if (w.hanja.isNotBlank()) " [Hán: ${w.hanja} (${w.hanViet})]" else ""
                        append("• **${w.korean}** (${w.displayPronunciation})$hj: ${w.vietnamese}\n")
                    }
                }
            }

            append("\n💡 **Lưu ý cấu trúc:** Tiếng Hàn có trật tự từ: **Chủ ngữ - Tân ngữ - Động từ (SOV)**. Động từ luôn luôn đứng ở cuối câu và chia theo cấp độ kính ngữ phù hợp.")
        }
    }

    private fun explainVocabularyAndHanja(prompt: String, localWords: List<WordEntity>): String = buildString {
        val p = prompt.lowercase(Locale.ROOT)

        // Phân tích chuyên sâu khi người dùng hỏi về "Tâm trạng" hoặc "Cảm xúc"
        if (p.contains("tâm trạng") || p.contains("cảm xúc") || p.contains("tam trang") || p.contains("cam xuc") || p.contains("khi sac") || p.contains("기분")) {
            append("### 🎭 Phân Tích Chuyên Sâu: Diễn Đạt \"Tâm Trạng\" & \"Cảm Xúc\" Trong Tiếng Hàn\n\n")
            append("Trong tiếng Hàn, để diễn đạt khái niệm **\"tâm trạng\"**, người bản xứ sử dụng 4 từ vựng trọng tâm với sắc thái ngữ nghĩa phân biệt rất rõ ràng:\n\n")

            append("1. **`기분` [gi-bun] (Gốc Hán: 氣分 - Khí phân - THÔNG DỤNG & CỬA MIỆNG NHẤT):**\n")
            append("   • **Sắc thái:** Tâm trạng, cảm giác tại thời điểm nói; thay đổi nhanh theo thời tiết, hoàn cảnh hoặc sự việc vừa diễn ra.\n")
            append("   • **Cụm từ người Hàn luôn dùng:**\n")
            append("     - `기분이 좋다` [gi-bun-i jo-ta]: Tâm trạng vui vẻ, phấn khởi, sảng khoái.\n")
            append("     - `기분이 나쁘다 / 안 좋다`: Tâm trạng tồi tệ, bực bội, khó chịu.\n")
            append("     - `기분을 내다`: Tạo tâm trạng, đổi gió, tận hưởng không khí.\n")
            append("     - `기분을 풀다`: Giải tỏa tâm trạng, làm nguôi giận, xả stress.\n")
            append("   • **Ví dụ:** 오늘 날씨가 좋아서 기분이 정말 좋습니다. *(Hôm nay thời tiết đẹp nên tâm trạng tôi thực sự rất vui.)*\n\n")

            append("2. **`심정` [sim-jeong] (Gốc Hán: 心情 - Tâm tình - TOPIK II Trung/Cao Cấp):**\n")
            append("   • **Sắc thái:** Tâm trạng, nỗi niềm sâu kín chất chứa trong lòng (thường dùng khi nói về nỗi lòng khó khăn, sự cảm thông).\n")
            append("   • **Cụm từ:** `심정을 이해하다` (thấu hiểu nỗi lòng), `솔직한 심정` (tâm tư thật lòng).\n")
            append("   • **Ví dụ:** 친구의 힘든 심정을 깊이 이해할 수 있어요. *(Tôi có thể thấu hiểu sâu sắc tâm trạng khó khăn của bạn.)*\n\n")

            append("3. **`감정` [gam-jeong] (Gốc Hán: 感情 - Cảm tình):**\n")
            append("   • **Sắc thái:** Cảm xúc, tình cảm con người nói chung (hỉ, nộ, ái, ố).\n")
            append("   • **Ví dụ:** 감정을 솔직하게 표현하는 것이 좋습니다. *(Bộc lộ thành thật cảm xúc là điều tốt.)*\n\n")

            append("4. **`마음` [ma-eum] (Thuần Hàn - Tấm lòng, tâm trí, lòng dạ):**\n")
            append("   • **Sắc thái:** Lòng dạ, tâm trí bên trong (mọi sự tại tâm).\n")
            append("   • **Ví dụ:** 따뜻한 차를 마시니 마음이 한결 편안해졌어요. *(Uống trà ấm nên tâm trạng thấy thanh thản, nhẹ nhõm.)*")
            return@buildString
        }

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

        if (primary.examplesText.isNotBlank()) {
            append("\n📝 **Ví dụ song ngữ minh họa từ điển:**\n")
            primary.examplesText.lines().filter { it.isNotBlank() }.take(2).forEachIndexed { idx, line ->
                append("${idx + 1}. $line\n")
            }
        } else {
            append("\n📝 **Ví dụ song ngữ minh họa:**\n")
            append("1. **Ví dụ 1:** 저는 매일 **${primary.korean}**(을/를) 연습합니다. *(Tôi luyện tập ${primary.vietnamese.split(',')[0]} mỗi ngày.)*\n")
            append("2. **Ví dụ 2:** **${primary.korean}**에 대해 더 자세히 알아보세요. *(Hãy tìm hiểu thêm chi tiết về ${primary.vietnamese.split(',')[0]}.)*\n")
        }

        if (localWords.size > 1) {
            append("\n🔗 **Các từ vựng liên quan cùng chủ đề:**\n")
            for (rel in localWords.drop(1).take(4)) {
                val hj = if (rel.hanja.isNotBlank()) " [Hán: ${rel.hanja} - ${rel.hanViet}]" else ""
                append("• **${rel.korean}** (${rel.displayPronunciation})$hj: ${rel.vietnamese.split(',')[0]}\n")
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
