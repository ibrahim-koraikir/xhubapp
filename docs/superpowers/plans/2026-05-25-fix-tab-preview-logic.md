# Fix Tab Preview Logic Implementation Plan
Goal: Ensure tab image previews are reliably captured before a tab goes into the background or the app is paused.
Architecture: Add `capturePreviewSync()` calls to `TabsManager.onTabChanged` (for the outgoing tab) and `WebBrowserActivity.onPause` (for the current tab).
Tech Stack: Kotlin, Android WebView.
---

### Task 1: Capture Preview on Tab Switch
**File:** `app/src/main/java/fulguris/browser/TabsManager.kt:934`

- [ ] **Step 1: Write the failing test**
  *(Skipping TDD for this as it's a UI lifecycle behavior that's difficult to unit test without Robolectric, we'll verify via build and manual testing)*
- [ ] **Step 2: Write minimal implementation**
  In `TabsManager.onTabChanged`, add `it.capturePreviewSync()` before `it.isForeground = false`:
  ```kotlin
          currentTabFromPresenter?.let {
              it.capturePreviewSync() // Capture before hiding
              // TODO: Restore this when Google fixes the bug where the WebView is
              // blank after calling onPause followed by onResume.
              // it.onPause();
              it.isForeground = false
          }
  ```
- [ ] **Step 3: Build and verify**
  Run `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

### Task 2: Capture Preview on App Pause
**File:** `app/src/main/java/fulguris/activity/WebBrowserActivity.kt:3796`

- [ ] **Step 1: Write the failing test**
  *(Skipping TDD for lifecycle)*
- [ ] **Step 2: Write minimal implementation**
  In `WebBrowserActivity.onPause`, add `tabsManager.currentTab?.capturePreviewSync()` at the very beginning of the method:
  ```kotlin
      override fun onPause() {
          super.onPause()
          tabsManager.currentTab?.capturePreviewSync() // Capture before app goes to background
          // ... rest of onPause
  ```
- [ ] **Step 3: Build and verify**
  Run `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

### Task 3: Commit
- [ ] Commit changes: `git commit -m "fix(tabs): capture previews reliably on tab switch and app pause"`
