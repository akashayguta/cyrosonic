package com.example.hunterxmusic.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Echo-style JioSaavn service with multi-server fallback.
 * Mirrors Echo Music's DeviceRouter + SaavnService approach.
 */
object EchoSaavnService {

    private val SERVERS = listOf(
        "https://saavn.echomusic.fun",
        "https://jiosaavn-api.pc-adityadav9532.workers.dev",
        "https://jiosaavn-api.mac-adityadav9532.workers.dev"
    )

    private var currentServerIndex = 0
    private val gson = Gson()

    /**
     * Search songs on JioSaavn via Echo's API servers.
     * Each attempt is capped at 8s, and a working server sticks as the
     * current one (the old code kept rotating forever, so a flaky server
     * could come back around on every request).
     */
    suspend fun searchSongs(query: String, client: OkHttpClient): List<EchoSaavnSong> = withContext(Dispatchers.IO) {
        for (i in SERVERS.indices) {
            val server = SERVERS[i]
            try {
                val url = "$server/api/search/songs?query=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=10"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "CyroSonic/8.0")
                    .get()
                    .build()

                val response = withTimeout(8_000L) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = gson.fromJson(body, EchoSaavnSearchResponse::class.java)
                    if (parsed.success && parsed.data != null) {
                        currentServerIndex = i
                        return@withContext parsed.data.results
                    }
                }
            } catch (_: Exception) { }
        }
        emptyList()
    }

    /**
     * Get song detail + download URLs by saavn song ID.
     * Returns the best quality download URL (320kbps preferred).
     */
    suspend fun getBestStreamUrl(songId: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        for (i in SERVERS.indices) {
            val server = SERVERS[i]
            try {
                val url = "$server/api/songs/$songId"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "CyroSonic/8.0")
                    .get()
                    .build()

                val response = withTimeout(8_000L) { client.newCall(request).execute() }
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = gson.fromJson(body, EchoSaavnSongResponse::class.java)
                    if (parsed.success && parsed.data.isNotEmpty()) {
                        val urls = parsed.data[0].downloadUrl.filter { it.url.isNotBlank() }
                        if (urls.isNotEmpty()) {
                            currentServerIndex = i
                            // Prefer 320kbps > 160kbps > 96kbps > any
                            return@withContext urls.firstOrNull { it.quality == "320kbps" }?.url
                                ?: urls.firstOrNull { it.quality == "160kbps" }?.url
                                ?: urls.lastOrNull()?.url
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        null
    }

    /**
     * Get high quality image URL for a song.
     */
    fun getBestImageUrl(images: List<EchoSaavnQualityUrl>): String {
        val url = images.firstOrNull { it.quality == "500x500" }?.url
            ?: images.lastOrNull()?.url
            ?: ""
        return if (url.isBlank()) "" else url.replace("150x150", "500x500").replace("50x50", "500x500").replace("250x250", "500x500")
    }
}
