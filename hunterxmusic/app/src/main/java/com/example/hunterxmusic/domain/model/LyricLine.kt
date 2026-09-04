package com.example.hunterxmusic.domain.model

/**
 * One sung word with its real timestamp, straight from YouTube's ASR
 * caption stream (json3 segs[].tOffsetMs). Drives true word-by-word
 * karaoke — never estimated, only rendered when a source actually
 * provides per-word times.
 */
data class WordCue(
    val text: String,
    val startMs: Long,
    val endMs: Long = startMs
)

/**
 * Domain model representing a single line of time-synced lyrics.
 *
 * [timestampMs] is when this line should light up — it drives the karaoke glow
 * and the auto-scroll. It never appears as text on screen.
 *
 * [isEstimated] marks timestamps that were proportionally fitted to the
 * track duration rather than taken from a real synced LRC source — the UI
 * shows an amber "Estimated" state for those so users always know what
 * they're looking at.
 *
 * [isInstrumental] marks a stretch of music with no vocals. LRCLIB encodes
 * these as timestamped blank lines (e.g. `[00:56.58]` followed by nothing
 * until `[01:09.95]`), and long gaps between sung lines are the same thing.
 * The UI renders them as an animated ♪ marker instead of empty space.
 *
 * [wordCues] carries REAL per-word timestamps when the source provides them
 * (YouTube ASR json3). When non-null the lyrics screen renders word-by-word
 * karaoke; when null it falls back to whole-line glow. Never fabricated.
 */
data class LyricLine(
    val timestampMs: Long,
    val words: String,
    val isEstimated: Boolean = false,
    val isInstrumental: Boolean = false,
    val wordCues: List<WordCue>? = null
)
