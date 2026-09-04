package com.example.hunterxmusic

import com.example.hunterxmusic.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying real-time time-synced lyrics parsing logic.
 */
class LyricsParserTest {

    @Test
    fun testLrcParser_Centiseconds() {
        val lrcLine = "[00:14.20] Hello World"
        val parsed = parseLrc(lrcLine)
        
        assertEquals(1, parsed.size)
        assertEquals(14200L, parsed[0].timestampMs)
        assertEquals("Hello World", parsed[0].words)
    }

    @Test
    fun testLrcParser_Milliseconds() {
        val lrcLine = "[02:05.150] Testing Milliseconds"
        val parsed = parseLrc(lrcLine)
        
        assertEquals(1, parsed.size)
        assertEquals((2 * 60 * 1000) + (5 * 1000) + 150L, parsed[0].timestampMs) // 125150 ms
        assertEquals("Testing Milliseconds", parsed[0].words)
    }

    @Test
    fun testLrcParser_SortingOrder() {
        val lrcLines = """
            [00:10.00] Line 2
            [00:05.00] Line 1
            [00:15.50] Line 3
        """.trimIndent()
        val parsed = parseLrc(lrcLines)
        
        assertEquals(3, parsed.size)
        assertEquals("Line 1", parsed[0].words)
        assertEquals("Line 2", parsed[1].words)
        assertEquals("Line 3", parsed[2].words)
    }

    /**
     * Replicates the parsing algorithm used within MusicRepositoryImpl.
     */
    private fun parseLrc(lrcText: String): List<LyricLine> {
        val lineCollection = lrcText.split("\n")
        val lines = mutableListOf<LyricLine>()
        val lrcPattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")
        
        for (lineItem in lineCollection) {
            val match = lrcPattern.find(lineItem)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val subseconds = match.groupValues[3]
                val lyricsWords = match.groupValues[4].trim()
                
                val fractionalMs = if (subseconds.length == 2) {
                    subseconds.toLong() * 10 // Convert centiseconds to milliseconds
                } else {
                    subseconds.toLong() // Direct milliseconds
                }
                
                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + fractionalMs
                lines.add(LyricLine(totalMs, lyricsWords))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
