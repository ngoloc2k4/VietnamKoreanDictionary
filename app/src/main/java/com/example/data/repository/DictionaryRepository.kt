package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.initial.PreloadedWords
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.TranslationHistoryEntity
import com.example.data.model.WordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryRepository(context: Context) {
    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_REGEX = Regex("[,.!?~]")
    }

    private val db = AppDatabase.getDatabase(context)
    private val wordDao = db.wordDao()
    private val searchHistoryDao = db.searchHistoryDao()
    private val translationHistoryDao = db.translationHistoryDao()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            if (wordDao.getCount() == 0) {
                wordDao.insertWords(PreloadedWords.getInitialWords())
            }
        }
    }

    fun getAllWords(): Flow<List<WordEntity>> = wordDao.getAllWords()

    fun searchWords(query: String): Flow<List<WordEntity>> {
        return if (query.isBlank()) {
            wordDao.getAllWords()
        } else {
            wordDao.searchWords(query.trim())
        }
    }

    suspend fun findCandidates(query: String): List<WordEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList() else wordDao.findCandidates(query.trim())
    }

    fun getFavoriteWords(): Flow<List<WordEntity>> = wordDao.getFavoriteWords()

    fun getUnmasteredWords(): Flow<List<WordEntity>> = wordDao.getUnmasteredWords()

    fun getWordsByLevel(level: String): Flow<List<WordEntity>> = wordDao.getWordsByLevel(level)

    fun getTotalWordCount(): Flow<Int> = wordDao.getTotalCountFlow()

    suspend fun getWordById(id: Long): WordEntity? = withContext(Dispatchers.IO) {
        wordDao.getWordById(id)
    }

    suspend fun toggleFavorite(id: Long, currentVal: Boolean) = withContext(Dispatchers.IO) {
        wordDao.updateFavorite(id, !currentVal)
    }

    suspend fun markReviewed(id: Long, isMastered: Boolean) = withContext(Dispatchers.IO) {
        wordDao.markReviewed(id, isMastered)
    }

    // Search History
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>> = searchHistoryDao.getAllHistory()

    suspend fun addSearchHistory(query: String, word: WordEntity) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertHistory(
                SearchHistoryEntity(
                    query = query.trim(),
                    koreanResult = word.korean,
                    vietnameseResult = word.vietnamese
                )
            )
        }
    }

    suspend fun deleteSearchHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteById(id)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAll()
    }

    // Translation History
    fun getTranslationHistory(): Flow<List<TranslationHistoryEntity>> = translationHistoryDao.getAllTranslations()

    suspend fun saveTranslation(sourceText: String, translatedText: String, fromLang: String, toLang: String) = withContext(Dispatchers.IO) {
        if (sourceText.isNotBlank() && translatedText.isNotBlank()) {
            translationHistoryDao.insertTranslation(
                TranslationHistoryEntity(
                    sourceText = sourceText.trim(),
                    translatedText = translatedText.trim(),
                    sourceLang = fromLang,
                    targetLang = toLang
                )
            )
        }
    }

    suspend fun deleteTranslationHistoryItem(id: Long) = withContext(Dispatchers.IO) {
        translationHistoryDao.deleteById(id)
    }

    suspend fun clearTranslationHistory() = withContext(Dispatchers.IO) {
        translationHistoryDao.clearAll()
    }

    // Smart Offline Translation Engine
    suspend fun translateOffline(text: String, fromKorean: Boolean): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""

        // 1. Exact match check
        if (fromKorean) {
            val direct = wordDao.getWordByKorean(trimmed)
                ?: wordDao.searchWords(trimmed).first().firstOrNull {
                    it.korean.equals(trimmed, ignoreCase = true) ||
                    it.rootForm.equals(trimmed, ignoreCase = true) ||
                    it.presentTense.contains(trimmed) ||
                    it.pastTense.contains(trimmed) ||
                    it.politeLife.equals(trimmed)
                }
            if (direct != null) {
                return@withContext direct.vietnamese + " (" + direct.pronunciation + ")"
            }
        } else {
            val direct = wordDao.findFirstMatchingVietnamese(trimmed)
                ?: wordDao.searchWords(trimmed).first().firstOrNull {
                    it.vietnamese.contains(trimmed, ignoreCase = true) ||
                    it.hanViet.contains(trimmed, ignoreCase = true)
                }
            if (direct != null) {
                return@withContext direct.korean + " (" + direct.pronunciation + " - " + direct.vietnamese + ")"
            }
        }

        // 2. Tokenized & Phrase mapping
        val tokens = trimmed.split(WHITESPACE_REGEX)
        val translatedParts = mutableListOf<String>()

        if (fromKorean) {
            for (token in tokens) {
                // Strip punctuation
                val clean = token.replace(PUNCTUATION_REGEX, "")
                val found = wordDao.getWordByKorean(clean)
                    ?: wordDao.searchWords(clean).first().firstOrNull {
                        it.korean == clean ||
                        clean.startsWith(it.korean.removeSuffix("다")) ||
                        it.presentTense.contains(clean) ||
                        it.pastTense.contains(clean)
                    }
                if (found != null) {
                    val primaryMeaning = found.vietnamese.split(",")[0].trim()
                    translatedParts.add(primaryMeaning)
                } else {
                    // Try basic particle / ending replacements
                    val translatedToken = when {
                        clean.endsWith("은") || clean.endsWith("는") -> clean.dropLast(1) + " (chủ ngữ)"
                        clean.endsWith("이") || clean.endsWith("가") -> clean.dropLast(1)
                        clean.endsWith("을") || clean.endsWith("를") -> clean.dropLast(1)
                        clean.endsWith("에서") -> clean.dropLast(2) + " ở"
                        clean.endsWith("에") -> clean.dropLast(1) + " đến/ở"
                        clean.endsWith("과") || clean.endsWith("와") -> clean.dropLast(1) + " và"
                        clean.endsWith("도") -> clean.dropLast(1) + " cũng"
                        clean == "안녕" || clean == "안녕하세요" -> "Xin chào"
                        clean == "감사합니다" || clean == "고마워요" -> "Cảm ơn bạn"
                        clean == "죄송합니다" || clean == "미안해요" -> "Xin lỗi"
                        clean == "네" -> "Vâng / Đúng vậy"
                        clean == "아니요" -> "Không phải"
                        else -> token
                    }
                    translatedParts.add(translatedToken)
                }
            }
            return@withContext translatedParts.joinToString(" ")
        } else {
            for (token in tokens) {
                val clean = token.lowercase().replace(PUNCTUATION_REGEX, "")
                val found = wordDao.findFirstMatchingVietnamese(clean)
                    ?: wordDao.searchWords(clean).first().firstOrNull {
                        it.vietnamese.lowercase().contains(clean) || it.hanViet.lowercase() == clean
                    }
                if (found != null) {
                    translatedParts.add(found.korean)
                } else {
                    val quickKr = when (clean) {
                        "xin chào", "chào" -> "안녕하세요"
                        "cảm ơn" -> "감사합니다"
                        "tạm biệt" -> "안녕히 가세요"
                        "tôi" -> "저"
                        "bạn" -> "당신"
                        "học" -> "공부하다"
                        "yêu" -> "사랑하다"
                        "đi" -> "가다"
                        "ăn" -> "먹다"
                        "uống" -> "마시다"
                        "xem", "nhìn" -> "보다"
                        "nghe" -> "듣다"
                        "nói" -> "말하다"
                        "nhà" -> "집"
                        "trường", "trường học" -> "학교"
                        "bạn bè" -> "친구"
                        "hạnh phúc" -> "행복"
                        "rất" -> "아주"
                        "nhiều" -> "많이"
                        else -> token
                    }
                    translatedParts.add(quickKr)
                }
            }
            return@withContext translatedParts.joinToString(" ")
        }
    }
}
