package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val koreanResult: String,
    val vietnameseResult: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String, // "KR" or "VI"
    val targetLang: String, // "VI" or "KR"
    val timestamp: Long = System.currentTimeMillis()
)
