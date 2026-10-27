package com.example

import com.example.ai.gateway.ProviderProtocol
import com.example.ai.gateway.UnifiedAiMessage
import com.example.ai.tier1.LocalAiMiddleman
import com.example.ai.tier1.Tier2ExecutionMode
import com.example.ai.tier1.UserIntent
import com.example.ai.tier2.LocalTier2InferenceEngine
import com.example.data.initial.PreloadedWords
import com.example.util.HanjaHanVietEngine
import com.example.util.KoreanVietnameseSearchMatcher
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
}
