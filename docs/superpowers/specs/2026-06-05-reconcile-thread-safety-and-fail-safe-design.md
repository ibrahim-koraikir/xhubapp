# Reconcile Thread Safety and Fail-Safe Design

Design to resolve thread safety and unreadable session file deletion bugs in `reconcileAsync`.

## Proposed Changes

### 1. Thread-Safe Session List Snapshotting
- **Problem**: `reconcileAsync` iterates the live Hilt-managed list of sessions in `sessionsManager` on a background thread.
- **Solution**: Capture a snapshot of session identifiers (`List<Pair<String, String>>`) on the main thread during `finishInitialization()` and pass it to `reconcileAsync`. The background thread then processes only this read-only snapshot.
- **Files**: `TabsManager.kt`, `TabThumbnailCache.kt`

### 2. Fail-Safe Deletion Pass
- **Problem**: If reading any session file fails, it returns `null` and is treated as having zero live tabs, which causes its valid thumbnails to be permanently deleted from the shared cache directory.
- **Solution**: If any session file bundle cannot be read (returns `null`), we will abort the entire reconciliation/deletion pass for this run.
- **Files**: `TabThumbnailCache.kt`

### 3. Key Constants Reuse
- **Problem**: Prefix `TAB_` and id key `TAB_ID` are hardcoded in `reconcileAsync`.
- **Solution**: Promote `TabsManager.TAB_KEY_PREFIX` to `const val` and use it along with `TabModel.KEY_TAB_ID` in `reconcileAsync`.
- **Files**: `TabsManager.kt`, `TabThumbnailCache.kt`
