package com.example.hunterxmusic.data.local

import android.content.Context
import com.example.hunterxmusic.domain.model.Track
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local snapshot of the last successfully loaded home shelves.
 *
 * Cold start used to stare at shimmer rows until ~8 sequential network
 * searches finished. This persists the last good trending / for you / quick
 * picks / country shelves and restores them synchronously at ViewModel init,
 * so the home screen renders instantly from local storage while the network
 * refreshes underneath. Playback data (streaming URL, local file) is
 * deliberately NOT cached — those are re-resolved per play.
 */
class HomeShelvesCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("home_shelves_cache", Context.MODE_PRIVATE)

    data class CachedShelves(
        val trending: List<Track>,
        val forYou: List<Track>,
        val quickPicks: List<Track>,
        val countrySongs: List<Track>
    )

    fun save(shelves: CachedShelves) {
        try {
            prefs.edit()
                .putString(KEY_SHELVES, JSONObject()
                    .put("trending", tracksToJson(shelves.trending))
                    .put("forYou", tracksToJson(shelves.forYou))
                    .put("quickPicks", tracksToJson(shelves.quickPicks))
                    .put("countrySongs", tracksToJson(shelves.countrySongs))
                    .toString())
                .apply()
        } catch (_: Exception) { }
    }

    fun load(): CachedShelves? {
        return try {
            val raw = prefs.getString(KEY_SHELVES, null) ?: return null
            val root = JSONObject(raw)
            val trending = tracksFromJson(root.optJSONArray("trending"))
            val forYou = tracksFromJson(root.optJSONArray("forYou"))
            val quickPicks = tracksFromJson(root.optJSONArray("quickPicks"))
            val countrySongs = tracksFromJson(root.optJSONArray("countrySongs"))
            if (trending.isEmpty() && forYou.isEmpty() && quickPicks.isEmpty()) null
            else CachedShelves(trending, forYou, quickPicks, countrySongs)
        } catch (_: Exception) { null }
    }

    fun clear() {
        prefs.edit().remove(KEY_SHELVES).apply()
    }

    /**
     * The country picker used to auto-pop on every launch until a country was
     * chosen — users who dismissed it got asked again and again. Prompt it
     * exactly once (first launch); afterwards the country pill in the header
     * is the only way in.
     */
    var countryPickerPrompted: Boolean
        get() = prefs.getBoolean(KEY_COUNTRY_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_COUNTRY_PROMPTED, value).apply()

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
        const val KEY_SHELVES = "shelves_v1"
        const val KEY_COUNTRY_PROMPTED = "country_picker_prompted_v1"
    }
}