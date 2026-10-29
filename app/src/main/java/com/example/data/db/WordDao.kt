package com.example.data.db

import androidx.room.*
import com.example.data.model.WordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY examAppearances DESC, korean ASC LIMIT 200")
    fun getAllWords(): Flow<List<WordEntity>>

    @Query("""
        SELECT * FROM words 
        WHERE korean LIKE '%' || :query || '%' 
           OR vietnamese LIKE '%' || :query || '%' 
           OR definition LIKE '%' || :query || '%'
           OR hanja LIKE '%' || :query || '%' 
           OR hanViet LIKE '%' || :query || '%' 
           OR pronunciation LIKE '%' || :query || '%'
           OR synonyms LIKE '%' || :query || '%'
        ORDER BY 
           CASE WHEN examAppearances >= 9000 THEN 0 ELSE 1 END,
           CASE WHEN korean = :query THEN 0
                WHEN vietnamese LIKE :query THEN 1
                WHEN pronunciation LIKE '% ' || :query || ' %' OR pronunciation LIKE '%• ' || :query || ',%' OR pronunciation LIKE '%• ' || :query || ' %' THEN 2
                WHEN korean LIKE :query || '%' THEN 3
                WHEN vietnamese LIKE :query || '%' THEN 4
                ELSE 5 END,
           examAppearances DESC
        LIMIT 200
    """)
    fun searchWords(query: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id LIMIT 1")
    suspend fun getWordById(id: Long): WordEntity?

    @Query("""
        SELECT * FROM words 
        WHERE korean LIKE '%' || :query || '%' 
           OR vietnamese LIKE '%' || :query || '%' 
           OR definition LIKE '%' || :query || '%'
           OR hanViet LIKE '%' || :query || '%'
           OR pronunciation LIKE '%' || :query || '%'
        ORDER BY 
           CASE WHEN examAppearances >= 9000 THEN 0 ELSE 1 END,
           examAppearances DESC 
        LIMIT 25
    """)
    suspend fun findCandidates(query: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE korean = :korean LIMIT 1")
    suspend fun getWordByKorean(korean: String): WordEntity?

    @Query("""
        SELECT * FROM words 
        WHERE vietnamese LIKE '%' || :query || '%' 
           OR definition LIKE '%' || :query || '%'
        ORDER BY 
           CASE WHEN examAppearances >= 9000 THEN 0 ELSE 1 END,
           examAppearances DESC 
        LIMIT 1
    """)
    suspend fun findFirstMatchingVietnamese(query: String): WordEntity?

    @Query("SELECT * FROM words WHERE isFavorite = 1 ORDER BY lastReviewedAt DESC, korean ASC")
    fun getFavoriteWords(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE level = :level ORDER BY examAppearances DESC")
    fun getWordsByLevel(level: String): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE isMastered = 0 ORDER BY reviewCount ASC, examAppearances DESC")
    fun getUnmasteredWords(): Flow<List<WordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity): Long

    @Update
    suspend fun updateWord(word: WordEntity)

    @Query("UPDATE words SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE words SET isMastered = :isMastered, reviewCount = reviewCount + 1, lastReviewedAt = :timestamp WHERE id = :id")
    suspend fun markReviewed(id: Long, isMastered: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM words")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM words")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isFavorite = 1")
    fun getFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE isMastered = 1")
    fun getMasteredCount(): Flow<Int>
}
