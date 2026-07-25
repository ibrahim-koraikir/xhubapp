package com.xhub.browser.download

import android.content.Context
import com.xhub.browser.R
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [YtDlpDownloadService.summarizeYtDlpError].
 *
 * Verifies that raw yt-dlp error output is condensed into the correct user-facing string:
 *  - unsupported/protected-site errors  -> R.string.video_download_error_unsupported
 *  - network/connection errors          -> R.string.video_download_error_network
 *  - anything else (raw traceback)       -> first non-blank line, ERROR: stripped, capped at 140 chars
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class YtDlpDownloadServiceTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun summarize(error: String): String =
        YtDlpDownloadService.summarizeYtDlpError(context, error)

    private val unsupportedString: String
        get() = context.getString(R.string.video_download_error_unsupported)

    private val networkString: String
        get() = context.getString(R.string.video_download_error_network)

    // ── Unsupported / protected site ──────────────────────────────────────────

    @Test
    fun `unsupported URL error maps to unsupported string`() {
        assertThat(summarize("ERROR: Unsupported URL: https://example.com/video"))
            .isEqualTo(unsupportedString)
    }

    @Test
    fun `no video formats error maps to unsupported string`() {
        assertThat(summarize("ERROR: No video formats found for this page"))
            .isEqualTo(unsupportedString)
    }

    @Test
    fun `unable to extract error maps to unsupported string`() {
        assertThat(summarize("ERROR: Unable to extract player response"))
            .isEqualTo(unsupportedString)
    }

    @Test
    fun `unsupported classification is case insensitive`() {
        assertThat(summarize("unsupported url: whatever"))
            .isEqualTo(unsupportedString)
    }

    // ── Network / connection ──────────────────────────────────────────────────

    @Test
    fun `unable to download error maps to network string`() {
        assertThat(summarize("ERROR: Unable to download webpage: HTTP Error 503"))
            .isEqualTo(networkString)
    }

    @Test
    fun `timed out error maps to network string`() {
        assertThat(summarize("ERROR: The read operation timed out"))
            .isEqualTo(networkString)
    }

    @Test
    fun `connection error maps to network string`() {
        assertThat(summarize("ERROR: Connection refused by the server"))
            .isEqualTo(networkString)
    }

    @Test
    fun `network keyword error maps to network string`() {
        assertThat(summarize("ERROR: A network error occurred"))
            .isEqualTo(networkString)
    }

    // ── Traceback fallback ────────────────────────────────────────────────────

    @Test
    fun `unrecognised error falls back to first non-blank line with ERROR prefix stripped`() {
        val raw = """
            ERROR: Something unexpected happened
            Traceback (most recent call last):
              File "yt_dlp/extractor.py", line 42, in run
        """.trimIndent()

        // Not one of the recognised categories -> fallback path.
        // First non-blank line is "ERROR: Something unexpected happened"; the "ERROR:" prefix
        // is stripped and the result trimmed.
        assertThat(summarize(raw)).isEqualTo("Something unexpected happened")
    }

    @Test
    fun `fallback skips leading blank lines and uses first non-blank line`() {
        val raw = "\n\n   \nWeird failure with no known keyword\nsecond line"
        assertThat(summarize(raw)).isEqualTo("Weird failure with no known keyword")
    }

    @Test
    fun `fallback truncates the summary to 140 characters`() {
        // A single-line error with no recognised keyword, longer than the 140-char cap.
        val longMessage = "Boom " + "x".repeat(300)
        val result = summarize(longMessage)
        assertThat(result).hasSize(140)
        assertThat(longMessage).startsWith(result)
    }

    @Test
    fun `fallback returns trimmed original when input is a single blank-padded line`() {
        assertThat(summarize("   just spaces around text   "))
            .isEqualTo("just spaces around text")
    }
}
