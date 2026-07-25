# Session Save Serialization and Race Condition Prevention

## Summary
Implemented serialized session saving to prevent race conditions from overlapping save operations. Added explicit save coalescing, verified file operations, and proper backup management to ensure session integrity even during rapid successive saves or app termination.

## Problem Statement

### Before:
1. **Unordered File Operations**: Multiple `saveCurrentSession()` calls could launch concurrent jobs on `iScopeThreadPool`
2. **Race Conditions**: Overlapping saves could interleave file operations:
   - Job A: Write temp1 → Rename session to backup → ...
   - Job B: Write temp2 → Rename session to backup (overwrites A's backup!) → ...
   - Result: Corrupted or lost session data
3. **No Verification**: File operations (rename, delete) didn't verify success
4. **Premature Backup Deletion**: Backup deleted before verifying new session was written
5. **Recovery Path Dependency**: Startup often relied on recovery mechanisms due to save failures

## Changes Made

### 1. FileUtils.java - Verified File Operations

**Modified Method: `renameBundleInStorage()`**
- **Before**: `void` return type, no verification
- **After**: `boolean` return type, verifies rename success
- Logs success/failure for debugging
- Returns `false` if source file doesn't exist or rename fails

**New Method: `deleteBundleInStorageVerified()`**
- Returns `boolean` to indicate success/failure
- Logs deletion operations
- Returns `true` if file doesn't exist (idempotent)
- Returns `false` only if deletion actually failed

**New Method: `fileExists()`**
- Checks if a file exists in application storage
- Used to verify file operations completed successfully
- Enables defensive programming in save sequence

### 2. TabsManager.kt - Serialized Session Saving

**New Fields:**
```kotlin
// Mutex to serialize session save operations
private val saveMutex = Mutex()

// Track the current save job to allow cancellation of superseded saves
private var currentSaveJob: Job? = null

// Counter to track save requests for coalescing
@Volatile
private var pendingSaveRequests = 0
```

**New Imports:**
```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

**Completely Rewritten: `saveCurrentSession(aName: String)`**

#### Save Coalescing:
- Increments `pendingSaveRequests` counter on each call
- Cancels previous `currentSaveJob` if still running
- Checks counter inside mutex - if more saves pending, skips this one
- Prevents redundant saves when multiple rapid calls occur

#### Serialization with Mutex:
- Uses `saveMutex.withLock { }` to ensure only one save runs at a time
- Subsequent saves wait for current save to complete
- Prevents interleaved file operations

#### Verified Save Sequence:

**Step 1: Write Temporary File**
```kotlin
fulguris.utils.FileUtils.writeBundleToStorage(application, outState, temp)
// Verify temp file was created
if (!fulguris.utils.FileUtils.fileExists(application, temp)) {
    Timber.e("Failed to create temporary session file")
    return@withLock
}
```

**Step 2: Backup Current Session (if exists)**
```kotlin
if (fulguris.utils.FileUtils.fileExists(application, session)) {
    // Delete old backup if exists
    if (fulguris.utils.FileUtils.fileExists(application, backup)) {
        deleteBundleInStorageVerified(application, backup)
    }
    
    // Rename current session to backup
    if (!renameBundleInStorage(application, session, backup)) {
        Timber.e("Failed to rename session to backup")
        // Critical failure - clean up temp and abort
        deleteBundleInStorageVerified(application, temp)
        return@withLock
    }
    
    // Verify backup was created
    if (!fileExists(application, backup)) {
        Timber.e("Backup file not found after rename")
        // Try to restore from temp
        renameBundleInStorage(application, temp, session)
        return@withLock
    }
}
```

**Step 3: Commit New Session**
```kotlin
// Rename temp to session
if (!renameBundleInStorage(application, temp, session)) {
    Timber.e("Failed to rename temp to session")
    
    // Critical failure - restore from backup
    if (fileExists(application, backup)) {
        Timber.w("Attempting to restore from backup")
        renameBundleInStorage(application, backup, session)
    }
    return@withLock
}

// Verify session file was created
if (!fileExists(application, session)) {
    Timber.e("Session file not found after rename")
    
    // Try to restore from backup
    if (fileExists(application, backup)) {
        renameBundleInStorage(application, backup, session)
    }
    return@withLock
}
```

**Step 4: Delete Backup (only after success)**
```kotlin
// Only now delete backup - new session is fully committed
if (fileExists(application, backup)) {
    if (!deleteBundleInStorageVerified(application, backup)) {
        Timber.w("Failed to delete backup after successful save")
        // Not critical - backup can remain
    }
}
```

## Key Improvements

### 1. Serialization
- **Mutex Lock**: Only one save operation can execute at a time
- **No Interleaving**: File operations from different saves cannot overlap
- **Ordered Execution**: Saves execute in the order they acquire the mutex

### 2. Save Coalescing
- **Cancellation**: Previous save job is cancelled when new save is requested
- **Counter Check**: If more saves are pending, current save is skipped
- **Efficiency**: Avoids redundant saves during rapid successive calls

### 3. Verified Operations
- **Every Step Checked**: Each file operation is verified before proceeding
- **Early Abort**: If any critical step fails, save is aborted
- **Logging**: All failures are logged for debugging

### 4. Backup Management
- **Keep Until Committed**: Previous backup is kept until new session is fully written
- **Restore on Failure**: If commit fails, backup is restored
- **Delete Last**: Backup is only deleted after verifying new session exists

### 5. Error Recovery
- **Graceful Degradation**: Failures don't crash the app
- **Automatic Restore**: Failed saves attempt to restore from backup
- **Detailed Logging**: All failures are logged with context

## Race Condition Prevention

### Scenario 1: Rapid Successive Saves
**Before:**
```
Time 0: Save A starts → Write temp_A
Time 1: Save B starts → Write temp_B
Time 2: Save A → Rename session to backup_A
Time 3: Save B → Rename session to backup_B (overwrites backup_A!)
Time 4: Save A → Rename temp_A to session
Time 5: Save B → Rename temp_B to session (overwrites A's session!)
Result: Session from A is lost, only B's session remains
```

**After:**
```
Time 0: Save A starts → Acquires mutex
Time 1: Save B starts → Cancels A, increments counter, waits for mutex
Time 2: Save A → Checks counter, sees B pending, skips save, releases mutex
Time 3: Save B → Acquires mutex, decrements counter
Time 4: Save B → Completes full save sequence
Time 5: Save B → Releases mutex
Result: Only B's session is saved (A was coalesced), no data loss
```

### Scenario 2: Overlapping Saves (without coalescing)
**Before:**
```
Job A: Write temp → Rename session to backup → [interrupted]
Job B: Write temp → Rename session to backup (fails - no session file!)
Result: Both saves fail, session lost
```

**After:**
```
Job A: Acquires mutex → Write temp → Rename session to backup → Rename temp to session → Delete backup → Release mutex
Job B: Waits for mutex → Acquires mutex → Write temp → Rename session to backup → Rename temp to session → Delete backup → Release mutex
Result: Both saves complete successfully in order
```

### Scenario 3: Save Failure with Recovery
**Before:**
```
Write temp → Delete backup → Rename session to backup → [Rename temp to session FAILS]
Result: Session lost, backup deleted, no recovery possible
```

**After:**
```
Write temp → Verify temp exists
Rename session to backup → Verify backup exists
Rename temp to session → [FAILS]
→ Detect failure → Restore from backup
Result: Original session preserved, no data loss
```

## Performance Considerations

### Save Coalescing Benefits:
- **Reduced I/O**: Skips redundant saves when multiple calls occur rapidly
- **Lower CPU**: Avoids unnecessary bundle serialization
- **Faster Response**: Cancels superseded saves immediately

### Mutex Overhead:
- **Minimal**: Mutex acquisition is fast when uncontended
- **Necessary**: Prevents data corruption worth the small overhead
- **Bounded**: Save operations are already I/O bound, mutex adds negligible time

### Verification Overhead:
- **Small**: `fileExists()` is a fast filesystem check
- **Essential**: Prevents silent failures and data loss
- **Worthwhile**: Reliability improvement far outweighs minimal performance cost

## Testing Recommendations

1. **Rapid Successive Saves**:
   - Trigger multiple saves in quick succession (< 100ms apart)
   - Verify only the last save is executed
   - Check logs for coalescing messages
   - Verify session file is valid

2. **Concurrent Session Switches**:
   - Switch sessions rapidly
   - Verify each session is saved correctly
   - Check that saves don't interleave
   - Verify no session data is lost

3. **Save Failure Scenarios**:
   - Simulate disk full (difficult on Android)
   - Simulate permission errors
   - Verify backup is restored on failure
   - Check error logging

4. **App Termination During Save**:
   - Force-close app during save operation
   - Restart and verify session loads correctly
   - Check for backup files (should be cleaned up)
   - Verify no corruption

5. **Long-Running Saves**:
   - Test with 100+ tabs (large session file)
   - Verify mutex doesn't cause UI blocking
   - Check that subsequent saves wait properly
   - Verify all saves complete successfully

## Migration Notes

### Backward Compatibility:
- File format unchanged - only save process improved
- Existing session files load normally
- Backup files use same naming convention
- No migration needed

### Logging Changes:
- More detailed logging for save operations
- Success/failure logged for each file operation
- Easier to diagnose save issues in production

### Error Handling:
- Saves no longer fail silently
- Backup restoration is automatic
- App continues even if save fails (graceful degradation)

## Technical Details

### Mutex vs Synchronized:
- Kotlin coroutine `Mutex` used instead of Java `synchronized`
- Allows suspension instead of blocking threads
- Better integration with coroutine-based save operations
- Non-blocking for other coroutines

### Job Cancellation:
- Previous save job is cancelled when new save is requested
- Cancellation is cooperative - job must check cancellation
- File operations are not cancellable (atomic)
- Cancellation prevents redundant work, not mid-operation interruption

### Volatile Counter:
- `pendingSaveRequests` is `@Volatile` for thread-safe reads
- Incremented/decremented atomically
- Used for coalescing decision inside mutex
- Simple counter sufficient (no need for AtomicInteger with mutex)

### Backup Strategy:
- Temp file written first (atomic operation)
- Current session renamed to backup (preserves old data)
- Temp renamed to session (commits new data)
- Backup deleted only after verification (safety net)
- If any step fails, backup can restore previous state

## Future Enhancements

### Possible Improvements:
1. **Async Verification**: Use coroutines for file existence checks
2. **Retry Logic**: Retry failed operations with exponential backoff
3. **Checksum Verification**: Verify file integrity after write
4. **Compression**: Compress session bundles to reduce I/O time
5. **Incremental Saves**: Only save changed tabs (complex)

### Not Recommended:
- **Multiple Backups**: Adds complexity, current backup is sufficient
- **Distributed Saves**: Session must be atomic, can't split across files
- **Async File Operations**: Android filesystem operations are synchronous
