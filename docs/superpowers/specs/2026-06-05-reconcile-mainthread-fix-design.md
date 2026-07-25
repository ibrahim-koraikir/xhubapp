# Reconcile Main Thread Fix Design

Design to resolve the main-thread disk-read jank during startup.

## Proposed Changes

### 1. Move Reconciliation to Background Thread
- **Problem**: Deserializing all session files during `finishInitialization()` runs synchronously on the main thread, risking startup jank or ANR.
- **Solution**: We will create `reconcileAsync(liveCurrentIds: Set<Int>, sessionsManager: SessionsManager, application: Application)` in `TabThumbnailCache`. This method will offload the entire session file deserialization and folder reconciliation task to the background `diskExecutor`.
- **Optimization**: To avoid redundant disk reads, we will skip deserializing the active session file since its live tab IDs are already passed via `liveCurrentIds`.
- **Files**: `TabThumbnailCache.kt`, `TabsManager.kt`
