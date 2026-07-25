# Reconcile Main Thread Fix Implementation Plan
Goal: Move the session list deserialization and disk reconciliation logic onto a background executor, and skip deserializing the active session file.
Architecture: Offload work to single-threaded `diskExecutor` in TabThumbnailCache.
Tech Stack: Kotlin, Android SDK.
---

## Proposed Changes

### Task 1: TabThumbnailCache.kt Async Reconciliation
File: [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)

Add `reconcileAsync` which runs on `diskExecutor` and avoids reading the current session file:
```kotlin
    /**
     * Asynchronously reconciles the disk cache against the set of live tab IDs across all sessions.
     * Runs entirely on [diskExecutor] to keep the startup/UI thread free from disk I/O.
     *
     * @param liveCurrentIds Set of tab IDs currently loaded in the active session.
     * @param sessionsManager The SessionsManager instance to query all sessions.
     * @param application The Application context for disk I/O.
     */
    fun reconcileAsync(
        liveCurrentIds: Set<Int>,
        sessionsManager: fulguris.browser.SessionsManager,
        application: android.app.Application
    ) {
        diskExecutor.execute {
            try {
                val liveIds = mutableSetOf<Int>()
                liveIds.addAll(liveCurrentIds)

                val currentSession = sessionsManager.currentSessionName()

                sessionsManager.sessions().forEach { session ->
                    // Avoid re-reading/deserializing the current session bundle that loadSession already parsed
                    if (session.name == currentSession) {
                        return@forEach
                    }
                    val filename = sessionsManager.fileNameFromSessionName(session.name)
                    val bundle = fulguris.utils.FileUtils.readBundleFromStorage(application, filename)
                    if (bundle != null) {
                        try {
                            val tabKeys = bundle.keySet().filter { it.startsWith("TAB_") }
                            tabKeys.forEach { key ->
                                bundle.getBundle(key)?.let { tabBundle ->
                                    val id = tabBundle.getInt("TAB_ID", -1)
                                    if (id != -1) {
                                        liveIds.add(id)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to read tab IDs from session ${session.name}")
                        }
                    }
                }

                // Perform reconciliation on disk
                val dir = diskDir() ?: return@execute
                val files = dir.listFiles() ?: return@execute
                var deleted = 0
                for (file in files) {
                    val idStr = file.nameWithoutExtension
                    val id = idStr.toIntOrNull()
                    if (id == null || id !in liveIds) {
                        file.delete()
                        deleted++
                    }
                }
                if (deleted > 0) Timber.d("reconcileAsync: deleted $deleted orphaned thumbnail(s)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to reconcile thumbnail cache asynchronously")
            }
        }
    }
```

### Task 2: TabsManager.kt Call Update
File: [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt)

Call `reconcileAsync` instead of compiling the set of IDs on the main thread:
```kotlin
        // Reconcile disk cache with all live tab IDs across all saved sessions asynchronously
        val liveIds = allTabs.map { it.id }.toSet()
        fulguris.browser.tabs.TabThumbnailCache.reconcileAsync(liveIds, sessionsManager, application)
```
