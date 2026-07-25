package com.xhub.browser.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Snapshot of a single download's progress, keyed by its source [url].
 *
 * This is the payload carried over [DownloadProgressBus]. It intentionally holds everything the
 * in-app progress card needs to render itself (filename, percent, state and, on completion, the
 * saved [location] so the card can offer an "Open" action).
 */
data class DownloadProgress(
    val url: String,
    val filename: String,
    /** 0..100, or -1 for indeterminate (yt-dlp reports -1 before it knows the total size). */
    val percent: Int,
    val state: State,
    /**
     * Current download speed in bytes/second, or -1 if unknown (not yet parsed, or not RUNNING).
     * Parsed from yt-dlp's progress output line (e.g. "... at 2.50MiB/s ...").
     */
    val speedBytesPerSec: Long = -1L,
    /**
     * Estimated time remaining in seconds, or -1 if unknown. Reported directly by yt-dlp's
     * progress callback (its second parameter).
     */
    val etaSeconds: Long = -1L,
    /** Human-readable error message, only set when [state] is [State.ERROR]. */
    val message: String? = null,
    /** content:// URI or file path of the finished file, only set when [state] is [State.COMPLETE]. */
    val location: String? = null
) {
    enum class State { RUNNING, PAUSED, COMPLETE, ERROR, CANCELLED }
}

/**
 * A tiny in-process event bus used to surface video-download progress from
 * [YtDlpDownloadService] (which runs in the same process) to the browser UI.
 *
 * Why a plain object + [MutableStateFlow] rather than broadcasts or binding:
 *  - The service and the Activity live in the same process, so no serialization is needed.
 *  - A [StateFlow] replays its latest value to new collectors, so a recreated Activity (e.g. after
 *    a configuration change) immediately re-renders the current download state.
 *  - [MutableStateFlow.update] is atomic, so the service can safely emit from its IO coroutine.
 *
 * Crucially, the service emits here regardless of the POST_NOTIFICATIONS permission — this is the
 * whole point of the in-app card: progress is visible even when notifications are denied.
 *
 * The map is keyed by download URL so concurrent downloads are supported. The UI decides which one
 * to render (currently the newest active one) and owns removal of terminal entries via [remove].
 */
object DownloadProgressBus {

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    /** Observable map of url -> latest [DownloadProgress]. */
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    /** Insert or replace the progress entry for [progress].url. */
    fun update(progress: DownloadProgress) = _downloads.update { it + (progress.url to progress) }

    /** Remove a download from the bus once its terminal state has been consumed by the UI. */
    fun remove(url: String) = _downloads.update { it - url }

    /** Test-only: clear all in-flight progress entries between unit tests. */
    internal fun clearForTests() {
        _downloads.value = emptyMap()
    }
}
