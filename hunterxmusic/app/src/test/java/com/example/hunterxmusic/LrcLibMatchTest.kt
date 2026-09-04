package com.example.hunterxmusic

import com.example.hunterxmusic.data.repository.LyricsVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LRCLIB matching and timestamp hygiene.
 *
 * Real API shape these are modelled on:
 *   https://lrclib.net/api/search?q=starboy
 *   -> { name/trackName: "Starboy", artistName: "Starboy", duration: 227.0, ... }
 *
 * Note the artistName is "Starboy", not "The Weeknd" — LRCLIB's artist field is
 * frequently wrong, which is exactly why matching leans on duration.
 */
class LrcLibMatchTest {

    private val starboyMs = 227_000L

    @Test
    fun acceptsCorrectSongEvenWhenLrcLibArtistIsWrong() {
        val score = LyricsVerifier.lrcLibScore(
            queryTitle = "Starboy",
            queryArtist = "The Weeknd",
            candidateTitle = "Starboy",
            candidateArtist = "Starboy",   // wrong in LRCLIB's data
            candidateDurationSec = 227.0,  // but the duration is exact
            trackDurationMs = starboyMs
        )
        assertTrue("exact duration + title should match despite bad artist", score > 0.0)
        assertTrue(LyricsVerifier.isLrcLibSyncedAcceptance(score))
    }

    @Test
    fun rejectsDifferentSongWithSimilarName() {
        // "ZULFAAN ... | Starboy X" — 190s vs the real track's 227s.
        val score = LyricsVerifier.lrcLibScore(
            queryTitle = "Starboy",
            queryArtist = "The Weeknd",
            candidateTitle = "ZULFAAN (Official Audio) SARRB | Starboy X",
            candidateArtist = "StarBoy X",
            candidateDurationSec = 190.0,
            trackDurationMs = starboyMs
        )
        assertFalse("a 37s length gap is a different recording", LyricsVerifier.isLrcLibSyncedAcceptance(score))
    }

    @Test
    fun hardRejectsWildlyDifferentDuration() {
        val score = LyricsVerifier.lrcLibScore(
            "Starboy", "The Weeknd", "Starboy", "The Weeknd",
            candidateDurationSec = 60.0, trackDurationMs = starboyMs
        )
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun prefersCloserDurationWhenTwoCandidatesShareATitle() {
        val exact = LyricsVerifier.lrcLibScore(
            "Kesariya", "Arijit Singh", "Kesariya", "Unknown", 268.0, 268_000L
        )
        val remix = LyricsVerifier.lrcLibScore(
            "Kesariya", "Arijit Singh", "Kesariya", "Unknown", 255.0, 268_000L
        )
        assertTrue("closer duration must win", exact > remix)
    }

    @Test
    fun withoutDurationTitleMustCarryTheMatch() {
        val good = LyricsVerifier.lrcLibScore(
            "Starboy", "The Weeknd", "Starboy", "The Weeknd", null, 0L
        )
        assertTrue(good > 0.0)

        val bad = LyricsVerifier.lrcLibScore(
            "Starboy", "The Weeknd", "Completely Different Song", "Someone Else", null, 0L
        )
        assertEquals(0.0, bad, 0.0001)
    }

    // ── Timestamps must never render as lyric text ────────────────

    @Test
    fun stripsLrcLineMarkers() {
        assertEquals(
            "I'm tryna put you in the worst mood",
            LyricsVerifier.stripTimingMarkers("[00:15.95]I'm tryna put you in the worst mood")
        )
    }

    @Test
    fun stripsWordLevelKaraokeTags() {
        assertEquals(
            "Look what you've done",
            LyricsVerifier.stripTimingMarkers("<00:21.10>Look <00:21.60>what <00:22.00>you've done")
        )
    }

    @Test
    fun stripsBareSecondMarkers() {
        assertEquals("plain", LyricsVerifier.stripTimingMarkers("[03:41]plain"))
    }

    @Test
    fun leavesOrdinaryLyricsUntouched() {
        val line = "We don't need no education"
        assertEquals(line, LyricsVerifier.stripTimingMarkers(line))
    }

    @Test
    fun collapsesToEmptyForInstrumentalMarkerOnlyLines() {
        assertEquals("", LyricsVerifier.stripTimingMarkers("[01:27.43]"))
    }
}
