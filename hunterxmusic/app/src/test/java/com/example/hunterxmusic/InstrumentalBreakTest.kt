package com.example.hunterxmusic

import com.example.hunterxmusic.data.repository.LyricsVerifier
import com.example.hunterxmusic.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instrumental-break detection, using the real LRCLIB payload for "Barsaat"
 * (https://lrclib.net/api/search?q=barsaat).
 *
 * That sheet encodes its breaks as timestamped BLANK lines:
 *   [00:56.58]              <- blank; break starts
 *   [01:09.95] या तो...     <- vocals resume 13.4s later
 * so blank lines carrying a timestamp must survive cleanup, not be filtered.
 */
class InstrumentalBreakTest {

    /** Trimmed excerpt spanning the first real break in the Barsaat sheet. */
    private val barsaat = listOf(
        LyricLine(48_530L, "छोड़के ने चाळ पड़या तू"),
        LyricLine(52_080L, "तन्ने मेरी याद, आवेगी"),
        LyricLine(56_580L, "", isInstrumental = true),   // blank marker
        LyricLine(69_950L, "या तो मन्ने मार ज्यांदा रै"),
        LyricLine(73_220L, "न्यू क्यूं मन्ने, छोड़ के गया?")
    )

    @Test
    fun keepsLrcLibBlankMarkerAsAnInstrumentalLine() {
        val out = LyricsVerifier.markInstrumentalBreaks(barsaat)
        val marker = out.single { it.timestampMs == 56_580L }
        assertTrue("blank timestamped line must stay, flagged instrumental", marker.isInstrumental)
        assertEquals("", marker.words)
    }

    @Test
    fun doesNotDoubleUpWhenABlankMarkerAlreadyExists() {
        val out = LyricsVerifier.markInstrumentalBreaks(barsaat)
        // 52.08 -> 56.58 -> 69.95. The blank already covers the gap, so exactly
        // one marker should sit in there, not two.
        val markersInBreak = out.filter { it.isInstrumental && it.timestampMs in 52_080L..69_950L }
        assertEquals(1, markersInBreak.size)
    }

    @Test
    fun sungLinesAreNeverFlaggedInstrumental() {
        val out = LyricsVerifier.markInstrumentalBreaks(barsaat)
        assertTrue(out.filter { it.words.isNotEmpty() }.none { it.isInstrumental })
    }

    @Test
    fun synthesisesAMarkerForALongGapWithNoBlankLine() {
        // Some providers omit the blank line entirely — a 13s hole with nothing
        // in it would otherwise freeze on the previous lyric.
        val noMarker = listOf(
            LyricLine(52_080L, "तन्ने मेरी याद, आवेगी"),
            LyricLine(69_950L, "या तो मन्ने मार ज्यांदा रै")
        )
        val out = LyricsVerifier.markInstrumentalBreaks(noMarker)
        val inserted = out.single { it.isInstrumental && it.timestampMs > 52_080L }
        assertEquals(52_080L + LyricsVerifier.INSTRUMENTAL_MARKER_LEAD_MS, inserted.timestampMs)
        // It must land inside the gap, never on top of the next sung line.
        assertTrue(inserted.timestampMs < 69_950L)
    }

    @Test
    fun leavesNormalLineSpacingAlone() {
        // 3.5s apart — ordinary pacing, no marker.
        val tight = listOf(
            LyricLine(1_200L, "line one"),
            LyricLine(4_700L, "line two")
        )
        val out = LyricsVerifier.markInstrumentalBreaks(tight)
        assertEquals(2, out.size)
        assertTrue(out.none { it.isInstrumental })
    }

    @Test
    fun addsAnIntroMarkerWhenTheFirstLyricStartsLate() {
        // Barsaat's first line is at 13.60s — the screen shouldn't be empty
        // until then.
        val out = LyricsVerifier.markInstrumentalBreaks(
            listOf(
                LyricLine(13_600L, "छोड़के ने चाळ पड़या तू"),
                LyricLine(16_900L, "तोड़ के ने चाळ पड़या तू")
            )
        )
        assertEquals(3, out.size)
        assertEquals(0L, out.first().timestampMs)
        assertTrue(out.first().isInstrumental)
    }

    @Test
    fun noIntroMarkerWhenVocalsStartImmediately() {
        val out = LyricsVerifier.markInstrumentalBreaks(
            listOf(LyricLine(900L, "straight in"), LyricLine(3_200L, "second"))
        )
        assertTrue(out.none { it.isInstrumental })
    }

    @Test
    fun plainUntimedSheetsGetNoMarkersAtAll() {
        // Unsynced lyrics have no timings to reason about, and every line is 0L.
        val plain = listOf(
            LyricLine(0L, "first"),
            LyricLine(0L, "second"),
            LyricLine(0L, "third")
        )
        val out = LyricsVerifier.markInstrumentalBreaks(plain)
        assertEquals(3, out.size)
        assertTrue(out.none { it.isInstrumental })
    }

    @Test
    fun markersStayInChronologicalOrder() {
        val out = LyricsVerifier.markInstrumentalBreaks(barsaat)
        val stamps = out.map { it.timestampMs }
        assertEquals(stamps.sorted(), stamps)
    }

    @Test
    fun handlesTheOutroBlankLineAtTheEnd() {
        // Barsaat ends with "[03:02.96] " — a trailing blank.
        val withOutro = listOf(
            LyricLine(175_650L, "किसा Banjara था रै तू?"),
            LyricLine(182_960L, "", isInstrumental = true)
        )
        val out = LyricsVerifier.markInstrumentalBreaks(withOutro)
        assertTrue("trailing blank stays as the outro marker", out.last().isInstrumental)
        assertEquals(182_960L, out.last().timestampMs)
        // The sung line itself is untouched.
        assertFalse(out.single { it.words.isNotEmpty() }.isInstrumental)
    }
}
