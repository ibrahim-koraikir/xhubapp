package com.xhub.browser.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM tests — no Robolectric/Mockito needed because the bridge accepts a callback.
 */
class VideoJavascriptInterfaceTest {

    private data class Call(
        val url: String,
        val qualitiesJson: String?,
        val resolution: String?,
        val streamType: String
    )

    private val calls = mutableListOf<Call>()
    private lateinit var jsInterface: VideoJavascriptInterface

    @Before
    fun setup() {
        calls.clear()
        jsInterface = VideoJavascriptInterface { url, qualitiesJson, resolution, streamType ->
            calls += Call(url, qualitiesJson, resolution, streamType)
        }
    }

    @Test
    fun `valid video URL is forwarded to tab`() {
        jsInterface.onVideoDetected(
            "https://example.com/video.mp4",
            """{"720p":"https://example.com/720.mp4"}""",
            "1080p",
            "direct"
        )

        assertEquals(1, calls.size)
        assertEquals("https://example.com/video.mp4", calls[0].url)
        assertEquals("""{"720p":"https://example.com/720.mp4"}""", calls[0].qualitiesJson)
        assertEquals("1080p", calls[0].resolution)
        assertEquals("direct", calls[0].streamType)
    }

    @Test
    fun `null URL is rejected`() {
        jsInterface.onVideoDetected(null, null, null, null)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `blank URL is rejected`() {
        jsInterface.onVideoDetected("   ", null, null, null)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `URL exceeding max length is rejected`() {
        val longUrl = "https://example.com/" + "A".repeat(3000)
        jsInterface.onVideoDetected(longUrl, null, null, null)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `JSON exceeding max length is rejected`() {
        val hugeJson = "{" + "\"key\":\"value\",".repeat(10000) + "}"
        jsInterface.onVideoDetected("https://example.com/video.mp4", hugeJson, null, null)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `HTML tags in resolution are stripped`() {
        jsInterface.onVideoDetected(
            "https://example.com/video.mp4",
            null,
            "1080p<script>alert(1)</script>",
            "direct"
        )

        assertEquals(1, calls.size)
        assertEquals("1080palert(1)", calls[0].resolution)
        assertEquals("direct", calls[0].streamType)
    }

    @Test
    fun `malicious streamType is sanitized`() {
        jsInterface.onVideoDetected(
            "https://example.com/video.mp4",
            null,
            null,
            "../../../etc/passwd"
        )

        assertEquals(1, calls.size)
        assertNull(calls[0].resolution)
        assertEquals("etcpasswd", calls[0].streamType)
    }

    @Test
    fun `rate limiting blocks rapid calls`() {
        jsInterface.onVideoDetected("https://example.com/1.mp4", null, null, null)
        assertEquals(1, calls.size)

        jsInterface.onVideoDetected("https://example.com/2.mp4", null, null, null)
        assertEquals(1, calls.size)

        Thread.sleep(600)

        jsInterface.onVideoDetected("https://example.com/3.mp4", null, null, null)
        assertEquals(2, calls.size)
        assertEquals("https://example.com/3.mp4", calls[1].url)
    }
}
