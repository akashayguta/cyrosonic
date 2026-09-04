package com.example.hunterxmusic.data.remote

import com.example.hunterxmusic.domain.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal YouTube Music InnerTube client.
 * Uses the ANDROID_VR client (no auth/poToken needed) to get audio stream URLs.
 * This mirrors Echo Music's approach but simplified for our use case.
 */
object YouTubeInnerTube {

    private const val API_URL = "https://music.youtube.com/youtubei/v1/"
    private const val USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
    private const val JSON_TYPE = "application/json"

    private val gson = Gson()

    /**
     * Extracts a track duration ("3:45" / "1:02:33") from YT search subtitle
     * runs. Real durations feed lyric verification and duration UI everywhere.
     */
    private fun parseDurationMsFromRuns(runs: com.google.gson.JsonArray?): Long {
        if (runs == null) return 0L
        val timeRegex = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")
        for (runElement in runs) {
            val text = runElement.asJsonObject?.get("text")?.asString?.trim() ?: continue
            val match = timeRegex.find(text) ?: continue
            val g = match.groupValues
            return when {
                g[3].isNotEmpty() ->
                    (g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1000L
                else ->
                    (g[1].toLong() * 60 + g[2].toLong()) * 1000L
            }
        }
        return 0L
    }

    /**
     * Sanitizes an artist string assembled from YT subtitle runs. The raw join
     * can carry separator artifacts ("A, &, B"), bullet separators ("A • B"),
     * and the track duration ("A, B, 2:56") — which then leak into the media
     * notification artist line. Split on separator characters, drop duration
     * tokens and lone ampersands, rejoin cleanly.
     */
    private fun cleanArtist(raw: String): String {
        if (raw.isBlank()) return raw
        val durationToken = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")
        val parts = raw.split(Regex("[,•·|]"))
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !durationToken.matches(it) &&
                    it != "&" && it != "-" && it != "/" &&
                    !it.all { ch -> ch == ' ' }
            }
        val joined = parts.joinToString(", ")
        return joined.ifBlank { raw.trim() }
    }

    /** org.json twin of [parseDurationMsFromRuns] for watch-next/chart parsers. */
    private fun parseDurationMsFromOrgJsonRuns(runs: org.json.JSONArray?): Long {
        if (runs == null) return 0L
        val timeRegex = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")
        for (i in 0 until runs.length()) {
            val text = runs.optJSONObject(i)?.optString("text")?.trim() ?: continue
            val match = timeRegex.find(text) ?: continue
            val g = match.groupValues
            return when {
                g[3].isNotEmpty() ->
                    (g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1000L
                else ->
                    (g[1].toLong() * 60 + g[2].toLong()) * 1000L
            }
        }
        return 0L
    }

    /**
     * Fetches a full YouTube playlist (any public `list=` ID) as Tracks.
     * Browses the playlist via WEB_REMIX InnerTube — no API key needed.
     *
     * Two things used to make this always return an empty list:
     *  1. The request sent a desktop Firefox User-Agent, so YouTube answered
     *     with `twoColumnBrowseResultsRenderer` while the parser only read
     *     `singleColumnBrowseResultsRenderer`. Both shapes are handled now.
     *  2. Duration was read from `flexColumns`, where it doesn't live, so every
     *     imported track had durationMs = 0 — which in turn disabled the
     *     lyrics duration check. It comes from `fixedColumns` now.
     */
    suspend fun getPlaylistTracks(playlistId: String, client: OkHttpClient): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val seen = HashSet<String>()
        try {
            var continuation: String? = null
            var pass = 0

            do {
                val payload: MutableMap<String, Any> = mutableMapOf(
                    "context" to mapOf(
                        "client" to mapOf(
                            "clientName" to "WEB_REMIX",
                            "clientVersion" to "1.20260213.01.00",
                            "gl" to "IN",
                            "hl" to "en"
                        )
                    )
                )
                if (continuation == null) {
                    payload["browseId"] = "VL$playlistId"
                } else {
                    payload["continuation"] = continuation
                }

                val request = Request.Builder()
                    .url("${API_URL}browse")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://music.youtube.com/")
                    .addHeader("Origin", "https://music.youtube.com")
                    .addHeader("X-YouTube-Client-Name", "67")
                    .addHeader("X-YouTube-Client-Version", "1.20260213.01.00")
                    .addHeader("Content-Type", JSON_TYPE)
                    .post(gson.toJson(payload).toRequestBody(JSON_TYPE.toMediaType()))
                    .build()

                val json = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext tracks
                    org.json.JSONObject(response.body?.string() ?: "{}")
                }

                val shelf = findPlaylistShelf(json)
                val contents = shelf?.optJSONArray("contents")
                    ?: json.optJSONArray("onResponseReceivedActions")
                        ?.optJSONObject(0)
                        ?.optJSONObject("appendContinuationItemsAction")
                        ?.optJSONArray("continuationItems")
                    ?: break

                for (i in 0 until contents.length()) {
                    val wrapper = contents.optJSONObject(i) ?: continue
                    val item = wrapper.optJSONObject("musicResponsiveListItemRenderer")
                        ?: wrapper.optJSONObject("playlistVideoRenderer")
                        ?: continue

                    // WEB_REMIX moved the videoId out of the top level in 2026
                    // responses: it now lives in `playlistItemData.videoId`
                    // (and `navigationEndpoint.watchEndpoint.videoId`), with
                    // the old top-level field coming back empty. The overlay
                    // fallback is kept for older shapes.
                    val videoId = item.optString("videoId").ifBlank {
                        item.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: item.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId")
                            ?: item.optJSONObject("overlay")
                                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                                ?.optJSONObject("content")
                                ?.optJSONObject("musicPlayButtonRenderer")
                                ?.optJSONObject("playNavigationEndpoint")
                                ?.optJSONObject("watchEndpoint")
                                ?.optString("videoId")
                            ?: ""
                    }
                    if (videoId.isBlank() || !seen.add(videoId)) continue

                    val flexColumns = item.optJSONArray("flexColumns")
                    val title = flexColumnText(flexColumns, 0)
                        ?: item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: item.optJSONObject("title")?.optString("simpleText")
                        ?: continue

                    val secondRowRuns = flexColumns
                        ?.optJSONObject(1)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")

                    val artist = secondRowRuns?.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: "Various Artists"

                    // Duration lives in fixedColumns for music rows, and in
                    // lengthText for classic playlistVideoRenderer rows.
                    val durationMs = fixedColumnDurationMs(item)
                        .takeIf { it > 0L }
                        ?: parseDurationMsFromOrgJsonRuns(
                            item.optJSONObject("lengthText")?.optJSONArray("runs")
                        ).takeIf { it > 0L }
                        ?: parseDurationMsFromOrgJsonRuns(secondRowRuns)

                    val thumbs = item.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?: item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        ?: item.optJSONArray("thumbnails")

                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = cleanArtist(artist),
                            album = "",
                            albumArtUrl = getHighResThumbnailUrl(
                                if (thumbs != null && thumbs.length() > 0) {
                                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                                } else null
                            ),
                            durationMs = durationMs,
                            streamingUrl = null,
                            localFilePath = null,
                            isDownloaded = false,
                            encryptionIv = null,
                            isLiked = false
                        )
                    )
                }

                // Long playlists paginate; without this only the first ~100
                // tracks ever imported.
                continuation = extractContinuation(contents, shelf)
                pass++
            } while (continuation != null && pass < 20)
        } catch (_: Exception) { }
        tracks
    }

    /**
     * Playlist rows live under a `musicPlaylistShelfRenderer`, but its path
     * differs between the single-column (mobile) and two-column (desktop)
     * browse layouts. Walk both.
     */
    private fun findPlaylistShelf(json: org.json.JSONObject): org.json.JSONObject? {
        val contents = json.optJSONObject("contents") ?: return null

        // Single-column (mobile / tablet) shape
        contents.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { sections ->
                for (i in 0 until sections.length()) {
                    sections.optJSONObject(i)?.optJSONObject("musicPlaylistShelfRenderer")
                        ?.let { return it }
                }
            }

        // Two-column (desktop) shape
        contents.optJSONObject("twoColumnBrowseResultsRenderer")?.let { two ->
            two.optJSONObject("secondaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.let { sections ->
                    for (i in 0 until sections.length()) {
                        sections.optJSONObject(i)?.optJSONObject("musicPlaylistShelfRenderer")
                            ?.let { return it }
                    }
                }
            two.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?.let { sections ->
                    for (i in 0 until sections.length()) {
                        sections.optJSONObject(i)?.optJSONObject("musicPlaylistShelfRenderer")
                            ?.let { return it }
                    }
                }
        }
        return null
    }

    /** Plain text of one flexColumn cell. */
    private fun flexColumnText(flexColumns: org.json.JSONArray?, index: Int): String? {
        return flexColumns
            ?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
    }

    /** Duration from `fixedColumns`, where music rows actually put it. */
    private fun fixedColumnDurationMs(item: org.json.JSONObject): Long {
        val fixed = item.optJSONArray("fixedColumns") ?: return 0L
        for (i in 0 until fixed.length()) {
            val runs = fixed.optJSONObject(i)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            val parsed = parseDurationMsFromOrgJsonRuns(runs)
            if (parsed > 0L) return parsed
        }
        return 0L
    }

    /** Next-page token, from either the shelf or a continuation item row. */
    private fun extractContinuation(
        contents: org.json.JSONArray,
        shelf: org.json.JSONObject?
    ): String? {
        for (i in 0 until contents.length()) {
            contents.optJSONObject(i)
                ?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        shelf?.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    /**
     * Search YouTube Music for tracks. Returns a list of Track objects.
     */
    suspend fun searchTracks(query: String, client: OkHttpClient): Pair<List<Track>, String?> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        var continuationToken: String? = null
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "query" to query,
                "params" to "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("${API_URL}search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Pair(emptyList(), null)

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

            val tabs = json.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs") ?: return@withContext Pair(emptyList(), null)

            val sections = tabs[0]?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return@withContext Pair(emptyList(), null)

            for (section in sections) {
                val shelf = section.asJsonObject?.getAsJsonObject("musicShelfRenderer") ?: continue
                
                // Extract continuation token
                val continuations = shelf.getAsJsonArray("continuations")
                if (continuations != null && continuations.size() > 0) {
                    continuationToken = continuations.get(0)?.asJsonObject
                        ?.getAsJsonObject("nextContinuationData")
                        ?.get("continuation")?.asString
                }

                val contents = shelf.getAsJsonArray("contents") ?: continue

                for (item in contents) {
                    val renderer = item.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue

                    val videoId = renderer.getAsJsonObject("overlay")
                        ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("musicPlayButtonRenderer")
                        ?.getAsJsonObject("playNavigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString ?: continue

                    val title = renderer.getAsJsonArray("flexColumns")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                        ?.getAsJsonArray("runs")
                        ?.get(0)?.asJsonObject
                        ?.get("text")?.asString ?: "Unknown Title"

                    val subtitleRuns = renderer.getAsJsonArray("flexColumns")
                        ?.get(1)?.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.getAsJsonObject("text")
                        ?.getAsJsonArray("runs")

                    val artist = subtitleRuns?.let { runs ->
                        val artistList = mutableListOf<String>()
                        for (runElement in runs) {
                            val run = runElement.asJsonObject
                            val text = run.get("text")?.asString ?: ""
                            val nav = run.getAsJsonObject("navigationEndpoint")
                            val browseId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                            if (text != " • " && text.isNotBlank() && (browseId == null || browseId.startsWith("UC"))) {
                                  artistList.add(text)
                            }
                        }
                        if (artistList.isEmpty() && runs.size() > 0) {
                            runs.get(0).asJsonObject.get("text")?.asString ?: "Unknown Artist"
                        } else {
                            artistList.joinToString(", ")
                        }
                    } ?: "Unknown Artist"

                    val album = subtitleRuns?.let { runs ->
                        var albumName = ""
                        for (runElement in runs) {
                            val run = runElement.asJsonObject
                            val text = run.get("text")?.asString ?: ""
                            val nav = run.getAsJsonObject("navigationEndpoint")
                            val browseId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                            if (browseId != null && (browseId.startsWith("FEmusic_library_privately_owned_release_detail") || browseId.startsWith("MPRE"))) {
                                albumName = text
                                break
                            }
                        }
                        albumName
                    } ?: ""

                    val albumArtUrlRaw = renderer.getAsJsonObject("thumbnail")
                        ?.getAsJsonObject("musicThumbnailRenderer")
                        ?.getAsJsonObject("thumbnail")
                        ?.getAsJsonArray("thumbnails")
                        ?.let { thumbnails ->
                            if (thumbnails.size() > 0) {
                                thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString
                            } else null
                        }
                    val albumArtUrl = getHighResThumbnailUrl(albumArtUrlRaw)

                    tracks.add(
                        Track(
                            id = videoId,
                            title = title,
                            artist = cleanArtist(artist),
                            album = album,
                            albumArtUrl = albumArtUrl,
                            durationMs = parseDurationMsFromRuns(subtitleRuns),
                            streamingUrl = null,
                            localFilePath = null,
                            isDownloaded = false,
                            encryptionIv = null,
                            isLiked = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(tracks, continuationToken)
    }

    /**
     * Search YouTube Music next page using continuation token.
     */
    suspend fun searchTracksContinuation(continuationToken: String, client: OkHttpClient): Pair<List<Track>, String?> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        var nextContinuationToken: String? = null
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "continuation" to continuationToken
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("${API_URL}search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext Pair(emptyList(), null)

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

            val continuationContents = json.getAsJsonObject("continuationContents") ?: return@withContext Pair(emptyList(), null)
            val shelf = continuationContents.getAsJsonObject("musicShelfContinuation") ?: return@withContext Pair(emptyList(), null)

            val continuations = shelf.getAsJsonArray("continuations")
            if (continuations != null && continuations.size() > 0) {
                nextContinuationToken = continuations.get(0)?.asJsonObject
                    ?.getAsJsonObject("nextContinuationData")
                    ?.get("continuation")?.asString
            }

            val contents = shelf.getAsJsonArray("contents") ?: return@withContext Pair(emptyList(), null)
            for (item in contents) {
                val renderer = item.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue

                val videoId = renderer.getAsJsonObject("overlay")
                    ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                    ?.getAsJsonObject("content")
                    ?.getAsJsonObject("musicPlayButtonRenderer")
                    ?.getAsJsonObject("playNavigationEndpoint")
                    ?.getAsJsonObject("watchEndpoint")
                    ?.get("videoId")?.asString ?: continue

                val title = renderer.getAsJsonArray("flexColumns")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")
                    ?.get(0)?.asJsonObject
                    ?.get("text")?.asString ?: "Unknown Title"

                val subtitleRuns = renderer.getAsJsonArray("flexColumns")
                    ?.get(1)?.asJsonObject
                    ?.getAsJsonObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.getAsJsonObject("text")
                    ?.getAsJsonArray("runs")

                val artist = subtitleRuns?.let { runs ->
                    val artistList = mutableListOf<String>()
                    for (runElement in runs) {
                        val run = runElement.asJsonObject
                        val text = run.get("text")?.asString ?: ""
                        val nav = run.getAsJsonObject("navigationEndpoint")
                        val browseId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                        if (text != " • " && text.isNotBlank() && (browseId == null || browseId.startsWith("UC"))) {
                            artistList.add(text)
                        }
                    }
                    if (artistList.isEmpty() && runs.size() > 0) {
                        runs.get(0).asJsonObject.get("text")?.asString ?: "Unknown Artist"
                    } else {
                        artistList.joinToString(", ")
                    }
                } ?: "Unknown Artist"

                val album = subtitleRuns?.let { runs ->
                    var albumName = ""
                    for (runElement in runs) {
                        val run = runElement.asJsonObject
                        val text = run.get("text")?.asString ?: ""
                        val nav = run.getAsJsonObject("navigationEndpoint")
                        val browseId = nav?.getAsJsonObject("browseEndpoint")?.get("browseId")?.asString
                        if (browseId != null && (browseId.startsWith("FEmusic_library_privately_owned_release_detail") || browseId.startsWith("MPRE"))) {
                            albumName = text
                            break
                        }
                    }
                    albumName
                } ?: ""

                val albumArtUrlRaw = renderer.getAsJsonObject("thumbnail")
                    ?.getAsJsonObject("musicThumbnailRenderer")
                    ?.getAsJsonObject("thumbnail")
                    ?.getAsJsonArray("thumbnails")
                    ?.let { thumbnails ->
                        if (thumbnails.size() > 0) {
                            thumbnails.get(thumbnails.size() - 1).asJsonObject.get("url")?.asString
                        } else null
                    }
                val albumArtUrl = getHighResThumbnailUrl(albumArtUrlRaw)

                tracks.add(
                    Track(
                        id = videoId,
                        title = title,
                        artist = cleanArtist(artist),
                        album = album,
                        albumArtUrl = albumArtUrl,
                        durationMs = parseDurationMsFromRuns(subtitleRuns),
                        streamingUrl = null,
                        localFilePath = null,
                        isDownloaded = false,
                        encryptionIv = null,
                        isLiked = false
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Pair(tracks, nextContinuationToken)
    }

    /**
     * Search YouTube Music for a song. Returns videoId of the best match.
     */
    suspend fun searchVideoId(query: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "query" to query,
                "params" to "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("${API_URL}search")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

            // Navigate: contents → tabbedSearchResultsRenderer → tabs[0] → tabRenderer → content → sectionListRenderer → contents
            val tabs = json.getAsJsonObject("contents")
                ?.getAsJsonObject("tabbedSearchResultsRenderer")
                ?.getAsJsonArray("tabs") ?: return@withContext null

            val sections = tabs[0]?.asJsonObject
                ?.getAsJsonObject("tabRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return@withContext null

            // Look through sections for musicShelfRenderer with song results
            for (section in sections) {
                val shelf = section.asJsonObject?.getAsJsonObject("musicShelfRenderer") ?: continue
                val contents = shelf.getAsJsonArray("contents") ?: continue

                for (item in contents) {
                    val renderer = item.asJsonObject
                        ?.getAsJsonObject("musicResponsiveListItemRenderer") ?: continue

                    // Extract videoId from overlay
                    val videoId = renderer.getAsJsonObject("overlay")
                        ?.getAsJsonObject("musicItemThumbnailOverlayRenderer")
                        ?.getAsJsonObject("content")
                        ?.getAsJsonObject("musicPlayButtonRenderer")
                        ?.getAsJsonObject("playNavigationEndpoint")
                        ?.getAsJsonObject("watchEndpoint")
                        ?.get("videoId")?.asString

                    if (videoId != null) return@withContext videoId
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    /**
     * Fetch search suggestions directly from YouTube Music InnerTube.
     */
    suspend fun getSearchSuggestions(query: String, client: OkHttpClient): List<String> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<String>()
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "input" to q
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("${API_URL}music/get_search_suggestions")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val contents = json.getAsJsonArray("contents") ?: return@withContext emptyList()

            for (item in contents) {
                val section = item.asJsonObject?.getAsJsonObject("searchSuggestionsSectionRenderer") ?: continue
                val items = section.getAsJsonArray("contents") ?: continue
                for (subItem in items) {
                    val historyRenderer = subItem.asJsonObject?.getAsJsonObject("historySuggestionRenderer")
                    val queryRenderer = subItem.asJsonObject?.getAsJsonObject("searchSuggestionRenderer")
                    val targetRenderer = queryRenderer ?: historyRenderer ?: continue
                    val runs = targetRenderer.getAsJsonObject("suggestion")?.getAsJsonArray("runs")
                    val suggestionText = runs?.joinToString("") { it.asJsonObject.get("text")?.asString ?: "" } ?: ""
                    if (suggestionText.isNotBlank()) {
                        suggestions.add(suggestionText)
                    }
                }
            }
        } catch (_: Exception) {}
        suggestions.distinct().take(10)
    }

    /**
     * Fetch automatic radio queue tracks related to a video ID.
     */
    suspend fun getNextRadioTracks(videoId: String, client: OkHttpClient): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "videoId" to videoId,
                "enablePersistentPlaylistPanel" to true,
                "isAudioOnly" to true
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("${API_URL}next")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)
            val tabs = json.getAsJsonObject("contents")
                ?.getAsJsonObject("singleColumnMusicWatchNextResultsRenderer")
                ?.getAsJsonObject("tabbedRenderer")
                ?.getAsJsonObject("watchNextTabbedResultsRenderer")
                ?.getAsJsonArray("tabs") ?: return@withContext emptyList()

            val tabRenderer = tabs[0]?.asJsonObject?.getAsJsonObject("tabRenderer")
            val queueRenderer = tabRenderer?.getAsJsonObject("content")
                ?.getAsJsonObject("musicQueueRenderer")
                ?.getAsJsonObject("content")
                ?.getAsJsonObject("playlistPanelRenderer")
                ?.getAsJsonArray("contents") ?: return@withContext emptyList()

            for (item in queueRenderer) {
                val renderer = item.asJsonObject?.getAsJsonObject("playlistPanelVideoRenderer") ?: continue
                val vId = renderer.get("videoId")?.asString ?: continue
                if (vId == videoId) continue // skip current song

                val title = renderer.getAsJsonObject("title")?.getAsJsonArray("runs")?.get(0)?.asJsonObject?.get("text")?.asString ?: "Unknown Title"
                val artistRuns = renderer.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")
                    ?: renderer.getAsJsonObject("shortBylineText")?.getAsJsonArray("runs")
                val artist = artistRuns?.let { runs ->
                    val artists = mutableListOf<String>()
                    for (r in runs) {
                        val text = r.asJsonObject.get("text")?.asString ?: ""
                        if (text != " • " && text.isNotBlank()) artists.add(text)
                    }
                    artists.firstOrNull() ?: "Unknown Artist"
                } ?: "Unknown Artist"

                val thumbnails = renderer.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")
                val artUrl = thumbnails?.let {
                    if (it.size() > 0) it.get(it.size() - 1).asJsonObject.get("url")?.asString else null
                }
                val lengthRuns = renderer.getAsJsonObject("lengthText")?.getAsJsonArray("runs")

                tracks.add(
                    Track(
                        id = vId,
                        title = title,
                        artist = cleanArtist(artist),
                        album = "",
                        albumArtUrl = getHighResThumbnailUrl(artUrl),
                        durationMs = parseDurationMsFromRuns(lengthRuns),
                        streamingUrl = null,
                        localFilePath = null,
                        isDownloaded = false,
                        encryptionIv = null,
                        isLiked = false
                    )
                )
            }
        } catch (_: Exception) {}
        tracks.take(20)
    }

    /**
     * Get the best audio stream URL for a YouTube videoId.
     * Uses ANDROID_TESTSUITE + ANDROID_VR + IOS + NewPipe fallback.
     */
    suspend fun getStreamUrl(videoId: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        // 1. Primary: ANDROID_TESTSUITE (Official YouTube automated test client - unciphered & unthrottled)
        val testSuiteUrl = resolveStreamWithClient(
            videoId = videoId,
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            osName = "Android",
            osVersion = "12",
            sdkVersion = 31,
            userAgent = "Dalvik/2.1.0 (Linux; U; Android 12; Pixel 6 Build/SD1A.210817.037)",
            client = client
        )
        if (!testSuiteUrl.isNullOrBlank()) return@withContext testSuiteUrl

        // 2. Secondary: ANDROID_VR client on main YouTube endpoint
        val primaryUrl = resolveStreamWithClient(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            osName = "Android",
            osVersion = "12",
            sdkVersion = 32,
            userAgent = USER_AGENT,
            client = client
        )
        if (!primaryUrl.isNullOrBlank()) return@withContext primaryUrl

        // 3. Fallback: IOS client
        val iosUrl = resolveStreamWithClient(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "19.29.1",
            osName = "iOS",
            osVersion = "17.5.1.21F90",
            sdkVersion = null,
            userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)",
            client = client
        )
        if (!iosUrl.isNullOrBlank()) return@withContext iosUrl

        // 4. Fallback: NewPipe Extractor (signature / cipher decryption)
        try {
            val newPipeUrl = NewPipeYouTubeResolver.resolveStreamUrl(videoId, client)
            if (!newPipeUrl.isNullOrBlank()) return@withContext newPipeUrl
        } catch (_: Exception) { }

        null
    }

    private fun resolveStreamWithClient(
        videoId: String,
        clientName: String,
        clientVersion: String,
        osName: String,
        osVersion: String,
        sdkVersion: Int?,
        userAgent: String,
        client: OkHttpClient
    ): String? {
        try {
            val clientMap = mutableMapOf<String, Any>(
                "clientName" to clientName,
                "clientVersion" to clientVersion,
                "osName" to osName,
                "osVersion" to osVersion,
                "gl" to "IN",
                "hl" to "en"
            )
            if (sdkVersion != null) {
                clientMap["androidSdkVersion"] = sdkVersion
            }

            val bodyMap = mapOf(
                "context" to mapOf("client" to clientMap),
                "videoId" to videoId,
                "playbackContext" to mapOf(
                    "contentPlaybackContext" to mapOf(
                        "signatureTimestamp" to 20250
                    )
                ),
                "racyCheckOk" to true,
                "contentCheckOk" to true
            )
            val body = gson.toJson(bodyMap)

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .addHeader("User-Agent", userAgent)
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            // Hard 10s cap per client attempt so one hung YouTube endpoint
            // can't stall the whole resolver chain (shared client has a 30s
            // read timeout, which is too long to chew through four clients).
            val response = client.newBuilder()
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
            if (!response.isSuccessful) return null

            val json = gson.fromJson(response.body?.string(), JsonObject::class.java)

            // Check playability
            val playabilityStatus = json.getAsJsonObject("playabilityStatus")
            val status = playabilityStatus?.get("status")?.asString
            if (status != "OK") return null

            // Get streaming data
            val streamingData = json.getAsJsonObject("streamingData") ?: return null
            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            val formats = streamingData.getAsJsonArray("formats")

            var bestUrl: String? = null
            var bestBitrate = 0
            var bestIsOpus = false

            // 1. Search adaptive audio formats (Prioritizing Opus itag 251 at 160kbps, then AAC)
            if (adaptiveFormats != null) {
                for (format in adaptiveFormats) {
                    val fmt = format.asJsonObject
                    val mimeType = fmt.get("mimeType")?.asString ?: continue
                    if (!mimeType.startsWith("audio/")) continue

                    val bitrate = fmt.get("bitrate")?.asInt ?: 0
                    val url = fmt.get("url")?.asString ?: continue
                    val isOpus = mimeType.contains("opus")

                    val shouldSelect = when {
                        bestUrl == null -> true
                        isOpus && !bestIsOpus -> true
                        !isOpus && bestIsOpus -> false
                        else -> bitrate > bestBitrate
                    }

                    if (shouldSelect) {
                        bestBitrate = bitrate
                        bestUrl = url
                        bestIsOpus = isOpus
                    }
                }
            }

            // 2. Fallback to combined progressive formats if audio-only URL not found
            if (bestUrl == null && formats != null) {
                for (format in formats) {
                    val fmt = format.asJsonObject
                    val url = fmt.get("url")?.asString
                    val bitrate = fmt.get("bitrate")?.asInt ?: 0
                    if (url != null && bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestUrl = url
                    }
                }
            }

            return bestUrl
        } catch (_: Exception) {
            return null
        }
    }

    private fun getHighResThumbnailUrl(url: String?): String? {
        if (url == null) return null
        var upgraded = url
            .replace(Regex("=w\\d+-h\\d+[^\\s]*"), "=w800-h800-l90-rj")
            .replace(Regex("=s\\d+[^\\s]*"), "=s800-l90-rj")
        if (upgraded.contains("ytimg.com")) {
            upgraded = upgraded.replace("default.jpg", "sddefault.jpg")
        }
        return upgraded
    }

    /**
     * Reverse-engineered YouTube Music InnerTube Timed Lyrics Engine.
     * 1. Queries /next with videoId to resolve the lyrics tab browseId (MPLYt_...)
     * 2. Queries /browse with browseId to retrieve timedLyricsModel (millisecond synced cues directly matching YT audio)
     */
    suspend fun getYouTubeTimedLyrics(videoId: String, client: OkHttpClient): List<com.example.hunterxmusic.domain.model.LyricLine>? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        try {
            // Step 1: Query /next to get browseId
            val nextBody = gson.toJson(
                mapOf(
                    "context" to mapOf(
                        "client" to mapOf(
                            "clientName" to "WEB_REMIX",
                            "clientVersion" to "1.20260213.01.00",
                            "gl" to "IN",
                            "hl" to "en"
                        )
                    ),
                    "videoId" to videoId
                )
            )

            val nextRequest = Request.Builder()
                .url("${API_URL}next")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(nextBody.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val nextResponse = client.newCall(nextRequest).execute()
            if (!nextResponse.isSuccessful) return@withContext null
            val nextJsonStr = nextResponse.body?.string() ?: return@withContext null
            val nextJson = org.json.JSONObject(nextJsonStr)

            // Extract browseId for lyrics
            var lyricsBrowseId: String? = null
            val tabs = nextJson.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")

            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val tabRenderer = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                    val title = tabRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    val endpoint = tabRenderer.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                    val browseId = endpoint?.optString("browseId")
                    if (title.equals("Lyrics", ignoreCase = true) || browseId?.startsWith("MPLYt_") == true) {
                        lyricsBrowseId = browseId
                        break
                    }
                }
            }

            if (lyricsBrowseId.isNullOrBlank()) return@withContext null

            // Step 2: Query /browse with browseId (Android Music client returns timedLyricsModel)
            val browseBody = gson.toJson(
                mapOf(
                    "context" to mapOf(
                        "client" to mapOf(
                            "clientName" to "ANDROID_MUSIC",
                            "clientVersion" to "6.41.52",
                            "gl" to "IN",
                            "hl" to "en"
                        )
                    ),
                    "browseId" to lyricsBrowseId
                )
            )

            val browseRequest = Request.Builder()
                .url("${API_URL}browse")
                .addHeader("User-Agent", "com.google.android.apps.youtube.music/6.41.52 (Linux; U; Android 14; en_US; Pixel 8; Build/UD1A.230803.041)")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(browseBody.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val browseResponse = client.newCall(browseRequest).execute()
            if (!browseResponse.isSuccessful) return@withContext null
            val browseJsonStr = browseResponse.body?.string() ?: return@withContext null
            val browseJson = org.json.JSONObject(browseJsonStr)

            // Step 3: Parse Timed Lyrics or Plain Text Lyrics
            val parsedLines = extractTimedLyricsFromBrowseJson(browseJson)
            if (!parsedLines.isNullOrEmpty()) {
                return@withContext parsedLines
            }
        } catch (_: Exception) { }
        return@withContext null
    }

    private fun extractTimedLyricsFromBrowseJson(root: org.json.JSONObject): List<com.example.hunterxmusic.domain.model.LyricLine>? {
        val lines = mutableListOf<com.example.hunterxmusic.domain.model.LyricLine>()
        try {
            // Traverse recursively to find timedLyricsData or cueRange objects
            findTimedLyricsRecursive(root, lines)
            if (lines.isNotEmpty()) {
                lines.sortBy { it.timestampMs }
                return lines
            }

            // Fallback to plain description runs — timestamps stay at 0 so this
            // renders as an unsynced reading sheet, never as fake karaoke cues.
            findPlainLyricsRecursive(root, lines)
            if (lines.isNotEmpty()) {
                return lines.map { line ->
                    com.example.hunterxmusic.domain.model.LyricLine(0L, line.words)
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun findTimedLyricsRecursive(obj: Any?, result: MutableList<com.example.hunterxmusic.domain.model.LyricLine>) {
        when (obj) {
            is org.json.JSONObject -> {
                if (obj.has("cueRange") && obj.has("lyricLine")) {
                    val lyricText = obj.optString("lyricLine").trim()
                    val cue = obj.optJSONObject("cueRange")
                    val startMs = cue?.optLong("startTimeMilliseconds", -1L) ?: -1L
                    if (startMs >= 0 && lyricText.isNotEmpty()) {
                        result.add(com.example.hunterxmusic.domain.model.LyricLine(startMs, lyricText))
                    }
                }
                val keys = obj.keys()
                while (keys.hasNext()) {
                    findTimedLyricsRecursive(obj.opt(keys.next()), result)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until obj.length()) {
                    findTimedLyricsRecursive(obj.opt(i), result)
                }
            }
        }
    }

    private fun findPlainLyricsRecursive(obj: Any?, result: MutableList<com.example.hunterxmusic.domain.model.LyricLine>) {
        if (obj is org.json.JSONObject) {
            val shelf = obj.optJSONObject("musicDescriptionShelfRenderer")
            if (shelf != null) {
                val description = shelf.optJSONObject("description")?.optJSONArray("runs")
                if (description != null) {
                    for (i in 0 until description.length()) {
                        val text = description.optJSONObject(i)?.optString("text")?.trim() ?: continue
                        text.lineSequence().forEach { line ->
                            val clean = line.trim()
                            if (clean.isNotEmpty()) {
                                result.add(com.example.hunterxmusic.domain.model.LyricLine(0L, clean))
                            }
                        }
                    }
                    return
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                findPlainLyricsRecursive(obj.opt(keys.next()), result)
            }
        } else if (obj is org.json.JSONArray) {
            for (i in 0 until obj.length()) {
                findPlainLyricsRecursive(obj.opt(i), result)
            }
        }
    }

    /**
     * WORD-LEVEL timed lyrics from YouTube's caption stream.
     *
     * Live-tested contract (2026-08):
     * 1. POST /youtubei/v1/player with the IOS client (WEB/ANDROID_MUSIC get
     *    bot-checked with LOGIN_REQUIRED / UNPLAYABLE) → captions
     *    .playerCaptionsTracklistRenderer.captionTracks[]
     * 2. Each track's baseUrl + "&fmt=json3" returns JSON events; every event
     *    has tStartMs + segs[] where each seg may carry tOffsetMs — a REAL
     *    per-word millisecond offset. Only ASR auto-tracks carry word offsets;
     *    creator captions are line-only.
     * 3. We scan ALL tracks, keep the one with the most word-timed events,
     *    and return null when none qualifies — callers then fall back to the
     *    line-level engines. No fabricated karaoke, ever.
     */
    suspend fun getYouTubeWordTimedLyrics(videoId: String, client: OkHttpClient): List<com.example.hunterxmusic.domain.model.LyricLine>? = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || videoId.length != 11) return@withContext null
        try {
            val playerBody = gson.toJson(
                mapOf(
                    "context" to mapOf(
                        "client" to mapOf(
                            "clientName" to "IOS",
                            "clientVersion" to "20.10.4",
                            "deviceModel" to "iPhone16,2",
                            "hl" to "en",
                            "gl" to "US"
                        )
                    ),
                    "videoId" to videoId,
                    "contentCheckOk" to true,
                    "racyCheckOk" to true
                )
            )
            val playerRequest = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player")
                .addHeader("User-Agent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
                .addHeader("X-Youtube-Client-Name", "5")
                .addHeader("X-Youtube-Client-Version", "20.10.4")
                .addHeader("Content-Type", JSON_TYPE)
                .post(playerBody.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val playerResponse = client.newCall(playerRequest).execute()
            if (!playerResponse.isSuccessful) {
                playerResponse.close() // don't leak the connection on bot-checks
                return@withContext null
            }
            val playerJsonStr = playerResponse.body?.string() ?: return@withContext null
            val playerJson = org.json.JSONObject(playerJsonStr)

            val captionTracks = playerJson.optJSONObject("captions")
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks")
                ?: return@withContext null

            // Scan tracks with smart language prioritization (native original audio language first)
            var bestEvents: org.json.JSONArray? = null
            var bestScore = -1.0

            val noiseTagRegex = Regex("^\\[([^\\]]+)\\]$")

            for (i in 0 until captionTracks.length()) {
                val track = captionTracks.optJSONObject(i) ?: continue
                val baseUrl = track.optString("baseUrl").takeIf { it.isNotBlank() } ?: continue
                val langCode = track.optString("languageCode", "").lowercase()
                val vssId = track.optString("vssId", "").lowercase()
                val kind = track.optString("kind", "").lowercase()

                var trackPriority = 1.0
                if (vssId.contains("-orig") || vssId.startsWith("a.") || kind == "asr") trackPriority += 2.5
                if (!langCode.startsWith("en") && langCode.isNotEmpty()) trackPriority += 1.5 // Native regional language

                try {
                    val req = Request.Builder()
                        .url("$baseUrl&fmt=json3")
                        .addHeader("User-Agent", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)")
                        .get()
                        .build()
                    val resp = client.newCall(req).execute()
                    resp.use { r ->
                        if (!r.isSuccessful) return@use
                        val bodyStr = r.body?.string() ?: return@use
                        val events = org.json.JSONObject(bodyStr).optJSONArray("events") ?: return@use
                        var wordTimed = 0
                        for (e in 0 until events.length()) {
                            val ev = events.optJSONObject(e) ?: continue
                            val segs = ev.optJSONArray("segs") ?: continue
                            for (s in 0 until segs.length()) {
                                val off = segs.optJSONObject(s)?.optLong("tOffsetMs", -1L) ?: -1L
                                if (off > 0) { wordTimed++; break }
                            }
                        }
                        val score = (wordTimed.toDouble() * trackPriority)
                        if (score > bestScore) {
                            bestScore = score
                            bestEvents = events
                        }
                    }
                } catch (_: Exception) { }
            }

            val events = bestEvents ?: return@withContext null

            val lines = mutableListOf<com.example.hunterxmusic.domain.model.LyricLine>()
            for (e in 0 until events.length()) {
                val ev = events.optJSONObject(e) ?: continue
                val startMs = ev.optLong("tStartMs", -1L)
                if (startMs < 0) continue
                val segs = ev.optJSONArray("segs") ?: continue

                val textBuilder = StringBuilder()
                val cues = mutableListOf<com.example.hunterxmusic.domain.model.WordCue>()
                for (s in 0 until segs.length()) {
                    val seg = segs.optJSONObject(s) ?: continue
                    val raw = seg.optString("utf8")
                    if (raw.isEmpty()) continue
                    val offset = seg.optLong("tOffsetMs", 0L).coerceAtLeast(0L)
                    val pieces = raw.split(Regex("(?<=\\s)")).filter { it.isNotBlank() }
                    for ((pi, piece) in pieces.withIndex()) {
                        val pieceTrim = piece.trim()
                        // Filter out non-lyric noise tokens like [Music], [संगीत], [Applause], etc.
                        if (pieceTrim.isNotEmpty() && !noiseTagRegex.matches(pieceTrim)) {
                            cues.add(
                                com.example.hunterxmusic.domain.model.WordCue(
                                    text = pieceTrim,
                                    startMs = startMs + offset,
                                    endMs = startMs + offset + (pieceTrim.length * 90L).coerceIn(200L, 1000L)
                                )
                            )
                            textBuilder.append(piece)
                        }
                    }
                }
                val lineText = textBuilder.toString().trim()
                if (lineText.isEmpty() || cues.isEmpty()) continue

                // Compute exact word bounds:
                // Consecutive words with short gaps (<1500ms) transition smoothly.
                // Pauses (>1500ms) and the LAST word strictly preserve their natural phonetic endMs,
                // so they NEVER stretch across instrumental gaps or line ends!
                val boundedCues = cues.mapIndexed { ci, cue ->
                    val nextStart = cues.getOrNull(ci + 1)?.startMs
                    if (nextStart != null && nextStart > cue.startMs) {
                        val gap = nextStart - cue.startMs
                        if (gap <= 1500L) {
                            cue.copy(endMs = nextStart)
                        } else {
                            cue
                        }
                    } else {
                        // Final word: strictly retains its exact spoken/sung endMs
                        cue
                    }
                }

                lines.add(
                    com.example.hunterxmusic.domain.model.LyricLine(
                        timestampMs = startMs,
                        words = lineText,
                        wordCues = boundedCues
                    )
                )
            }

            if (lines.size < 4) return@withContext null
            lines.sortBy { it.timestampMs }
            lines
        } catch (_: Exception) { null }
    }

    /**
     * Smart Radio / Autoplay Watch Playlist recommendations (from ytmusicapi).
     */
    suspend fun getWatchPlaylistTracks(videoId: String, client: OkHttpClient): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "videoId" to videoId,
                "isAudioOnly" to true,
                "enablePersistentPlaylistPanel" to true
            )
            val body = gson.toJson(bodyMap)
            val request = Request.Builder()
                .url("${API_URL}next")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = org.json.JSONObject(response.body?.string() ?: "{}")
                val tabs = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.optJSONObject("tabbedRenderer")
                    ?.optJSONObject("watchNextTabbedResultsRenderer")
                    ?.optJSONArray("tabs")
                
                val queueRenderer = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                    ?.optJSONObject("playlist")
                    ?.optJSONObject("playlist")
                    ?.optJSONArray("contents")

                if (queueRenderer != null) {
                    for (i in 0 until queueRenderer.length()) {
                        val item = queueRenderer.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
                        val vId = item.optString("videoId")
                        val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown Title"
                        val artistRuns = item.optJSONObject("shortBylineText")?.optJSONArray("runs")
                        val artist = artistRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"
                        val thumbs = item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbs != null && thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
                        
                        if (vId.isNotBlank() && vId != videoId) {
                            val lengthRuns = item.optJSONObject("lengthText")?.optJSONArray("runs")
                            val durationMs = parseDurationMsFromOrgJsonRuns(lengthRuns)
                            tracks.add(
                                Track(
                                    id = vId,
                                    title = title,
                                    artist = cleanArtist(artist),
                                    album = "",
                                    albumArtUrl = getHighResThumbnailUrl(thumbUrl),
                                    durationMs = durationMs,
                                    streamingUrl = null,
                                    localFilePath = null,
                                    isDownloaded = false,
                                    encryptionIv = null,
                                    isLiked = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        tracks
    }

    /**
     * Top Charts & Trending Tracks for India / Global (from ytmusicapi).
     */
    suspend fun getChartsTracks(client: OkHttpClient): List<Track> = withContext(Dispatchers.IO) {
        browseMusicShelf("FEmusic_charts", "Top Charts", client)
    }

    /**
     * Official New Releases (Albums & Singles) from YouTube Music (browseId = FEmusic_new_releases).
     */
    suspend fun getNewReleasesTracks(client: OkHttpClient): List<Track> = withContext(Dispatchers.IO) {
        browseMusicShelf("FEmusic_new_releases", "New Releases", client)
    }

    private fun browseMusicShelf(browseId: String, defaultAlbum: String, client: OkHttpClient): List<Track> {
        val tracks = mutableListOf<Track>()
        val seen = HashSet<String>()
        try {
            val bodyMap = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20260213.01.00",
                        "gl" to "IN",
                        "hl" to "en"
                    )
                ),
                "browseId" to browseId
            )
            val body = gson.toJson(bodyMap)
            val request = Request.Builder()
                .url("${API_URL}browse")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("Content-Type", JSON_TYPE)
                .post(body.toRequestBody(JSON_TYPE.toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = org.json.JSONObject(response.body?.string() ?: "{}")
                extractTracksFromBrowseJson(json, defaultAlbum, tracks, seen)
            }
        } catch (_: Exception) { }
        return tracks.take(40)
    }

    private fun extractTracksFromBrowseJson(
        root: Any?,
        defaultAlbum: String,
        results: MutableList<Track>,
        seen: MutableSet<String>
    ) {
        when (root) {
            is org.json.JSONObject -> {
                if (root.has("musicResponsiveListItemRenderer")) {
                    val item = root.optJSONObject("musicResponsiveListItemRenderer")
                    if (item != null) {
                        val vId = item.optJSONObject("overlay")
                            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                            ?.optJSONObject("content")
                            ?.optJSONObject("musicPlayButtonRenderer")
                            ?.optJSONObject("playNavigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                            ?: item.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: ""

                        if (vId.isNotBlank() && seen.add(vId)) {
                            val title = item.optJSONArray("flexColumns")
                                ?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: "Track"

                            val artist = item.optJSONArray("flexColumns")
                                ?.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")
                                ?.optJSONObject(0)
                                ?.optString("text") ?: "Artist"

                            val thumbs = item.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            val thumbUrl = if (thumbs != null && thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null

                            val subtitleRuns = item.optJSONArray("flexColumns")
                                ?.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                                ?.optJSONArray("runs")

                            results.add(
                                Track(
                                    id = vId,
                                    title = title,
                                    artist = cleanArtist(artist),
                                    album = defaultAlbum,
                                    albumArtUrl = getHighResThumbnailUrl(thumbUrl),
                                    durationMs = parseDurationMsFromOrgJsonRuns(subtitleRuns),
                                    streamingUrl = null,
                                    localFilePath = null,
                                    isDownloaded = false,
                                    encryptionIv = null,
                                    isLiked = false
                                )
                            )
                        }
                    }
                }
                val keys = root.keys()
                while (keys.hasNext()) {
                    extractTracksFromBrowseJson(root.opt(keys.next()), defaultAlbum, results, seen)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until root.length()) {
                    extractTracksFromBrowseJson(root.opt(i), defaultAlbum, results, seen)
                }
            }
        }
    }
}
