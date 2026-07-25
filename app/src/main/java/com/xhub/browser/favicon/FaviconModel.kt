package com.xhub.browser.favicon

import com.xhub.browser.R
import com.xhub.browser.extensions.invert
import com.xhub.browser.extensions.isInvalid
import com.xhub.browser.extensions.pad
import com.xhub.browser.extensions.safeUse
import com.xhub.browser.settings.preferences.UserPreferences
import com.xhub.browser.utils.DrawableUtils
import com.xhub.browser.utils.FileUtils
import com.xhub.browser.utils.getFilteredColor
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.LruCache
import androidx.annotation.ColorInt
import androidx.annotation.WorkerThread
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import io.reactivex.Completable
import io.reactivex.Maybe
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive model that can fetch favicons
 * from URLs and also cache them.
 */
@Singleton
class FaviconModel @Inject constructor(
    private val application: Application,
    private val userPreferences: UserPreferences
) {

    private val loaderOptions = BitmapFactory.Options()
    private val bookmarkIconSize = application.resources.getDimensionPixelSize(R.dimen.material_grid_small_icon)
    private val faviconCache = object : LruCache<String, Bitmap>(com.xhub.browser.utils.FileUtils.megabytesToBytes(1).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Normalizes a URL down to its hostname for use as a memory-cache key.
     *
     * COMMENT 5: the memory cache was previously keyed on the full URL, while the disk cache is
     * keyed on the host. That mismatch meant a favicon fetched for `https://example.com/page`
     * while browsing was NOT reused by the home-screen shortcut for `https://example.com` — each
     * path hit the disk again (or the network). Keying both caches on the host aligns them and
     * makes a browse-time fetch immediately available to the shortcut tile of the same domain.
     * Falls back to the raw input if the URL can't be parsed.
     */
    private fun memCacheKey(url: String): String =
        url.toUri().host?.takeIf { it.isNotBlank() } ?: url

    /**
     * Retrieves a favicon from the memory cache.Bitmap may not be present if no bitmap has been
     * added for the URL or if it has been evicted from the memory cache.
     *
     * @param url the URL to retrieve the bitmap for.
     * @return the bitmap associated with the URL, may be null.
     */
    private fun getFaviconFromMemCache(url: String): Bitmap? {
        synchronized(faviconCache) {
            return faviconCache.get(memCacheKey(url))
        }
    }

    fun createDefaultBitmapForTitle(title: String?): Bitmap {
        val firstTitleCharacter = title?.takeIf(String::isNotBlank)?.let { it[0] } ?: '?'

        @ColorInt val defaultFaviconColor = com.xhub.browser.utils.DrawableUtils.characterToColorHash(firstTitleCharacter, application)

        return com.xhub.browser.utils.DrawableUtils.createRoundedLetterImage(
            firstTitleCharacter,
            bookmarkIconSize,
            bookmarkIconSize,
            defaultFaviconColor
        )
    }

    /**
     * Adds a bitmap to the memory cache for the given URL.
     *
     * @param url    the URL to map the bitmap to.
     * @param bitmap the bitmap to store.
     */
    private fun addFaviconToMemCache(url: String, bitmap: Bitmap) {
        synchronized(faviconCache) {
            faviconCache.put(memCacheKey(url), bitmap)
        }
    }

    /**
     * Retrieves the favicon for a URL, may be from network or cache.
     *
     * @param url   The URL that we should retrieve the favicon for.
     * @param title The title for the web page.
     * @param aOnDark Whether the favicon should be optimised for display on a dark background.
     * @param isIncognito Whether this request is from an incognito session (disables third-party lookups) - REQUIRED parameter
     */
    fun faviconForUrl(url: String, title: String, aOnDark: Boolean, isIncognito: Boolean): Maybe<Bitmap> = Maybe.create {
        val uri = url.toUri().toValidUri()
            ?: return@create it.onSuccess(createDefaultBitmapForTitle(title).pad())

        val cachedFavicon = getFaviconFromMemCache(url)

        if (cachedFavicon != null) {
            return@create it.onSuccess(cachedFavicon.pad())
        }

        // Try get the icon for the theme that was asked
        var faviconCacheFile = getFaviconCacheFile(application, uri, aOnDark)
        // If no icon and we ask for the dark variant
        if (!faviconCacheFile.exists() && aOnDark) {
            // Try get the light variant then
            faviconCacheFile = getFaviconCacheFile(application, uri, false)
        }

        if (faviconCacheFile.exists()) {
            val storedFavicon = BitmapFactory.decodeFile(faviconCacheFile.path, loaderOptions)

            if (storedFavicon != null) {
                addFaviconToMemCache(url, storedFavicon)
                return@create it.onSuccess(storedFavicon.pad())
            }
        }

        // Try downloading from multiple favicon sources before falling back to a letter image
        val downloaded = downloadFaviconForHost(uri.host, isIncognito, forceThirdParty = false)
        if (downloaded != null) {
            addFaviconToMemCache(url, downloaded)
            try {
                FileOutputStream(getFaviconCacheFile(application, uri, false)).safeUse { out ->
                    downloaded.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to cache downloaded favicon for ${uri.host}")
            }
            return@create it.onSuccess(downloaded.pad())
        }

        return@create it.onSuccess(createDefaultBitmapForTitle(title).pad())
    }

    /**
     * Like [faviconForUrl] but emits via [io.reactivex.MaybeObserver.onComplete] (no value) when
     * no genuine favicon is available instead of returning a letter-based fallback bitmap.
     *
     * Use this when the caller already shows a letter placeholder and only wants to update the UI
     * when a REAL favicon arrives (e.g. home-screen shortcut tiles).
     *
     * @param url       The URL to retrieve the favicon for.
     * @param aOnDark   Whether the favicon should be optimised for display on a dark background.
     * @param isIncognito Whether this request is from an incognito session (disables third-party lookups) - REQUIRED parameter
     */
    fun realFaviconForUrl(url: String, aOnDark: Boolean, isIncognito: Boolean): Maybe<Bitmap> =
        realFaviconForUrl(url, aOnDark, isIncognito, forceThirdParty = false)

    /**
     * Overload that allows a caller to bypass the [userPreferences.thirdPartyFaviconServicesEnabled]
     * privacy gate. Use ONLY for contexts where the user has explicitly opted in to the site (e.g.
     * their own home-screen shortcuts) and where [isIncognito] is known to be false — the home-screen
     * shortcut grid is never incognito, so fetching reliable favicons there is expected behaviour
     * regardless of the global privacy toggle.
     *
     * @param forceThirdParty When true (and [isIncognito] is false), DuckDuckGo / Google S2 lookups
     *                        are always attempted after the site's own favicon.ico fails.
     */
    fun realFaviconForUrl(url: String, aOnDark: Boolean, isIncognito: Boolean, forceThirdParty: Boolean): Maybe<Bitmap> = Maybe.create {
        val uri = url.toUri().toValidUri() ?: return@create it.onComplete()

        // Memory cache
        val cachedFavicon = getFaviconFromMemCache(url)
        if (cachedFavicon != null) {
            return@create it.onSuccess(cachedFavicon.pad())
        }

        // Disk cache
        var faviconCacheFile = getFaviconCacheFile(application, uri, aOnDark)
        if (!faviconCacheFile.exists() && aOnDark) {
            faviconCacheFile = getFaviconCacheFile(application, uri, false)
        }
        if (faviconCacheFile.exists()) {
            val storedFavicon = BitmapFactory.decodeFile(faviconCacheFile.path, loaderOptions)
            if (storedFavicon != null) {
                addFaviconToMemCache(url, storedFavicon)
                return@create it.onSuccess(storedFavicon.pad())
            }
        }

        // Network download — tries favicon.ico, DuckDuckGo, and Google S2 in order
        val downloaded = downloadFaviconForHost(uri.host, isIncognito, forceThirdParty)
        if (downloaded != null) {
            addFaviconToMemCache(url, downloaded)
            try {
                FileOutputStream(getFaviconCacheFile(application, uri, false)).safeUse { out ->
                    downloaded.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to cache downloaded favicon for ${uri.host}")
            }
            return@create it.onSuccess(downloaded.pad())
        }

        // No real favicon found — complete without a value so callers retain their letter placeholder
        return@create it.onComplete()
    }

    /**
     * Downloads the best available favicon for [host] by trying several sources in order.
     *
     * PRIVACY NOTE: By default, only the site's own favicon.ico is fetched to avoid leaking
     * browsing data. Third-party services (DuckDuckGo, Google) are contacted ONLY when the user
     * explicitly enables [userPreferences.thirdPartyFaviconServicesEnabled] AND the request is
     * not from an incognito session.
     *
     * INCOGNITO PROTECTION: Even when third-party services are enabled globally, incognito-origin
     * requests are exempted and limited to the site's own favicon.ico to prevent leaking private
     * browsing history to third parties.
     *
     * Sources tried in order:
     * 1. `https://{host}/favicon.ico` (always attempted)
     * 2. `https://icons.duckduckgo.com/ip3/{host}.ico` (only if preference enabled AND not incognito)
     * 3. `https://www.google.com/s2/favicons?domain={host}&sz=128` (only if preference enabled AND not incognito)
     *
     * Returns `null` if all sources fail or yield an invalid / placeholder bitmap.
     *
     * @param host The hostname to fetch a favicon for
     * @param isIncognito Whether this request is from an incognito session - REQUIRED parameter
     * @param forceThirdParty When true (and not incognito), bypass the [userPreferences] gate so
     *                        reliable third-party lookups are always attempted (e.g. home-screen
     *                        shortcuts the user explicitly added).
     * @return A valid favicon bitmap, or null if none found
     */
    @WorkerThread
    private fun downloadFaviconForHost(host: String, isIncognito: Boolean, forceThirdParty: Boolean = false): Bitmap? {
        if (host.isBlank()) return null

        // Always try the site's own favicon.ico first (no privacy leak)
        val siteFavicon = tryDownloadFavicon("https://$host/favicon.ico", host)
        if (siteFavicon != null) return siteFavicon

        // Skip third-party services if incognito (privacy protection always wins)
        if (isIncognito) {
            Timber.d("Third-party favicon services skipped for $host — incognito mode protects privacy")
            return null
        }

        // Skip third-party services if the user has disabled them AND the caller hasn't forced them
        if (!forceThirdParty && !userPreferences.thirdPartyFaviconServicesEnabled) {
            Timber.d("Third-party favicon services disabled for $host — no fallback attempted")
            return null
        }
        
        // Third-party fallbacks (user opted in AND not incognito)
        Timber.i("Attempting third-party favicon lookup for $host (user opted in, non-incognito)")
        val sources = listOf(
            "https://icons.duckduckgo.com/ip3/$host.ico",
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        )
        
        for (urlStr in sources) {
            val bitmap = tryDownloadFavicon(urlStr, host)
            if (bitmap != null) return bitmap
        }
        
        Timber.w("No favicon found for $host")
        return null
    }
    
    /**
     * Attempts to download and decode a favicon from the given URL.
     * Returns null if the request fails, the response is invalid, or the bitmap is a placeholder.
     *
     * SECURITY: Caps response size at 1 MB to prevent memory exhaustion from malicious servers.
     *
     * @param urlStr The URL to download from
     * @param host The hostname (for logging only)
     * @return A valid favicon bitmap, or null
     */
    @WorkerThread
    private fun tryDownloadFavicon(urlStr: String, host: String): Bitmap? {
        try {
            val connection = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connect()
            
            if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                // SECURITY: Cap response length to prevent arbitrarily large payloads
                val maxSize = 1_048_576 // 1 MB — favicons are typically < 50 KB
                val contentLength = connection.contentLengthLong
                if (contentLength > maxSize) {
                    Timber.w("Favicon from $urlStr exceeds size limit (${contentLength} bytes)")
                    connection.disconnect()
                    return null
                }
                
                // Read with size limit enforcement
                val bytes = connection.inputStream.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val data = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (total > maxSize) {
                            Timber.w("Favicon from $urlStr exceeded size limit during read")
                            return@use null
                        }
                        buffer.write(data, 0, count)
                    }
                    buffer.toByteArray()
                }
                
                connection.disconnect()
                
                if (bytes == null || bytes.isEmpty()) return null
                
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // Reject 1×1 pixel placeholders that some servers return
                if (bitmap != null && !bitmap.isRecycled && bitmap.width > 4 && bitmap.height > 4) {
                    Timber.d("Favicon for $host downloaded from $urlStr")
                    return bitmap
                }
            } else {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.d("Favicon source $urlStr failed: ${e.message}")
        }
        return null
    }

    /**
     * Caches a favicon for a particular URL.
     *
     * @param favicon the favicon to cache.
     * @param url     the URL to cache the favicon for.
     * @return an observable that notifies the consumer when it is complete.
     */
    fun cacheFaviconForUrl(favicon: Bitmap, url: String): Completable = Completable.create { emitter ->
        val uri = url.toUri().toValidUri() ?: return@create emitter.onComplete()

        Timber.d("Caching icon for ${uri.host}")

        // Validate bitmap before using it with Palette
        // Bitmap must not be recycled and must have valid dimensions to avoid IllegalArgumentException
        if (favicon.isInvalid()) {
            Timber.w("cacheFaviconForUrl: Invalid bitmap (recycled=${favicon.isRecycled}, width=${favicon.width}, height=${favicon.height})")
            // Skip caching for invalid bitmaps
            return@create emitter.onComplete()
        }

        // NOTE (comment 2): the RxJava contract forbids calling onComplete() more than once.
        // The previous implementation emitted onComplete() inside the dark-file safeUse block AND
        // again at the end of the function, which is a contract violation. Both writes are now
        // wrapped in a single try/catch; the emitter is signalled exactly once, after BOTH files
        // have been written (or onError if anything threw).
        try {
            /** TODO: This code was duplicated from [ImageView.setImageForTheme] fix it, somehow */
            // Check if that favicon is dark enough that it needs an inverted variant to be used on dark theme
            val palette = Palette.from(favicon).generate()
            val filteredColor = Color.BLACK or getFilteredColor(favicon) // OR with opaque black to remove transparency glitches
            val filteredLuminance = ColorUtils.calculateLuminance(filteredColor)
            //val color = Color.BLACK or (it.getVibrantColor(it.getLightVibrantColor(it.getDominantColor(Color.BLACK))))
            val color = palette.getDominantColor(Color.BLACK)
            val luminance = ColorUtils.calculateLuminance(color)
            // Lowered threshold from 0.025 to 0.02 for it to work with bbc.com/future
            // At 0.015 it does not kick in for GitHub
            val threshold = 0.02
            // Use white filter on darkest favicons
            // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.co.uk
            // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
            if (luminance < threshold && filteredLuminance < threshold
                // Needed to exclude white favicon variant provided by GitHub dark web theme
                && palette.dominantSwatch != null)
            {
                // Yes, that favicon needs an inverted variant
                FileOutputStream(getFaviconCacheFile(application, uri, true)).safeUse {
                    favicon.invert().compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.flush()
                }
            } else {
                // Dark favicon cache not needed anymore then, just delete that file if any.
                // Notably the case after switching to app dark theme and using GitHub or other sites providing favicon for dark web theme.
                getFaviconCacheFile(application, uri, true).delete()
            }

            // Light-mode variant — always written. No onComplete() here (see comment 2).
            FileOutputStream(getFaviconCacheFile(application, uri, false)).safeUse {
                favicon.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.flush()
            }

            // Single terminal signal, after both writes succeeded.
            emitter.onComplete()
        } catch (e: Exception) {
            // Avoid crashing the Completable on malformed/locked cache files; surface the error.
            if (!emitter.isDisposed) emitter.onError(e)
        }
    }

    companion object {

        private const val TAG = "FaviconModel"

        /**
         * Creates the cache file for the favicon image. File name will be in the form of "hash of URI host".png
         *
         * @param app the context needed to retrieve the cache directory.
         * @param validUri the URI to use as a unique identifier.
         * @return a valid cache file.
         */
        @WorkerThread
        fun getFaviconCacheFile(app: Application, validUri: ValidUri, aOnDark: Boolean): File {
            val hash = validUri.host.hashCode().toString()

            // NOTE (comment 3): the previous expression
            //     if (aOnDark) "ondark-" else {""} + "$hash.png"
            // relied on ambiguous operator precedence — the `+` could bind to {""} first, producing
            // a light-mode filename of ".png". Build the name explicitly so the intent is clear.
            val prefix = if (aOnDark) "ondark-" else ""
            return File(app.cacheDir, "$prefix$hash.png")
        }
    }

}
