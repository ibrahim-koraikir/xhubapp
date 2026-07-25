package com.xhub.browser.download

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

/**
 * In-process progress bus for yt-dlp downloads. Tests isolation via [clearForTests].
 */
class DownloadProgressBusTest {

    @Before
    fun clear() {
        DownloadProgressBus.clearForTests()
    }

    @Test
    fun `update inserts progress and remove deletes it`() {
        val progress = DownloadProgress(
            url = "https://example.com/v",
            filename = "video.mp4",
            percent = 40,
            state = DownloadProgress.State.RUNNING
        )
        DownloadProgressBus.update(progress)

        assertThat(DownloadProgressBus.downloads.value).containsKey(progress.url)
        assertThat(DownloadProgressBus.downloads.value[progress.url]?.percent).isEqualTo(40)

        DownloadProgressBus.remove(progress.url)
        assertThat(DownloadProgressBus.downloads.value).doesNotContainKey(progress.url)
    }

    @Test
    fun `update replaces existing entry for same url`() {
        DownloadProgressBus.update(
            DownloadProgress("https://a", "a.mp4", 10, DownloadProgress.State.RUNNING)
        )
        DownloadProgressBus.update(
            DownloadProgress("https://a", "a.mp4", 90, DownloadProgress.State.RUNNING)
        )

        assertThat(DownloadProgressBus.downloads.value).hasSize(1)
        assertThat(DownloadProgressBus.downloads.value["https://a"]?.percent).isEqualTo(90)
    }

    @Test
    fun `concurrent urls are tracked independently`() {
        DownloadProgressBus.update(
            DownloadProgress("https://a", "a.mp4", 10, DownloadProgress.State.RUNNING)
        )
        DownloadProgressBus.update(
            DownloadProgress("https://b", "b.mp4", 50, DownloadProgress.State.PAUSED)
        )

        assertThat(DownloadProgressBus.downloads.value).hasSize(2)
        assertThat(DownloadProgressBus.downloads.value["https://b"]?.state)
            .isEqualTo(DownloadProgress.State.PAUSED)
    }
}
