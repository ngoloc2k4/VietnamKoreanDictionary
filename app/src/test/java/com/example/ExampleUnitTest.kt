package com.example

import com.example.ai.gateway.AiProviderConfig
import com.example.ai.gateway.ProviderProtocol
import com.example.ai.gateway.UnifiedAiMessage
import com.example.ai.tier1.LocalAiMiddleman
import com.example.ai.tier1.Tier2ExecutionMode
import com.example.ai.tier1.UserIntent
import com.example.ai.tier2.LocalTier2InferenceEngine
import com.example.data.initial.PreloadedWords
import com.example.data.model.WordEntity
import com.example.util.BilingualTranslationService
import com.example.util.HanjaHanVietEngine
import com.example.util.KoreanVietnameseSearchMatcher
import com.example.util.TranslationEngine
import com.example.util.WordThemeClassifier
import com.example.ui.components.parseMarkdownInline
import com.example.ui.components.parseMarkdownToBlocks
import com.example.ui.components.MarkdownBlock
import com.example.ui.viewmodel.WordFilter
import com.example.ui.viewmodel.FlashcardFilter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    private val words = PreloadedWords.getInitialWords()

    @Test
    fun `unaccented vietnamese search finds correct words`() {
        // "hoc" should match "공부하다" (Học, học tập)
        val resultsHoc = KoreanVietnameseSearchMatcher.searchAndRank(words, "hoc")
        assertTrue("Results for 'hoc' should not be empty", resultsHoc.isNotEmpty())
        assertEquals("공부하다", resultsHoc.first().word.korean)
        assertTrue(resultsHoc.first().isBestMatch)

        // "hanh phuc" should match "행복하다" (Hạnh phúc)
        val resultsHanhPhuc = KoreanVietnameseSearchMatcher.searchAndRank(words, "hanh phuc")
        assertTrue("Results for 'hanh phuc' should not be empty", resultsHanhPhuc.isNotEmpty())
        assertEquals("행복하다", resultsHanhPhuc.first().word.korean)

        // "tien sinh" should match "선생님" (Tiên sinh)
        val resultsTienSinh = KoreanVietnameseSearchMatcher.searchAndRank(words, "tien sinh")
        assertTrue("Results for 'tien sinh' should not be empty", resultsTienSinh.isNotEmpty())
        assertEquals("선생님", resultsTienSinh.first().word.korean)

        // "thanh cong" should match "성공하다" (Thành công)
        val resultsThanhCong = KoreanVietnameseSearchMatcher.searchAndRank(words, "thanh cong")
        assertTrue("Results for 'thanh cong' should not be empty", resultsThanhCong.isNotEmpty())
        assertEquals("성공하다", resultsThanhCong.first().word.korean)
    }

    @Test
    fun `korean conjugated forms recognize root word`() {
        val resultsGayo = KoreanVietnameseSearchMatcher.searchAndRank(words, "가요")
        assertTrue(resultsGayo.isNotEmpty())
        assertEquals("가다", resultsGayo.first().word.korean)

        val resultsGongbu = KoreanVietnameseSearchMatcher.searchAndRank(words, "공부해요")
        assertTrue(resultsGongbu.isNotEmpty())
        assertEquals("공부하다", resultsGongbu.first().word.korean)

        val resultsEoryeowo = KoreanVietnameseSearchMatcher.searchAndRank(words, "어려워")
        assertTrue(resultsEoryeowo.isNotEmpty())
        assertEquals("어렵다", resultsEoryeowo.first().word.korean)
    }

    @Test
    fun `korean chosung initial consonants search finds words`() {
        val resultsGb = KoreanVietnameseSearchMatcher.searchAndRank(words, "ㄱㅂ")
        assertTrue("Results for 'ㄱㅂ' should not be empty", resultsGb.isNotEmpty())
        assertTrue("Top results should contain 공부하다", resultsGb.any { it.word.korean == "공부하다" })

        val resultsSsn = KoreanVietnameseSearchMatcher.searchAndRank(words, "ㅅㅅㄴ")
        assertTrue("Results for 'ㅅㅅㄴ' should not be empty", resultsSsn.isNotEmpty())
        assertEquals("선생님", resultsSsn.first().word.korean)
    }

    @Test
    fun `fuzzy approximate match works for typos and romanization`() {
        val resultsGongbu = KoreanVietnameseSearchMatcher.searchAndRank(words, "gongbu")
        assertTrue(resultsGongbu.isNotEmpty())
        assertEquals("공부하다", resultsGongbu.first().word.korean)

        val resultsKamsa = KoreanVietnameseSearchMatcher.searchAndRank(words, "kamsaham")
        assertTrue(resultsKamsa.isNotEmpty())
        assertTrue("Matches should contain either 감사하다 or 감사합니다", resultsKamsa.first().word.korean in listOf("감사하다", "감사합니다"))
    }

    @Test
    fun `related words search finds synonyms and antonyms`() {
        val resultsSyn = KoreanVietnameseSearchMatcher.searchAndRank(words, "배우다")
        assertTrue(resultsSyn.isNotEmpty())
        assertEquals("공부하다", resultsSyn.first().word.korean)
    }

    // ==========================================
    // FLOSS SPECIFICATION VALIDATION TESTS
    // ==========================================

    @Test
    fun `tier 1 local ai classifies intent and handles offline lookups directly`() {
        // Direct word lookup should be handled offline without calling cloud
        val decisionOffline = LocalAiMiddleman.processQuery("가다", words)
        assertTrue("Direct single word should be handled offline", decisionOffline.canHandleOffline)
        assertNotNull(decisionOffline.offlineAnswer)
        assertTrue(decisionOffline.offlineAnswer!!.contains("가다"))

        // Grammar inquiry should route to Cloud AI with optimized prompt and Local RAG context
        val decisionGrammar = LocalAiMiddleman.processQuery("Phân biệt ngữ pháp -는데 và -지만", words)
        assertFalse("Grammar comparison requires Cloud AI", decisionGrammar.canHandleOffline)
        assertEquals(UserIntent.GRAMMAR_EXPLANATION, decisionGrammar.intent)
        assertNotNull(decisionGrammar.optimizedRequest)
        assertTrue(decisionGrammar.optimizedRequest!!.messages.any { it.role == "system" })
    }

    @Test
    fun `hanja engine maps Sino-Vietnamese to Sino-Korean accurately`() {
        // Search "Học" -> should return "學" (학) with compounds like "학교", "학생"
        val morphemesHoc = HanjaHanVietEngine.searchMorphemes("Học", words)
        assertTrue("Morphemes for 'Học' should not be empty", morphemesHoc.isNotEmpty())
        val hoc = morphemesHoc.first()
        assertEquals("學", hoc.hanja)
        assertEquals("학", hoc.hanKor)
        assertTrue("Should include '학교' in compounds", hoc.commonCompounds.any { it.korean == "학교" })

        // Check phonetic rules list
        val rules = HanjaHanVietEngine.PHONETIC_RULES
        assertTrue("Should have at least 5 phonetic rules", rules.size >= 5)
    }

    @Test
    fun `provider protocol defaults and endpoints are valid`() {
        assertEquals("https://api.openai.com/v1", ProviderProtocol.OPENAI_COMPATIBLE.defaultBaseUrl)
        assertEquals("https://api.anthropic.com/v1", ProviderProtocol.ANTHROPIC_COMPATIBLE.defaultBaseUrl)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", ProviderProtocol.GEMINI.defaultBaseUrl)
    }

    @Test
    fun `app log manager records and clears logs safely`() {
        com.example.util.AppLogManager.clear()
        com.example.util.AppLogManager.log("TestTag", "Sample log message", "INFO")
        com.example.util.AppLogManager.log("TestTag", "Error occurred", "ERROR")

        val currentLogs = com.example.util.AppLogManager.getLogs()
        assertTrue("Logs should contain at least 2 entries", currentLogs.size >= 2)
        assertTrue(currentLogs.any { it.message.contains("Sample log message") })
        assertTrue(currentLogs.any { it.level == "ERROR" })

        com.example.util.AppLogManager.clear()
        val afterClear = com.example.util.AppLogManager.getLogs()
        assertTrue("Logs should be reset", afterClear.size <= 2)
    }

    @Test
    fun `ai model manager presets have valid URLs and respect FLOSS 150MB budget`() {
        val presets = com.example.ai.tier1.AiModelManager.PRESET_MODELS
        assertTrue("Should have at least 2 model presets", presets.size >= 2)

        val smollm4 = presets.first { it.id == "smollm2_135m_q4" }
        assertTrue("SmolLM2 Q4 should be recommended", smollm4.isRecommended)
        assertTrue("SmolLM2 Q4 must be <= 120MB for budget", smollm4.approximateSizeMb <= 120.0)
        assertTrue("Must be from Hugging Face resolve", smollm4.downloadUrl.contains("huggingface.co"))
        assertTrue("Must be GGUF file format", smollm4.fileName.endsWith(".gguf"))

        val embedding = presets.first { it.id == "minilm_l6_v2_embedding" }
        assertTrue("Embedding model should be <= 50MB", embedding.approximateSizeMb <= 50.0)
        assertEquals("EMBEDDING", embedding.tag)
    }

    @Test
    fun `local ai middleman integrates active on-device model into tier 1 decision`() {
        // Query requiring cloud Tier 2, but with active local model on Tier 1
        val decisionWithModel = LocalAiMiddleman.processQuery(
            userQuery = "Phân biệt giúp tôi ngữ pháp -아/어서 và -(으)니까",
            localWords = words,
            activeModelName = "smollm2-135m-instruct-q4_k_m.gguf"
        )

        assertFalse(decisionWithModel.canHandleOffline)
        assertTrue(decisionWithModel.isLocalModelActive)
        assertEquals("smollm2-135m-instruct-q4_k_m.gguf", decisionWithModel.activeModelName)
        assertNotNull(decisionWithModel.optimizedRequest)
        val systemPrompt = decisionWithModel.optimizedRequest!!.messages.first { it.role == "system" }.content
        assertTrue("System prompt should mention active on-device model", systemPrompt.contains("smollm2-135m-instruct-q4_k_m.gguf"))

        // Query with heuristic fallback (activeModelName = null)
        val decisionHeuristic = LocalAiMiddleman.processQuery(
            userQuery = "Phân biệt giúp tôi ngữ pháp -아/어서 và -(으)니까",
            localWords = words,
            activeModelName = null
        )
        assertFalse(decisionHeuristic.isLocalModelActive)
        assertNull(decisionHeuristic.activeModelName)
    }

    @Test
    fun `tier 2 model presets include 0_5B to 0_8B models for offline execution`() {
        val presets = com.example.ai.tier1.AiModelManager.PRESET_MODELS
        val tier2Presets = presets.filter { it.tier == 2 }

        assertTrue("Should have at least 3 Tier 2 presets", tier2Presets.size >= 3)

        // Qwen 2.5 0.5B
        val qwen = tier2Presets.find { it.id == "qwen2_5_05b_q4" }
        assertNotNull("Qwen 2.5 0.5B must exist", qwen)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m.gguf", qwen!!.fileName)
        assertTrue("Qwen size should be ~390MB", qwen.approximateSizeMb in 350.0..450.0)
        assertTrue(qwen.downloadUrl.contains("Qwen/Qwen2.5-0.5B-Instruct-GGUF"))

        // SmolLM2 360M
        val smol360 = tier2Presets.find { it.id == "smollm2_360m_q4" }
        assertNotNull("SmolLM2 360M must exist", smol360)
        assertEquals("smollm2-360m-instruct-q4_k_m.gguf", smol360!!.fileName)
        assertTrue("SmolLM2 360M size should be ~260MB", smol360.approximateSizeMb in 240.0..300.0)

        // Danube3 500M
        val danube = tier2Presets.find { it.id == "danube3_500m_q4" }
        assertNotNull("Danube3 500M must exist", danube)
        assertEquals("h2o-danube3-500m-chat-q4_k_m.gguf", danube!!.fileName)
    }

    @Test
    fun `tier 2 local inference engine produces structured response offline`() {
        // Test grammar explanation
        val grammarResponse = LocalTier2InferenceEngine.executeOfflineInference(
            query = "Phân biệt ngữ pháp -는데 và -지만",
            localWords = words,
            modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        )

        assertTrue("Inference response must succeed", grammarResponse.isSuccess)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m.gguf", grammarResponse.modelName)
        assertTrue("Provider name must indicate Tier 2 local", grammarResponse.providerName.contains("Tier 2 Local"))
        assertTrue("Grammar analysis should contain content", grammarResponse.content.isNotEmpty())
        assertTrue("Latency should be non-negative", grammarResponse.latencyMs >= 0)

        // Test vocabulary inquiry with Hanja morpheme linkage
        val vocabResponse = LocalTier2InferenceEngine.executeOfflineInference(
            query = "Học tập trong tiếng Hàn là gì?",
            localWords = words,
            modelFileName = "smollm2-360m-instruct-q4_k_m.gguf"
        )

        assertTrue(vocabResponse.isSuccess)
        assertTrue("Should include Korean vocabulary", vocabResponse.content.contains("공부하다") || vocabResponse.content.contains("Học tập"))

        // Test Korean to Vietnamese sentence translation
        val translationKrResponse = LocalTier2InferenceEngine.executeOfflineInference(
            query = "Dịch câu: 저는 베트남 사람입니다",
            localWords = words,
            modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        )
        assertTrue(translationKrResponse.isSuccess)
        assertTrue("Translation should identify Vietnamese meaning", translationKrResponse.content.contains("Việt Nam"))

        // Test Vietnamese to Korean sentence translation
        val translationVnResponse = LocalTier2InferenceEngine.executeOfflineInference(
            query = "Dịch sang tiếng Hàn: Cảm ơn bạn rất nhiều",
            localWords = words,
            modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        )
        assertTrue(translationVnResponse.isSuccess)
        assertTrue("Translation should provide polite and formal Korean", translationVnResponse.content.contains("감사합니다") || translationVnResponse.content.contains("고마워"))
    }

    @Test
    fun `displayPronunciation cleans compound search index from UI display`() {
        val wordWithCompoundIndex = words.first { it.korean == "안녕하세요" }
        assertEquals("[an-nyeong-ha-se-yo]", wordWithCompoundIndex.displayPronunciation)

        val wordGongbu = words.first { it.korean == "공부하다" }
        assertEquals("[gong-bu-ha-da]", wordGongbu.displayPronunciation)
    }

    @Test
    fun `essential everyday vocabulary search returns top ranked matches`() {
        val xinChaoResults = KoreanVietnameseSearchMatcher.searchAndRank(words, "xin chao")
        assertTrue(xinChaoResults.isNotEmpty())
        assertEquals("안녕하세요", xinChaoResults.first().word.korean)

        val camOnResults = KoreanVietnameseSearchMatcher.searchAndRank(words, "cam on")
        assertTrue(camOnResults.isNotEmpty())
        assertTrue("Matches should contain either 감사합니다 or 감사하다", camOnResults.first().word.korean in listOf("감사합니다", "감사하다"))
    }

    @Test
    fun `markdown inline parser converts markers to styled spans`() {
        val parsed = parseMarkdownInline("**Đậm** và *Nghiêng* và ***Đậm Nghiêng*** và `code`")
        assertEquals("Đậm và Nghiêng và Đậm Nghiêng và  code ", parsed.text)
        assertTrue("Span styles should not be empty", parsed.spanStyles.isNotEmpty())
        assertTrue("Should contain bold style", parsed.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue("Should contain italic style", parsed.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `word theme classifier identifies everyday life topics accurately`() {
        val dailyWord = WordEntity(
            korean = "가족",
            vietnamese = "Gia đình, người thân",
            pronunciation = "[ga-jok]",
            partOfSpeech = "Danh từ"
        )
        assertTrue("Should match daily theme", WordThemeClassifier.matchesDaily(dailyWord))

        val foodWord = WordEntity(
            korean = "밥을 먹다",
            vietnamese = "Ăn cơm, dùng bữa",
            pronunciation = "[ba-beul meok-da]",
            partOfSpeech = "Động từ"
        )
        assertTrue("Should match food theme", WordThemeClassifier.matchesFood(foodWord))

        val workWord = WordEntity(
            korean = "출근하다",
            vietnamese = "Đi làm, đến văn phòng công ty",
            pronunciation = "[chul-geun-ha-da]",
            partOfSpeech = "Động từ"
        )
        assertTrue("Should match work theme", WordThemeClassifier.matchesWork(workWord))

        val eduWord = WordEntity(
            korean = "대학교",
            vietnamese = "Trường đại học",
            pronunciation = "[dae-hak-gyo]",
            partOfSpeech = "Danh từ"
        )
        assertTrue("Should match education theme", WordThemeClassifier.matchesEducation(eduWord))

        val travelWord = WordEntity(
            korean = "비행기",
            vietnamese = "Máy bay du lịch",
            pronunciation = "[bi-haeng-gi]",
            partOfSpeech = "Danh từ"
        )
        assertTrue("Should match travel theme", WordThemeClassifier.matchesTravel(travelWord))

        val shopWord = WordEntity(
            korean = "시장",
            vietnamese = "Chợ, nơi mua bán đồ",
            pronunciation = "[si-jang]",
            partOfSpeech = "Danh từ"
        )
        assertTrue("Should match shopping theme", WordThemeClassifier.matchesShopping(shopWord))

        val healthWord = WordEntity(
            korean = "병원",
            vietnamese = "Bệnh viện, nơi bác sĩ khám bệnh",
            pronunciation = "[byeong-won]",
            partOfSpeech = "Danh từ"
        )
        assertTrue("Should match health theme", WordThemeClassifier.matchesHealth(healthWord))

        val emotionWord = WordEntity(
            korean = "행복하다",
            vietnamese = "Hạnh phúc, vui sướng",
            pronunciation = "[haeng-bok-ha-da]",
            partOfSpeech = "Tính từ"
        )
        assertTrue("Should match emotion theme", WordThemeClassifier.matchesEmotion(emotionWord))
    }

    @Test
    fun `topik level filtering supports all levels 1 to 6`() {
        val testWords = (1..6).map { levelNum ->
            WordEntity(
                korean = "단어$levelNum",
                vietnamese = "Từ vựng cấp $levelNum",
                pronunciation = "[dan-eo]",
                partOfSpeech = "Danh từ",
                topikLevelNumber = levelNum
            )
        }

        assertEquals(1, testWords.filter { it.topikLevelNumber == 1 }.size)
        assertEquals(1, testWords.filter { it.topikLevelNumber == 2 }.size)
        assertEquals(1, testWords.filter { it.topikLevelNumber == 3 }.size)
        assertEquals(1, testWords.filter { it.topikLevelNumber == 4 }.size)
        assertEquals(1, testWords.filter { it.topikLevelNumber == 5 }.size)
        assertEquals(1, testWords.filter { it.topikLevelNumber == 6 }.size)

        assertEquals("TOPIK 1", WordFilter.TOPIK_1.label)
        assertEquals("TOPIK 6", WordFilter.TOPIK_6.label)
        assertEquals("TOPIK 1", FlashcardFilter.TOPIK_1.label)
        assertEquals("TOPIK 6", FlashcardFilter.TOPIK_6.label)
    }

    @Test
    fun `markdown block parser structures headings quotes and lists`() {
        val markdown = """
            # Tiêu đề 1
            ## Tiêu đề 2
            > Đây là trích dẫn ghi chú
            • Gạch đầu dòng thứ nhất
            1. Mục đánh số 1
            ---
            Đoạn văn bình thường.
        """.trimIndent()

        val blocks = parseMarkdownToBlocks(markdown)
        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 1 && it.text == "Tiêu đề 1" })
        assertTrue(blocks.any { it is MarkdownBlock.Heading && it.level == 2 && it.text == "Tiêu đề 2" })
        assertTrue(blocks.any { it is MarkdownBlock.Quote && it.text == "Đây là trích dẫn ghi chú" })
        assertTrue(blocks.any { it is MarkdownBlock.BulletItem && it.text == "Gạch đầu dòng thứ nhất" })
        assertTrue(blocks.any { it is MarkdownBlock.NumberedItem && it.prefix == "1." && it.text == "Mục đánh số 1" })
        assertTrue(blocks.any { it is MarkdownBlock.Divider })
        assertTrue(blocks.any { it is MarkdownBlock.Paragraph && it.text == "Đoạn văn bình thường." })
    }

    @Test
    fun `translation engines cover Google Translate DeepL Local and Cloud AI`() {
        assertEquals("Google Translate", TranslationEngine.GOOGLE_TRANSLATE.title)
        assertEquals("DeepL", TranslationEngine.DEEPL.title)
        assertEquals("Model Local (Tầng 2)", TranslationEngine.LOCAL_MODEL.title)
        assertEquals("AI Model lớn (Online)", TranslationEngine.CLOUD_AI.title)

        assertFalse("Local model must be offline", TranslationEngine.LOCAL_MODEL.isOnline)
        assertTrue("Google Translate should be online", TranslationEngine.GOOGLE_TRANSLATE.isOnline)
        assertTrue("DeepL should be online", TranslationEngine.DEEPL.isOnline)
        assertTrue("Cloud AI should be online", TranslationEngine.CLOUD_AI.isOnline)
    }

    @Test
    fun `bilingual local model executes offline translation with vocabulary insights`() = kotlinx.coroutines.test.runTest {
        val result = BilingualTranslationService.translateWithLocalModel(
            text = "안녕하세요",
            fromKorean = true,
            localWords = words,
            activeModelName = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        )
        assertTrue("Translation result should not be empty", result.isNotEmpty())
        assertTrue("Result should contain Korean text or greeting", result.contains("안녕하세요") || result.contains("Xin chào"))
    }

    @Test
    fun `cloud ai translation requirement validation gates unauthorized usage`() {
        val emptyProviders = emptyList<AiProviderConfig>()
        val hasValidEmpty = emptyProviders.filter { it.isEnabled }.any { it.apiKey.isNotBlank() }
        assertFalse("Empty provider list cannot satisfy cloud requirements", hasValidEmpty)

        val unconfiguredProviders = listOf(
            AiProviderConfig(
                id = "groq",
                name = "Groq",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = "",
                modelName = "llama-3.3-70b-versatile",
                isEnabled = true
            )
        )
        val hasValidUnconfigured = unconfiguredProviders.filter { it.isEnabled }.any { it.apiKey.isNotBlank() }
        assertFalse("Provider without API key cannot satisfy cloud requirements", hasValidUnconfigured)

        val configuredProviders = listOf(
            AiProviderConfig(
                id = "groq",
                name = "Groq",
                protocol = ProviderProtocol.OPENAI_COMPATIBLE,
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = "gsk_valid_mock_key_12345",
                modelName = "llama-3.3-70b-versatile",
                isEnabled = true
            )
        )
        val hasValidConfigured = configuredProviders.filter { it.isEnabled }.any { it.apiKey.isNotBlank() }
        assertTrue("Configured provider with API key must satisfy cloud requirements", hasValidConfigured)
    }

    @Test
    fun `multi-word sentence translation does not truncate to single word and explains compound grammar`() {
        // Query: "học chào hỏi rồi học văn hoá"
        val query = "học chào hỏi rồi học văn hoá"
        val candidateWords = KoreanVietnameseSearchMatcher.searchAndRank(words, "học").map { it.word }

        val response = LocalTier2InferenceEngine.executeOfflineInference(
            query = query,
            localWords = candidateWords,
            modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            modelSizeMb = 390.0
        )

        assertTrue("Inference must succeed", response.isSuccess)
        val content = response.content

        // Verify it translates the full sentence, not just the word 'học'
        assertTrue("Must contain Korean translation for greeting and culture", content.contains("인사") && content.contains("문화"))
        assertTrue("Must explain the sequential conjunction -고 나서", content.contains("-고 나서"))
        assertTrue("Must contain vocabulary breakdown for chào hỏi", content.contains("chào hỏi") || content.contains("인사"))
        assertTrue("Must contain vocabulary breakdown for văn hoá", content.contains("văn hoá") || content.contains("문화"))
        assertFalse("Must NOT truncate to a single-word dictionary lookup of only 'học'", content.startsWith("### 🔍 Phân Tích Chuyên Sâu Từ Vựng & Chiết Tự Hán-Hàn"))
    }

    @Test
    fun `settings navigation tab and ai tutor execution tiers exist and are well defined`() {
        val settingsTab = com.example.NavigationTab.SETTINGS
        assertEquals("Cài đặt", settingsTab.title)
        assertEquals("nav_settings_tab", settingsTab.testTag)

        assertEquals("⚡ Tầng 1 Local", com.example.ui.viewmodel.AiTutorExecutionTier.TIER1_LOCAL.label)
        assertEquals("📱 Tầng 2 Ngoại tuyến", com.example.ui.viewmodel.AiTutorExecutionTier.TIER2_OFFLINE.label)
        assertEquals("☁️ Tầng 2 Đám mây", com.example.ui.viewmodel.AiTutorExecutionTier.TIER2_CLOUD.label)
    }
}
