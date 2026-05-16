# Tab Preview Foreground-Only Implementation Plan

**Goal:** Remove unreliable background tab preview captures to eliminate wrong/glitched thumbnails and OOMs.
**Architecture:** Delete `scheduleDeferredPreviewCapture` entirely. Previews will only be captured synchronously via `capturePreviewSync` when the tab switcher is opened (foreground). Background tabs will fall back to the placeholder icon.
**Tech Stack:** Kotlin, Android WebView.

---

### Task 1: Remove Deferred Capture from WebPageTab

**Files:**
- Modify: `app/src/main/java/fulguris/view/WebPageTab.kt:1405-1418`

- [ ] **Step 1: Write the failing test**
  *(No test for this specific step as we are removing a private/internal method that shouldn't be publicly tested for existence, we'll verify via build)*
- [ ] **Step 2: Write minimal implementation**
  Remove `scheduleDeferredPreviewCapture` method and its references from `WebPageTab.kt`.
- [ ] **Step 3: Build and verify**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD FAILED (due to WebPageClient still calling it)

### Task 2: Remove Deferred Capture from WebPageClient

**Files:**
- Modify: `app/src/main/java/fulguris/view/WebPageClient.kt:481-484`

- [ ] **Step 1: Write the failing test**
  *(Skipping TDD for straight deletion of deprecated logic)*
- [ ] **Step 2: Write minimal implementation**
  Remove the `webPageTab.scheduleDeferredPreviewCapture()` call from `onPageFinished`.
- [ ] **Step 3: Build and verify**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit**
  `git commit -m "fix(tabs): disable background preview capture to fix wrong images"`
