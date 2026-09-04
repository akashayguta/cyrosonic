package com.example.hunterxmusic

import com.example.hunterxmusic.data.repository.LyricsVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the lyrics candidate verification engine — the layer that
 * decides whether a provider's result actually belongs to the playing track.
 */
class LyricsVerifierTest {

    // ── Normalization ────────────────────────────────────────────

    @Test
    fun testNormalize_stripsPunctuationAndCase() {
        assertEquals("kesariya brahmastra", LyricsVerifier.normalizeForMatch("Kesariya (Brahmastra)!"))
        assertEquals("sorry justin bieber", LyricsVerifier.normalizeForMatch("Sorry — Justin Bieber"))
    }

    @Test
    fun testTitleTokens_dropsNoiseWords() {
        val tokens = LyricsVerifier.titleTokens("Kesariya (Official Video) Lyrical")
        assertTrue("noise words should be dropped", tokens.none { it == "official" || it == "video" || it == "lyrical" })
        assertTrue("real title words must survive", "kesariya" in tokens)
    }

    // ── Token overlap ────────────────────────────────────────────

    @Test
    fun testTokenOverlap_identicalTitles() {
        assertEquals(1.0, LyricsVerifier.tokenOverlap("Kesariya", "Kesariya"), 0.001)
        // Decorated variant fully contains the requested title → near-exact
        assertTrue(LyricsVerifier.tokenOverlap("Kesariya", "Kesariya (Brahmastra)") >= 0.9)
    }

    @Test
    fun testTokenOverlap_differentTitles() {
        val score = LyricsVerifier.tokenOverlap("Sorry", "Blank Space")
        assertTrue("unrelated titles must score low", score < 0.3)
    }

    // ── Candidate scoring ────────────────────────────────────────

    @Test
    fun testCandidateScore_exactMatch_passesSyncedThreshold() {
        val score = LyricsVerifier.candidateScore(
            queryTitle = "Kesariya", queryArtist = "Arijit Singh",
            candidateTitle = "Kesariya (Brahmastra)", candidateArtist = "Arijit Singh",
            candidateDurationSec = 268.0, trackDurationMs = 268_000L
        )
        assertTrue("exact match must pass synced acceptance, got $score", LyricsVerifier.isSyncedAcceptance(score))
    }

    @Test
    fun testCandidateScore_sameTitleWrongArtist_rejectedForSynced() {
        val score = LyricsVerifier.candidateScore(
            queryTitle = "Sorry", queryArtist = "Justin Bieber",
            candidateTitle = "Sorry", candidateArtist = "Nothing But Thieves",
            candidateDurationSec = 202.0, trackDurationMs = 202_000L
        )
        assertFalse("wrong artist must fail synced acceptance, got $score", LyricsVerifier.isSyncedAcceptance(score))
    }

    @Test
    fun testCandidateScore_durationMismatch_penalized() {
        val matching = LyricsVerifier.candidateScore(
            "Kesariya", "Arijit Singh", "Kesariya", "Arijit Singh",
            candidateDurationSec = 268.0, trackDurationMs = 268_000L
        )
        val mismatched = LyricsVerifier.candidateScore(
            "Kesariya", "Arijit Singh", "Kesariya", "Arijit Singh",
            candidateDurationSec = 195.0, trackDurationMs = 268_000L
        )
        assertTrue("matching duration should outscore mismatched", matching > mismatched)
    }

    // ── Duration plausibility ────────────────────────────────────

    @Test
    fun testDurationPlausible_fullSongLrcAgainstFullTrack() {
        val stamps = (0 until 40).map { it * 6000L } // ~4 min of cues
        assertTrue(LyricsVerifier.isDurationPlausible(stamps, 240_000L))
    }

    @Test
    fun testDurationPlausible_fullAlbumLrcAgainstShortTrack_rejected() {
        val stamps = (0 until 60).map { it * 6000L } // 6 min of cues
        assertFalse(LyricsVerifier.isDurationPlausible(stamps, 150_000L))
    }

    @Test
    fun testDurationPlausible_unknownTrackDuration_skipsCheck() {
        assertTrue(LyricsVerifier.isDurationPlausible(listOf(1000L, 999_000L), 0L))
    }

    @Test
    fun testDurationPlausible_plainZeroTimestamps_alwaysOk() {
        assertTrue(LyricsVerifier.isDurationPlausible(listOf(0L, 0L, 0L), 240_000L))
    }

    // ── Junk detection ───────────────────────────────────────────

    @Test
    fun testLooksLikeLyrics_realLyricsAccepted() {
        val lines = listOf(
            "Kesariya shaam hai aaj bhi",
            "Teri aankhon mein main rahoon",
            "Udne de mujhe kahin door",
            "Tere vaaste hi toh main",
            "Aaya hai yahan se"
        )
        assertTrue(LyricsVerifier.looksLikeLyrics(lines))
    }

    @Test
    fun testLooksLikeLyrics_videoDescriptionRejected() {
        val lines = listOf(
            "Subscribe to my channel!",
            "Follow me on Instagram @artist",
            "New album out now: https://example.com",
            "Click here for merch"
        )
        assertFalse(LyricsVerifier.looksLikeLyrics(lines))
    }

    @Test
    fun testLooksLikeLyrics_tooFewLinesRejected() {
        assertFalse(LyricsVerifier.looksLikeLyrics(listOf("One line", "Two lines")))
    }
}
