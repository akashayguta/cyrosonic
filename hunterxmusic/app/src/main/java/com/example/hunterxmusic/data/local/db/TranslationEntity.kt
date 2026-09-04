package com.example.hunterxmusic.data.local.db

import androidx.room.*

@Entity(
    tableName = "lyrics_translations",
    primaryKeys = ["targetLang", "songFingerprint", "lineIndex"],
    indices = [Index(value = ["songFingerprint", "targetLang"])]
)
data class TranslationEntity(
    val targetLang: String,
    val songFingerprint: String,
    val lineIndex: Int,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationDao {
    @Query("SELECT translatedText FROM lyrics_translations WHERE targetLang = :lang AND songFingerprint = :fingerprint AND lineIndex = :lineIndex LIMIT 1")
    suspend fun getTranslation(lang: String, fingerprint: String, lineIndex: Int): String?

    @Query("SELECT * FROM lyrics_translations WHERE targetLang = :lang AND songFingerprint = :fingerprint")
    suspend fun getAllTranslationsForSong(lang: String, fingerprint: String): List<TranslationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(entity: TranslationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TranslationEntity>)

    @Query("DELETE FROM lyrics_translations WHERE timestamp < :olderThanMs")
    suspend fun deleteOldTranslations(olderThanMs: Long)

    @Query("DELETE FROM lyrics_translations")
    suspend fun clearAll()
}
