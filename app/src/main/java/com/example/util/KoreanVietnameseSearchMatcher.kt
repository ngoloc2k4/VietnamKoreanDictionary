package com.example.util

import com.example.data.model.WordEntity
import java.text.Normalizer
import java.util.regex.Pattern

data class MatchResult(
    val word: WordEntity,
    val score: Int,
    val matchReason: String,
    val isBestMatch: Boolean = false
)

object KoreanVietnameseSearchMatcher {

    // Korean Chosung consonants
    private val CHOSUNG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
        'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    // Common Korean verb/adjective suffixes to stem
    private val KOREAN_ENDINGS = listOf(
        "하십니다", "하셨습니다", "하세요", "하셨어요", "하시다",
        "합니다", "합니까", "했습니다", "했어요", "할게요", "할거예요", "할 거예요",
        "해요", "하고", "하면", "해서", "하니", "하러",
        "습니다", "습니까", "었어요", "았어요", "셨어요", "십니다",
        "어요", "아요", "여요", "었다", "았다", "렸다", "렸다",
        "을게요", "ㄹ게요", "을거예요", "ㄹ거예요", "을 거예요", "ㄹ 거예요",
        "으면", "면", "어서", "아서", "어서요", "아서요", "으니", "니까",
        "은", "는", "을", "를", "고", "지만", "네", "지", "죠"
    )

    // Precompiled Regexes to avoid high-frequency allocations in tight loops
    private val COMMA_SEMICOLON_REGEX = Regex("[,;]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val VIET_DELIMITERS_REGEX = Regex("[,;\\s/]+")

    /**
     * Remove Vietnamese accents / diacritics
     * e.g. "Hạnh phúc" -> "hanh phuc", "Động từ" -> "dong tu", "Tiên sinh" -> "tien sinh"
     */
    fun removeVietnameseAccents(str: String): String {
        if (str.isBlank()) return ""
        var nfd = Normalizer.normalize(str, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        nfd = pattern.matcher(nfd).replaceAll("")
        return nfd.replace('đ', 'd').replace('Đ', 'D')
            .replace('đ', 'd').replace('Đ', 'd')
            .lowercase()
            .trim()
    }

    /**
     * Extract Korean Initial Consonants (Chosung 초성)
     * e.g. "공부하다" -> "ㄱㅂㅎㄷ", "가다" -> "ㄱㄷ"
     */
    fun extractChosung(text: String): String {
        val result = StringBuilder()
        for (ch in text) {
            if (ch in '\uAC00'..'\uD7A3') {
                val syllableIndex = ch.code - 0xAC00
                val chosungIndex = syllableIndex / (21 * 28)
                result.append(CHOSUNG[chosungIndex])
            } else if (isHangulJamo(ch)) {
                result.append(ch)
            } else if (!ch.isWhitespace()) {
                result.append(ch)
            }
        }
        return result.toString()
    }

    fun isHangulJamo(ch: Char): Boolean {
        return ch in '\u3131'..'\u318E'
    }

    fun isAllChosung(query: String): Boolean {
        if (query.isBlank()) return false
        return query.all { isHangulJamo(it) || it.isWhitespace() }
    }

    /**
     * Normalize Korean verb form to likely root candidates
     * e.g. "가요" -> "가다", "갔어요" -> "가다", "공부해요" -> "공부하다", "어려워요" -> "어렵다"
     */
    fun extractKoreanStemCandidates(query: String): List<String> {
        val clean = query.trim()
        val candidates = mutableSetOf(clean)

        for (ending in KOREAN_ENDINGS) {
            if (clean.endsWith(ending) && clean.length > ending.length) {
                val stem = clean.substring(0, clean.length - ending.length)
                candidates.add(stem)
                candidates.add(stem + "다")
                candidates.add(stem + "하다")
                // Handle irregulars
                if (stem.endsWith("워")) {
                    candidates.add(stem.dropLast(1) + "ㅂ다")
                    candidates.add(stem.dropLast(1) + "웁다")
                }
                if (stem.endsWith("들")) {
                    candidates.add(stem.dropLast(1) + "듣다")
                }
                if (stem.endsWith("갔")) {
                    candidates.add("가다")
                }
                if (stem.endsWith("봤")) {
                    candidates.add("보다")
                }
                if (stem.endsWith("먹었")) {
                    candidates.add("먹다")
                }
            }
        }

        // Common irregular special conjugations
        when (clean) {
            "가", "가요", "갔어", "갔어요", "갑니다", "갈게요", "갈거예요" -> candidates.add("가다")
            "봐", "봐요", "봤어", "봤어요", "봅니다", "볼게요", "볼거예요" -> candidates.add("보다")
            "먹어", "먹어요", "먹었어", "먹었어요", "먹습니다", "드세요", "드셔" -> candidates.add("먹다")
            "들어", "들어요", "들었어", "들었어요", "듣습니다" -> candidates.add("듣다")
            "써", "써요", "썼어", "썼어요", "씁니다" -> candidates.add("쓰다")
            "어려워", "어려워요", "어려웠어", "어려웠어요", "어렵습니다", "어려운" -> candidates.add("어렵다")
            "쉬워", "쉬워요", "쉬웠어", "쉬웠어요", "쉽습니다", "쉬운" -> candidates.add("쉽다")
            "커", "커요", "컸어", "컸어요", "큽니다", "큰" -> candidates.add("크다")
            "작아", "작아요", "작았어", "작았어요", "작습니다", "작은" -> candidates.add("작다")
            "좋아", "좋아요", "좋았어", "좋았어요", "좋습니다", "좋은" -> candidates.add("좋다")
        }

        return candidates.toList()
    }

    /**
     * Levenshtein Distance for fuzzy approximate match
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun similarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    /**
     * Score a word against the user's query
     */
    fun scoreWord(word: WordEntity, query: String): MatchResult? {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) return MatchResult(word, 0, "")

        val qLower = rawQuery.lowercase()
        val qNoAccent = removeVietnameseAccents(rawQuery)

        val korLower = word.korean.lowercase()
        val rootLower = word.rootForm.lowercase()
        val vietLower = word.vietnamese.lowercase()
        val vietNoAccent = removeVietnameseAccents(word.vietnamese)
        val defNoAccent = removeVietnameseAccents(word.definition)
        val hanVietNoAccent = removeVietnameseAccents(word.hanViet)
        val hanjaLower = word.hanja.lowercase()
        val pronLower = word.pronunciation.lowercase().replace("[", "").replace("]", "")

        // 1. Exact matches (Korean / Vietnamese)
        if (word.korean == rawQuery || word.rootForm == rawQuery) {
            return MatchResult(word, 100, "Khớp chính xác 100%")
        }

        // 2. Korean Conjugated Form Match (e.g. user typed "가요", "공부해요", "어려워요")
        val stemCandidates = extractKoreanStemCandidates(rawQuery)
        for (candidate in stemCandidates) {
            if (candidate == word.korean || candidate == word.rootForm || word.korean.startsWith(candidate)) {
                return MatchResult(word, 95, "Khớp dạng chia thì: $rawQuery → ${word.korean}")
            }
        }
        if (word.presentTense.contains(rawQuery) || word.pastTense.contains(rawQuery) ||
            word.futureTense.contains(rawQuery) || word.formalHigh.contains(rawQuery) ||
            word.politeLife.contains(rawQuery) || word.informal.contains(rawQuery) ||
            word.honorificSubject.contains(rawQuery)) {
            return MatchResult(word, 92, "Khớp dạng biến chia kính ngữ / thì")
        }

        // 3. Unaccented Vietnamese Exact / Leading Match (gõ không dấu chuẩn xác)
        val primaryVietNoAccent = vietNoAccent.split(COMMA_SEMICOLON_REGEX)[0].trim()
        if (primaryVietNoAccent == qNoAccent) {
            return MatchResult(word, 96, "Khớp nghĩa chính xác không dấu: \"$qNoAccent\"")
        }
        val primaryTokens = primaryVietNoAccent.split(WHITESPACE_REGEX)
        if (primaryTokens.contains(qNoAccent)) {
            val isFirstToken = primaryTokens.firstOrNull() == qNoAccent
            val score = if (isFirstToken) 94 else 91
            return MatchResult(word, score, "Khớp từ nghĩa chính không dấu: \"$qNoAccent\"")
        }

        val vietWords = vietNoAccent.split(VIET_DELIMITERS_REGEX).filter { it.isNotBlank() }
        if (vietWords.any { it == qNoAccent } || vietNoAccent == qNoAccent) {
            val isFirst = vietWords.firstOrNull() == qNoAccent
            val score = if (isFirst) 90 else 88
            return MatchResult(word, score, "Khớp từ tiếng Việt không dấu: \"$qNoAccent\"")
        }
        if (hanVietNoAccent.isNotBlank() && (hanVietNoAccent == qNoAccent || hanVietNoAccent.split(" ").contains(qNoAccent))) {
            return MatchResult(word, 88, "Khớp âm Hán-Việt không dấu: ${word.hanViet}")
        }

        // 4. Korean Initial Consonants (Chosung 초성 검색: e.g. "ㄱㅂ" -> "공부하다")
        if (isAllChosung(rawQuery)) {
            val wordChosung = extractChosung(word.korean)
            val cleanQueryChosung = rawQuery.replace(" ", "")
            if (wordChosung == cleanQueryChosung) {
                return MatchResult(word, 86, "Khớp viết tắt 초성 chính xác: $cleanQueryChosung")
            } else if (wordChosung.startsWith(cleanQueryChosung)) {
                return MatchResult(word, 82, "Khớp viết tắt 초성 bắt đầu: $cleanQueryChosung")
            } else if (wordChosung.contains(cleanQueryChosung)) {
                return MatchResult(word, 75, "Chứa phụ âm 초성: $cleanQueryChosung")
            }
        }

        // 5. Prefix match & Romaja phonetic equivalence (Hangul / Vietnamese / Pronunciation)
        if (korLower.startsWith(qLower) || rootLower.startsWith(qLower)) {
            return MatchResult(word, 80, "Khớp tiền tố tiếng Hàn")
        }
        if (vietNoAccent.startsWith(qNoAccent)) {
            return MatchResult(word, 78, "Khớp bắt đầu nghĩa tiếng Việt")
        }

        val cleanPron = pronLower.replace("-", "")
        val normPron = cleanPron.replace("k", "g").replace("p", "b").replace("t", "d")
        val normQ = qLower.replace("-", "").replace("k", "g").replace("p", "b").replace("t", "d")

        if (cleanPron.startsWith(qLower) || normPron.startsWith(normQ)) {
            return MatchResult(word, 76, "Khớp phiên âm Romaja (${word.displayPronunciation})")
        }

        // 6. Substring / Contains match
        if (korLower.contains(qLower)) {
            return MatchResult(word, 70, "Chứa từ khóa tiếng Hàn")
        }
        if (vietNoAccent.contains(qNoAccent)) {
            return MatchResult(word, 68, "Chứa từ khóa tiếng Việt không dấu")
        }
        if (hanjaLower.contains(qLower) && rawQuery.any { it.code in 0x4E00..0x9FFF }) {
            return MatchResult(word, 67, "Khớp chữ Hán (Hanja: ${word.hanja})")
        }
        if (cleanPron.contains(qLower) || normPron.contains(normQ)) {
            return MatchResult(word, 65, "Chứa phiên âm phát âm")
        }

        // 7. Related Words: Synonyms & Antonyms
        val synNoAccent = removeVietnameseAccents(word.synonyms)
        val antNoAccent = removeVietnameseAccents(word.antonyms)
        if (word.synonyms.contains(rawQuery) || synNoAccent.contains(qNoAccent)) {
            return MatchResult(word, 60, "Từ đồng nghĩa liên quan: ${word.synonyms.take(25)}")
        }
        if (word.antonyms.contains(rawQuery) || antNoAccent.contains(qNoAccent)) {
            return MatchResult(word, 58, "Từ trái nghĩa liên quan: ${word.antonyms.take(25)}")
        }
        if (defNoAccent.contains(qNoAccent)) {
            return MatchResult(word, 55, "Tìm thấy trong phần giải nghĩa chi tiết")
        }

        // 8. Fuzzy / Approximate matching (gần đúng cho lỗi chính tả, sai dấu hoặc thiếu ký tự)
        if (rawQuery.length >= 3) {
            val pronSim = similarity(normQ, normPron.take(normQ.length + 2))
            if (pronSim >= 0.65 || similarity(normQ, normPron) >= 0.65) {
                return MatchResult(word, 53, "Gợi ý gần đúng phiên âm Romaja (~${(pronSim * 100).toInt()}%)")
            }

            val vietSim = similarity(qNoAccent, vietNoAccent.take(qNoAccent.length + 2))
            if (vietSim >= 0.70) {
                return MatchResult(word, 50, "Gợi ý gần đúng tiếng Việt (~${(vietSim * 100).toInt()}%)")
            }

            val korSim = similarity(qLower, korLower)
            if (korSim >= 0.60) {
                return MatchResult(word, 48, "Gợi ý gần đúng tiếng Hàn (~${(korSim * 100).toInt()}%)")
            }
        }

        return null
    }

    /**
     * Search and rank words with intelligent sorting
     */
    fun searchAndRank(words: List<WordEntity>, query: String): List<MatchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return words.map { MatchResult(it, 0, "", isBestMatch = false) }
        }

        val results = words.mapNotNull { scoreWord(it, trimmed) }
            .sortedWith(
                compareByDescending<MatchResult> { it.score }
                    .thenByDescending { it.word.examAppearances }
                    .thenBy { it.word.korean.length }
            )

        if (results.isEmpty()) return emptyList()

        // Mark the single top score as best match if it has high confidence (score >= 85)
        val highestScore = results.first().score
        return results.mapIndexed { index, item ->
            if (index == 0 && highestScore >= 80) {
                item.copy(isBestMatch = true)
            } else {
                item
            }
        }
    }
}
