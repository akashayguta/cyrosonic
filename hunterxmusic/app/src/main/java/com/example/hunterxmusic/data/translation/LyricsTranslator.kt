package com.example.hunterxmusic.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Line-level lyrics translator, no API key required.
 *
 * Primary: MyMemory (https://mymemory.translated.net) — free, keyless,
 * supports autodetect source, ~5000 chars/day anonymous which is plenty
 * for lyric lines.
 * Fallback: public LibreTranslate instances (source=auto) for when MyMemory
 * is unreachable. All I/O runs on Dispatchers.IO — the lyrics screen calls
 * this from LaunchedEffects on the main dispatcher, and a bare execute()
 * there throws NetworkOnMainThreadException, silently killing translation.
 */
class LyricsTranslator(
    private val okHttpClient: OkHttpClient,
    private val translationDao: com.example.hunterxmusic.data.local.db.TranslationDao? = null
) {

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String = "auto",
        fingerprint: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        if (text.length > 900) return@withContext null

        val fp = if (fingerprint.isNotBlank()) fingerprint else text.take(64)
        val lineHash = text.hashCode()

        // Check persistent Room cache first
        try {
            val cached = translationDao?.getTranslation(targetLang, fp, lineHash)
            if (!cached.isNullOrBlank()) return@withContext cached
        } catch (_: Exception) { }

        val translated = translateViaGoogleTranslate(text, targetLang)
            ?: translateViaMyMemory(text, targetLang, sourceLang)
            ?: translateViaLibreTranslate(text, targetLang)

        // Persist to Room cache
        if (!translated.isNullOrBlank()) {
            try {
                translationDao?.insertTranslation(
                    com.example.hunterxmusic.data.local.db.TranslationEntity(
                        targetLang = targetLang,
                        songFingerprint = fp,
                        lineIndex = lineHash,
                        translatedText = translated
                    )
                )
            } catch (_: Exception) { }
        }

        translated
    }

    private fun translateViaGoogleTranslate(
        text: String,
        targetLang: String
    ): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONArray(body)
                val sentencesArray = json.optJSONArray(0) ?: return null
                val sb = StringBuilder()
                for (i in 0 until sentencesArray.length()) {
                    val piece = sentencesArray.optJSONArray(i)?.optString(0)
                    if (!piece.isNullOrBlank()) {
                        sb.append(piece)
                    }
                }
                val translated = sb.toString().trim()
                if (translated.isNotBlank() && !translated.equals(text, ignoreCase = true)) translated else null
            }
        } catch (_: Exception) { null }
    }

    private fun translateViaMyMemory(
        text: String,
        targetLang: String,
        sourceLang: String
    ): String? {
        return try {
            val source = if (sourceLang.isBlank() || sourceLang == "auto") "autodetect" else sourceLang
            val url = "https://api.mymemory.translated.net/get" +
                "?q=" + java.net.URLEncoder.encode(text, "UTF-8") +
                "&langpair=" + java.net.URLEncoder.encode("$source|$targetLang", "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CyroSonic/3.0 (Android)")
                .header("Accept", "application/json")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val translated = JSONObject(body)
                    .optJSONObject("responseData")
                    ?.optString("translatedText")
                    ?.takeIf { it.isNotBlank() && it != "NO QUERY SPECIFIED" }
                if (translated != null && !translated.equals(text, ignoreCase = true)) translated else null
            }
        } catch (_: Exception) { null }
    }

    private fun translateViaLibreTranslate(
        text: String,
        targetLang: String
    ): String? {
        val endpoints = listOf(
            "https://libretranslate.de/translate",
            "https://translate.terraprint.co/translate"
        )
        for (ep in endpoints) {
            try {
                val body = JSONObject()
                    .put("q", text)
                    .put("source", "auto")
                    .put("target", targetLang)
                    .put("format", "text")
                    .toString()
                    .toRequestBody(jsonType)
                val request = Request.Builder()
                    .url(ep)
                    .header("User-Agent", "CyroSonic/3.0 (Android)")
                    .post(body)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val translated = JSONObject(resp.body?.string() ?: return@use)
                        .optString("translatedText")
                        .takeIf { it.isNotBlank() && !it.equals(text, ignoreCase = true) }
                    if (translated != null) return translated
                }
            } catch (_: Exception) { }
        }
        return null
    }
}