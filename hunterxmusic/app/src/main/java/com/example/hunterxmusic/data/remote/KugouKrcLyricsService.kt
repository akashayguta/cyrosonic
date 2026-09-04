package com.example.hunterxmusic.data.remote

import android.util.Base64
import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.WordCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.regex.Pattern
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * High-Precision Studio Word-by-Word Lyrics Provider (Kugou KRC Engine).
 * Decodes microsecond-accurate word and syllable timestamps for global and regional songs.
 */
object KugouKrcLyricsService {

    private val KRC_KEY = byteArrayOf(
        0x40.toByte(), 0x47.toByte(), 0x61.toByte(), 0x77.toByte(),
        0x5e.toByte(), 0x32.toByte(), 0x74.toByte(), 0x47.toByte(),
        0x51.toByte(), 0x36.toByte(), 0x31.toByte(), 0x2d.toByte(),
        0xce.toByte(), 0xd2.toByte(), 0x6e.toByte(), 0x69.toByte()
    )

    private val LINE_REGEX = Pattern.compile("\\[(\\d+),(\\d+)\\](.*)")
    private val WORD_REGEX = Pattern.compile("<(\\d+),(\\d+),\\d+>([^<]+)")

    suspend fun getWordSyncedLyrics(
        title: String,
        artist: String,
        client: OkHttpClient
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(audio.*?\\)|\\[audio.*?\\]|\\(video.*?\\)|\\[video.*?\\]|\\|.*"), "").trim()
        val cleanArtist = if (artist.contains("Unknown", ignoreCase = true)) "" else artist.trim()
        val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "http://mobilecdn.kugou.com/api/v3/search/song?keyword=$encodedQuery&page=1&pagesize=5"

            val searchReq = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "KuGouApp/11.0.0")
                .get()
                .build()

            val searchResp = client.newCall(searchReq).execute()
            val searchBody = searchResp.body?.string() ?: return@withContext null
            val searchJson = JSONObject(searchBody)
            val songList = searchJson.optJSONObject("data")?.optJSONArray("info") ?: return@withContext null

            for (i in 0 until songList.length()) {
                val song = songList.optJSONObject(i) ?: continue
                val hash = song.optString("hash").takeIf { it.isNotBlank() } ?: continue
                val duration = song.optInt("duration", 0)
                val songName = song.optString("songname", "")

                val lrcSearchUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${URLEncoder.encode(songName, "UTF-8")}&duration=$duration&hash=$hash"
                val lrcSearchReq = Request.Builder()
                    .url(lrcSearchUrl)
                    .addHeader("User-Agent", "KuGouApp/11.0.0")
                    .get()
                    .build()

                val lrcSearchResp = client.newCall(lrcSearchReq).execute()
                val lrcSearchBody = lrcSearchResp.body?.string() ?: continue
                val lrcSearchJson = JSONObject(lrcSearchBody)
                val candidates = lrcSearchJson.optJSONArray("candidates") ?: continue

                for (c in 0 until candidates.length()) {
                    val cand = candidates.optJSONObject(c) ?: continue
                    val id = cand.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val accessKey = cand.optString("accesskey").takeIf { it.isNotBlank() } ?: continue

                    val dlUrl = "http://krcs.kugou.com/download?ver=1&man=yes&client=mobi&fmt=krc&id=$id&accesskey=$accessKey"
                    val dlReq = Request.Builder()
                        .url(dlUrl)
                        .addHeader("User-Agent", "KuGouApp/11.0.0")
                        .get()
                        .build()

                    val dlResp = client.newCall(dlReq).execute()
                    val dlBody = dlResp.body?.string() ?: continue
                    val dlJson = JSONObject(dlBody)
                    val contentB64 = dlJson.optString("content").takeIf { it.isNotBlank() } ?: continue

                    val krcText = decodeKrc(contentB64) ?: continue
                    val parsedLines = parseKrc(krcText)
                    if (parsedLines.size >= 4) {
                        return@withContext parsedLines
                    }
                }
            }
        } catch (_: Exception) { }
        null
    }

    private fun decodeKrc(b64Content: String): String? {
        return try {
            val rawData = Base64.decode(b64Content, Base64.DEFAULT)
            if (rawData.size <= 4) return null
            // Skip 4-byte 'krc1' magic header
            val encrypted = ByteArray(rawData.size - 4)
            System.arraycopy(rawData, 4, encrypted, 0, encrypted.size)

            val decrypted = ByteArray(encrypted.size)
            for (i in encrypted.indices) {
                decrypted[i] = (encrypted[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
            }

            val inflater = Inflater()
            val inflaterInputStream = InflaterInputStream(ByteArrayInputStream(decrypted), inflater)
            val decompressedBytes = inflaterInputStream.readBytes()
            inflater.end()

            String(decompressedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKrc(krcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val rawLines = krcText.split("\n")

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val lineMatcher = LINE_REGEX.matcher(trimmed)
            if (!lineMatcher.matches()) continue

            val lineStartMs = lineMatcher.group(1)?.toLongOrNull() ?: continue
            val rest = lineMatcher.group(3) ?: continue

            val wordMatcher = WORD_REGEX.matcher(rest)
            val wordCues = mutableListOf<WordCue>()
            val lineTextBuilder = StringBuilder()

            while (wordMatcher.find()) {
                val offsetMs = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                val durMs = wordMatcher.group(2)?.toLongOrNull() ?: 180L
                val wordText = wordMatcher.group(3) ?: ""

                if (wordText.isNotEmpty()) {
                    val wStart = lineStartMs + offsetMs
                    val wEnd = wStart + durMs.coerceAtLeast(100L)
                    wordCues.add(
                        WordCue(
                            text = wordText,
                            startMs = wStart,
                            endMs = wEnd
                        )
                    )
                    lineTextBuilder.append(wordText)
                }
            }

            val fullLineText = lineTextBuilder.toString().trim()
            if (fullLineText.isNotEmpty() && wordCues.isNotEmpty()) {
                // Filter out non-lyric metadata headers like "Produced by" / "Composed by" if at the very beginning
                if (lines.isEmpty() && (fullLineText.contains("Produced by", ignoreCase = true) || fullLineText.contains("Lyrics by", ignoreCase = true) || fullLineText.contains("Composed by", ignoreCase = true))) {
                    continue
                }

                lines.add(
                    LyricLine(
                        timestampMs = lineStartMs,
                        words = fullLineText,
                        wordCues = wordCues
                    )
                )
            }
        }

        return lines.sortedBy { it.timestampMs }
    }
}
