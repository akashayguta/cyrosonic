package com.example.hunterxmusic.data.remote.model

/**
 * One LRCLIB record.
 *
 * Field names mirror the API exactly. Note that [artistName] is frequently
 * wrong in LRCLIB's data (searching "starboy" returns The Weeknd's track with
 * `artistName: "Starboy"`), so matching must lean on [duration] and
 * [trackName] rather than trusting the artist. See
 * `LyricsVerifier.lrcLibScore`.
 */
data class LrcResponse(
    val id: Long,
    val name: String?,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    /** Track length in SECONDS (e.g. 227.0) — the most reliable match signal. */
    val duration: Double?,
    val instrumental: Boolean?,
    /** LRC text with `[mm:ss.xx]` markers, or null when only plain text exists. */
    val syncedLyrics: String?,
    val plainLyrics: String?
) {
    /** Best available title: the API populates both, `trackName` is canonical. */
    val bestTitle: String? get() = trackName?.takeIf { it.isNotBlank() } ?: name
}
