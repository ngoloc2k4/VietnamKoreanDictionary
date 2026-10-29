package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.gateway.*
import com.example.ai.tier1.*
import com.example.ai.tier2.LocalTier2InferenceEngine
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.TranslationHistoryEntity
import com.example.data.model.WordEntity
import com.example.data.repository.DictionaryRepository
import com.example.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class WordFilterGroup(val label: String) {
    ALL("Tất cả"),
    TOPIK("Cấp độ TOPIK (1-6)"),
    THEME("Chủ đề đời sống"),
    PART_OF_SPEECH("Từ loại")
}

enum class WordFilter(val label: String, val group: WordFilterGroup = WordFilterGroup.ALL) {
    ALL("Tất cả", WordFilterGroup.ALL),
    
    // Cấp độ TOPIK đủ từ 1 đến 6
    TOPIK_1("TOPIK 1", WordFilterGroup.TOPIK),
    TOPIK_2("TOPIK 2", WordFilterGroup.TOPIK),
    TOPIK_3("TOPIK 3", WordFilterGroup.TOPIK),
    TOPIK_4("TOPIK 4", WordFilterGroup.TOPIK),
    TOPIK_5("TOPIK 5", WordFilterGroup.TOPIK),
    TOPIK_6("TOPIK 6", WordFilterGroup.TOPIK),
    
    // Chủ đề đời sống thiết thực
    THEME_DAILY("🏠 Đời sống", WordFilterGroup.THEME),
    THEME_FOOD("🍜 Ẩm thực", WordFilterGroup.THEME),
    THEME_WORK("💼 Công sở", WordFilterGroup.THEME),
    THEME_EDUCATION("🎓 Trường học", WordFilterGroup.THEME),
    THEME_TRAVEL("✈️ Du lịch", WordFilterGroup.THEME),
    THEME_SHOPPING("🛍️ Mua sắm", WordFilterGroup.THEME),
    THEME_HEALTH("🏥 Sức khỏe", WordFilterGroup.THEME),
    THEME_EMOTION("❤️ Cảm xúc", WordFilterGroup.THEME),
    
    // Từ loại & Đặc biệt
    VERB("Động từ", WordFilterGroup.PART_OF_SPEECH),
    ADJECTIVE("Tính từ", WordFilterGroup.PART_OF_SPEECH),
    NOUN("Danh từ", WordFilterGroup.PART_OF_SPEECH),
    HIGH_FREQ("⭐ Tần suất cao", WordFilterGroup.PART_OF_SPEECH)
}

enum class FlashcardFilterGroup(val label: String) {
    STUDY("Ôn tập"),
    TOPIK("Cấp độ TOPIK (1-6)"),
    THEME("Chủ đề đời sống")
}

enum class FlashcardFilter(val label: String, val group: FlashcardFilterGroup = FlashcardFilterGroup.STUDY) {
    ALL("Tất cả", FlashcardFilterGroup.STUDY),
    FAVORITES("Yêu thích", FlashcardFilterGroup.STUDY),
    NEED_REVIEW("Cần ôn tập", FlashcardFilterGroup.STUDY),

    // Cấp độ TOPIK 1-6
    TOPIK_1("TOPIK 1", FlashcardFilterGroup.TOPIK),
    TOPIK_2("TOPIK 2", FlashcardFilterGroup.TOPIK),
    TOPIK_3("TOPIK 3", FlashcardFilterGroup.TOPIK),
    TOPIK_4("TOPIK 4", FlashcardFilterGroup.TOPIK),
    TOPIK_5("TOPIK 5", FlashcardFilterGroup.TOPIK),
    TOPIK_6("TOPIK 6", FlashcardFilterGroup.TOPIK),

    // Chủ đề đời sống
    THEME_DAILY("🏠 Đời sống", FlashcardFilterGroup.THEME),
    THEME_FOOD("🍜 Ẩm thực", FlashcardFilterGroup.THEME),
    THEME_WORK("💼 Công sở", FlashcardFilterGroup.THEME),
    THEME_EDUCATION("🎓 Trường học", FlashcardFilterGroup.THEME),
    THEME_TRAVEL("✈️ Du lịch", FlashcardFilterGroup.THEME),
    THEME_SHOPPING("🛍️ Mua sắm", FlashcardFilterGroup.THEME),
    THEME_HEALTH("🏥 Sức khỏe", FlashcardFilterGroup.THEME),
    THEME_EMOTION("❤️ Cảm xúc", FlashcardFilterGroup.THEME)
}

enum class AiTutorExecutionTier(val label: String, val description: String) {
    TIER1_LOCAL("⚡ Tầng 1 Local", "Siêu nhẹ on-device / RAG / Heuristic (0MB - 140MB)"),
    TIER2_OFFLINE("📱 Tầng 2 Ngoại tuyến", "Mô hình LLM 0.5B-0.8B chạy offline trên thiết bị"),
    TIER2_CLOUD("☁️ Tầng 2 Đám mây", "Mô hình lớn đa nhà cung cấp (Groq, Gemini, Claude, OpenAI)")
}

data class ChatMessageUi(
    val role: String, // "user", "assistant", "middleman"
    val content: String,
    val isOfflineHandled: Boolean = false,
    val debugInfo: UnifiedAiResponse? = null
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {
    val repository = DictionaryRepository(application)
    val ttsManager = TtsManager(application)
    val aiGateway = MultiProviderAiGateway(application)

    // Search & Dictionary State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(WordFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()

    // Paging / Display limit for high performance rendering of 78,000 words
    private val _displayLimit = MutableStateFlow(30)
    val displayLimit = _displayLimit.asStateFlow()

    fun loadMoreResults() {
        _displayLimit.value += 30
        AppLogManager.log("Search", "Tải thêm kết quả: Hiển thị tối đa ${_displayLimit.value} từ", "INFO")
    }

    // TTS Speed configuration state
    private val _ttsSpeed = MutableStateFlow(ttsManager.getSpeechRate())
    val ttsSpeed = _ttsSpeed.asStateFlow()

    fun setTtsSpeed(speed: Float) {
        ttsManager.setSpeechRate(speed)
        _ttsSpeed.value = ttsManager.getSpeechRate()
    }

    // In-App HTTP Debug Server on Port 8888
    val isDebugServerRunning: StateFlow<Boolean> = DebugHttpServer.isRunning
    val debugServerUrl: StateFlow<String> = DebugHttpServer.serverUrl

    fun toggleDebugServer() {
        DebugHttpServer.toggleServer(getApplication())
    }

    private val _allWords = repository.getAllWords()
    val allWordsList: StateFlow<List<WordEntity>> = _allWords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val totalWordCount: StateFlow<Int> = repository.getTotalWordCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        78000
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val searchResults: StateFlow<List<MatchResult>> = _searchQuery
        .debounce { if (it.isBlank()) 0L else 250L }
        .flatMapLatest { query ->
            combine(
                repository.searchWords(query),
                _selectedFilter
            ) { words, filter ->
                val list = KoreanVietnameseSearchMatcher.searchAndRank(words, query)
                when (filter) {
                    WordFilter.ALL -> list
                    WordFilter.TOPIK_1 -> list.filter { it.word.topikLevelNumber == 1 }
                    WordFilter.TOPIK_2 -> list.filter { it.word.topikLevelNumber == 2 }
                    WordFilter.TOPIK_3 -> list.filter { it.word.topikLevelNumber == 3 }
                    WordFilter.TOPIK_4 -> list.filter { it.word.topikLevelNumber == 4 }
                    WordFilter.TOPIK_5 -> list.filter { it.word.topikLevelNumber == 5 }
                    WordFilter.TOPIK_6 -> list.filter { it.word.topikLevelNumber == 6 }
                    WordFilter.THEME_DAILY -> list.filter { WordThemeClassifier.matchesDaily(it.word) }
                    WordFilter.THEME_FOOD -> list.filter { WordThemeClassifier.matchesFood(it.word) }
                    WordFilter.THEME_WORK -> list.filter { WordThemeClassifier.matchesWork(it.word) }
                    WordFilter.THEME_EDUCATION -> list.filter { WordThemeClassifier.matchesEducation(it.word) }
                    WordFilter.THEME_TRAVEL -> list.filter { WordThemeClassifier.matchesTravel(it.word) }
                    WordFilter.THEME_SHOPPING -> list.filter { WordThemeClassifier.matchesShopping(it.word) }
                    WordFilter.THEME_HEALTH -> list.filter { WordThemeClassifier.matchesHealth(it.word) }
                    WordFilter.THEME_EMOTION -> list.filter { WordThemeClassifier.matchesEmotion(it.word) }
                    WordFilter.VERB -> list.filter { it.word.partOfSpeech.contains("Động từ", ignoreCase = true) }
                    WordFilter.ADJECTIVE -> list.filter { it.word.partOfSpeech.contains("Tính từ", ignoreCase = true) }
                    WordFilter.NOUN -> list.filter { it.word.partOfSpeech.contains("Danh từ", ignoreCase = true) }
                    WordFilter.HIGH_FREQ -> list.filter { it.word.examFrequency.contains("★★★★★") || it.word.examFrequency.contains("Rất cao") }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bestMatch: StateFlow<MatchResult?> = searchResults.map { list ->
        list.firstOrNull { it.isBestMatch }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favoriteWords: StateFlow<List<WordEntity>> = repository.getFavoriteWords().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.getSearchHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val translationHistory: StateFlow<List<TranslationHistoryEntity>> = repository.getTranslationHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Detailed dialog selection
    private val _selectedWord = MutableStateFlow<WordEntity?>(null)
    val selectedWord = _selectedWord.asStateFlow()

    // Translation state
    private val _translationSource = MutableStateFlow("")
    val translationSource = _translationSource.asStateFlow()

    private val _translationResult = MutableStateFlow("")
    val translationResult = _translationResult.asStateFlow()

    private val _isFromKorean = MutableStateFlow(true)
    val isFromKorean = _isFromKorean.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating = _isTranslating.asStateFlow()

    private val _selectedTranslationEngine = MutableStateFlow(TranslationEngine.GOOGLE_TRANSLATE)
    val selectedTranslationEngine = _selectedTranslationEngine.asStateFlow()

    fun setTranslationEngine(engine: TranslationEngine) {
        _selectedTranslationEngine.value = engine
    }

    fun hasValidCloudAiProvider(): Boolean {
        return aiGateway.providers.value.filter { it.isEnabled }.any {
            it.apiKey.isNotBlank() || it.baseUrl.contains("localhost") || it.baseUrl.contains("10.0.2.2")
        }
    }

    // Flashcard state
    private val _flashcardFilter = MutableStateFlow(FlashcardFilter.ALL)
    val flashcardFilter = _flashcardFilter.asStateFlow()

    private val _flashcardIndex = MutableStateFlow(0)
    val flashcardIndex = _flashcardIndex.asStateFlow()

    private val _isCardFlipped = MutableStateFlow(false)
    val isCardFlipped = _isCardFlipped.asStateFlow()

    val flashcardDeck: StateFlow<List<WordEntity>> = combine(
        _flashcardFilter,
        _allWords,
        favoriteWords
    ) { filter, all, favs ->
        when (filter) {
            FlashcardFilter.ALL -> all
            FlashcardFilter.FAVORITES -> favs
            FlashcardFilter.NEED_REVIEW -> all.filter { !it.isMastered }
            FlashcardFilter.TOPIK_1 -> all.filter { it.topikLevelNumber == 1 }
            FlashcardFilter.TOPIK_2 -> all.filter { it.topikLevelNumber == 2 }
            FlashcardFilter.TOPIK_3 -> all.filter { it.topikLevelNumber == 3 }
            FlashcardFilter.TOPIK_4 -> all.filter { it.topikLevelNumber == 4 }
            FlashcardFilter.TOPIK_5 -> all.filter { it.topikLevelNumber == 5 }
            FlashcardFilter.TOPIK_6 -> all.filter { it.topikLevelNumber == 6 }
            FlashcardFilter.THEME_DAILY -> all.filter { WordThemeClassifier.matchesDaily(it) }
            FlashcardFilter.THEME_FOOD -> all.filter { WordThemeClassifier.matchesFood(it) }
            FlashcardFilter.THEME_WORK -> all.filter { WordThemeClassifier.matchesWork(it) }
            FlashcardFilter.THEME_EDUCATION -> all.filter { WordThemeClassifier.matchesEducation(it) }
            FlashcardFilter.THEME_TRAVEL -> all.filter { WordThemeClassifier.matchesTravel(it) }
            FlashcardFilter.THEME_SHOPPING -> all.filter { WordThemeClassifier.matchesShopping(it) }
            FlashcardFilter.THEME_HEALTH -> all.filter { WordThemeClassifier.matchesHealth(it) }
            FlashcardFilter.THEME_EMOTION -> all.filter { WordThemeClassifier.matchesEmotion(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // HÁN TỰ (HANJA ↔ HÁN-VIỆT) EXPLORER STATE
    // ==========================================
    private val _hanjaQuery = MutableStateFlow("")
    val hanjaQuery = _hanjaQuery.asStateFlow()

    val hanjaMorphemes: StateFlow<List<HanjaMorpheme>> = combine(
        _hanjaQuery,
        _allWords
    ) { query, allWords ->
        if (query.isBlank()) {
            HanjaHanVietEngine.getAllCoreMorphemes()
        } else {
            HanjaHanVietEngine.searchMorphemes(query, allWords)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HanjaHanVietEngine.getAllCoreMorphemes())

    fun onHanjaQueryChanged(newQuery: String) {
        _hanjaQuery.value = newQuery
    }

    // ==========================================
    // AI TUTOR & MULTI-PROVIDER GATEWAY STATE
    // ==========================================
    val aiProviders: StateFlow<List<AiProviderConfig>> = aiGateway.providers
    val lastAiDebugResponse: StateFlow<UnifiedAiResponse?> = aiGateway.lastDebugResponse

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking = _isAiThinking.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<ChatMessageUi>>(listOf(
        ChatMessageUi(
            role = "assistant",
            content = "Xin chào! Tôi là **VietKor AI Tutor** (Kiến trúc AI Hai Tầng).\n" +
                    "• **Tầng 1 (Local):** Tối ưu hóa prompt, kiểm chứng dữ liệu nội bộ 78.000 từ để tránh ảo giác.\n" +
                    "• **Tầng 2 (Cloud Gateway):** Hỗ trợ đa nhà cung cấp (Groq, OpenRouter, DeepSeek, Gemini, Claude, Ollama...).\n\n" +
                    "Bạn muốn hỏi về ngữ pháp, tra chữ Hán, hay luyện dịch câu gì hôm nay?"
        )
    ))
    val aiChatMessages = _aiChatMessages.asStateFlow()

    fun sendAiPrompt(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _isAiThinking.value) return

        val userMsg = ChatMessageUi(role = "user", content = trimmed)
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        viewModelScope.launch {
            _isAiThinking.value = true
            try {
                // 1. Gather local word candidates dynamically from 78,000 DB for Local RAG
                val extractedKeywords = extractKeywordsForDictionary(trimmed)
                val localCandidates = mutableListOf<WordEntity>()
                for (kw in extractedKeywords) {
                    val matches = repository.findCandidates(kw)
                    for (m in matches) {
                        if (localCandidates.none { it.id == m.id }) {
                            localCandidates.add(m)
                        }
                    }
                }
                if (localCandidates.isEmpty()) {
                    val fallback = repository.findCandidates(trimmed.take(30))
                    localCandidates.addAll(fallback)
                }
                if (localCandidates.isEmpty() && searchResults.value.isNotEmpty()) {
                    localCandidates.addAll(searchResults.value.map { it.word })
                }

                // Convert chat history to UnifiedAiMessage
                val history = _aiChatMessages.value.filter { it.role != "middleman" }.map {
                    UnifiedAiMessage(it.role, it.content)
                }

                val currentTier = _selectedAiTutorTier.value

                val response: UnifiedAiResponse = when (currentTier) {
                    AiTutorExecutionTier.TIER1_LOCAL -> {
                        // TIER 1: Process with LocalAiMiddleman and instant Local RAG
                        val decision = LocalAiMiddleman.processQuery(
                            userQuery = trimmed,
                            localWords = localCandidates,
                            chatHistory = history,
                            activeModelName = _activeLocalModel.value?.name
                        )
                        if (decision.canHandleOffline && decision.offlineAnswer != null) {
                            UnifiedAiResponse(
                                isSuccess = true,
                                content = decision.offlineAnswer,
                                providerName = "Tầng 1 Local (${_activeLocalModel.value?.name ?: "Heuristic"})",
                                modelName = _activeLocalModel.value?.name ?: "Heuristic RAG"
                            )
                        } else {
                            // Synthesize answer through Tier 1 local model
                            LocalTier2InferenceEngine.executeOfflineInference(
                                query = trimmed,
                                localWords = localCandidates,
                                modelFileName = _activeLocalModel.value?.fileName ?: "smollm2-135m-instruct-q4_k_m.gguf",
                                modelSizeMb = _activeLocalModel.value?.sizeMb ?: 100.0
                            )
                        }
                    }

                    AiTutorExecutionTier.TIER2_OFFLINE -> {
                        // TIER 2 OFFLINE: Run LocalTier2InferenceEngine directly with selected Tier 2 model without single-word truncation
                        val offlineModel = _activeTier2Model.value ?: _downloadedModels.value.firstOrNull { it.tier == 2 } ?: _activeLocalModel.value
                        val request = UnifiedAiRequest(
                            messages = listOf(UnifiedAiMessage("user", trimmed))
                        )
                        if (offlineModel != null) {
                            LocalTier2InferenceEngine.executeOfflineInference(request, offlineModel, localCandidates)
                        } else {
                            LocalTier2InferenceEngine.executeOfflineInference(
                                query = trimmed,
                                localWords = localCandidates,
                                modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                                modelSizeMb = 390.0
                            )
                        }
                    }

                    AiTutorExecutionTier.TIER2_CLOUD -> {
                        // TIER 2 CLOUD: Call Cloud Multi-Provider Gateway with automatic offline fallback
                        val decision = LocalAiMiddleman.processQuery(
                            userQuery = trimmed,
                            localWords = localCandidates,
                            chatHistory = history,
                            activeModelName = _activeLocalModel.value?.name
                        )
                        val request = decision.optimizedRequest ?: UnifiedAiRequest(
                            messages = listOf(UnifiedAiMessage("user", trimmed))
                        )
                        val onlineRes = aiGateway.executeWithFallback(request)
                        if (!onlineRes.isSuccess) {
                            val offlineModel = _activeTier2Model.value ?: _downloadedModels.value.firstOrNull { it.tier == 2 } ?: _activeLocalModel.value
                            if (offlineModel != null) {
                                val fallbackRes = LocalTier2InferenceEngine.executeOfflineInference(request, offlineModel, localCandidates)
                                fallbackRes.copy(content = "⚠️ *Không có kết nối đám mây, tự động chuyển sang mô hình ngoại tuyến Tầng 2:*\n\n" + fallbackRes.content)
                            } else {
                                val localFallback = LocalTier2InferenceEngine.executeOfflineInference(
                                    query = trimmed,
                                    localWords = localCandidates,
                                    modelFileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                                    modelSizeMb = 390.0
                                )
                                localFallback.copy(content = "⚠️ *Chưa cấu hình API Key đám mây, tự động giải đáp bằng mô hình ngoại tuyến:*\n\n" + localFallback.content)
                            }
                        } else {
                            onlineRes
                        }
                    }
                }

                val replyMsg = if (response.isSuccess) {
                    ChatMessageUi(
                        role = "assistant",
                        content = response.content,
                        isOfflineHandled = currentTier != AiTutorExecutionTier.TIER2_CLOUD || response.providerName.contains("Offline") || response.providerName.contains("Tầng 1"),
                        debugInfo = response
                    )
                } else {
                    ChatMessageUi(
                        role = "assistant",
                        content = "⚠️ ${response.errorMessage ?: "Không thể kết nối đến AI"}\n\n*Gợi ý:* Hãy vào **Cài đặt AI** để kiểm tra API Key hoặc chọn mô hình ngoại tuyến.",
                        isOfflineHandled = false,
                        debugInfo = response
                    )
                }
                _aiChatMessages.value = _aiChatMessages.value + replyMsg
            } catch (e: Exception) {
                _aiChatMessages.value = _aiChatMessages.value + ChatMessageUi(
                    role = "assistant",
                    content = "⚠️ Đã xảy ra lỗi không mong muốn: ${e.localizedMessage ?: e.message}",
                    isOfflineHandled = false
                )
            } finally {
                _isAiThinking.value = false
            }
        }
    }

    private fun extractKeywordsForDictionary(prompt: String): List<String> {
        val keywords = mutableListOf<String>()
        val hangulRegex = Regex("[가-힣]+")
        hangulRegex.findAll(prompt).forEach { match ->
            val word = match.value.trim()
            if (word.isNotEmpty()) {
                keywords.add(word)
            }
        }

        val cleanPrompt = prompt
            .replace(Regex("(?i)\\b(dịch|dịch câu|translate|nghĩa là gì|phân tích|ngữ pháp|cấu trúc|so sánh|khác nhau|là gì|thế nào|cách dùng|cho tôi|giúp tôi|hãy|từ|chữ|trong|tiếng hàn|tiếng việt)\\b"), " ")
            .trim()

        val tokens = cleanPrompt.split(Regex("[\\s,?.!;:\"'()]+")).filter { it.length >= 2 }
        if (tokens.isNotEmpty()) {
            keywords.add(tokens.joinToString(" ").take(40))

            // 2-gram compound Vietnamese words (e.g. "chào hỏi", "văn hoá", "tâm trạng", "học tập")
            if (tokens.size >= 2) {
                tokens.windowed(2, 1).forEach { pair ->
                    keywords.add(pair.joinToString(" "))
                }
            }

            for (t in tokens.take(8)) {
                if (!keywords.contains(t) && t !in listOf("rồi", "xong", "hoặc", "và", "nhưng", "thì")) {
                    keywords.add(t)
                }
            }
        }

        return keywords.distinct()
    }

    fun clearAiChat() {
        _aiChatMessages.value = listOf(
            ChatMessageUi(
                role = "assistant",
                content = "Đã làm mới cuộc hội thoại. Hãy đặt câu hỏi bất kỳ về tiếng Hàn hoặc ngữ pháp!"
            )
        )
    }

    fun saveProvider(config: AiProviderConfig) {
        aiGateway.addOrUpdateProvider(config)
    }

    fun deleteProvider(id: String) {
        aiGateway.deleteProvider(id)
    }

    fun setDefaultProvider(id: String) {
        aiGateway.setDefaultProvider(id)
    }

    fun testProviderConnection(config: AiProviderConfig, onResult: (UnifiedAiResponse) -> Unit) {
        viewModelScope.launch {
            val res = aiGateway.testConnection(config)
            onResult(res)
        }
    }

    fun resetProvidersToDefaults() {
        aiGateway.resetToDefaults()
    }

    // ==========================================
    // STORAGE & CACHE MANAGER STATE
    // ==========================================
    private val _storageReport = MutableStateFlow(StorageManager.getStorageReport(application))
    val storageReport = _storageReport.asStateFlow()

    // ==========================================
    // ON-DEVICE LOCAL AI MODELS (TIER 1 & TIER 2)
    // ==========================================
    private val _downloadedModels = MutableStateFlow<List<LocalModelInfo>>(AiModelManager.getDownloadedModels(application))
    val downloadedModels = _downloadedModels.asStateFlow()

    private val _activeLocalModel = MutableStateFlow<LocalModelInfo?>(AiModelManager.getActiveTier1Model(application))
    val activeLocalModel = _activeLocalModel.asStateFlow()

    private val _activeTier2Model = MutableStateFlow<LocalModelInfo?>(AiModelManager.getActiveTier2Model(application))
    val activeTier2Model = _activeTier2Model.asStateFlow()

    private val _tier2ExecutionMode = MutableStateFlow(AiModelManager.getTier2ExecutionMode(application))
    val tier2ExecutionMode = _tier2ExecutionMode.asStateFlow()

    private val _selectedAiTutorTier = MutableStateFlow(
        if (_tier2ExecutionMode.value == Tier2ExecutionMode.OFFLINE_LOCAL) AiTutorExecutionTier.TIER2_OFFLINE
        else AiTutorExecutionTier.TIER2_CLOUD
    )
    val selectedAiTutorTier = _selectedAiTutorTier.asStateFlow()

    fun setAiTutorExecutionTier(tier: AiTutorExecutionTier) {
        _selectedAiTutorTier.value = tier
        when (tier) {
            AiTutorExecutionTier.TIER1_LOCAL -> {
                // Tier 1 focuses on local heuristic & lightweight RAG
            }
            AiTutorExecutionTier.TIER2_OFFLINE -> {
                setTier2ExecutionMode(Tier2ExecutionMode.OFFLINE_LOCAL)
            }
            AiTutorExecutionTier.TIER2_CLOUD -> {
                setTier2ExecutionMode(Tier2ExecutionMode.ONLINE_CLOUD)
            }
        }
    }

    private val suggestionPools = listOf(
        "Dịch câu: học chào hỏi rồi học văn hoá",
        "Phân biệt ngữ pháp -는데 và -지만",
        "Phân tích chiết tự Hán-Hàn từ 'Học sinh' (학생)",
        "Cách dùng cấu trúc -고 나서 và liên từ -고",
        "Dịch câu: Hôm nay tôi muốn học tiếng Hàn để đi du học",
        "Kính ngữ -(으)시- và các từ vựng kính ngữ đặc biệt",
        "Hán tự chữ 'Văn hoá' (문화 - 文化) có ý nghĩa gì?",
        "So sánh ngữ pháp -아/어서 và -(으)니까",
        "Dịch câu: Ăn cơm xong rồi uống cà phê",
        "Topik 2: Cách chia thì quá khứ và tương lai",
        "Phân biệt tiểu từ chủ ngữ 이/가 và tiểu từ chủ đề 은/는",
        "Hội thoại: Cách gọi món trong quán ăn Hàn Quốc"
    )

    private val _dynamicSuggestions = MutableStateFlow(suggestionPools.shuffled().take(4))
    val dynamicSuggestions = _dynamicSuggestions.asStateFlow()

    fun refreshDynamicSuggestions() {
        val pool = suggestionPools.toMutableList()
        val recent = searchHistory.value.firstOrNull()?.query
        if (!recent.isNullOrBlank()) {
            pool.add(0, "Dịch câu & phân tích ngữ pháp liên quan đến: $recent")
        }
        _dynamicSuggestions.value = pool.shuffled().take(4)
    }

    val modelDownloadState: StateFlow<DownloadState> = AiModelManager.downloadState

    fun refreshDownloadedModels() {
        val app = getApplication<Application>()
        _downloadedModels.value = AiModelManager.getDownloadedModels(app)
        _activeLocalModel.value = AiModelManager.getActiveTier1Model(app)
        _activeTier2Model.value = AiModelManager.getActiveTier2Model(app)
        _tier2ExecutionMode.value = AiModelManager.getTier2ExecutionMode(app)
        refreshStorageReport()
    }

    fun setTier2ExecutionMode(mode: Tier2ExecutionMode) {
        val app = getApplication<Application>()
        AiModelManager.setTier2ExecutionMode(app, mode)
        _tier2ExecutionMode.value = mode
    }

    fun setActiveTier2Model(fileName: String?) {
        val app = getApplication<Application>()
        AiModelManager.setActiveTier2Model(app, fileName)
        if (fileName != null) {
            setTier2ExecutionMode(Tier2ExecutionMode.OFFLINE_LOCAL)
        }
        refreshDownloadedModels()
    }

    fun downloadModelPreset(preset: ModelPreset) {
        viewModelScope.launch {
            val res = AiModelManager.downloadPreset(getApplication(), preset)
            if (res.isSuccess && preset.tier == 2) {
                setTier2ExecutionMode(Tier2ExecutionMode.OFFLINE_LOCAL)
            }
            refreshDownloadedModels()
        }
    }

    fun downloadCustomModel(url: String, fileName: String, tier: Int = 1) {
        viewModelScope.launch {
            val res = AiModelManager.downloadCustomUrl(getApplication(), url, fileName, tier)
            if (res.isSuccess && tier == 2) {
                setTier2ExecutionMode(Tier2ExecutionMode.OFFLINE_LOCAL)
            }
            refreshDownloadedModels()
        }
    }

    fun cancelModelDownload() {
        AiModelManager.cancelDownload()
    }

    fun resetModelDownloadState() {
        AiModelManager.resetDownloadState()
    }

    fun deleteLocalModel(fileName: String) {
        AiModelManager.deleteModel(getApplication(), fileName)
        refreshDownloadedModels()
    }

    fun setCustomModelTier(fileName: String, tier: Int) {
        AiModelManager.setCustomModelTier(getApplication(), fileName, tier)
        refreshDownloadedModels()
    }

    fun setActiveLocalModel(fileName: String?) {
        val app = getApplication<Application>()
        AiModelManager.setActiveTier1Model(app, fileName)
        refreshDownloadedModels()
    }

    fun importLocalModel(uri: Uri, name: String, tier: Int = 1) {
        viewModelScope.launch {
            AiModelManager.importFromUri(getApplication(), uri, name, tier)
            refreshDownloadedModels()
        }
    }

    fun refreshStorageReport() {
        _storageReport.value = StorageManager.getStorageReport(getApplication())
    }

    fun cleanStorageCache() {
        StorageManager.cleanCache(getApplication())
        refreshStorageReport()
    }

    // Core dictionary & search actions
    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        _displayLimit.value = 30
    }

    fun onFilterSelected(filter: WordFilter) {
        _selectedFilter.value = filter
        _displayLimit.value = 30
    }

    fun openWordDetail(word: WordEntity) {
        _selectedWord.value = word
        viewModelScope.launch {
            repository.addSearchHistory(_searchQuery.value.ifBlank { word.korean }, word)
        }
    }

    fun closeWordDetail() {
        _selectedWord.value = null
    }

    fun toggleFavorite(word: WordEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(word.id, word.isFavorite)
            if (_selectedWord.value?.id == word.id) {
                _selectedWord.value = _selectedWord.value?.copy(isFavorite = !word.isFavorite)
            }
        }
    }

    // Translation methods
    fun setTranslationSource(text: String) {
        _translationSource.value = text
    }

    fun swapLanguages() {
        _isFromKorean.value = !_isFromKorean.value
        val oldResult = _translationResult.value
        _translationResult.value = ""
        _translationSource.value = oldResult
    }

    fun performTranslation() {
        val src = _translationSource.value.trim()
        if (src.isEmpty()) return

        val engine = _selectedTranslationEngine.value

        // Kiểm tra yêu cầu hợp lệ đối với AI Model lớn (Online)
        if (engine == TranslationEngine.CLOUD_AI && !hasValidCloudAiProvider()) {
            _translationResult.value = "⚠️ Yêu cầu chưa hợp lệ:\n\n" +
                    "Bạn đã chọn chế độ dịch 'AI Model lớn hơn (Online)', nhưng chưa có nhà cung cấp AI nào (như Groq, OpenRouter, DeepSeek, Gemini...) được cấu hình API Key hợp lệ.\n\n" +
                    "👉 Vui lòng nhấn vào biểu tượng bánh răng 'Cài đặt AI' để nhập API Key hợp lệ trước khi sử dụng."
            return
        }

        viewModelScope.launch {
            _isTranslating.value = true
            try {
                val res = when (engine) {
                    TranslationEngine.GOOGLE_TRANSLATE -> {
                        BilingualTranslationService.translateWithGoogle(src, _isFromKorean.value)
                    }
                    TranslationEngine.DEEPL -> {
                        BilingualTranslationService.translateWithDeepL(src, _isFromKorean.value)
                    }
                    TranslationEngine.LOCAL_MODEL -> {
                        val activeModel = _activeTier2Model.value?.fileName ?: "qwen2.5-0.5b-instruct-q4_k_m.gguf"
                        BilingualTranslationService.translateWithLocalModel(
                            src,
                            _isFromKorean.value,
                            allWordsList.value,
                            activeModel
                        )
                    }
                    TranslationEngine.CLOUD_AI -> {
                        BilingualTranslationService.translateWithCloudAi(
                            src,
                            _isFromKorean.value,
                            aiGateway
                        )
                    }
                }
                _translationResult.value = res

                val fromLang = if (_isFromKorean.value) "Tiếng Hàn" else "Tiếng Việt"
                val toLang = if (_isFromKorean.value) "Tiếng Việt" else "Tiếng Hàn"
                repository.saveTranslation(src, res, fromLang, toLang)
            } catch (e: Exception) {
                try {
                    val offlineFallback = repository.translateOffline(src, _isFromKorean.value)
                    _translationResult.value = "$offlineFallback\n\n*(Lưu ý: Không thể kết nối tới ${engine.title}, hệ thống đã chuyển sang bản dịch từ điển ngoại tuyến)*"
                } catch (_: Exception) {
                    _translationResult.value = "⚠️ Lỗi dịch: ${e.localizedMessage ?: e.message}"
                }
            } finally {
                _isTranslating.value = false
            }
        }
    }

    fun clearTranslation() {
        _translationSource.value = ""
        _translationResult.value = ""
    }

    // Flashcard methods
    fun setFlashcardFilter(filter: FlashcardFilter) {
        _flashcardFilter.value = filter
        _flashcardIndex.value = 0
        _isCardFlipped.value = false
    }

    fun flipCard() {
        _isCardFlipped.value = !_isCardFlipped.value
    }

    fun nextCard() {
        val deck = flashcardDeck.value
        if (deck.isNotEmpty()) {
            _flashcardIndex.value = (_flashcardIndex.value + 1) % deck.size
            _isCardFlipped.value = false
        }
    }

    fun previousCard() {
        val deck = flashcardDeck.value
        if (deck.isNotEmpty()) {
            _flashcardIndex.value = if (_flashcardIndex.value > 0) _flashcardIndex.value - 1 else deck.size - 1
            _isCardFlipped.value = false
        }
    }

    fun markCard(word: WordEntity, isMastered: Boolean) {
        viewModelScope.launch {
            repository.markReviewed(word.id, isMastered)
            nextCard()
        }
    }

    // Audio TTS
    fun speakKorean(text: String) {
        ttsManager.speakKorean(text)
    }

    fun speakVietnamese(text: String) {
        ttsManager.speakVietnamese(text)
    }

    // History cleaning
    fun deleteSearchHistory(id: Long) {
        viewModelScope.launch { repository.deleteSearchHistoryItem(id) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    fun deleteTranslationHistory(id: Long) {
        viewModelScope.launch { repository.deleteTranslationHistoryItem(id) }
    }

    fun clearTranslationHistory() {
        viewModelScope.launch { repository.clearTranslationHistory() }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
