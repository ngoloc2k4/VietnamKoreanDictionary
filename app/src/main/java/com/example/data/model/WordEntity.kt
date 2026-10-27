package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val korean: String,               // e.g. 공부하다
    val vietnamese: String,           // e.g. Học, học tập
    val pronunciation: String,        // e.g. [gong-bu-ha-da]
    val partOfSpeech: String,         // e.g. Động từ, Danh từ, Tính từ
    val hanja: String = "",           // e.g. 功夫-
    val hanViet: String = "",         // e.g. Công phu
    val definition: String = "",      // Detailed Vietnamese definition
    val level: String = "TOPIK I",     // TOPIK I (Cấp 1-2), TOPIK II (Cấp 3-6)
    val topikLevelNumber: Int = 1,    // 1 to 6
    val examFrequency: String = "Rất cao", // Rất cao, Cao, Thường gặp
    val examAppearances: Int = 45,    // Estimated appearances in recent exams
    val rootForm: String = "",        // Dạng nguyên thể / từ gốc
    val presentTense: String = "",    // Thì hiện tại: 공부해요 / 공부합니다
    val pastTense: String = "",       // Thì quá khứ: 공부했어요 / 공부했습니다
    val futureTense: String = "",     // Thì tương lai: 공부할 거예요 / 공부하겠습니다
    val formalHigh: String = "",      // Kính ngữ trang trọng (합쇼체): 공부합니다 / 공부하십니까
    val politeLife: String = "",      // Lịch sự đời sống (해요체): 공부해요
    val informal: String = "",        // Thân mật đời sống (반말): 공부해
    val honorificSubject: String = "",// Kính ngữ chủ ngữ: 공부하시다 / 공부하세요
    val grammarNotes: String = "",    // Chú thích ngữ pháp & lưu ý khi đi thi
    val examplesText: String = "",    // Pipe-separated or multi-line examples: Korean || Vietnamese
    val synonyms: String = "",        // Từ đồng nghĩa (유의어)
    val antonyms: String = "",        // Từ trái nghĩa (반의어)
    val isFavorite: Boolean = false,  // Đánh dấu yêu thích
    val isMastered: Boolean = false,  // Đã thuộc trong flashcard
    val reviewCount: Int = 0,         // Số lần ôn tập
    val lastReviewedAt: Long = 0L     // Thời gian ôn gần nhất
) {
    val displayPronunciation: String
        get() {
            val trimmed = pronunciation.trim()
            if (trimmed.startsWith("[") && trimmed.contains("]")) {
                return trimmed.substringBefore("]") + "]"
            }
            return if (trimmed.contains("•")) trimmed.substringBefore("•").trim() else trimmed
        }
}

data class ExampleSentence(
    val korean: String,
    val vietnamese: String
)

fun WordEntity.parseExamples(): List<ExampleSentence> {
    if (examplesText.isBlank()) return emptyList()
    return examplesText.split("\n").mapNotNull { line ->
        val parts = line.split("||")
        if (parts.size >= 2) {
            ExampleSentence(parts[0].trim(), parts[1].trim())
        } else null
    }
}
