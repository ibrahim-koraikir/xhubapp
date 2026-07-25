package com.xhub.browser.download

/**
 * User-selectable download formats for yt-dlp (adaptive/embed) video downloads.
 *
 * Each entry maps to:
 *  - [fileExtension]: the expected output container/extension (informational; yt-dlp appends the
 *    real extension itself based on [ytDlpOptions]).
 *  - [ytDlpOptions]: the list of yt-dlp command-line options to apply. Each inner list is the
 *    argument(s) for a single YoutubeDLRequest.addOption(...) call (1 or 2 elements), mirroring
 *    how the service already adds options.
 *
 * The video (mp4) variants prefer the best video+audio and merge to mp4 (requires ffmpeg). The
 * height-capped variants fall back gracefully to the best combined stream at or below that height.
 * [AUDIO_MP3] extracts audio and transcodes to mp3 (also requires ffmpeg).
 *
 * Deliberately free of any Android (`R`, Context) dependency so the option mapping can be
 * unit-tested on a plain JVM without Robolectric (see DownloadFormatTest). The human-readable
 * label is resolved in the UI layer (WebPageTab) rather than stored here.
 */
enum class DownloadFormat(
    val fileExtension: String,
    val ytDlpOptions: List<List<String>>
) {
    BEST(
        "mp4",
        listOf(
            listOf("-f", "bestvideo*+bestaudio/best"),
            listOf("--merge-output-format", "mp4")
        )
    ),
    P1080(
        "mp4",
        listOf(
            listOf("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"),
            listOf("--merge-output-format", "mp4")
        )
    ),
    P720(
        "mp4",
        listOf(
            listOf("-f", "bestvideo[height<=720]+bestaudio/best[height<=720]/best"),
            listOf("--merge-output-format", "mp4")
        )
    ),
    P480(
        "mp4",
        listOf(
            listOf("-f", "bestvideo[height<=480]+bestaudio/best[height<=480]/best"),
            listOf("--merge-output-format", "mp4")
        )
    ),
    AUDIO_MP3(
        "mp3",
        listOf(
            listOf("-x"),
            listOf("--audio-format", "mp3"),
            listOf("--audio-quality", "0")
        )
    );

    companion object {
        /** Default when none is specified (or an unknown name is received over the intent). */
        val DEFAULT = BEST

        /**
         * Resolve a [DownloadFormat] from its [name] (as carried over the service intent extra),
         * falling back to [DEFAULT] for null/unknown values so a malformed extra can never crash
         * the download.
         */
        fun fromName(name: String?): DownloadFormat =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
