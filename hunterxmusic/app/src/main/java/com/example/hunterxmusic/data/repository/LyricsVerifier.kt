package com.example.hunterxmusic.data.repository

import com.example.hunterxmusic.domain.model.LyricLine

/**
 * Pure-Kotlin verification engine for lyrics candidate matching.
 *
 * The #1 cause of "lyrics don't match the song" is accepting the first search
 * hit from a lyrics provider. A track titled "Sorry" matches dozens of songs by
 * different artists. This engine scores every candidate on three axes:
 *
 *   1. Title similarity  (token overlap between requested and candidate titles)
 *   2. Artist similarity (token overlap, primary artist only)
 *   3. Duration sanity   (candidate length vs the actual playing track length)
 *
 * It also detects junk "lyrics" that are really video descriptions
 * ("Subscribe!", hashtags, URLs) before they ever reach the screen.
 */
object LyricsVerifier {

    private val JUNK_LINE_MARKERS = listOf(
        "subscribe", "follow me", "http://", "https://", "www.", "#", "@",
        "click here", "buy now", "spotify:", "itunes", "apple music", "deep focus"
    )

    private val TITLE_NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "lyrical", "hd", "4k",
        "remix", "cover", "live", "visualizer", "song", "music", "full", "lrc",
        "feat", "ft", "with", "the", "a", "an"
    )

    /**
     * Normalize a string for fuzzy comparison: lowercase, strip punctuation,
     * romanize common unicode dashes/quotes, collapse whitespace.
     */
    fun normalizeForMatch(input: String): String {
        return input
            .lowercase()
            .replace("–", "-").replace("—", "-").replace("'", "")
            .replace(Regex("[\\p{Punct}&&[^&]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Meaningful title tokens: noise words like "official"/"video" removed so
     * "(Official Video)" decorations never skew the similarity score.
     */
    fun titleTokens(input: String): Set<String> {
        return normalizeForMatch(input)
            .split(" ", "-", "&")
            .map { it.trim() }
            .filter { it.length > 1 && it !in TITLE_NOISE_WORDS }
            .toSet()
    }

    /**
     * Jaccard-style token overlap in [0,1]. When one side's tokens fully cover
     * the other's ("Kesariya" vs "Kesariya (Brahmastra)") it's treated as a
     * near-exact match: 0.9.
     */
    fun tokenOverlap(a: String, b: String): Double {
        val ta = titleTokens(a)
        val tb = titleTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val intersection = ta.intersect(tb).size.toDouble()
        val union = ta.union(tb).size.toDouble()
        val jaccard = intersection / union
        return if (ta.all { it in tb } || tb.all { it in ta }) {
            (jaccard + 0.3).coerceIn(0.9, 1.0)
        } else {
            jaccard
        }
    }

    /**
     * Composite score for a lyrics candidate against the requested track.
     * A perfect score is ~1.0; anything is better than nothing — callers
     * decide the acceptance threshold per source tier.
     */
    fun candidateScore(
        queryTitle: String,
        queryArtist: String,
        candidateTitle: String?,
        candidateArtist: String?,
        candidateDurationSec: Double? = null,
        trackDurationMs: Long = 0L
    ): Double {
        // Artist veto: both artists known and sharing zero tokens means this is
        // a different act's song with a same/similar title. No duration bonus
        // may rescue it — wrong-song karaoke is the worst failure mode.
        // Script guard: providers romanize regional metadata inconsistently
        // ("సిద్ శ్రీరామ్" vs "Sid Sriram"), so the veto only applies when
        // both sides use compatible scripts. Otherwise title + duration decide.
        if (!queryArtist.isBlank() && !candidateArtist.isNullOrBlank() &&
            isScriptComparable(queryArtist, candidateArtist)
        ) {
            if (tokenOverlap(queryArtist, candidateArtist) < 0.15) {
                return 0.30
            }
        }

        val titleScore = if (candidateTitle.isNullOrBlank()) 0.35 else {
            if (isScriptComparable(queryTitle, candidateTitle)) {
                tokenOverlap(queryTitle, candidateTitle)
            } else {
                // Cross-script titles (native vs romanized) can't token-match;
                // treat as neutral-positive and let other signals decide.
                0.55
            }
        }
        val artistScore = if (candidateArtist.isNullOrBlank()) {
            0.3 // Unknown candidate artist: neutral, let title + duration decide
        } else if (queryArtist.isBlank() || !isScriptComparable(queryArtist, candidateArtist)) {
            0.3
        } else {
            tokenOverlap(queryArtist, candidateArtist)
        }

        var score = titleScore * 0.6 + artistScore * 0.4

        if (candidateDurationSec != null && candidateDurationSec > 0 && trackDurationMs > 0) {
            val trackSec = trackDurationMs / 1000.0
            val delta = kotlin.math.abs(candidateDurationSec - trackSec)
            score += when {
                delta <= 3.0 -> 0.25
                delta <= 7.0 -> 0.10
                delta <= 15.0 -> 0.0
                else -> -0.45 // Different cut of the song: strong penalty
            }
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * True when both strings are written in compatible (Latin-ish) scripts so
     * token comparison is meaningful. Any substantial non-Latin content on
     * either side makes the comparison unreliable → not comparable.
     */
    private fun isScriptComparable(a: String, b: String): Boolean {
        return latinRatio(a) >= 0.6 && latinRatio(b) >= 0.6
    }

    private fun latinRatio(s: String): Double {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return 1.0
        val latin = letters.count { it in 'a'..'z' || it in 'A'..'Z' }
        return latin.toDouble() / letters.length
    }

    /**
     * Threshold for accepting SYNCED lyrics from a search-based provider.
     * Synced karaoke that highlights the wrong song is worse than no lyrics,
     * so the bar is high.
     *
     * [durationKnown] tightens the bar when the track length is unknown. With
     * no duration, [isDurationPlausible] cannot reject anything, so title and
     * artist agreement have to carry the whole decision on their own.
     */
    fun isSyncedAcceptance(score: Double, durationKnown: Boolean = true): Boolean =
        score >= (if (durationKnown) 0.55 else 0.72)

    /**
     * Slightly lower bar for plain (unsynced) text — it renders in reading
     * mode, so mild mismatches are less jarring, but still verified.
     */
    fun isPlainAcceptance(score: Double, durationKnown: Boolean = true): Boolean =
        score >= (if (durationKnown) 0.40 else 0.58)

    /** True when a track duration is usable for verification. */
    fun isDurationUsable(trackDurationMs: Long): Boolean = trackDurationMs > 30_000L

    /**
     * Matches an LRCLIB candidate against the playing track.
     *
     * LRCLIB is scored differently from every other provider because its
     * `artistName` is unreliable — searching "starboy" returns The Weeknd's
     * track with `artistName: "Starboy"`. Applying the usual artist veto there
     * would throw away correct lyrics.
     *
     * So duration leads: a candidate whose length matches the playing track to
     * within a couple of seconds is almost certainly the same recording. Title
     * agreement confirms it, and artist agreement is a small bonus that can
     * never veto.
     *
     * Returns 0.0 for a hard reject.
     */
    fun lrcLibScore(
        queryTitle: String,
        queryArtist: String,
        candidateTitle: String?,
        candidateArtist: String?,
        candidateDurationSec: Double?,
        trackDurationMs: Long
    ): Double {
        val titleScore = if (candidateTitle.isNullOrBlank()) {
            0.0
        } else if (isScriptComparable(queryTitle, candidateTitle)) {
            tokenOverlap(queryTitle, candidateTitle)
        } else {
            0.55 // cross-script (native vs romanized) — can't token-match
        }

        val durationKnown = isDurationUsable(trackDurationMs) &&
            candidateDurationSec != null && candidateDurationSec > 0

        if (durationKnown) {
            val delta = kotlin.math.abs(candidateDurationSec!! - trackDurationMs / 1000.0)
            // A different recording/edit of the same song is the main failure
            // mode for karaoke, and length is what gives it away.
            if (delta > 20.0) return 0.0

            val durationScore = when {
                delta <= 2.0 -> 0.55
                delta <= 5.0 -> 0.40
                delta <= 10.0 -> 0.22
                else -> 0.05
            }
            // Title still has to be in the right neighbourhood.
            if (titleScore < 0.25 && delta > 5.0) return 0.0

            val artistBonus = if (!queryArtist.isBlank() && !candidateArtist.isNullOrBlank() &&
                isScriptComparable(queryArtist, candidateArtist)
            ) {
                tokenOverlap(queryArtist, candidateArtist) * 0.15
            } else 0.0

            return (durationScore + titleScore * 0.40 + artistBonus).coerceIn(0.0, 1.0)
        }

        // No usable duration: title has to carry it, with the artist helping.
        if (titleScore < 0.5) return 0.0
        val artistScore = if (!queryArtist.isBlank() && !candidateArtist.isNullOrBlank() &&
            isScriptComparable(queryArtist, candidateArtist)
        ) {
            tokenOverlap(queryArtist, candidateArtist)
        } else 0.0
        return (titleScore * 0.75 + artistScore * 0.25).coerceIn(0.0, 1.0)
    }

    /** Acceptance bar for an LRCLIB candidate carrying real LRC timestamps. */
    fun isLrcLibSyncedAcceptance(score: Double): Boolean = score >= 0.62

    /** Acceptance bar for LRCLIB plain text (reading mode, less risky). */
    fun isLrcLibPlainAcceptance(score: Double): Boolean = score >= 0.45

    /** `[mm:ss.xx]` / `[mm:ss]` LRC markers. */
    private val LRC_TIMESTAMP = Regex("\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?\\]")

    /** Word-level karaoke tags some providers embed, e.g. `<00:12.34>`. */
    private val WORD_TIMESTAMP = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")

    /** Audio cue / noise tags in caption streams (e.g. [Music], [संगीत], [गाना गाने की आवाज़]) */
    private val AUDIO_NOISE_TAGS = Regex("\\[(?:music|संगीत|गाना गाने की आवाज़|गाना|गीत|applause|laughter|cheering|sound|instrumental|vocal|guitar solo|solo|intro|outro|beat|drop)\\]", RegexOption.IGNORE_CASE)

    /**
     * Strips every timing marker and non-lyric audio noise tag out of a lyric line.
     * The timing lives in [com.example.hunterxmusic.domain.model.LyricLine.timestampMs] —
     * it must never appear in the words.
     */
    fun stripTimingMarkers(words: String): String {
        return words
            .replace(LRC_TIMESTAMP, " ")
            .replace(WORD_TIMESTAMP, " ")
            .replace(AUDIO_NOISE_TAGS, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * A silence of at least this long between two sung lines counts as an
     * instrumental break and earns a ♪ marker.
     */
    const val INSTRUMENTAL_GAP_MS = 7_000L

    /** How soon after the previous line the ♪ marker lights up. */
    const val INSTRUMENTAL_MARKER_LEAD_MS = 2_500L

    /**
     * Marks the instrumental stretches in a synced lyric sheet.
     *
     * Two sources of truth, both handled here:
     *  1. LRCLIB's own blank timestamped lines — `[00:56.58]` with no text,
     *     followed by vocals at `[01:09.95]`. Already flagged by the caller
     *     (blank words), kept in place.
     *  2. A long silence between two sung lines with no marker between them.
     *     Not every provider inserts a blank line, so any gap of at least
     *     [INSTRUMENTAL_GAP_MS] gets a synthetic ♪ so the screen keeps moving
     *     instead of holding the last sung line frozen.
     *
     * Long intros count too: if the first line starts well into the track, a
     * marker goes at 0 so something is on screen from the first second.
     */
    fun markInstrumentalBreaks(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        // Only meaningful for real synced sheets; plain text has no timings.
        if (lines.none { it.timestampMs > 0L }) {
            return lines.filter { it.words.isNotEmpty() }
        }

        val out = ArrayList<LyricLine>(lines.size + 4)

        val first = lines.first()
        if (!first.isInstrumental && first.timestampMs >= INSTRUMENTAL_GAP_MS) {
            out.add(LyricLine(timestampMs = 0L, words = "", isInstrumental = true))
        }

        for ((index, line) in lines.withIndex()) {
            out.add(line)
            val next = lines.getOrNull(index + 1) ?: continue
            // A blank marker already announces the break — don't double up.
            if (line.isInstrumental || next.isInstrumental) continue
            if (next.timestampMs - line.timestampMs >= INSTRUMENTAL_GAP_MS) {
                // Sit the marker just after the line that finished, so it lights
                // up while the music plays rather than retroactively.
                out.add(
                    LyricLine(
                        timestampMs = line.timestampMs + INSTRUMENTAL_MARKER_LEAD_MS,
                        words = "",
                        isInstrumental = true
                    )
                )
            }
        }
        return out
    }

    /**
     * Duration sanity for a parsed lyric list against the real track length.
     * Guards against full-song LRC files glued onto short preview streams
     * (and vice versa).
     *
     * When the duration is unknown this still returns true — it has nothing to
     * compare against — so callers MUST pair it with the tightened score
     * thresholds above. Previously they didn't, which silently disabled the
     * entire duration guard for every track whose metadata carried no length
     * (all imported playlist tracks, among others).
     */
    fun isDurationPlausible(lines: List<Long>, trackDurationMs: Long): Boolean {
        if (!isDurationUsable(trackDurationMs)) return true // Unknown / too-short duration: nothing to check
        val lastMs = lines.maxOrNull() ?: return false
        if (lastMs <= 0L) return true // Plain (unsynced) candidate: nothing to check
        val coverRatio = lastMs.toDouble() / trackDurationMs.toDouble()
        return coverRatio in 0.25..1.18
    }

    /**
     * Re-bases synced LRC timestamps onto the track that is actually playing.
     *
     * Providers like LRCLIB carry one specific edit of a song — radio edit,
     * sped-up master, live take, YouTube upload. When the record's own
     * duration differs from the playing track by a small factor, the whole
     * sheet drifts proportionally: line 90 lands on the wrong beat by
     * mid-song, which reads exactly like "the lyrics don't match the speed".
     * Scaling every timestamp by trackDuration / recordDuration keeps each
     * line on its beat regardless of which edit is playing.
     *
     * The band is deliberately narrow. Outside it the difference is a
     * different song or a fundamentally different recording — the caller's
     * scoring rejects those before this ever runs.
     */
    fun rescaleTimestamps(
        lines: List<LyricLine>,
        recordDurationMs: Long?,
        trackDurationMs: Long
    ): List<LyricLine> {
        if (recordDurationMs == null || recordDurationMs <= 0L) return lines
        if (!isDurationUsable(trackDurationMs)) return lines
        val ratio = trackDurationMs.toDouble() / recordDurationMs.toDouble()
        if (ratio !in 0.85..1.18) return lines
        if (kotlin.math.abs(ratio - 1.0) < 0.01) return lines
        return lines.map { line ->
            if (line.timestampMs <= 0L) line
            else line.copy(timestampMs = (line.timestampMs * ratio).toLong())
        }
    }

    /**
     * Heuristic junk filter — rejects "lyrics" that are really video/channel
     * descriptions, ad copy, or playlists.
     */
    fun looksLikeLyrics(lines: List<String>): Boolean {
        val meaningful = lines.filter { it.isNotBlank() }
        if (meaningful.size < 4) return false

        var junk = 0
        var wordy = 0
        for (line in meaningful) {
            val lower = line.lowercase()
            if (JUNK_LINE_MARKERS.any { lower.contains(it) }) junk++
            if (lower.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 2) wordy++
        }

        val junkRatio = junk.toDouble() / meaningful.size
        val wordyRatio = wordy.toDouble() / meaningful.size
        return junkRatio <= 0.15 && wordyRatio >= 0.6
    }
}
