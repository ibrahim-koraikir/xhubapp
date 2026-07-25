# Reconcile Thread Safety and Fail-Safe Plan
Goal: Snapshot sessions on the main thread, abort reconcile if any session bundle is unreadable, and reuse canonical key constants.
Architecture: Snapshot state on main thread, add early break/abort in reconcileAsync.
Tech Stack: Kotlin, Android SDK.
---

## Proposed Changes

### Task 1: TabsManager.kt Constant Promotion and Session Snapshotting
File: [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt)

1. Promote `TAB_KEY_PREFIX` to `const val` (remove `private` modifier).
2. Update `finishInitialization()` to map and snapshot sessions before running async reconciliation:
```kotlin
        // Reconcile disk cache with all live tab IDs across all saved sessions asynchronously
        val liveIds = allTabs.map { it.id }.toSet()
        val sessionsSnapshot = sessionsManager.sessions().map { session ->
            Pair(session.name, sessionsManager.fileNameFromSessionName(session.name))
        }
        val currentSession = sessionsManager.currentSessionName()
        fulguris.browser.tabs.TabThumbnailCache.reconcileAsync(liveIds, sessionsSnapshot, currentSession, application)
```

### Task 2: TabThumbnailCache.kt reconcileAsync Updates
File: [TabThumbnailCache.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabThumbnailCache.kt)

1. Accept the snapshot parameters in `reconcileAsync`.
2. Reuse `TabsManager.TAB_KEY_PREFIX` and `TabModel.KEY_TAB_ID`.
3. Stop pruning if any session bundle is unreadable (`bundle == null`).

```kotlin
    /**
     * Asynchronously reconciles the disk cache against the set of live tab IDs across all sessions.
     * Runs entirely on [diskExecutor] to keep the startup/UI thread free from disk I/O.
     *
     * @param liveCurrentIds Set of tab IDs currently loaded in the active session.
     * @param sessionsSnapshot List of Pair(sessionName, filename) captured from the main thread.
     * @param currentSession Name of the currently loaded active session.
     * @param application The Application context for disk I/O.
     */
    fun reconcileAsync(
        liveCurrentIds: Set<Int>,
        sessionsSnapshot: List<Pair<String, String>>,
        currentSession: String,
        application: android.app.Application
    ) {
        diskExecutor.execute {
            try {
                val liveIds = mutableSetOf<Int>()
                liveIds.addAll(liveCurrentIds)

                var hasReadError = false

                for (sessionInfo in sessionsSnapshot) {
                    val (name, filename) = sessionInfo
                    // Avoid re-reading/deserializing the current session bundle that loadSession already parsed
                    if (name == currentSession) {
                        continue
                    }
                    val bundle = try {
                        fulguris.utils.FileUtils.readBundleFromStorage(application, filename)
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading session file $filename")
                        null
                    }
                    if (bundle == null) {
                        // Mark read error so we don't proceed with deleting thumbnails
                        Timber.w("Session bundle $filename was unreadable. Aborting reconcile to prevent deletion of valid thumbnails.")
                        hasReadError = true
                        break
                    }
                    try {
                        val tabKeys = bundle.keySet().filter { it.startsWith(fulguris.browser.TabsManager.TAB_KEY_PREFIX) }
                        tabKeys.forEach { key ->
                            bundle.getBundle(key)?.let { tabBundle ->
                                val id = tabBundle.getInt(fulguris.browser.TabModel.KEY_TAB_ID, -1)
                                if (id != -1) {
                                    liveIds.add(id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to read tab IDs from session $name")
                        hasReadError = true
                        break
                    }
                }

                if (hasReadError) {
                    Timber.d("Skipping thumbnail reconciliation pass due to read errors.")
                    return@execute
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
