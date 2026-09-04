package com.example.hunterxmusic.data.remote

import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.WordCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * High-Speed Universal Word-by-Word Lyrics Engine powered by LrcMux (https://lrcmux.dev).
 * Aggregates Apple Music, Spotify, Kugou, Musixmatch, NetEase & LRCLIB with microsecond word timings.
 */
object LrcMuxLyricsService {

    private const val BASE_URL = "https://api.lrcmux.dev/get"

    suspend fun getWordSyncedLyrics(
        title: String,
        artist: String,
        durationMs: Long?,
        client: OkHttpClient
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(
            Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(audio.*?\\)|\\[audio.*?\\]|\\(video.*?\\)|\\[video.*?\\]|\\|.*"),
            ""
        ).trim()
        val cleanArtist = if (artist.contains("Unknown", ignoreCase = true)) "" else artist.trim()

        if (cleanTitle.isBlank()) return@withContext null

        try {
            val queryParams = StringBuilder()
            queryParams.append("title=").append(URLEncoder.encode(cleanTitle, "UTF-8"))
            if (cleanArtist.isNotBlank()) {
                queryParams.append("&artist=").append(URLEncoder.encode(cleanArtist, "UTF-8"))
            }
            if (durationMs != null && durationMs > 10_000L) {
                queryParams.append("&duration=").append(durationMs / 1000L)
            }

            val requestUrl = "$BASE_URL?$queryParams"
            val request = Request.Builder()
                .url(requestUrl)
                .addHeader("User-Agent", "CyroSonic/1.0 (Android; Universal)")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val rootJson = JSONObject(bodyString)

            val linesArray = rootJson.optJSONArray("lines")
            if (linesArray != null && linesArray.length() > 0) {
                val parsedLines = mutableListOf<LyricLine>()

                for (i in 0 until linesArray.length()) {
                    val lineObj = linesArray.optJSONObject(i) ?: continue
                    val lineText = lineObj.optString("text", "").trim()
                    val lineStart = lineObj.optLong("start", -1L)
                    if (lineStart < 0 && lineText.isBlank()) continue

                    val wordsArray = lineObj.optJSONArray("words")
                    val wordCues = mutableListOf<WordCue>()

                    if (wordsArray != null && wordsArray.length() > 0) {
                        for (w in 0 until wordsArray.length()) {
                            val wObj = wordsArray.optJSONObject(w) ?: continue
                            val wText = wObj.optString("text", "")
                            val wStart = wObj.optLong("start", -1L)
                            val wEnd = wObj.optLong("end", -1L)

                            if (wText.isNotBlank() && wStart >= 0) {
                                val resolvedEnd = if (wEnd > wStart) wEnd else (wStart + (wText.length * 90L + 180L).coerceIn(180L, 900L))
                                wordCues.add(
                                    WordCue(
                                        text = wText,
                                        startMs = wStart,
                                        endMs = resolvedEnd
                                    )
                                )
                            }
                        }
                    }

                    if (lineText.isNotEmpty() || wordCues.isNotEmpty()) {
                        parsedLines.add(
                            LyricLine(
                                timestampMs = if (lineStart >= 0) lineStart else (wordCues.firstOrNull()?.startMs ?: 0L),
                                words = if (lineText.isNotEmpty()) lineText else wordCues.joinToString("") { it.text }.trim(),
                                wordCues = if (wordCues.isNotEmpty()) wordCues else null
                            )
                        )
                    }
                }

                if (parsedLines.isNotEmpty()) {
                    return@withContext parsedLines.sortedBy { it.timestampMs }
                }
            }

            // Fallback to plain/synced text inside lrcmux if 'lines' format wasn't present
            val syncedLrc = rootJson.optString("syncedLyrics").takeIf { it.isNotBlank() }
                ?: rootJson.optString("lyrics").takeIf { it.isNotBlank() }

            if (!syncedLrc.isNullOrBlank()) {
                return@withContext parseLrcText(syncedLrc)
            }
        } catch (_: Exception) { }

        null
    }

    private fun parseLrcText(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val lrcRegex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)")

        for (rawLine in lrc.lines()) {
            val match = lrcRegex.find(rawLine) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: 0L
            val sec = match.groupValues[2].toLongOrNull() ?: 0L
            val msStr = match.groupValues[3]
            val ms = when (msStr.length) {
                2 -> (msStr.toLongOrNull() ?: 0L) * 10
                3 -> msStr.toLongOrNull() ?: 0L
                else -> 0L
            }
            val timestamp = min * 60000 + sec * 1000 + ms
            val text = match.groupValues[4].trim()

            lines.add(
                LyricLine(
                    timestampMs = timestamp,
                    words = text,
                    isInstrumental = text.isEmpty()
                )
            )
        }
        return lines.sortedBy { it.timestampMs }
    }
}
