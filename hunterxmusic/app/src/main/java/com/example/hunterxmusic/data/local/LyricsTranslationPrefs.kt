package com.example.hunterxmusic.data.local

import android.content.Context
import org.json.JSONObject

/**
 * Lyrics translation preferences: the two languages the listener picks in
 * Settings, plus an on-device line cache so a translated song only ever
 * hits the translation API once per (song, language, line).
 */
class LyricsTranslationPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lyrics_translation", Context.MODE_PRIVATE)

    var lang1: String
        get() = prefs.getString(KEY_LANG1, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_LANG1, value).apply()

    var lang2: String
        get() = prefs.getString(KEY_LANG2, "hi") ?: "hi"
        set(value) = prefs.edit().putString(KEY_LANG2, value).apply()

    var wordSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_WORD_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_WORD_SYNC, value).apply()

    fun cachedTranslation(lang: String, songKey: String, lineIndex: Int): String? {
        return try {
            val root = JSONObject(prefs.getString(cacheKey(lang), "{}") ?: "{}")
            val song = root.optJSONObject(songKey) ?: return null
            song.optString(lineIndex.toString()).takeIf { it.isNotBlank() && it != "null" }
        } catch (_: Exception) { null }
    }

    fun saveTranslation(lang: String, songKey: String, lineIndex: Int, text: String) {
        if (text.isBlank()) return
        try {
            val raw = prefs.getString(cacheKey(lang), "{}") ?: "{}"
            val root = JSONObject(raw)
            val song = root.optJSONObject(songKey) ?: JSONObject().also { root.put(songKey, it) }
            song.put(lineIndex.toString(), text)
            prefs.edit().putString(cacheKey(lang), root.toString()).apply()
        } catch (_: Exception) { }
    }

    private fun cacheKey(lang: String) = "cache_$lang"

    private companion object {
        const val KEY_LANG1 = "lang1"
        const val KEY_LANG2 = "lang2"
        const val KEY_WORD_SYNC = "word_sync_enabled"
    }
}