package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.gateway.*
import com.example.ai.tier1.LocalAiMiddleman
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.TranslationHistoryEntity
import com.example.data.model.WordEntity
import com.example.data.repository.DictionaryRepository
import com.example.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class WordFilter(val label: String) {
    ALL("Tất cả"),
    VERB("Động từ"),
    ADJECTIVE("Tính từ"),
    NOUN("Danh từ"),
    TOPIK_1("TOPIK I"),
    TOPIK_2("TOPIK II"),
    HIGH_FREQ("Tần suất cao ★")
}

enum class FlashcardFilter(val label: String) {
    ALL("Tất cả"),
    FAVORITES("Yêu thích"),
    NEED_REVIEW("Cần ôn tập"),
    TOPIK_1("TOPIK I"),
    TOPIK_2("TOPIK II")
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
                    WordFilter.VERB -> list.filter { it.word.partOfSpeech.contains("Động từ", ignoreCase = true) }
                    WordFilter.ADJECTIVE -> list.filter { it.word.partOfSpeech.contains("Tính từ", ignoreCase = true) }
                    WordFilter.NOUN -> list.filter { it.word.partOfSpeech.contains("Danh từ", ignoreCase = true) }
                    WordFilter.TOPIK_1 -> list.filter { it.word.level.contains("TOPIK I", ignoreCase = true) }
                    WordFilter.TOPIK_2 -> list.filter { it.word.level.contains("TOPIK II", ignoreCase = true) }
                    WordFilter.HIGH_FREQ -> list.filter { it.word.examFrequency.contains("★★★★★") }
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
            FlashcardFilter.TOPIK_1 -> all.filter { it.level.contains("TOPIK I") }
            FlashcardFilter.TOPIK_2 -> all.filter { it.level.contains("TOPIK II") }
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
                // Gather local word candidates for Local RAG
                val localCandidates = searchResults.value.map { it.word }

                // Convert chat history to UnifiedAiMessage
                val history = _aiChatMessages.value.filter { it.role != "middleman" }.map {
                    UnifiedAiMessage(it.role, it.content)
                }

                // TIER 1: Embedded AI Middleman processing
                val decision = LocalAiMiddleman.processQuery(trimmed, localCandidates, history)

                if (decision.canHandleOffline && decision.offlineAnswer != null) {
                    // Handled 100% locally: Zero tokens, instant response!
                    val offlineMsg = ChatMessageUi(
                        role = "assistant",
                        content = decision.offlineAnswer,
                        isOfflineHandled = true
                    )
                    _aiChatMessages.value = _aiChatMessages.value + offlineMsg
                } else {
                    // TIER 2: Call Multi-Provider Cloud Gateway with optimized RAG request
                    val request = decision.optimizedRequest ?: UnifiedAiRequest(
                        messages = listOf(UnifiedAiMessage("user", trimmed))
                    )

                    val response = aiGateway.executeWithFallback(request)

                    val replyMsg = if (response.isSuccess) {
                        ChatMessageUi(
                            role = "assistant",
                            content = response.content,
                            isOfflineHandled = false,
                            debugInfo = response
                        )
                    } else {
                        ChatMessageUi(
                            role = "assistant",
                            content = "⚠️ ${response.errorMessage ?: "Không thể kết nối đến AI"}\n\n*Gợi ý:* Hãy vào **Cài đặt AI** để kiểm tra API Key hoặc chọn provider dự phòng.",
                            isOfflineHandled = false,
                            debugInfo = response
                        )
                    }
                    _aiChatMessages.value = _aiChatMessages.value + replyMsg
                }
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

        viewModelScope.launch {
            _isTranslating.value = true
            try {
                val res = repository.translateOffline(src, _isFromKorean.value)
                _translationResult.value = res

                val fromLang = if (_isFromKorean.value) "Tiếng Hàn" else "Tiếng Việt"
                val toLang = if (_isFromKorean.value) "Tiếng Việt" else "Tiếng Hàn"
                repository.saveTranslation(src, res, fromLang, toLang)
            } catch (e: Exception) {
                _translationResult.value = "⚠️ Lỗi dịch: ${e.localizedMessage ?: e.message}"
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
