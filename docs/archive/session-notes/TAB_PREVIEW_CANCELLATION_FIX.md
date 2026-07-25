# Tab Preview Cancellation Fix

## Issue
Delayed preview captures were never cancelled, allowing stale callbacks to overwrite newer thumbnails and update UI for removed tabs. This could cause:
- Stale thumbnails from previous pages overwriting current page thumbnails
- UI updates for tabs that no longer exist
- Race conditions where older captures complete after newer ones
- Pending captures running after tab destruction, wasting resources
- Background captures committing after tab transitions to foreground

## Root Cause
The preview capture system used a 500ms delay (`postDelayed`) to ensure pages were fully rendered before capturing. However:
1. No mechanism existed to cancel pending delayed tasks when navigation started
2. No sequence tracking to invalidate in-flight async captures (PixelCopy callbacks)
3. No validation in `doTabUpdate` to prevent updates for invalid/removed tabs
4. No cancellation when tabs were destroyed or transitioned to foreground

## Solution
Implemented a comprehensive cancellable capture scheduling mechanism with five layers of protection:

### 1. Sequence Counter Guard (WebPageTab.kt)
Added a volatile sequence counter that increments on each navigation:
- `@Volatile private var captureSequence = 0` - Thread-safe sequence tracking
- Each capture operation receives the current sequence number
- Before committing results, callbacks check if their sequence matches the current sequence
- Mismatched sequences are discarded as stale

### 2. Delayed Task Cancellation (WebPageTab.kt)
Added ability to cancel the 500ms delayed task before it executes:
- `private var pendingCaptureRunnable: Runnable?` - Reference to pending task
- `cancelPendingCapture()` - Cancels delayed task and increments sequence
- Called in multiple lifecycle points to prevent stale captures

### 3. Tab Index Validation (WebBrowserActivity.kt)
Added guard in `doTabUpdate` to prevent updates for invalid tabs:
- Checks if tab index is >= 0 before calling `notifyTabViewChanged`
- Logs warning when stale callbacks attempt to update removed tabs
- Prevents unnecessary RecyclerView updates and potential crashes

### 4. Destruction Cancellation (WebPageTab.kt)
Added cancellation in `destroyWebView()`:
- Calls `cancelPendingCapture()` before destroying WebView
- Prevents pending captures from running after tab is destroyed
- Avoids wasted CPU/memory on destroyed tabs

### 5. Foreground Transition Cancellation (WebPageTab.kt)
Added cancellation in `isForeground` setter:
- Calls `cancelPendingCapture()` when tab transitions to foreground
- Prevents queued background captures from committing after state change
- Ensures preview is cleared for active tabs without stale background captures interfering

## Implementation Details

### WebPageTab.kt Changes

**Added Fields:**
```kotlin
@Volatile
private var captureSequence = 0

private var pendingCaptureRunnable: Runnable? = null
```

**Modified `capturePreviewAsync()`:**
- Cancels any pending delayed task
- Increments sequence counter
- Stores runnable reference for future cancellation
- Passes sequence to capture methods

**Added `cancelPendingCapture()`:**
- Removes pending delayed callback from WebView's message queue
- Increments sequence to invalidate in-flight captures
- Called from `WebPageClient.onPageStarted()`

**Modified `captureWithPixelCopy()`:**
- Accepts `expectedSequence` parameter
- Checks sequence before committing bitmap to cache
- Recycles bitmap and returns early if sequence mismatch

**Modified `captureWithDrawing()`:**
- Accepts `expectedSequence` parameter
- Checks sequence before committing bitmap to cache
- Returns early if sequence mismatch

**Modified `isForeground` setter:**
- Added call to `cancelPendingCapture()` when transitioning to foreground
- Prevents queued background captures from committing after state change
- Ensures preview is cleared for active tabs without stale background captures interfering

**Modified `destroyWebView()`:**
- Added call to `cancelPendingCapture()` before destroying WebView
- Prevents pending captures from running after tab destruction
- Avoids wasted CPU/memory on destroyed tabs

### WebPageClient.kt Changes

**Modified `onPageStarted()`:**
- Added call to `webPageTab.cancelPendingCapture()` before `invalidatePreview()`
- Ensures pending captures from previous page are cancelled
- Prevents race conditions during navigation

### WebBrowserActivity.kt Changes

**Modified `doTabUpdate()`:**
- Added index validation: `if (index >= 0)`
- Added warning log for invalid tab indices
- Prevents `notifyTabViewChanged` calls for removed tabs

## How It Works

### Scenario 1: User navigates before delayed capture executes
1. Page A starts loading → `capturePreviewAsync()` called (seq=1, delayed 500ms)
2. User navigates to Page B → `onPageStarted()` called
3. `cancelPendingCapture()` removes delayed task and increments seq to 2
4. Page A's delayed task never executes
5. Page B loads → new capture scheduled (seq=2)

### Scenario 2: User navigates after delayed capture executes but before callback
1. Page A loads → capture starts (seq=1)
2. PixelCopy request sent to system
3. User navigates to Page B → seq incremented to 2
4. PixelCopy callback returns for Page A
5. Callback checks: `expectedSequence (1) != captureSequence (2)`
6. Bitmap recycled, cache not updated
7. Page B's capture proceeds normally (seq=2)

### Scenario 3: Tab is closed while capture is in progress
1. Tab capture in progress (seq=5)
2. Tab is closed and removed from TabsManager
3. Capture completes and calls `webBrowser.onTabChanged(this)`
4. `doTabUpdate()` calls `tabsManager.indexOfTab(aTab)` → returns -1
5. Guard prevents `notifyTabViewChanged(-1)` call
6. Warning logged, no UI update attempted

### Scenario 4: Tab is destroyed while capture is pending
1. Tab has pending capture scheduled (seq=3, delayed 500ms)
2. Tab is destroyed → `destroyWebView()` called
3. `cancelPendingCapture()` removes delayed task and increments seq to 4
4. Pending capture never executes
5. WebView is destroyed cleanly without wasted work

### Scenario 5: Tab transitions to foreground with pending background capture
1. Tab in background has pending capture (seq=7, delayed 500ms)
2. User switches to this tab → `isForeground = true`
3. `cancelPendingCapture()` removes delayed task and increments seq to 8
4. Background capture never executes
5. Preview is cleared for active tab
6. No stale background capture can commit after foreground transition

## Benefits

1. **No Stale Thumbnails**: Captures from previous pages cannot overwrite current page thumbnails
2. **No Wasted Work**: Delayed tasks are cancelled before execution, saving CPU/memory
3. **Thread-Safe**: Volatile sequence counter ensures visibility across threads
4. **Crash Prevention**: Tab index validation prevents crashes from stale callbacks
5. **Memory Efficient**: Stale bitmaps are recycled immediately, not stored in cache
6. **Clean Destruction**: Pending captures are cancelled when tabs are destroyed
7. **State Consistency**: Background captures cannot interfere with foreground tab state

## Testing
Build completed successfully with:
```bash
./gradlew assembleSlionsFullFdroidDebug
```

## Files Modified
- `app/src/main/java/fulguris/view/WebPageTab.kt` - Added sequence tracking and cancellation
- `app/src/main/java/fulguris/view/WebPageClient.kt` - Added cancellation in onPageStarted
- `app/src/main/java/fulguris/activity/WebBrowserActivity.kt` - Added tab index validation

## Related Context
- Works in conjunction with bounded thumbnail cache (Task 4)
- Works with scroll position capture fix (previous task)
- Thumbnails stored in `TabThumbnailCache` with LRU eviction (max 20 thumbnails)
