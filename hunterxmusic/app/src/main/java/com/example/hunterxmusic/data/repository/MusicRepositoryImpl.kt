package com.example.hunterxmusic.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.hunterxmusic.core.security.CryptoManager
import com.example.hunterxmusic.data.local.db.DownloadState
import com.example.hunterxmusic.data.local.db.TrackDao
import com.example.hunterxmusic.data.local.db.TrackEntity
import com.example.hunterxmusic.data.remote.EchoSaavnService
import com.example.hunterxmusic.data.remote.EchoSaavnSong
import com.example.hunterxmusic.data.remote.LrcLibService
import com.example.hunterxmusic.data.remote.MusicCatalogService
import com.example.hunterxmusic.data.remote.YouTubeInnerTube
import com.example.hunterxmusic.data.remote.NewPipeYouTubeResolver
import com.example.hunterxmusic.data.remote.model.SaavnSearchItem
import com.example.hunterxmusic.domain.model.LyricLine
import com.example.hunterxmusic.domain.model.Track
import com.example.hunterxmusic.domain.repository.DownloadStatus
import com.example.hunterxmusic.domain.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.hunterxmusic.domain.repository.AudioSourceType
import com.example.hunterxmusic.domain.repository.ResolvedAudioSource
import com.example.hunterxmusic.domain.repository.DownloadProgress
import com.example.hunterxmusic.domain.repository.ListeningStats
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.withContext

class MusicRepositoryImpl(
    private val context: Context,
    private val trackDao: TrackDao,
    private val cryptoManager: CryptoManager,
    private val okHttpClient: OkHttpClient,
    private val lrcLibService: LrcLibService,
    private val musicCatalogService: MusicCatalogService? = null,
    private val searchHistoryDao: com.example.hunterxmusic.data.local.db.SearchHistoryDao? = null,
    private val translationDao: com.example.hunterxmusic.data.local.db.TranslationDao? = null
) : MusicRepository {

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    private val streamUrlCache = android.util.LruCache<String, String>(100)
    private val resolvedAudioSourceMap = java.util.concurrent.ConcurrentHashMap<String, ResolvedAudioSource>()

    override fun getResolvedAudioSource(trackId: String): ResolvedAudioSource? {
        return resolvedAudioSourceMap[trackId]
    }

    private val prefs = context.getSharedPreferences("hunterx_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_LANGUAGE = "preferred_language"
        private const val KEY_LISTEN_HISTORY = "listen_history"
        private const val KEY_SPONSOR_BLOCK = "sponsor_block_enabled"
        private const val MAX_SEARCH_HISTORY = 50
        private const val MAX_LISTEN_HISTORY = 100
        // Was 3 minutes: a single offline attempt used to suppress lyrics long
        // after the network came back.
        private const val NEGATIVE_LYRICS_TTL_MS = 45 * 1000L
        /** Per-provider deadline, so one slow host can't stall all lyrics. */
        private const val PROVIDER_TIMEOUT_MS = 4_000L
        /** Overall deadline for the whole provider fan-out. */
        private const val LYRICS_TOTAL_TIMEOUT_MS = 7_000L
        /**
         * Budget for the LRCLIB-only fast path. LRCLIB is the primary source of
         * real timestamps, so when it answers in time the other eight providers
         * are never contacted and lyrics appear almost immediately.
         */
        private const val LRCLIB_FAST_PATH_TIMEOUT_MS = 6_000L
    }

    // ──────────────────────────────────────────────────────────
    // SEARCH — Uses Echo's JioSaavn servers (primary) + old API (fallback)
    // ──────────────────────────────────────────────────────────

    override suspend fun searchTracks(query: String): List<Track> {
        return searchTracksPaginated(query).first
    }

    override suspend fun searchTracksPaginated(query: String): Pair<List<Track>, String?> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext Pair(emptyList(), null)

        val (ytTracks, token, echoResults) = coroutineScope {
            val ytDeferred = async {
                try {
                    YouTubeInnerTube.searchTracks(q, okHttpClient)
                } catch (_: Exception) {
                    Pair(emptyList<Track>(), null)
                }
            }

            val saavnDeferred = async {
                try {
                    EchoSaavnService.searchSongs(q, okHttpClient)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val (yt, tok) = ytDeferred.await()
            val saavn = saavnDeferred.await()
            Triple(yt, tok, saavn)
        }

        val combined = mutableListOf<Track>()
        val seenSignatures = mutableSetOf<String>()

        // Add YouTube results
        for (track in ytTracks) {
            val sig = "${track.title.lowercase().trim()}_${track.artist.lowercase().trim()}"
            if (sig !in seenSignatures) {
                seenSignatures.add(sig)
                val existing = trackDao.getTrackById(track.id)
                combined.add(
                    track.copy(
                        isLiked = existing?.isLiked ?: false,
                        isDownloaded = existing?.downloadState == DownloadState.COMPLETED_OFFLINE,
                        localFilePath = existing?.localFilePath,
                        encryptionIv = existing?.encryptionIv
                    )
                )
            }
        }

        // Add JioSaavn results
        for (song in echoResults) {
            val bestImage = EchoSaavnService.getBestImageUrl(song.image)
            val primaryArtists = song.artists.primary.joinToString(", ") { it.name }
            val allArtists = song.artists.all.joinToString(", ") { it.name }
            val artistName = if (primaryArtists.isNotBlank()) primaryArtists else if (allArtists.isNotBlank()) allArtists else "Unknown Artist"

            val sig = "${song.name.lowercase().trim()}_${artistName.lowercase().trim()}"
            if (sig !in seenSignatures) {
                seenSignatures.add(sig)
                val validUrls = song.downloadUrl.filter { it.url.isNotBlank() }
                val bestDownloadUrl = validUrls.firstOrNull { it.quality == "320kbps" }?.url
                    ?: validUrls.firstOrNull { it.quality == "160kbps" }?.url
                    ?: validUrls.lastOrNull()?.url

                val existing = trackDao.getTrackById(song.id)
                combined.add(
                    Track(
                        id = song.id,
                        title = song.name,
                        artist = artistName,
                        album = "",
                        albumArtUrl = bestImage,
                        durationMs = (song.duration ?: 0) * 1000L,
                        streamingUrl = bestDownloadUrl,
                        localFilePath = existing?.localFilePath,
                        isDownloaded = existing?.downloadState == DownloadState.COMPLETED_OFFLINE,
                        encryptionIv = existing?.encryptionIv,
                        isLiked = existing?.isLiked ?: false
                    )
                )
            }
        }

        if (combined.isNotEmpty()) {
            return@withContext Pair(combined, token)
        }

        // Fallback to old catalog service
        try {
            val response = musicCatalogService?.searchSongs(q)
            if (response != null && response.status && !response.results.isNullOrEmpty()) {
                val mapped = response.results.map { item ->
                    val track = mapSearchItemToTrack(item)
                    val existing = trackDao.getTrackById(track.id)
                    track.copy(
                        isLiked = existing?.isLiked ?: false,
                        isDownloaded = existing?.downloadState == DownloadState.COMPLETED_OFFLINE,
                        localFilePath = existing?.localFilePath,
                        encryptionIv = existing?.encryptionIv
                    )
                }
                Pair(mapped, null)
            } else {
                Pair(emptyList(), null)
            }
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    override suspend fun searchTracksNextPage(continuationToken: String): Pair<List<Track>, String?> {
        return try {
            val (ytTracks, token) = YouTubeInnerTube.searchTracksContinuation(continuationToken, okHttpClient)
            val mapped = ytTracks.map { track ->
                val existing = trackDao.getTrackById(track.id)
                track.copy(
                    isLiked = existing?.isLiked ?: false,
                    isDownloaded = existing?.downloadState == DownloadState.COMPLETED_OFFLINE,
                    localFilePath = existing?.localFilePath,
                    encryptionIv = existing?.encryptionIv
                )
            }
            Pair(mapped, token)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    override suspend fun getTrendingSongs(language: String): List<Track> {
        val allTracks = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()

        // 1. Direct official YouTube Music Charts (FEmusic_charts) ONLY when querying global / worldwide
        val trimmed = language.trim().lowercase()
        val isGlobal = trimmed.isEmpty() || 
            trimmed == "global" || 
            trimmed == "english" ||
            trimmed == "global top 50 chart" ||
            trimmed == "viral 50 global music charts" ||
            (trimmed.contains("global") && !trimmed.contains("india") && !trimmed.contains("pakistan") && !trimmed.contains("japan") && !trimmed.contains("korea") && !trimmed.contains("canada") && !trimmed.contains("uk") && !trimmed.contains("united"))
        if (isGlobal) {
            try {
                val officialCharts = YouTubeInnerTube.getChartsTracks(okHttpClient)
                for (track in officialCharts) {
                    if (track.id !in seenIds && allTracks.size < 20) {
                        seenIds.add(track.id)
                        allTracks.add(track)
                    }
                }
            } catch (_: Exception) { }
        }

        // 2. Language-specific or country-specific regional queries and fallback
        if (allTracks.size < 20) {
            val trendingQueries = getTrendingQueriesForLanguage(language)
            for (query in trendingQueries) {
                if (allTracks.size >= 20) break
                try {
                    val results = searchTracks(query)
                    for (track in results) {
                        if (track.id !in seenIds && allTracks.size < 20) {
                            seenIds.add(track.id)
                            allTracks.add(track)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
        return allTracks
    }

    private fun getTrendingQueriesForLanguage(language: String): List<String> {
        val lower = language.lowercase().trim()
        if (lower.contains("india") || lower == "hindi") {
            return listOf("latest hindi songs 2026", "trending indian music hits", "new bollywood songs", "top indie india")
        }
        if (lower.contains("pakistan") || lower == "urdu") {
            return listOf("coke studio pakistan hits", "latest urdu songs", "pakistani pop trending")
        }
        if (lower.contains("punjabi")) {
            return listOf("new punjabi songs", "ap dhillon", "diljit dosanjh", "karan aujla")
        }
        if (lower.contains("tamil")) {
            return listOf("new tamil songs", "anirudh ravichander", "sid sriram tamil", "ar rahman")
        }
        if (lower.contains("telugu")) {
            return listOf("new telugu songs", "thaman hits", "sid sriram telugu", "devi sri prasad")
        }
        if (lower.contains("korean") || lower.contains("kpop") || lower.contains("korea")) {
            return listOf("kpop hits 2026", "bts", "blackpink", "newjeans", "stray kids")
        }
        if (lower.contains("japanese") || lower.contains("jpop") || lower.contains("japan")) {
            return listOf("jpop top hits", "yoasobi", "kenshi yonezu", "ado", "anime hits")
        }
        if (lower.contains("spanish") || lower.contains("latin") || lower.contains("mexico")) {
            return listOf("latin hits 2026", "reggaeton top tracks", "bad bunny", "karol g")
        }
        if (lower.contains("bengali")) {
            return listOf("new bengali songs", "arijit singh bangla", "anupam roy")
        }
        if (lower.contains("marathi")) {
            return listOf("new marathi songs", "ajay atul marathi", "shankar mahadevan")
        }
        // If the query is already country-specific (e.g. "Canada latest hits 2026"), search that query directly
        if (lower.length > 5 && !lower.contains("global")) {
            return listOf(language, "$language top tracks", "$language popular songs")
        }
        return listOf("trending global songs 2026", "viral hits", "billboard hot 100")
    }

    // ──────────────────────────────────────────────────────────
    // SEARCH HISTORY & SUGGESTIONS
    // ──────────────────────────────────────────────────────────

    override fun getSearchSuggestions(partialQuery: String): List<String> {
        val q = partialQuery.trim().lowercase()
        return try {
            if (searchHistoryDao != null) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    if (q.isEmpty()) searchHistoryDao.getRecentQueries(8)
                    else searchHistoryDao.getSuggestions(q)
                }
            } else {
                getRecentSearches().filter { q.isEmpty() || it.lowercase().contains(q) }.take(8)
            }
        } catch (_: Exception) {
            getRecentSearches().filter { q.isEmpty() || it.lowercase().contains(q) }.take(8)
        }
    }

    override fun saveSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val history = getRecentSearches().toMutableList()
        history.remove(trimmed)
        history.add(0, trimmed)
        val capped = history.take(MAX_SEARCH_HISTORY)
        prefs.edit().putString(KEY_SEARCH_HISTORY, capped.joinToString("||")).apply()

        searchHistoryDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.insertOrUpdate(
                        com.example.hunterxmusic.data.local.db.SearchHistoryEntity(
                            query = trimmed,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) { }
            }
        }
    }

    override fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        searchHistoryDao?.let { dao ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.clearAll()
                } catch (_: Exception) { }
            }
        }
    }

    private fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }

    // ──────────────────────────────────────────────────────────
    // LANGUAGE PREFERENCES
    // ──────────────────────────────────────────────────────────

    override fun getPreferredLanguage(): String? = prefs.getString(KEY_LANGUAGE, null)
    override fun setPreferredLanguage(language: String) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    // ──────────────────────────────────────────────────────────
    // LISTENING HISTORY
    // ──────────────────────────────────────────────────────────

    override fun recordListenedTrack(track: Track) {
        val entry = "${track.artist} - ${track.title}"
        val history = getListenHistory().toMutableList()
        history.remove(entry)
        history.add(0, entry)
        val capped = history.take(MAX_LISTEN_HISTORY)
        prefs.edit().putString(KEY_LISTEN_HISTORY, capped.joinToString("||")).apply()
    }

    override fun getListeningHistoryQueries(): List<String> {
        val history = getListenHistory()
        if (history.isEmpty()) return emptyList()
        // History is most-recent-first — collect distinct artists from the TOP
        // of the list so suggestions follow what the user listens to NOW, not
        // what they played months ago.
        val artists = mutableListOf<String>()
        for (entry in history) {
            val parts = entry.split(" - ")
            if (parts.size >= 2) {
                val artist = parts[0].trim()
                if (artist.isNotBlank() && artists.none { it.equals(artist, ignoreCase = true) }) {
                    artists.add(artist)
                }
            }
            if (artists.size >= 5) break
        }
        return artists.map { "$it songs" }
    }

    override fun getRecentlyPlayedKeys(): Set<String> {
        // "artist|title" keys for the last 15 plays — used to keep fresh
        // suggestions from re-serving songs the user just heard.
        return getListenHistory().take(15).mapNotNull { entry ->
            val parts = entry.split(" - ")
            if (parts.size >= 2) {
                "${parts[0].trim().lowercase()}|${parts[1].trim().lowercase()}"
            } else null
        }.toSet()
    }

    private fun getListenHistory(): List<String> {
        val raw = prefs.getString(KEY_LISTEN_HISTORY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }

    // ──────────────────────────────────────────────────────────
    // STREAMING — Cloud Render Server primary + YouTube + JioSaavn fallback
    // ──────────────────────────────────────────────────────────

    private suspend fun resolveStreamFromCloudServer(videoId: String): String? = withContext(Dispatchers.IO) {
        val serverEndpoints = listOf(
            "https://api.cyrosonic.com/youtube/stream?id=$videoId",
            "https://cyrosonic.com/youtube/stream?id=$videoId"
        )
        for (endpoint in serverEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val json = org.json.JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val url = json.optString("url", "")
                            if (url.isNotBlank() && url.startsWith("http")) {
                                return@withContext url
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        null
    }

    override suspend fun getStreamingUrl(track: Track): String {
        // If downloaded offline, we use the local path (represented as an encrypted:// URI)
        if (track.isDownloaded && !track.localFilePath.isNullOrBlank()) {
            val base64Iv = track.encryptionIv ?: ""
            return Uri.parse("encrypted://play")
                .buildUpon()
                .appendQueryParameter("path", track.localFilePath)
                .appendQueryParameter("iv", base64Iv)
                .build()
                .toString()
        }

        // 1. If we already have a pre-populated valid streaming URL, return instantly (0ms)
        if (!track.streamingUrl.isNullOrBlank() && (track.streamingUrl.startsWith("http") || track.streamingUrl.startsWith("encrypted"))) {
            streamUrlCache.put(track.id, track.streamingUrl)
            return track.streamingUrl
        }

        // 2. Check in-memory cache
        val cached = streamUrlCache.get(track.id)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        val cleanTitle = track.title
            .replace(Regex("(?i)\\(official.*?\\)|\\[official.*?\\]|\\(lyric.*?\\)|\\[lyric.*?\\]|\\(audio.*?\\)|\\[audio.*?\\]|\\(video.*?\\)|\\[video.*?\\]|\\|.*"), "")
            .trim()
        val cleanArtist = if (track.artist.contains("Unknown Artist", ignoreCase = true) || track.artist.isBlank()) "" else track.artist
        val searchQuery = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle

        // 3. Direct YouTube Video ID check (length of 11 characters)
        if (track.id.length == 11) {
            try {
                // Primary: Cloud CyroSonic Render server resolver
                val cloudUrl = resolveStreamFromCloudServer(track.id)
                if (!cloudUrl.isNullOrBlank()) {
                    streamUrlCache.put(track.id, cloudUrl)
                    return cloudUrl
                }
            } catch (_: Exception) { }

            try {
                // Try YouTube Music InnerTube multi-client resolver first (fastest)
                val ytUrl = YouTubeInnerTube.getStreamUrl(track.id, okHttpClient)
                if (!ytUrl.isNullOrBlank() && ytUrl.startsWith("http")) {
                    streamUrlCache.put(track.id, ytUrl)
                    return ytUrl
                }
            } catch (_: Exception) { }

            try {
                // Fallback to NewPipe
                val ytUrl = NewPipeYouTubeResolver.resolveStreamUrl(track.id, okHttpClient)
                if (!ytUrl.isNullOrBlank() && ytUrl.startsWith("http")) {
                    streamUrlCache.put(track.id, ytUrl)
                    return ytUrl
                }
            } catch (_: Exception) { }
        }

        // 4. JioSaavn Title + Artist search fallback (320kbps CDNs, zero bot-detection)
        try {
            val saavnSongs = EchoSaavnService.searchSongs(searchQuery, okHttpClient)
            val bestSong = pickBestSaavnMatch(searchQuery, saavnSongs)
            if (bestSong != null) {
                val bestUrl = bestSong.downloadUrl
                    .filter { it.url.isNotBlank() }
                    .let { urls ->
                        when (requestedQualityPref()) {
                            "high" -> urls.firstOrNull { it.quality == "320kbps" }?.url
                                ?: urls.firstOrNull { it.quality == "160kbps" }?.url
                                ?: urls.lastOrNull()?.url
                            "saver" -> urls.firstOrNull { it.quality == "96kbps" }?.url
                                ?: urls.firstOrNull { it.quality == "160kbps" }?.url
                                ?: urls.firstOrNull { it.quality == "320kbps" }?.url
                                ?: urls.lastOrNull()?.url
                            else -> urls.firstOrNull { it.quality == "160kbps" }?.url
                                ?: urls.firstOrNull { it.quality == "320kbps" }?.url
                                ?: urls.lastOrNull()?.url
                        }
                    }
                if (!bestUrl.isNullOrBlank() && bestUrl.startsWith("http")) {
                    streamUrlCache.put(track.id, bestUrl)
                    return bestUrl
                }
            }
        } catch (_: Exception) { }

        // 5. Try YouTube Music search -> resolution
        try {
            val videoId = YouTubeInnerTube.searchVideoId(searchQuery, okHttpClient)
            if (videoId != null) {
                try {
                    val cloudUrl = resolveStreamFromCloudServer(videoId)
                    if (!cloudUrl.isNullOrBlank()) {
                        streamUrlCache.put(track.id, cloudUrl)
                        return cloudUrl
                    }
                } catch (_: Exception) { }

                // Try InnerTube first
                val ytUrl = YouTubeInnerTube.getStreamUrl(videoId, okHttpClient)
                if (!ytUrl.isNullOrBlank() && ytUrl.startsWith("http")) {
                    streamUrlCache.put(track.id, ytUrl)
                    return ytUrl
                }
                // Fallback to NewPipe
                val fallbackUrl = NewPipeYouTubeResolver.resolveStreamUrl(videoId, okHttpClient)
                if (!fallbackUrl.isNullOrBlank() && fallbackUrl.startsWith("http")) {
                    streamUrlCache.put(track.id, fallbackUrl)
                    return fallbackUrl
                }
            }
        } catch (_: Exception) { }

        // 6. Fallback: Try Echo's JioSaavn direct songId lookup
        try {
            val saavnUrl = EchoSaavnService.getBestStreamUrl(track.id, okHttpClient)
            if (!saavnUrl.isNullOrBlank() && saavnUrl.startsWith("http")) {
                streamUrlCache.put(track.id, saavnUrl)
                return saavnUrl
            }
        } catch (_: Exception) { }

        // 7. Fallback: Try old JioSaavn API
        try {
            val response = musicCatalogService?.getSongDetail(track.id)
            if (response != null && response.status) {
                val url = response.mediaUrls?.high
                    ?: response.mediaUrls?.medium
                    ?: response.mediaUrls?.low
                    ?: response.mediaUrl
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    streamUrlCache.put(track.id, url)
                    return url
                }
            }
        } catch (_: Exception) { }

        return ""
    }

    // ──────────────────────────────────────────────────────────
    // LYRICS (Multi-Source Engine: Musixmatch + LRCLIB + JioSaavn + Genius)
    // ──────────────────────────────────────────────────────────

    private var musixmatchUserToken: String? = null
    private val wordSyncCache = com.example.hunterxmusic.data.local.WordSyncCache(context)
    // Bounded: this was an unbounded ConcurrentHashMap that grew for the life
    // of the process.
    private val lyricsCache = android.util.LruCache<String, List<LyricLine>>(200)
    private val negativeLyricsCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * A lyrics candidate from one provider, pre-scored by [LyricsVerifier].
     * tier 0 = exact-lock sources (same audio ID / exact-match APIs)
     * tier 1 = search + metadata verification
     * tier 2 = search without usable metadata (duration sanity only)
     */
    private data class LyricCandidate(
        val lines: List<LyricLine>,
        val isSynced: Boolean,
        val score: Double,
        val tier: Int,
        /**
         * Set for candidates already vetted by a source-specific matcher
         * (LRCLIB via [LyricsVerifier.lrcLibScore]). Those skip the generic
         * acceptance thresholds, which are calibrated for providers whose
         * metadata is trustworthy — LRCLIB's artist field is not, so re-testing
         * it with the generic bar would throw away correct matches.
         */
        val preVerified: Boolean = false
    ) {
        val syncedTimestamps: List<Long> get() = lines.map { it.timestampMs }
    }

    private suspend fun getMusixmatchToken(): String? = withContext(Dispatchers.IO) {
        if (!musixmatchUserToken.isNullOrBlank()) return@withContext musixmatchUserToken
        try {
            val request = Request.Builder()
                .url("https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = org.json.JSONObject(response.body?.string() ?: "")
                val token = json.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
                if (!token.isNullOrBlank()) {
                    musixmatchUserToken = token
                    return@withContext token
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private suspend fun fetchMusixmatchLyrics(title: String, artist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        try {
            val token = getMusixmatchToken() ?: return@withContext null
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
            val url = "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&q_track=$encodedTitle&q_artist=$encodedArtist&app_id=web-desktop-app-v1.0&usertoken=$token"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val root = org.json.JSONObject(response.body?.string() ?: "")
                val macroCalls = root.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("macro_calls")
                
                // 1. Try subtitles_get (Line-by-line Synced LRC)
                val subtitlesGet = macroCalls?.optJSONObject("track.subtitles_get")
                val subtitleList = subtitlesGet?.optJSONObject("message")?.optJSONObject("body")?.optJSONArray("subtitle_list")
                if (subtitleList != null && subtitleList.length() > 0) {
                    val subtitleBody = subtitleList.getJSONObject(0).optJSONObject("subtitle")?.optString("subtitle_body")
                    if (!subtitleBody.isNullOrBlank()) {
                        val parsed = parseLrcString(subtitleBody)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }

                // 2. Try lyrics_get (Plain lyrics)
                val lyricsGet = macroCalls?.optJSONObject("track.lyrics_get")
                val lyricsBody = lyricsGet?.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("lyrics")?.optString("lyrics_body")
                if (!lyricsBody.isNullOrBlank()) {
                    val cleanLyrics = lyricsBody.replace(Regex("(?i)\\*\\*\\*\\*\\*\\*\\* This Lyrics is NOT for Commercial use.*"), "").trim()
                    if (cleanLyrics.isNotEmpty()) {
                        return@withContext parsePlainLyrics(cleanLyrics)
                    }
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private suspend fun fetchSaavnLyricsById(songId: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (songId.isBlank() || songId.length == 11) return@withContext null // Skip youtube video IDs
        val servers = listOf("https://saavn.dev", "https://saavn.echomusic.fun", "https://jiosaavn-api.mac-adityadav9532.workers.dev")
        for (server in servers) {
            try {
                val req = Request.Builder().url("$server/api/songs/$songId/lyrics").build()
                val resp = okHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val lyrJson = org.json.JSONObject(resp.body?.string() ?: "")
                    val lyricsHtml = lyrJson.optJSONObject("data")?.optString("lyrics")
                    if (!lyricsHtml.isNullOrBlank()) {
                        val cleanText = lyricsHtml.replace("<br>", "\n").replace("<br/>", "\n").replace(Regex("<.*?>"), "")
                        val parsed = parsePlainLyrics(cleanText)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }
            } catch (_: Exception) { }
        }
        return@withContext null
    }

    /**
     * Verified JioSaavn lyrics lookup: searches by query, then only accepts a
     * song whose name actually matches the requested title (first 5 hits scored).
     */
    private suspend fun fetchSaavnLyrics(query: String, expectedTitle: String, expectedArtist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val servers = listOf("https://saavn.dev", "https://saavn.echomusic.fun", "https://jiosaavn-api.mac-adityadav9532.workers.dev")
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        for (server in servers) {
            try {
                val searchUrl = "$server/api/search/songs?query=$encoded"
                val req = Request.Builder().url(searchUrl).build()
                val resp = okHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val json = org.json.JSONObject(resp.body?.string() ?: "")
                    val results = json.optJSONObject("data")?.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val limit = minOf(results.length(), 5)
                        for (i in 0 until limit) {
                            val song = results.getJSONObject(i)
                            val songId = song.optString("id")
                            val songName = song.optString("name")
                            if (songId.isBlank()) continue
                            val score = LyricsVerifier.candidateScore(expectedTitle, expectedArtist, songName, null)
                            if (!LyricsVerifier.isPlainAcceptance(score)) continue
                            fetchSaavnLyricsById(songId)?.let {
                                if (it.isNotEmpty()) return@withContext it
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return@withContext null
    }

    /**
     * Kugou synced-LRC lookup with title/singer verification where the API
     * exposes metadata on the candidate.
     */
    private data class KugouCandidate(val id: String, val accessKey: String, val score: Double)

    private suspend fun fetchKugouLyrics(query: String, expectedTitle: String, expectedArtist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "http://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=$enc"
            val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = org.json.JSONObject(resp.body?.string() ?: "")
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val limit = minOf(candidates.length(), 5)
                    var chosen: KugouCandidate? = null
                    for (i in 0 until limit) {
                        val c = candidates.getJSONObject(i)
                        val id = c.optString("id")
                        val accessKey = c.optString("accesskey")
                        if (id.isBlank() || accessKey.isBlank()) continue
                        // Kugou returns title/singer sometimes as ["", "value"] arrays, sometimes strings
                        val title = parseKugouMetaField(c, "title")
                        val singer = parseKugouMetaField(c, "singer")
                        val score = LyricsVerifier.candidateScore(expectedTitle, expectedArtist, title, singer)
                        if (chosen == null || score > chosen.score) {
                            chosen = KugouCandidate(id, accessKey, score)
                        }
                    }
                    if (chosen != null && LyricsVerifier.isSyncedAcceptance(chosen.score)) {
                        val downUrl = "http://lyrics.kugou.com/download?ver=1&client=pc&id=${chosen.id}&accesskey=${chosen.accessKey}&fmt=lrc&charset=utf8"
                        val downReq = Request.Builder().url(downUrl).header("User-Agent", "Mozilla/5.0").build()
                        val downResp = okHttpClient.newCall(downReq).execute()
                        if (downResp.isSuccessful) {
                            val downJson = org.json.JSONObject(downResp.body?.string() ?: "")
                            val base64Content = downJson.optString("content")
                            if (!base64Content.isNullOrBlank()) {
                                val decoded = String(android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                val parsed = parseLrcString(decoded)
                                if (parsed.isNotEmpty()) return@withContext parsed
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private fun parseKugouMetaField(obj: org.json.JSONObject, field: String): String? {
        return try {
            val arr = obj.optJSONArray(field)
            if (arr != null && arr.length() > 0) {
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("value") ?: arr.optString(it, null) }
                    .firstOrNull { !it.isNullOrBlank() && it != "null" }
            } else {
                obj.optString(field).takeIf { it.isNotBlank() && it != "null" }
            }
        } catch (_: Exception) { null }
    }

    /**
     * QQ Music synced-LRC lookup with songname/singer/interval verification.
     */
    private suspend fun fetchQQMusicLyrics(query: String, expectedTitle: String, expectedArtist: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$enc&format=json&p=1&n=5"
            val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = org.json.JSONObject(resp.body?.string() ?: "")
                val songList = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                if (songList != null && songList.length() > 0) {
                    var bestMid: String? = null
                    var bestScore = -1.0
                    for (i in 0 until songList.length()) {
                        val song = songList.getJSONObject(i)
                        val songMid = song.optString("songmid")
                        if (songMid.isBlank()) continue
                        val songName = song.optString("songname").takeIf { it.isNotBlank() }
                            ?: song.optString("name").takeIf { it.isNotBlank() }
                        val singers = song.optJSONArray("singer")?.let { arr ->
                            (0 until arr.length()).joinToString(", ") { arr.optJSONObject(it)?.optString("name") ?: "" }
                        }?.takeIf { it.isNotBlank() }
                        val intervalSec = song.optInt("interval", 0).takeIf { it > 0 }?.toDouble()
                        val score = LyricsVerifier.candidateScore(expectedTitle, expectedArtist, songName, singers, intervalSec)
                        if (score > bestScore) {
                            bestScore = score
                            bestMid = songMid
                        }
                    }
                    if (bestMid != null && LyricsVerifier.isSyncedAcceptance(bestScore)) {
                        val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$bestMid&format=json&nobase64=0"
                        val lyrReq = Request.Builder()
                            .url(lyricUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .header("Referer", "https://y.qq.com")
                            .build()
                        val lyrResp = okHttpClient.newCall(lyrReq).execute()
                        if (lyrResp.isSuccessful) {
                            val lyrJson = org.json.JSONObject(lyrResp.body?.string() ?: "")
                            val base64Lyric = lyrJson.optString("lyric")
                            if (!base64Lyric.isNullOrBlank()) {
                                val decoded = String(android.util.Base64.decode(base64Lyric, android.util.Base64.DEFAULT), Charsets.UTF_8)
                                val parsed = parseLrcString(decoded)
                                if (parsed.isNotEmpty()) return@withContext parsed
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private suspend fun fetchMegalobizLyrics(query: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.megalobiz.com/search/all?qry=$enc&display=more"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val regexHref = Regex("(?i)href=\"(/lrc/maker/[^\"]+)\"")
                val match = regexHref.find(html)
                if (match != null) {
                    val lrcPath = match.groupValues[1]
                    val detailUrl = "https://www.megalobiz.com$lrcPath"
                    val detailReq = Request.Builder().url(detailUrl).header("User-Agent", "Mozilla/5.0").build()
                    val detailResp = okHttpClient.newCall(detailReq).execute()
                    if (detailResp.isSuccessful) {
                        val detailHtml = detailResp.body?.string() ?: ""
                        val spanMatch = Regex("(?s)<span id=\"lrc_[^\"]+_details\">(.*?)</span>").find(detailHtml)
                        if (spanMatch != null) {
                            val rawLrc = spanMatch.groupValues[1]
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("<br>", "\n")
                                .replace("<br/>", "\n")
                            val parsed = parseLrcString(rawLrc)
                            if (parsed.isNotEmpty()) return@withContext parsed
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private suspend fun fetchOvhLyrics(artist: String, title: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (artist.isBlank() || title.isBlank()) return@withContext null
        try {
            val encArtist = java.net.URLEncoder.encode(artist, "UTF-8")
            val encTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val url = "https://api.lyrics.ovh/v1/$encArtist/$encTitle"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = org.json.JSONObject(resp.body?.string() ?: "")
                val lyrics = json.optString("lyrics")
                if (!lyrics.isNullOrBlank()) {
                    return@withContext parsePlainLyrics(lyrics)
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    /**
     * NetEase synced-LRC lookup. Scores the first search hits by name/artist/duration
     * and fetches lyrics only for the best verified song.
     */
    private suspend fun fetchNetEaseLyrics(query: String, expectedTitle: String, expectedArtist: String, trackDurationMs: Long): List<LyricLine>? = withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlposttag=&hlpretag=&type=1&offset=0&total=true&limit=5&s=$enc"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://music.163.com")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = org.json.JSONObject(resp.body?.string() ?: "")
                val songs = json.optJSONObject("result")?.optJSONArray("songs")
                if (songs != null && songs.length() > 0) {
                    var bestId = 0L
                    var bestScore = -1.0
                    for (i in 0 until songs.length()) {
                        val song = songs.getJSONObject(i)
                        val songId = song.optLong("id", 0L)
                        if (songId <= 0) continue
                        val name = song.optString("name").takeIf { it.isNotBlank() }
                        val artists = song.optJSONArray("artists")?.let { arr ->
                            (0 until arr.length()).joinToString(", ") { arr.optJSONObject(it)?.optString("name") ?: "" }
                        }?.takeIf { it.isNotBlank() }
                        val durationSec = song.optLong("duration", 0L).takeIf { it > 0 }?.let { it / 1000.0 }
                        val score = LyricsVerifier.candidateScore(expectedTitle, expectedArtist, name, artists, durationSec, trackDurationMs)
                        if (score > bestScore) {
                            bestScore = score
                            bestId = songId
                        }
                    }
                    if (bestId > 0 && LyricsVerifier.isSyncedAcceptance(bestScore)) {
                        val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$bestId&lv=-1&kv=-1&tv=-1"
                        val lyrReq = Request.Builder()
                            .url(lyricUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .header("Referer", "https://music.163.com")
                            .build()
                        val lyrResp = okHttpClient.newCall(lyrReq).execute()
                        if (lyrResp.isSuccessful) {
                            val lyrJson = org.json.JSONObject(lyrResp.body?.string() ?: "")
                            val lrcText = lyrJson.optJSONObject("lrc")?.optString("lyric")
                            if (!lrcText.isNullOrBlank()) {
                                val parsed = parseLrcString(lrcText)
                                if (parsed.isNotEmpty()) return@withContext parsed
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    override suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        audioSource: AudioSourceType,
        audioSourceId: String?,
        durationMs: Long
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val (cleanTitle, primaryArtist) = cleanLyricsQuery(title, artist)
        // Stash for the word-timed ladder (resolved source + explicit probe id)
        currentLyricsTrackId = trackId
        audioSourceIdProbe = audioSourceId

        // Resolve effective source information
        val resolved = resolvedAudioSourceMap[trackId]
        val effectiveSource = if (audioSource != AudioSourceType.UNKNOWN) audioSource else (resolved?.sourceType ?: if (trackId.length == 11 && !trackId.all { it.isDigit() }) AudioSourceType.YOUTUBE else AudioSourceType.UNKNOWN)
        val effectiveSourceId = if (!audioSourceId.isNullOrBlank()) audioSourceId else (resolved?.sourceId ?: if (trackId.length == 11 && !trackId.all { it.isDigit() }) trackId else null)

        val cacheKey = if (!effectiveSourceId.isNullOrBlank()) "$effectiveSource::$effectiveSourceId" else "$cleanTitle::$primaryArtist".lowercase().trim()
        lyricsCache.get(cacheKey)?.let { if (it.isNotEmpty()) return@withContext it }

        val fingerprint = wordSyncCache.buildFingerprint(cleanTitle, primaryArtist, durationMs)
        val cachedWordSync = wordSyncCache.getCachedAlignment(fingerprint)
        if (cachedWordSync != null && cachedWordSync.isNotEmpty()) {
            lyricsCache.put(cacheKey, cachedWordSync)
            return@withContext cachedWordSync
        }

        // Negative cache: avoid hammering 9 providers repeatedly when a track simply has no lyrics
        negativeLyricsCache[cacheKey]?.let { fetchedAt ->
            if (System.currentTimeMillis() - fetchedAt < NEGATIVE_LYRICS_TTL_MS) return@withContext emptyList()
        }

        fun syncedOrNull(lines: List<LyricLine>?): List<LyricLine>? {
            if (lines.isNullOrEmpty()) return null
            return lines.takeIf { it.size > 1 && it.any { line -> line.timestampMs > 0L } }
        }

        fun plainOrNull(lines: List<LyricLine>?): List<LyricLine>? {
            if (lines.isNullOrEmpty()) return null
            return lines.takeIf { it.isNotEmpty() }
        }

        fun sanitize(list: List<LyricLine>?): List<LyricLine>? {
            // Defensive cleanup applied to every candidate before it can reach
            // the UI. Timing markers are stripped from the WORDS — the timing
            // belongs in timestampMs, and a stray "[00:12.34]" showing up as
            // lyric text is the classic symptom of a provider handing us raw
            // LRC through a plain-text field.
            //
            // Blank lines are NOT discarded when they carry a timestamp:
            // LRCLIB encodes instrumental breaks exactly that way, e.g.
            //   [00:56.58]              <- blank, break starts here
            //   [01:09.95] या तो...     <- vocals resume 13s later
            // Dropping them threw that information away.
            val cleaned = list
                ?.map { line ->
                    val words = LyricsVerifier.stripTimingMarkers(line.words)
                    line.copy(words = words, isInstrumental = words.isEmpty())
                }
                ?.filter { it.words.isNotEmpty() || it.timestampMs > 0L }
                ?.distinctBy { it.timestampMs to it.words }
                ?.sortedBy { it.timestampMs }
                ?.takeIf { candidate -> candidate.any { it.words.isNotEmpty() } }
                ?: return null

            return LyricsVerifier.markInstrumentalBreaks(cleaned)
        }

        // ──────────────────────────────────────────────────────────
        // STEP 0: UNIVERSAL MULTI-SOURCE WORD-BY-WORD ENGINE (LrcMux)
        // Aggregates Apple Music, Spotify, Kugou, Musixmatch & NetEase
        // with microsecond-exact word and syllable timestamps.
        // ──────────────────────────────────────────────────────────
        try {
            val lrcMuxSynced = com.example.hunterxmusic.data.remote.LrcMuxLyricsService.getWordSyncedLyrics(cleanTitle, primaryArtist, durationMs, okHttpClient)
            if (!lrcMuxSynced.isNullOrEmpty() && lrcMuxSynced.size >= 4) {
                val cleaned = sanitize(lrcMuxSynced)
                if (cleaned != null) {
                    lyricsCache.put(cacheKey, cleaned)
                    return@withContext cleaned
                }
            }
        } catch (_: Exception) { }

        // ──────────────────────────────────────────────────────────
        // STEP 0.5: DIRECT STUDIO KRC MASTER ENGINE (Fallback 1)
        // ──────────────────────────────────────────────────────────
        try {
            val krcWordSynced = com.example.hunterxmusic.data.remote.KugouKrcLyricsService.getWordSyncedLyrics(cleanTitle, primaryArtist, okHttpClient)
            if (!krcWordSynced.isNullOrEmpty() && krcWordSynced.size >= 4) {
                val cleaned = sanitize(krcWordSynced)
                if (cleaned != null) {
                    lyricsCache.put(cacheKey, cleaned)
                    return@withContext cleaned
                }
            }
        } catch (_: Exception) { }

        // ──────────────────────────────────────────────────────────
        // STEP 1: SAME-SOURCE LOCK — real millisecond cues from the
        // exact audio the user is hearing.
        // ──────────────────────────────────────────────────────────
        val sameSourceCandidates = mutableListOf<LyricCandidate>()
        if (effectiveSource == AudioSourceType.YOUTUBE || effectiveSource == AudioSourceType.NEWPIPE || (!effectiveSourceId.isNullOrBlank() && effectiveSourceId.length == 11 && !effectiveSourceId.all { it.isDigit() })) {
            val ytVideoId = effectiveSourceId ?: trackId
            try {
                val ytTimed = com.example.hunterxmusic.data.remote.YouTubeInnerTube.getYouTubeTimedLyrics(ytVideoId, okHttpClient)
                val timed = sanitize(syncedOrNull(ytTimed))
                if (timed != null && LyricsVerifier.isDurationPlausible(timed.map { it.timestampMs }, durationMs)) {
                    sameSourceCandidates.add(LyricCandidate(timed, isSynced = true, score = 1.0, tier = 0))
                } else {
                    // Plain description lyrics for this exact video — honest unsynced tier-0 fallback
                    val plain = sanitize(plainOrNull(ytTimed))
                    if (plain != null && LyricsVerifier.looksLikeLyrics(plain.map { it.words })) {
                        sameSourceCandidates.add(LyricCandidate(plain, isSynced = false, score = 1.0, tier = 0))
                    }
                }
            } catch (_: Exception) { }
        } else if (effectiveSource == AudioSourceType.JIOSAAVN && !effectiveSourceId.isNullOrBlank()) {
            try {
                val saavnLyrics = sanitize(plainOrNull(fetchSaavnLyricsById(effectiveSourceId)))
                if (saavnLyrics != null) {
                    sameSourceCandidates.add(LyricCandidate(saavnLyrics, isSynced = false, score = 1.0, tier = 0))
                }
            } catch (_: Exception) { }
        }

        // ──────────────────────────────────────────────────────────
        // STEP 2: MULTI-SOURCE PARALLEL AGGREGATOR
        // Every candidate is scored before it is eligible. A wrong-song
        // karaoke is worse than no karaoke.
        // ──────────────────────────────────────────────────────────
        val durationSec = if (durationMs > 30_000L) durationMs / 1000.0 else null
        // With no usable duration the plausibility check cannot reject
        // anything, so score thresholds tighten instead.
        val durationKnown = LyricsVerifier.isDurationUsable(durationMs)

        fun buildFrom(lines: List<LyricLine>?, score: Double, tier: Int, preVerified: Boolean = false): LyricCandidate? {
            val clean = sanitize(lines) ?: return null
            val isSynced = clean.size > 1 && clean.any { it.timestampMs > 0L }
            return LyricCandidate(clean, isSynced, score, tier, preVerified)
        }

        /**
         * Picks the best-matching record out of a batch of LRCLIB hits.
         *
         * Scored with [LyricsVerifier.lrcLibScore] (duration-led), NOT the
         * generic candidateScore, because LRCLIB's artistName is often wrong
         * ("starboy" returns The Weeknd's track with artistName "Starboy") and
         * the usual artist veto would discard correct lyrics.
         */
        fun bestLrcLibCandidate(
            results: List<com.example.hunterxmusic.data.remote.model.LrcResponse>
        ): LyricCandidate? {
            var bestSynced: Pair<List<LyricLine>, Double>? = null
            var bestPlain: Pair<List<LyricLine>, Double>? = null

            for (result in results) {
                if (result.instrumental == true) continue
                val score = LyricsVerifier.lrcLibScore(
                    cleanTitle, primaryArtist,
                    result.bestTitle, result.artistName,
                    result.duration, durationMs
                )
                if (score <= 0.0) continue

                val synced = result.syncedLyrics
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parseLrcString(it) }
                    ?.takeIf { parsed -> parsed.size > 1 && parsed.any { it.timestampMs > 0L } }
                    ?.let { parsed ->
                        // Record carries its own edit's duration — if the playing
                        // stream is a slightly different cut, re-base the whole
                        // sheet onto the real length so lines never drift.
                        LyricsVerifier.rescaleTimestamps(
                            parsed,
                            result.duration?.let { d -> (d * 1000).toLong() },
                            durationMs
                        )
                    }

                if (synced != null && LyricsVerifier.isLrcLibSyncedAcceptance(score)) {
                    if (LyricsVerifier.isDurationPlausible(synced.map { it.timestampMs }, durationMs) &&
                        (bestSynced == null || score > bestSynced.second)
                    ) {
                        bestSynced = synced to score
                    }
                    continue
                }

                val plain = result.plainLyrics
                    ?.takeIf { it.isNotBlank() }
                    ?.let { parsePlainLyrics(it) }
                if (plain != null && LyricsVerifier.isLrcLibPlainAcceptance(score) &&
                    (bestPlain == null || score > bestPlain.second)
                ) {
                    bestPlain = plain to score
                }
            }

            // Synced always beats plain — karaoke is the point.
            bestSynced?.let { return buildFrom(it.first, it.second, tier = 0, preVerified = true) }
            bestPlain?.let { return buildFrom(it.first, it.second, tier = 1, preVerified = true) }
            return null
        }

        // ── FAST PATH: LRCLIB Sequential Ladder ──────────────────
        // LRCLIB's contract (https://lrclib.net/api-docs): requests MUST be
        // sequential with 200-500ms spacing and an identifying User-Agent.
        // The old 4-way parallel blast tripped the rate limiter (429), so the
        // primary karaoke source silently died and sheets degraded to fitted
        // timestamps that never line up with the seekbar. Ladder instead:
        //  1. exact /api/get with duration (most precise match)
        //  2. field-scoped search (track_name + artist_name)
        //  3. free-text search (title + artist)
        // Each step returns the moment a synced sheet passes scoring, and a
        // 429 backs off with a delay before the next attempt.
        val fastCandidate = withTimeoutOrNull(LRCLIB_FAST_PATH_TIMEOUT_MS) {
            // Step 1: exact lookup, duration-aware
            val exact = lrcLibCall {
                listOf(lrcLibService.getLyrics(primaryArtist, cleanTitle, null, durationSec))
            }
            val exactCandidate = exact?.let { bestLrcLibCandidate(it) }
            if (exactCandidate?.isSynced == true) {
                exactCandidate
            } else {
                // Step 2: field-scoped search
                val byFields = lrcLibCall(250) {
                    lrcLibService.searchLyricsByFields(
                        cleanTitle,
                        primaryArtist.takeIf { it.isNotBlank() }
                    )
                }
                val fieldsCandidate = byFields?.let { bestLrcLibCandidate(it) }
                if (fieldsCandidate?.isSynced == true) {
                    fieldsCandidate
                } else {
                    // Step 3: free-text search
                    val freeText = lrcLibCall(250) {
                        lrcLibService.searchLyrics(
                            if (primaryArtist.isNotBlank()) "$cleanTitle $primaryArtist" else cleanTitle
                        )
                    }
                    freeText?.let { bestLrcLibCandidate(it) }
                }
            }
        }

        if (fastCandidate != null && fastCandidate.isSynced) {
            // Word-timed outranks every line-level source, so before committing
            // to this synced sheet give YouTube's ASR stream one quick shot.
            // Miss (no captions / timeout) → line-level sheet as before; hit →
            // true word-by-word karaoke from the exact video being played.
            val wordLines = withTimeoutOrNull(6_000L) {
                fetchWordTimedLyricsResolved(cleanTitle, primaryArtist, durationMs, durationKnown)
            }
            if (!wordLines.isNullOrEmpty()) {
                lyricsCache.put(cacheKey, wordLines)
                negativeLyricsCache.remove(cacheKey)
                return@withContext wordLines
            }
            lyricsCache.put(cacheKey, fastCandidate.lines)
            negativeLyricsCache.remove(cacheKey)
            return@withContext fastCandidate.lines
        }

        val searchCandidates = kotlinx.coroutines.supervisorScope {
            // Pure builder — deferreds return their candidate and results are
            // collected after join, so nothing mutable is shared across threads.
            fun buildCandidate(lines: List<LyricLine>?, score: Double, tier: Int, preVerified: Boolean = false): LyricCandidate? =
                buildFrom(lines, score, tier, preVerified)

            /**
             * Every provider runs behind its own deadline. Nine of them used to
             * race with no timeout at all, so one slow or hanging host stalled
             * the entire lyrics screen.
             */
            fun provider(block: suspend () -> LyricCandidate?) = async {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                    try { block() } catch (_: Exception) { null }
                }
            }

            // ── WORD-LEVEL: YouTube ASR caption stream (live-tested) ──────
            // Real per-word millisecond cues from the exact video being
            // played. Tier -1 + preVerified: when this lands it outranks
            // every line-level source, because nothing can beat the audio's
            // own word timing. Returns null (cheap) when the video has no
            // word-timed captions — then the normal engines run as before.
            val ytWordTimedDeferred = provider {
                fetchWordTimedLyricsResolved(cleanTitle, primaryArtist, durationMs, durationKnown)
                    ?.let { lines -> buildCandidate(lines, score = 0.99, tier = -1, preVerified = true) }
            }

            // ── LRCLIB: the primary source of real synced timestamps ──────
            // A + B. LRCLIB already ran in the fast path above. Only its
            // free-text search is retried here, as a last attempt for tracks
            // whose title/artist fields didn't line up. Paced sequentially.
            val lrcLibSearchDeferred = provider {
                val freeText = lrcLibCall(300) {
                    lrcLibService.searchLyrics(
                        listOf(cleanTitle, primaryArtist).filter { it.isNotBlank() }.joinToString(" ")
                    )
                }
                freeText?.let { bestLrcLibCandidate(it) }
            }

            // C. YouTube Music timed lyrics via verified search lookup
            val ytSearchDeferred = provider {
                val (ytTracks, _) = com.example.hunterxmusic.data.remote.YouTubeInnerTube.searchTracks("$cleanTitle $primaryArtist", okHttpClient)
                var found: LyricCandidate? = null
                for (ytTrack in ytTracks.take(5)) {
                    val score = LyricsVerifier.candidateScore(
                        cleanTitle, primaryArtist, ytTrack.title, ytTrack.artist,
                        ytTrack.durationMs.takeIf { it > 0L }?.let { it / 1000.0 }, durationMs
                    )
                    if (!LyricsVerifier.isSyncedAcceptance(score, durationKnown)) continue
                    val timed = com.example.hunterxmusic.data.remote.YouTubeInnerTube.getYouTubeTimedLyrics(ytTrack.id, okHttpClient)
                    val synced = sanitize(syncedOrNull(timed))?.let {
                        LyricsVerifier.rescaleTimestamps(
                            it,
                            ytTrack.durationMs.takeIf { d -> d > 0L },
                            durationMs
                        )
                    }
                    if (synced != null && LyricsVerifier.isDurationPlausible(synced.map { it.timestampMs }, durationMs)) {
                        found = buildCandidate(synced, score, tier = 1)
                        break
                    }
                }
                found
            }

            // D. NetEase Timed LRC Engine (verified)
            val netEaseDeferred = provider {
                val lines = fetchNetEaseLyrics("$cleanTitle $primaryArtist", cleanTitle, primaryArtist, durationMs)
                    ?: fetchNetEaseLyrics(cleanTitle, cleanTitle, primaryArtist, durationMs)
                buildCandidate(lines, score = 0.72, tier = 1)
            }

            // E. Musixmatch exact query (q_track + q_artist)
            val musixmatchDeferred = provider {
                val lines = fetchMusixmatchLyrics(cleanTitle, primaryArtist) ?: fetchMusixmatchLyrics(cleanTitle, "")
                buildCandidate(lines, score = 0.85, tier = 1)
            }

            // F. JioSaavn search (verified plain text)
            val saavnQueryDeferred = provider {
                val lines = fetchSaavnLyrics("$cleanTitle $primaryArtist", cleanTitle, primaryArtist)
                    ?: fetchSaavnLyrics(cleanTitle, cleanTitle, primaryArtist)
                buildCandidate(lines, score = 0.68, tier = 1)
            }

            // G. Kugou Music Synced LRC (verified where metadata available)
            val kugouDeferred = provider {
                val lines = fetchKugouLyrics("$cleanTitle $primaryArtist", cleanTitle, primaryArtist)
                    ?: fetchKugouLyrics(cleanTitle, cleanTitle, primaryArtist)
                buildCandidate(lines, score = 0.66, tier = 2)
            }

            // H. QQ Music Synced LRC (verified)
            val qqMusicDeferred = provider {
                val lines = fetchQQMusicLyrics("$cleanTitle $primaryArtist", cleanTitle, primaryArtist)
                    ?: fetchQQMusicLyrics(cleanTitle, cleanTitle, primaryArtist)
                buildCandidate(lines, score = 0.70, tier = 2)
            }

            // I. Megalobiz Synced LRC (no metadata — duration sanity only)
            val megalobizDeferred = provider {
                val lines = fetchMegalobizLyrics("$cleanTitle $primaryArtist")
                    ?: fetchMegalobizLyrics(cleanTitle)
                buildCandidate(lines, score = 0.62, tier = 2)
            }

            val all = listOf(
                ytWordTimedDeferred,
                lrcLibSearchDeferred, ytSearchDeferred, netEaseDeferred,
                musixmatchDeferred, saavnQueryDeferred, kugouDeferred, qqMusicDeferred, megalobizDeferred
            )
            // Whole-fan-out deadline on top of the per-provider ones, so lyrics
            // always resolve in bounded time even if several hosts are slow.
            withTimeoutOrNull(LYRICS_TOTAL_TIMEOUT_MS) {
                all.mapNotNull { job -> try { job.await() } catch (_: Exception) { null } }
            }.also { all.forEach { it.cancel() } } ?: emptyList()
        }

        val allCandidates = sameSourceCandidates + listOfNotNull(fastCandidate) + searchCandidates

        // ──────────────────────────────────────────────────────────
        // SELECTION: best verified SYNCED candidate wins. Tiers first
        // (same-source > exact API > verified search), then score.
        // ──────────────────────────────────────────────────────────
        val syncedWinner = allCandidates
            .filter { it.isSynced && (it.preVerified || LyricsVerifier.isSyncedAcceptance(it.score, durationKnown)) }
            .filter { LyricsVerifier.isDurationPlausible(it.syncedTimestamps, durationMs) }
            .sortedWith(compareBy({ it.tier }, { -it.score }))
            .firstOrNull()

        if (syncedWinner != null) {
            val aligned = com.example.hunterxmusic.data.lyrics.WordAlignmentEngine.alignLyrics(syncedWinner.lines, durationMs)
            lyricsCache.put(cacheKey, aligned)
            wordSyncCache.saveAlignment(fingerprint, aligned)
            negativeLyricsCache.remove(cacheKey)
            return@withContext aligned
        }

        // Plain-text reading sheet — when the track duration is known, fit
        // proportional Estimated timestamps so the Apple-Music-style follow
        // animation still works (clearly labeled as estimated in the UI).
        val plainWinner = allCandidates
            .filter { !it.isSynced && (it.preVerified || LyricsVerifier.isPlainAcceptance(it.score, durationKnown)) }
            .filter { LyricsVerifier.looksLikeLyrics(it.lines.map { line -> line.words }) }
            .sortedWith(compareBy({ it.tier }, { -it.score }))
            .firstOrNull()

        if (plainWinner != null) {
            val finalLines = if (durationMs > 30_000L) {
                estimatePlainSync(plainWinner.lines, durationMs)
            } else {
                plainWinner.lines
            }
            val aligned = com.example.hunterxmusic.data.lyrics.WordAlignmentEngine.alignLyrics(finalLines, durationMs)
            lyricsCache.put(cacheKey, aligned)
            wordSyncCache.saveAlignment(fingerprint, aligned)
            negativeLyricsCache.remove(cacheKey)
            return@withContext aligned
        }

        negativeLyricsCache[cacheKey] = System.currentTimeMillis()
        return@withContext emptyList()
    }

    /**
     * Song → YouTube videoId → WORD-TIMED captions, the full fast ladder:
     * 1) resolved stream sourceId (the exact video being played — instant),
     * 2) raw trackId when it's already an 11-char video id,
     * 3) InnerTube search for "$title $artist" with duration verification,
     *    so even JioSaavn/library tracks resolve a video in one call.
     * Returns REAL per-word cues (YouTube ASR json3 segs[].tOffsetMs) or null
     * — never fabricated karaoke.
     */
    private suspend fun fetchWordTimedLyricsResolved(
        cleanTitle: String,
        primaryArtist: String,
        durationMs: Long,
        durationKnown: Boolean
    ): List<LyricLine>? {
        val resolvedSource = try { getResolvedAudioSource(currentLyricsTrackId ?: return null) } catch (_: Exception) { null }
        val probeIds = listOfNotNull(
            resolvedSource?.sourceId?.takeIf { it.length == 11 },
            audioSourceIdProbe?.takeIf { it.length == 11 },
            currentLyricsTrackId?.takeIf { it.length == 11 }
        ).distinct()

        for (id in probeIds) {
            try {
                val direct = com.example.hunterxmusic.data.remote.YouTubeInnerTube
                    .getYouTubeWordTimedLyrics(id, okHttpClient)
                if (!direct.isNullOrEmpty()) return direct
            } catch (_: Exception) { }
        }

        // Search ladder: one InnerTube search resolves title→videoId fast.
        return try {
            val (ytTracks, _) = com.example.hunterxmusic.data.remote.YouTubeInnerTube
                .searchTracks("$cleanTitle $primaryArtist", okHttpClient)
            for (ytTrack in ytTracks.take(4)) {
                val score = LyricsVerifier.candidateScore(
                    cleanTitle, primaryArtist, ytTrack.title, ytTrack.artist,
                    ytTrack.durationMs.takeIf { it > 0L }?.let { it / 1000.0 }, durationMs
                )
                if (!LyricsVerifier.isSyncedAcceptance(score, durationKnown)) continue
                val lines = com.example.hunterxmusic.data.remote.YouTubeInnerTube
                    .getYouTubeWordTimedLyrics(ytTrack.id, okHttpClient)
                    ?: continue
                if (!lines.isNullOrEmpty()) return lines
            }
            null
        } catch (_: Exception) { null }
    }

    /** Track id currently fetching lyrics — lets the word-ladder resolve its stream source. */
    @Volatile
    private var currentLyricsTrackId: String? = null

    @Volatile
    private var audioSourceIdProbe: String? = null

    /**
     * Fits plain lyric lines onto the track's real timeline: a short intro,
     * weighted distribution across the vocal window, a small outro. Marked
     * [com.example.hunterxmusic.domain.model.LyricLine.isEstimated] so the
     * UI can badge it honestly — this is a duration-fit, not a real LRC.
     */
    private fun estimatePlainSync(lines: List<LyricLine>, durationMs: Long): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val weights = lines.map { line ->
            val words = line.words.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
            (words * 1.6f + line.words.length * 0.12f).coerceAtLeast(1.2f)
        }
        val totalWeight = weights.sum().coerceAtLeast(1.0f)

        val introMs = (durationMs * 0.08f).toLong().coerceIn(3_000L, 12_000L)
        val outroMs = (durationMs * 0.03f).toLong().coerceAtMost(8_000L)
        val singingMs = (durationMs - introMs - outroMs).coerceAtLeast(30_000L)

        var currentMs = introMs
        return lines.mapIndexed { index, line ->
            val estimated = LyricLine(currentMs, line.words, isEstimated = true, wordCues = line.wordCues)
            val lineShare = (weights[index] / totalWeight) * singingMs
            currentMs += lineShare.toLong().coerceIn(1_200L, 9_000L)
            estimated
        }
    }

    @Volatile
    private var lrcLibRateLimitedUntilMs = 0L

    /**
     * LRCLIB request wrapper honoring their API contract: optional pacing
     * delay before the call, and a 429 (rate limited) trips the circuit breaker
     * for 30s so the multi-engine fan-out takes over instantly.
     */
    private suspend fun <T> lrcLibCall(stepDelayMs: Long = 0L, block: suspend () -> T?): T? {
        if (System.currentTimeMillis() < lrcLibRateLimitedUntilMs) return null
        if (stepDelayMs > 0L) delay(stepDelayMs)
        return try {
            block()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 429) {
                lrcLibRateLimitedUntilMs = System.currentTimeMillis() + 30_000L
                null
            } else null
        } catch (_: Exception) { null }
    }

    private fun cleanLyricsQuery(title: String, artist: String): Pair<String, String> {
        var cleanTitle = title
            .replace(Regex("(?i)\\[.*?\\]"), "")
            .replace(Regex("(?i)\\(from\\s*.*?\\)"), "")
            .replace(Regex("(?i)\\(feat.*?\\)"), "")
            .replace(Regex("(?i)\\(ft.*?\\)"), "")
            .replace(Regex("(?i)\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\(audio.*?\\)"), "")
            .replace(Regex("(?i)\\(video.*?\\)"), "")
            .replace(Regex("(?i)\\(lyric.*?\\)"), "")
            .replace(Regex("(?i)\\(full\\s*song.*?\\)"), "")
            .replace(Regex("(?i)\\b(official|music|video|audio|lyrics|lyric|hd|4k|remix|cover|live|visualizer|lrc)\\b"), "")
            .trim()

        // Handle "Title - Movie" or "Artist - Title"
        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ")
            if (parts.size >= 2) {
                val p0 = parts[0].trim()
                val p1 = parts[1].trim()
                if (artist.contains(p0, ignoreCase = true)) {
                    cleanTitle = p1
                } else {
                    cleanTitle = p0
                }
            }
        } else if (cleanTitle.contains("-")) {
            val parts = cleanTitle.split("-")
            if (parts.size >= 2) {
                val p0 = parts[0].trim()
                val p1 = parts[1].trim()
                if (artist.contains(p0, ignoreCase = true)) {
                    cleanTitle = p1
                } else {
                    cleanTitle = p0
                }
            }
        }

        // Extract Primary Artist
        var cleanArtist = artist
            .replace(Regex("(?i)\\b-\\s*Topic\\b"), "")
            .replace(Regex("(?i)\\bvevo\\b"), "")
            .replace(Regex("(?i)\\bofficial\\b"), "")
            .trim()

        val separators = listOf(",", "&", "feat.", "feat", "ft.", "ft", " x ", " / ", " and ")
        for (sep in separators) {
            if (cleanArtist.contains(sep, ignoreCase = true)) {
                cleanArtist = cleanArtist.split(Regex("(?i)" + java.util.regex.Pattern.quote(sep)))[0].trim()
            }
        }

        cleanTitle = cleanTitle.replace(Regex("[_\\|#~*]"), " ").replace(Regex("\\s+"), " ").trim()
        cleanArtist = cleanArtist.replace(Regex("[_\\|#~*]"), " ").replace(Regex("\\s+"), " ").trim()

        return Pair(cleanTitle, cleanArtist)
    }

    private fun parsePlainLyrics(plainText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        plainText.lineSequence().forEach { line ->
            // Metadata tags ([ti:], [ar:], [by:]…) are dropped, but timing
            // markers are only STRIPPED — a provider that returns LRC through a
            // plain-text field should still give us readable words instead of
            // an empty sheet.
            val withoutTiming = LyricsVerifier.stripTimingMarkers(line)
            if (withoutTiming.isEmpty()) return@forEach
            if (withoutTiming.startsWith("[") && withoutTiming.contains(":")) return@forEach
            lines.add(LyricLine(0L, withoutTiming))
        }
        return lines
    }

    private fun parseLrcString(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        var globalOffsetMs = 0L

        // Check for [offset:+/-ms] tag
        val offsetRegex = Regex("(?i)\\[offset:\\s*([+-]?\\d+)\\]")
        offsetRegex.find(lrcText)?.let {
            globalOffsetMs = it.groupValues[1].toLongOrNull() ?: 0L
        }

        val timestampPattern = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

        lrcText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") || 
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[offset:")) {
                return@forEach
            }

            val matches = timestampPattern.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val rawContent = timestampPattern.replace(line, "").trim()
                val isInstrumentalTag = rawContent.equals("[instrumental]", ignoreCase = true) || 
                                       rawContent.equals("(instrumental)", ignoreCase = true) ||
                                       rawContent.equals("instrumental", ignoreCase = true)
                val content = if (isInstrumentalTag) "" else rawContent

                for (match in matches) {
                    val min = match.groupValues[1].toLongOrNull() ?: 0L
                    val sec = match.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = match.groupValues[3].orEmpty()
                    val fracMs = when (fracStr.length) {
                        1 -> fracStr.toLong() * 100
                        2 -> fracStr.toLong() * 10
                        3 -> fracStr.toLong()
                        else -> 0L
                    }
                    val timestampMs = ((min * 60 + sec) * 1000 + fracMs - globalOffsetMs).coerceAtLeast(0L)
                    lines.add(LyricLine(timestampMs = timestampMs, words = content, isInstrumental = content.isEmpty()))
                }
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    // ──────────────────────────────────────────────────────────
    // DOWNLOAD (encrypted offline)
    // ──────────────────────────────────────────────────────────

    override fun downloadTrack(track: Track): Flow<DownloadStatus> = flow<DownloadStatus> {
        _activeDownloads.update { it + (track.id to DownloadProgress(track.id, 0f, 0L, 0L)) }
        emit(DownloadStatus.Started)
        val url = getStreamingUrl(track)
        if (url.isBlank()) {
            _activeDownloads.update { it - track.id }
            emit(DownloadStatus.Error(IOException("Could not resolve streaming URL")))
            return@flow
        }

        val request = Request.Builder().url(url).build()
        try {
            val existing = trackDao.getTrackById(track.id)
            val isLiked = existing?.isLiked ?: track.isLiked

            trackDao.insertTrack(
                TrackEntity(
                    id = track.id, title = track.title, artist = track.artist,
                    album = track.album, albumArtUrl = track.albumArtUrl,
                    durationMs = track.durationMs, onlineUrl = url,
                    localFilePath = null, encryptionIv = null,
                    downloadState = DownloadState.DOWNLOADING,
                    isLiked = isLiked,
                    timestamp = existing?.timestamp ?: System.currentTimeMillis()
                )
            )

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Failed download: ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val totalBytes = body.contentLength()

            val file = File(context.filesDir, "track_${track.id}.enc")
            val fileOut = FileOutputStream(file)
            val encryptResult = cryptoManager.getEncryptingStream(fileOut)
            val cipherOut = encryptResult.outputStream
            val base64Iv = Base64.encodeToString(encryptResult.iv, Base64.NO_WRAP)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0

            body.byteStream().use { input ->
                cipherOut.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = totalBytesRead.toFloat() / totalBytes
                            _activeDownloads.update { it + (track.id to DownloadProgress(track.id, progress, totalBytesRead, totalBytes)) }
                            emit(DownloadStatus.Progress(progress, totalBytesRead, totalBytes))
                        }
                    }
                }
            }

            val localPath = file.absolutePath
            trackDao.insertTrack(
                TrackEntity(
                    id = track.id, title = track.title, artist = track.artist,
                    album = track.album, albumArtUrl = track.albumArtUrl,
                    durationMs = track.durationMs, onlineUrl = url,
                    localFilePath = localPath, encryptionIv = base64Iv,
                    downloadState = DownloadState.COMPLETED_OFFLINE,
                    isLiked = isLiked,
                    timestamp = existing?.timestamp ?: System.currentTimeMillis()
                )
            )
            _activeDownloads.update { it - track.id }
            emit(DownloadStatus.Success(localPath, base64Iv))
            showDownloadNotification(track.title)
        } catch (e: Exception) {
            try {
                val file = java.io.File(context.filesDir, "track_${track.id}.enc")
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) { }
            _activeDownloads.update { it - track.id }
            val caughtExisting = trackDao.getTrackById(track.id)
            val isLiked = caughtExisting?.isLiked ?: track.isLiked
            // Persist the freshly resolved URL (not the possibly-expired
            // track.streamingUrl from search) so a retry hits a live CDN link.
            trackDao.insertTrack(
                TrackEntity(
                    id = track.id, title = track.title, artist = track.artist,
                    album = track.album, albumArtUrl = track.albumArtUrl,
                    durationMs = track.durationMs, onlineUrl = url,
                    localFilePath = null, encryptionIv = null,
                    downloadState = DownloadState.STREAMING,
                    isLiked = isLiked,
                    timestamp = caughtExisting?.timestamp ?: System.currentTimeMillis()
                )
            )
            emit(DownloadStatus.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun showDownloadNotification(trackTitle: String) {
        val channelId = "hunterx_downloads"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Downloads",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("\"$trackTitle\" has been downloaded offline.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            notificationManager.notify(trackTitle.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission not granted
        }
    }

    override fun getOfflineTracks(): Flow<List<Track>> {
        return trackDao.getOfflineTracksFlow().map { entities ->
            entities.map { entity ->
                Track(
                    id = entity.id, title = entity.title, artist = entity.artist,
                    album = entity.album, albumArtUrl = entity.albumArtUrl,
                    durationMs = entity.durationMs, streamingUrl = entity.onlineUrl,
                    localFilePath = entity.localFilePath, isDownloaded = true,
                    encryptionIv = entity.encryptionIv, isLiked = entity.isLiked
                )
            }
        }
    }

    override fun getTrack(trackId: String): Flow<Track?> {
        return trackDao.getAllTracksFlow().map { list ->
            val entity = list.firstOrNull { it.id == trackId } ?: return@map null
            Track(
                id = entity.id, title = entity.title, artist = entity.artist,
                album = entity.album, albumArtUrl = entity.albumArtUrl,
                durationMs = entity.durationMs, streamingUrl = entity.onlineUrl,
                localFilePath = entity.localFilePath,
                isDownloaded = entity.downloadState == DownloadState.COMPLETED_OFFLINE,
                encryptionIv = entity.encryptionIv, isLiked = entity.isLiked
            )
        }
    }

    override fun getLikedTracks(): Flow<List<Track>> {
        return trackDao.getLikedTracksFlow().map { entities ->
            entities.map { entity ->
                Track(
                    id = entity.id, title = entity.title, artist = entity.artist,
                    album = entity.album, albumArtUrl = entity.albumArtUrl,
                    durationMs = entity.durationMs, streamingUrl = entity.onlineUrl,
                    localFilePath = entity.localFilePath,
                    isDownloaded = entity.downloadState == DownloadState.COMPLETED_OFFLINE,
                    encryptionIv = entity.encryptionIv, isLiked = true
                )
            }
        }
    }

    override suspend fun toggleLikeTrack(track: Track) {
        val existing = trackDao.getTrackById(track.id)
        if (existing != null) {
            trackDao.updateLikedStatus(track.id, !existing.isLiked)
        } else {
            trackDao.insertTrack(
                TrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    albumArtUrl = track.albumArtUrl,
                    durationMs = track.durationMs,
                    onlineUrl = track.streamingUrl,
                    localFilePath = null,
                    encryptionIv = null,
                    downloadState = DownloadState.STREAMING,
                    isLiked = true,
                    timestamp = existing?.timestamp ?: System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun deleteTrack(track: Track) {
        val existing = trackDao.getTrackById(track.id)
        if (existing != null) {
            existing.localFilePath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicRepositoryImpl", "Failed to delete offline file: ${e.message}")
                }
            }
            if (existing.isLiked) {
                trackDao.insertTrack(
                    existing.copy(
                        localFilePath = null,
                        encryptionIv = null,
                        downloadState = DownloadState.STREAMING
                    )
                )
            } else {
                trackDao.deleteTrack(existing)
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────

    /**
     * Streaming quality chosen in More → STREAMING QUALITY. Honored here by
     * the Saavn/Echo fallback tiers so the selector actually changes audio.
     */
    private fun requestedQualityPref(): String {
        return try {
            context.getSharedPreferences("stream_quality", android.content.Context.MODE_PRIVATE)
                .getString("q", "medium") ?: "medium"
        } catch (_: Exception) { "medium" }
    }

    /**
     * Picks the Saavn result that actually matches the requested title/artist
     * by token overlap — the old code always took saavnSongs[0], which could
     * serve a wrong recording when the search API fuzzed the match.
     */
    private fun pickBestSaavnMatch(query: String, songs: List<EchoSaavnSong>): EchoSaavnSong? {
        if (songs.isEmpty()) return null
        if (songs.size == 1) return songs[0]
        val tokens = query.lowercase().trim()
            .split(Regex("[^a-z0-9\\p{L}]+"))
            .filter { it.length > 1 }
            .toSet()
        if (tokens.isEmpty()) return songs[0]

        fun score(song: EchoSaavnSong): Int {
            val name = song.name.lowercase()
            val artists = (song.artists.primary + song.artists.featured + song.artists.all)
                .joinToString(" ") { it.name.lowercase() }
            var s = 0
            for (tok in tokens) {
                if (name.contains(tok)) s += 2
                if (artists.contains(tok)) s += 1
            }
            return s
        }

        val best = songs.maxByOrNull { score(it) } ?: songs[0]
        return if (score(best) > 0) best else null
    }

    private fun mapSearchItemToTrack(item: SaavnSearchItem): Track {
        val rawImage = item.images?.large ?: item.images?.medium ?: item.image ?: ""
        val highResImage = if (rawImage.isBlank()) "" else rawImage.replace("150x150", "500x500").replace("50x50", "500x500").replace("250x250", "500x500")
        return Track(
            id = item.id,
            title = item.title,
            artist = item.moreInfo?.singers ?: extractArtistFromDescription(item.description),
            album = item.album ?: "",
            albumArtUrl = highResImage,
            durationMs = 0L,
            streamingUrl = null,
            localFilePath = null,
            isDownloaded = false,
            encryptionIv = null,
            isLiked = false
        )
    }

    private fun extractArtistFromDescription(description: String?): String {
        if (description.isNullOrBlank()) return "Unknown Artist"
        val cleanParts = description.split(Regex("\\s*[\\u00B7\\u2022]\\s*"))
        if (cleanParts.size >= 2) return cleanParts[1].trim()
        val parts = description.split("·")
        return if (parts.size >= 2) parts[1].trim() else description
    }

    private fun extractYouTubeVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && !trimmed.contains("/") && !trimmed.contains(".")) return trimmed
        val youtuBe = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.get(1)
        if (youtuBe != null) return youtuBe
        val vParam = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.get(1)
        if (vParam != null) return vParam
        val embedOrShorts = Regex("(?:embed|shorts|v)/([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.get(1)
        if (embedOrShorts != null) return embedOrShorts
        return null
    }

    override suspend fun getSkipSegments(videoId: String): List<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        val cleanVideoId = extractYouTubeVideoId(videoId) ?: videoId
        if (cleanVideoId.length != 11) return@withContext emptyList()
        val segments = mutableListOf<Pair<Long, Long>>()
        try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=$cleanVideoId&category=sponsor&category=selfpromo&category=interaction"
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val array = Gson().fromJson(body, JsonArray::class.java)
                for (element in array) {
                    val obj = element.asJsonObject
                    val segArray = obj.getAsJsonArray("segment")
                    if (segArray != null && segArray.size() >= 2) {
                        val start = Math.round(segArray.get(0).asDouble)
                        val end = Math.round(segArray.get(1).asDouble)
                        segments.add(Pair(start, end))
                    }
                }
            }
        } catch (e: Exception) {
            // Segment fetch failed or 404 segment not found
        }
        segments
    }

    override fun isSponsorBlockEnabled(): Boolean {
        return prefs.getBoolean(KEY_SPONSOR_BLOCK, true)
    }

    override fun setSponsorBlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPONSOR_BLOCK, enabled).apply()
    }

    override fun getListeningStats(): ListeningStats {
        val history = getListenHistory()
        val totalSongs = history.size
        
        val artistCounts = mutableMapOf<String, Int>()
        val songCounts = mutableMapOf<String, Int>()
        
        for (entry in history) {
            val parts = entry.split(" - ")
            if (parts.size >= 2) {
                val artist = parts[0].trim()
                val song = parts[1].trim()
                
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                songCounts[entry] = (songCounts[entry] ?: 0) + 1
            } else if (entry.isNotBlank()) {
                songCounts[entry] = (songCounts[entry] ?: 0) + 1
            }
        }
        
        val topArtists = artistCounts.toList()
            .sortedByDescending { it.second }
            .take(5)
            
        val topSongs = songCounts.toList()
            .sortedByDescending { it.second }
            .take(5)
            
        return ListeningStats(totalSongs, topArtists, topSongs)
    }
}
