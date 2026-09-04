package com.example.hunterxmusic.data.local

import android.content.Context
import com.example.hunterxmusic.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tiny "Continue Listening" memory: the last few tracks the listener
 * actually chose to play, newest first. Feeds the Home shelf so reopening
 * the app always has a one-tap resume row, even after a cold start.
 */
class RecentTracksStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("recent_tracks", Context.MODE_PRIVATE)

    fun add(track: Track) {
        if (track.id.isBlank()) return
        try {
            val current = load().filterNot { it.id == track.id }.toMutableList()
            current.add(0, track)
            prefs.edit()
                .putString(KEY_RECENT, JSONObject()
                    .put("tracks", tracksToJson(current.take(MAX_RECENT)))
                    .toString())
                .apply()
        } catch (_: Exception) { }
    }

    fun load(): List<Track> {
        return try {
            val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
            tracksFromJson(JSONObject(raw).optJSONArray("tracks"))
        } catch (_: Exception) { emptyList() }
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private fun tracksToJson(tracks: List<Track>): JSONArray {
        val arr = JSONArray()
        for (t in tracks) {
            arr.put(JSONObject()
                .put("id", t.id)
                .put("title", t.title)
                .put("artist", t.artist)
                .put("album", t.album)
                .put("albumArtUrl", t.albumArtUrl ?: JSONObject.NULL)
                .put("durationMs", t.durationMs)
                .put("isLiked", t.isLiked))
        }
        return arr
    }

    private fun tracksFromJson(arr: JSONArray?): List<Track> {
        if (arr == null) return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            if (id.isBlank()) continue
            out.add(
                Track(
                    id = id,
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    album = obj.optString("album"),
                    albumArtUrl = obj.optString("albumArtUrl").takeIf { it.isNotBlank() && it != "null" },
                    durationMs = obj.optLong("durationMs", 0L),
                    streamingUrl = null,
                    localFilePath = null,
                    isDownloaded = false,
                    encryptionIv = null,
                    isLiked = obj.optBoolean("isLiked", false)
                )
            )
        }
        return out
    }

    private companion object {
        const val KEY_RECENT = "recent_v1"
        const val MAX_RECENT = 6
    }
}