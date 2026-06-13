package com.xhub.browser.utils

import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class VideoValidationHelperTest {

    @Test
    fun `isAcceptableMediaUrl rejects URLs over 4096 characters`() {
        val longUrl = "http://example.com/" + "a".repeat(4096)
        assertThat(VideoValidationHelper.isAcceptableMediaUrl(longUrl)).isFalse()
    }

    @Test
    fun `isAcceptableMediaUrl accepts http, https, and blob schemes`() {
        assertThat(VideoValidationHelper.isAcceptableMediaUrl("http://example.com/video.mp4")).isTrue()
        assertThat(VideoValidationHelper.isAcceptableMediaUrl("https://example.com/video.mp4")).isTrue()
        assertThat(VideoValidationHelper.isAcceptableMediaUrl("blob:http://example.com/uuid")).isTrue()
        assertThat(VideoValidationHelper.isAcceptableMediaUrl("ftp://example.com/video.mp4")).isFalse()
        assertThat(VideoValidationHelper.isAcceptableMediaUrl("file:///android_asset/video.mp4")).isFalse()
    }

    @Test
    fun `isDownloadableHttpUrl rejects URLs over 4096 characters`() {
        val longUrl = "http://example.com/" + "a".repeat(4096)
        assertThat(VideoValidationHelper.isDownloadableHttpUrl(longUrl)).isFalse()
    }

    @Test
    fun `isDownloadableHttpUrl accepts standard http and https but rejects m3u8, mpd, and blob`() {
        assertThat(VideoValidationHelper.isDownloadableHttpUrl("http://example.com/video.mp4")).isTrue()
        assertThat(VideoValidationHelper.isDownloadableHttpUrl("https://example.com/video.mp4")).isTrue()
        assertThat(VideoValidationHelper.isDownloadableHttpUrl("http://example.com/video.m3u8")).isFalse()
        assertThat(VideoValidationHelper.isDownloadableHttpUrl("https://example.com/video.mpd")).isFalse()
        assertThat(VideoValidationHelper.isDownloadableHttpUrl("blob:http://example.com/uuid")).isFalse()
    }

    @Test
    fun `parseQualitiesJson limits output to 20 entries`() {
        val jsonMap = (1..25).associate { "quality_$it" to "https://example.com/video_$it.mp4" }
        val qualitiesJson = org.json.JSONObject(jsonMap).toString()

        val parsed = VideoValidationHelper.parseQualitiesJson(qualitiesJson)
        assertThat(parsed).isNotNull()
        assertThat(parsed).hasSize(20)
    }

    @Test
    fun `parseQualitiesJson truncates keys to 50 characters and trims whitespaces`() {
        val longKey = "   " + "a".repeat(60) + "   "
        val expectedSanitizedKey = "a".repeat(50)
        
        val qualitiesJson = "{\"$longKey\": \"https://example.com/video.mp4\"}"
        val parsed = VideoValidationHelper.parseQualitiesJson(qualitiesJson)
        
        assertThat(parsed).isNotNull()
        assertThat(parsed).containsKey(expectedSanitizedKey)
        assertThat(parsed!![expectedSanitizedKey]).isEqualTo("https://example.com/video.mp4")
    }

    @Test
    fun `parseQualitiesJson filters out non-downloadable quality entries`() {
        val qualitiesJson = """
            {
                "1080p": "https://example.com/video_1080.mp4",
                "720p (HLS)": "https://example.com/video_720.m3u8",
                "480p (DASH)": "https://example.com/video_480.mpd",
                "360p": "https://example.com/video_360.mp4"
            }
        """.trimIndent()

        val parsed = VideoValidationHelper.parseQualitiesJson(qualitiesJson)
        assertThat(parsed).isNotNull()
        assertThat(parsed).hasSize(2)
        assertThat(parsed).containsOnlyKeys("1080p", "360p")
    }
}
