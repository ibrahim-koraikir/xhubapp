package com.xhub.browser.browser.tabs

import android.graphics.Bitmap
import com.xhub.browser.SDK_VERSION
import com.xhub.browser.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [SDK_VERSION])
class TabThumbnailCacheTest {

    @Before
    fun setup() {
        TabThumbnailCache.clear()
    }

    @Test
    fun `cache put and get stores and retrieves the bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        TabThumbnailCache.put(42, bitmap)

        assertThat(TabThumbnailCache.get(42)).isSameAs(bitmap)
    }

    @Test
    fun `cache remove deletes the cached bitmap`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        TabThumbnailCache.put(42, bitmap)
        assertThat(TabThumbnailCache.get(42)).isNotNull

        TabThumbnailCache.remove(42)
        assertThat(TabThumbnailCache.get(42)).isNull()
    }

    @Test
    fun `cache clear deletes all cached bitmaps`() {
        val bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        TabThumbnailCache.put(1, bitmap1)
        TabThumbnailCache.put(2, bitmap2)

        assertThat(TabThumbnailCache.get(1)).isNotNull
        assertThat(TabThumbnailCache.get(2)).isNotNull

        TabThumbnailCache.clear()
        assertThat(TabThumbnailCache.get(1)).isNull()
        assertThat(TabThumbnailCache.get(2)).isNull()
    }

    @Test
    fun `customCacheDir configuration overrides default path successfully`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            TabThumbnailCache.put(42, bitmap, true)
            // Allow disk executor to complete writing
            Thread.sleep(200)

            val writtenFile = java.io.File(tempDir, "42.jpg")
            assertThat(writtenFile.exists()).isTrue()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `file not found notifies all callbacks with null and cleans up pending list`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            var callbackCalled = false
            var callbackResult: Bitmap? = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

            // Tab ID 99 has no file on disk — should get immediate null callback
            TabThumbnailCache.get(99, true) { result ->
                callbackCalled = true
                callbackResult = result
            }

            assertThat(callbackCalled).isTrue()
            assertThat(callbackResult).isNull()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `concurrent get triggers only one disk read and notifies all callbacks`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            // Put a valid bitmap on disk first
            val initialBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            val file = java.io.File(tempDir, "100.jpg")
            java.io.FileOutputStream(file).use { out ->
                initialBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            var cb1Called = false
            var cb2Called = false
            var cb1Result: Bitmap? = null
            var cb2Result: Bitmap? = null

            // First call starts disk executor.
            TabThumbnailCache.get(100, true) { result ->
                cb1Called = true
                cb1Result = result
            }

            // Second call should append callback to the in-flight load list (not start another).
            TabThumbnailCache.get(100, true) { result ->
                cb2Called = true
                cb2Result = result
            }

            // Allow disk executor to complete reading
            Thread.sleep(200)

            // Drive main-thread looper so background post() runs synchronously in Robolectric.
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat(cb1Called).isTrue()
            assertThat(cb2Called).isTrue()
            assertThat(cb1Result).isNotNull()
            assertThat(cb2Result).isNotNull()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `remove notifies in-flight callbacks with null instead of silently discarding them`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            // Write a file so the disk load is actually started (not short-circuited by file-not-found)
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            val file = java.io.File(tempDir, "200.jpg")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            var callbackCalled = false
            var callbackResult: Bitmap? = bitmap // pre-load with non-null

            // Start in-flight disk load — disk executor is now running
            TabThumbnailCache.get(200, true) { result ->
                callbackCalled = true
                callbackResult = result
            }

            // Remove cancels the in-flight load; callback must still fire with null
            TabThumbnailCache.remove(200)

            // Drive main-thread looper to process the mainHandler.post() from remove()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat(callbackCalled).isTrue()
            assertThat(callbackResult).isNull()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }

    @Test
    fun `clear notifies all in-flight callbacks with null instead of silently discarding them`() {
        val tempDir = java.io.File.createTempFile("temp_thumb_cache", "").apply {
            delete()
            mkdir()
        }
        try {
            TabThumbnailCache.customCacheDir = tempDir
            // Write files so the disk loads are actually started
            listOf(201, 202).forEach { id ->
                val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(java.io.File(tempDir, "$id.jpg")).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            }

            var cb201Called = false
            var cb202Called = false
            var cb201Result: Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            var cb202Result: Bitmap? = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            TabThumbnailCache.get(201, true) { result ->
                cb201Called = true
                cb201Result = result
            }
            TabThumbnailCache.get(202, true) { result ->
                cb202Called = true
                cb202Result = result
            }

            // Clear cancels all in-flight loads; callbacks must still fire with null
            TabThumbnailCache.clear()

            // Drive main-thread looper to process the mainHandler.post() from clear()
            org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertThat(cb201Called).isTrue()
            assertThat(cb201Result).isNull()
            assertThat(cb202Called).isTrue()
            assertThat(cb202Result).isNull()
        } finally {
            tempDir.deleteRecursively()
            TabThumbnailCache.customCacheDir = null
        }
    }
}
