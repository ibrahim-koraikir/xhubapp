package com.xhub.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DownloadFormat] — the yt-dlp format/quality option mapping. Pure JVM, no Android
 * runtime required (labelRes is just an Int we never resolve here).
 */
class DownloadFormatTest {

    private fun DownloadFormat.flatOptions(): List<String> = ytDlpOptions.flatten()

    @Test
    fun `each option group has one or two arguments`() {
        // The service applies each group via addOption(a) or addOption(a, b); anything else would
        // be silently dropped, so guard the invariant here.
        DownloadFormat.values().forEach { fmt ->
            fmt.ytDlpOptions.forEach { group ->
                assertTrue(
                    "${fmt.name} has an option group of invalid size ${group.size}: $group",
                    group.size == 1 || group.size == 2
                )
            }
        }
    }

    @Test
    fun `best merges to mp4 and does not cap height`() {
        val opts = DownloadFormat.BEST.flatOptions()
        assertTrue(opts.contains("-f"))
        assertTrue(opts.contains("--merge-output-format"))
        assertTrue(opts.contains("mp4"))
        assertTrue(opts.none { it.contains("height<=") })
        assertEquals("mp4", DownloadFormat.BEST.fileExtension)
    }

    @Test
    fun `height-capped formats include the right height filter`() {
        assertTrue(DownloadFormat.P1080.flatOptions().any { it.contains("height<=1080") })
        assertTrue(DownloadFormat.P720.flatOptions().any { it.contains("height<=720") })
        assertTrue(DownloadFormat.P480.flatOptions().any { it.contains("height<=480") })
    }

    @Test
    fun `audio-only extracts mp3`() {
        val opts = DownloadFormat.AUDIO_MP3.flatOptions()
        assertTrue(opts.contains("-x"))
        assertTrue(opts.contains("--audio-format"))
        assertTrue(opts.contains("mp3"))
        assertEquals("mp3", DownloadFormat.AUDIO_MP3.fileExtension)
    }

    @Test
    fun `fromName resolves known names and falls back to default for unknown or null`() {
        assertEquals(DownloadFormat.P720, DownloadFormat.fromName("P720"))
        assertEquals(DownloadFormat.AUDIO_MP3, DownloadFormat.fromName("AUDIO_MP3"))
        assertEquals(DownloadFormat.DEFAULT, DownloadFormat.fromName("NOT_A_FORMAT"))
        assertEquals(DownloadFormat.DEFAULT, DownloadFormat.fromName(null))
    }

    @Test
    fun `default is best`() {
        assertEquals(DownloadFormat.BEST, DownloadFormat.DEFAULT)
    }
}
