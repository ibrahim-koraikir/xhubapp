package com.xhub.browser.settings.fragment

import android.app.DownloadManager
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import com.xhub.browser.database.downloads.DownloadEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the pure [mapYtDlpEntry] merge logic extracted from
 * [DownloadsFragment.loadDownloads]. The three side-effecting dependencies (MediaStore query,
 * file probing, MIME lookup) are injected as lambdas, so the mapping is exercised without any
 * real DownloadManager/ContentResolver/File. Robolectric is only used so the
 * [DownloadManager.STATUS_SUCCESSFUL] constant resolves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class DownloadsFragmentMergeTest {

    private val now = 1_700_000_000_000L

    /** A resolver that always fails — asserts it is never called for the branch under test. */
    private val neverContent: (String) -> YtDlpContentInfo? = { error("content resolver should not be called") }
    private val neverFile: (String) -> YtDlpFileInfo? = { error("file resolver should not be called") }
    private val noMime: (String) -> String? = { null }

    // ── URL filtering ─────────────────────────────────────────────────────────

    @Test
    fun `http url is skipped (owned by DownloadManager)`() {
        val entry = DownloadEntry(url = "https://example.com/video.mp4", title = "video.mp4", contentSize = "10 MB")
        val result = mapYtDlpEntry(entry, now, neverContent, neverFile, noMime)
        assertThat(result).isNull()
    }

    @Test
    fun `http (non-https) url is skipped`() {
        val entry = DownloadEntry(url = "http://example.com/video.mp4", title = "video.mp4", contentSize = "10 MB")
        val result = mapYtDlpEntry(entry, now, neverContent, neverFile, noMime)
        assertThat(result).isNull()
    }

    // ── content:// happy path ───────────────────────────────────────────────────

    @Test
    fun `content uri maps to a successful YTDLP item with MediaStore metadata`() {
        val loc = "content://media/external/downloads/42"
        val entry = DownloadEntry(url = loc, title = "fallback.mp4", contentSize = "221 MB")
        val info = YtDlpContentInfo(
            displayName = "real_name.mp4",
            sizeBytes = 221_000_000L,
            dateModifiedSeconds = 1_699_999_000L,
            mimeType = "video/mp4"
        )

        val result = mapYtDlpEntry(entry, now, { info }, neverFile, noMime)

        assertThat(result).isNotNull()
        result!!
        assertThat(result.source).isEqualTo(DownloadSource.YTDLP)
        assertThat(result.status).isEqualTo(DownloadManager.STATUS_SUCCESSFUL)
        assertThat(result.location).isEqualTo(loc)
        assertThat(result.localUri).isEqualTo(loc)
        assertThat(result.title).isEqualTo("real_name.mp4")
        assertThat(result.bytesDownloaded).isEqualTo(221_000_000L)
        assertThat(result.totalSize).isEqualTo(221_000_000L)
        // DATE_MODIFIED is stored in seconds and converted to millis.
        assertThat(result.lastModified).isEqualTo(1_699_999_000L * 1000L)
        assertThat(result.mimeType).isEqualTo("video/mp4")
        assertThat(result.isOrphaned).isFalse()
        assertThat(result.id).isEqualTo(loc.hashCode().toLong())
        assertThat(result.stableKey).isEqualTo("ytdlp:$loc")
    }

    @Test
    fun `content uri falls back to entry title when MediaStore display name is null`() {
        val loc = "content://media/external/downloads/7"
        val entry = DownloadEntry(url = loc, title = "stored_title.mp4", contentSize = "5 MB")
        val info = YtDlpContentInfo(
            displayName = null,
            sizeBytes = 5_000_000L,
            dateModifiedSeconds = 1_699_000_000L,
            mimeType = "video/mp4"
        )

        val result = mapYtDlpEntry(entry, now, { info }, neverFile, noMime)

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("stored_title.mp4")
    }

    // ── file path happy path ──────────────────────────────────────────────────

    @Test
    fun `absolute file path maps to a successful YTDLP item with file metadata`() {
        val loc = "/storage/emulated/0/Download/clip.mp4"
        val entry = DownloadEntry(url = loc, title = "clip.mp4", contentSize = "15 MB")
        val info = YtDlpFileInfo(name = "clip.mp4", sizeBytes = 15_000_000L, lastModifiedMillis = 1_695_000_000_000L)

        val result = mapYtDlpEntry(entry, now, neverContent, { info }, { "video/mp4" })

        assertThat(result).isNotNull()
        result!!
        assertThat(result.source).isEqualTo(DownloadSource.YTDLP)
        assertThat(result.location).isEqualTo(loc)
        assertThat(result.title).isEqualTo("clip.mp4")
        assertThat(result.bytesDownloaded).isEqualTo(15_000_000L)
        assertThat(result.totalSize).isEqualTo(15_000_000L)
        // File lastModified is already in millis — used verbatim.
        assertThat(result.lastModified).isEqualTo(1_695_000_000_000L)
        assertThat(result.mimeType).isEqualTo("video/mp4")
        assertThat(result.isOrphaned).isFalse()
    }

    // ── orphaned detection ──────────────────────────────────────────────────────

    @Test
    fun `content uri with missing MediaStore row is marked orphaned but still returned`() {
        val loc = "content://media/external/downloads/999"
        val entry = DownloadEntry(url = loc, title = "gone.mp4", contentSize = "3 MB")

        val result = mapYtDlpEntry(entry, now, { null }, neverFile, noMime)

        assertThat(result).isNotNull()
        result!!
        assertThat(result.isOrphaned).isTrue()
        // Falls back to the stored title and now() when the row is gone.
        assertThat(result.title).isEqualTo("gone.mp4")
        assertThat(result.lastModified).isEqualTo(now)
        assertThat(result.bytesDownloaded).isEqualTo(0L)
    }

    @Test
    fun `file path that no longer exists is marked orphaned but still returned`() {
        val loc = "/storage/emulated/0/Download/deleted.mp4"
        val entry = DownloadEntry(url = loc, title = "deleted.mp4", contentSize = "3 MB")

        val result = mapYtDlpEntry(entry, now, neverContent, { null }, noMime)

        assertThat(result).isNotNull()
        result!!
        assertThat(result.isOrphaned).isTrue()
        assertThat(result.title).isEqualTo("deleted.mp4")
        assertThat(result.bytesDownloaded).isEqualTo(0L)
    }

    // ── MIME inference ──────────────────────────────────────────────────────────

    @Test
    fun `null MediaStore mime is inferred from the display name extension`() {
        val loc = "content://media/external/downloads/11"
        val entry = DownloadEntry(url = loc, title = "fallback.mp4", contentSize = "8 MB")
        val info = YtDlpContentInfo(
            displayName = "movie.webm",
            sizeBytes = 8_000_000L,
            dateModifiedSeconds = 1_699_000_000L,
            mimeType = null
        )
        // The extension resolver should be asked for "webm" (lowercased extension of display name).
        val result = mapYtDlpEntry(entry, now, { info }, neverFile, { ext ->
            assertThat(ext).isEqualTo("webm")
            "video/webm"
        })

        assertThat(result).isNotNull()
        assertThat(result!!.mimeType).isEqualTo("video/webm")
    }

    @Test
    fun `present MediaStore mime is preserved and extension inference is not used`() {
        val loc = "content://media/external/downloads/12"
        val entry = DownloadEntry(url = loc, title = "video.mp4", contentSize = "8 MB")
        val info = YtDlpContentInfo(
            displayName = "video.mp4",
            sizeBytes = 8_000_000L,
            dateModifiedSeconds = 1_699_000_000L,
            mimeType = "video/mp4"
        )

        val result = mapYtDlpEntry(entry, now, { info }, neverFile, { error("mime inference should not be called") })

        assertThat(result).isNotNull()
        assertThat(result!!.mimeType).isEqualTo("video/mp4")
    }

    @Test
    fun `mime stays null when extension is unknown`() {
        val loc = "/storage/emulated/0/Download/mystery.xyz"
        val entry = DownloadEntry(url = loc, title = "mystery.xyz", contentSize = "1 MB")
        val info = YtDlpFileInfo(name = "mystery.xyz", sizeBytes = 1_000_000L, lastModifiedMillis = 1_695_000_000_000L)

        val result = mapYtDlpEntry(entry, now, neverContent, { info }, { null })

        assertThat(result).isNotNull()
        assertThat(result!!.mimeType).isNull()
    }

    @Test
    fun `extension is lowercased before mime inference for file path items`() {
        val loc = "/storage/emulated/0/Download/CLIP.MP4"
        val entry = DownloadEntry(url = loc, title = "CLIP.MP4", contentSize = "9 MB")
        val info = YtDlpFileInfo(name = "CLIP.MP4", sizeBytes = 9_000_000L, lastModifiedMillis = 1_695_000_000_000L)

        val result = mapYtDlpEntry(entry, now, neverContent, { info }, { ext ->
            assertThat(ext).isEqualTo("mp4")
            "video/mp4"
        })

        assertThat(result).isNotNull()
        assertThat(result!!.mimeType).isEqualTo("video/mp4")
    }
}
