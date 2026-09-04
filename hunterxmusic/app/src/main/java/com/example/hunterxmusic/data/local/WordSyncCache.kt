package com.example.hunterxmusic.data.local

import android.content.Context
import com.example.hunterxmusic.data.lyrics.WordAlignmentEngine
import com.example.hunterxmusic.domain.model.LyricLine

/**
 * On-device cache for Word-By-Word Synchronized Lyrics.
 * Stores and retrieves generated or parsed word alignments keyed by song fingerprint
 * (`"${title.lowercase()}|${artist.lowercase()}|${durationSec}"`), so word alignments
 * only ever get processed once per song.
 */
class WordSyncCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("word_sync_cache", Context.MODE_PRIVATE)

    /**
     * Builds a deterministic fingerprint based on title, artist, and duration.
     */
    fun buildFingerprint(title: String, artist: String, durationMs: Long): String {
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        val cleanArtist = artist.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        val durationSec = if (durationMs > 0) durationMs / 1000L else 0L
        return "${cleanTitle}_${cleanArtist}_$durationSec"
    }

    /**
     * Retrieves cached word-aligned lyrics if available.
     */
    fun getCachedAlignment(fingerprint: String): List<LyricLine>? {
        if (fingerprint.isBlank()) return null
        return try {
            val json = prefs.getString(fingerprint, null) ?: return null
            WordAlignmentEngine.parseWhisperXJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Persists word-aligned lyrics for a song fingerprint.
     */
    fun saveAlignment(fingerprint: String, lines: List<LyricLine>) {
        if (fingerprint.isBlank() || lines.isEmpty()) return
        try {
            val json = WordAlignmentEngine.exportToJson(lines)
            prefs.edit().putString(fingerprint, json).apply()
        } catch (_: Exception) { }
    }

    /**
     * Clears cached word sync data.
     */
    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
