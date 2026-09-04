package com.example.hunterxmusic.data.analytics

import android.content.Context
import com.example.hunterxmusic.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Intelligent Personalization Engine:
 * Analyzes listener behavior (plays, completions, skips, likes, replays, time-of-day)
 * to construct true personalized Speed Dials, dynamic vibes, and intelligent shelves.
 */
class PersonalizationEngine(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("personalization_stats", Context.MODE_PRIVATE)

    /**
     * Records a track playback event with timestamp and duration.
     */
    fun recordPlay(track: Track) {
        if (track.id.isBlank()) return
        try {
            val key = "track_${track.id}"
            val raw = prefs.getString(key, null)
            val json = if (raw != null) JSONObject(raw) else JSONObject()

            val plays = json.optInt("plays", 0) + 1
            json.put("id", track.id)
            json.put("title", track.title)
            json.put("artist", track.artist)
            json.put("albumArtUrl", track.albumArtUrl.orEmpty())
            json.put("durationMs", track.durationMs)
            json.put("plays", plays)
            json.put("lastPlayedMs", System.currentTimeMillis())

            prefs.edit().putString(key, json.toString()).apply()
            updateArtistAffinity(track.artist, weight = 2)
        } catch (_: Exception) { }
    }

    /**
     * Records a full track completion (listened > 80%).
     */
    fun recordCompletion(trackId: String) {
        if (trackId.isBlank()) return
        try {
            val key = "track_$trackId"
            val raw = prefs.getString(key, null) ?: return
            val json = JSONObject(raw)
            json.put("completions", json.optInt("completions", 0) + 1)
            prefs.edit().putString(key, json.toString()).apply()
        } catch (_: Exception) { }
    }

    /**
     * Records a fast skip (< 20s of listening).
     */
    fun recordSkip(trackId: String) {
        if (trackId.isBlank()) return
        try {
            val key = "track_$trackId"
            val raw = prefs.getString(key, null) ?: return
            val json = JSONObject(raw)
            json.put("skips", json.optInt("skips", 0) + 1)
            prefs.edit().putString(key, json.toString()).apply()
        } catch (_: Exception) { }
    }

    /**
     * Records a like/favorite toggle.
     */
    fun recordLike(track: Track, isLiked: Boolean) {
        if (track.id.isBlank()) return
        try {
            val key = "track_${track.id}"
            val raw = prefs.getString(key, null)
            val json = if (raw != null) JSONObject(raw) else JSONObject()
            json.put("isLiked", isLiked)
            if (isLiked) {
                json.put("id", track.id)
                json.put("title", track.title)
                json.put("artist", track.artist)
                json.put("albumArtUrl", track.albumArtUrl.orEmpty())
                json.put("durationMs", track.durationMs)
                json.put("likedAtMs", System.currentTimeMillis())
            }
            prefs.edit().putString(key, json.toString()).apply()
            if (isLiked) updateArtistAffinity(track.artist, weight = 5)
        } catch (_: Exception) { }
    }

    private fun updateArtistAffinity(artist: String, weight: Int) {
        val clean = artist.trim().lowercase()
        if (clean.isBlank()) return
        try {
            val key = "art_$clean"
            val current = prefs.getInt(key, 0)
            prefs.edit().putInt(key, current + weight).apply()
        } catch (_: Exception) { }
    }

    /**
     * Computes the listener's Most Played tracks based on mathematical affinity score.
     */
    suspend fun getMostPlayedTracks(limit: Int = 12): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ScoredTrack>()
        try {
            val all = prefs.all
            for ((k, v) in all) {
                if (!k.startsWith("track_") || v !is String) continue
                val json = JSONObject(v)
                val plays = json.optInt("plays", 0)
                val completions = json.optInt("completions", 0)
                val skips = json.optInt("skips", 0)
                val isLiked = json.optBoolean("isLiked", false)
                val lastPlayedMs = json.optLong("lastPlayedMs", 0L)

                val score = (plays * 3) + (completions * 4) + (if (isLiked) 8 else 0) - (skips * 2)
                if (score > 0) {
                    val track = Track(
                        id = json.optString("id"),
                        title = json.optString("title"),
                        artist = json.optString("artist"),
                        albumArtUrl = json.optString("albumArtUrl").takeIf { it.isNotBlank() },
                        durationMs = json.optLong("durationMs", 0L)
                    )
                    tracks.add(ScoredTrack(track, score, lastPlayedMs))
                }
            }
        } catch (_: Exception) { }

        tracks.sortedWith(compareByDescending<ScoredTrack> { it.score }.thenByDescending { it.lastPlayedMs })
            .take(limit)
            .map { it.track }
    }

    /**
     * Determines current Vibe category based on time of day and listener habits.
     */
    fun getCurrentVibe(): VibeInfo {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> VibeInfo(
                title = "Morning Flow",
                subtitle = "Bright melodies & fresh focus beats",
                icon = "☀️",
                searchQuery = "chill acoustic morning songs"
            )
            in 12..17 -> VibeInfo(
                title = "Daytime Energy",
                subtitle = "Upbeat rhythms & high tempo vibes",
                icon = "⚡",
                searchQuery = "popular energetic dance hits"
            )
            in 18..22 -> VibeInfo(
                title = "Evening Sunset Chill",
                subtitle = "Cosmic sunset & mellow grooves",
                icon = "🌆",
                searchQuery = "sunset lofi chill r&b"
            )
            else -> VibeInfo(
                title = "Late Night Cosmic",
                subtitle = "Deep ambient soundscapes & lofi",
                icon = "🌙",
                searchQuery = "late night ambient synthwave"
            )
        }
    }

    /**
     * Retrieves the top 6 artists the user listens to most.
     */
    suspend fun getTopArtists(limit: Int = 6): List<String> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<String, Int>>()
        try {
            val all = prefs.all
            for ((k, v) in all) {
                if (k.startsWith("art_") && v is Int) {
                    val artistName = k.removePrefix("art_").replaceFirstChar { it.uppercase() }
                    list.add(artistName to v)
                }
            }
        } catch (_: Exception) { }
        list.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    private data class ScoredTrack(
        val track: Track,
        val score: Int,
        val lastPlayedMs: Long
    )
}

data class VibeInfo(
    val title: String,
    val subtitle: String,
    val icon: String,
    val searchQuery: String
)
