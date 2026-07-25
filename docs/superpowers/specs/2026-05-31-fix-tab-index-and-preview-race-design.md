# Spec: Fix Tab Index and Preview Race Conditions Design

## Problem Description
The user reported two issues with the tab switcher UI:
1. "sometimes it shows 5 tabs open when it's only 4" (tab count/indexing race condition).
2. "the thumbnail images sometimes are bad, not showing, or show the wrong tab preview".

## Root Cause Investigation

### Issue 1: Tab Index Race Condition
In [TabViewHolder.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt), click listeners on the tab cards (`iCardView`) and close buttons (`exitButton`) use `adapterPosition` directly to determine which tab index was closed or clicked:
```kotlin
    override fun onClick(v: View) {
        if (v === exitButton) {
            webBrowser.tabCloseClicked(adapterPosition)
        } else if (v === iCardView) {
            webBrowser.tabClicked(adapterPosition)
        }
    }
```
During RecyclerView removal animations (which take ~200–300ms) or rapid successive clicks, `adapterPosition` can be outdated or return `RecyclerView.NO_POSITION` (-1). Because `adapterPosition` is index-based, a rapid click on a shifting card triggers a close request on the wrong index in the live tab manager list. This deletes a different tab than the one clicked, leading to mismatched lists, ghost views, incorrect counts, or the wrong tab being closed.

### Issue 2: Preview Captured on Destroyed Tab
In [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt), when a tab is removed, the manager removes it from the list and destroys it:
```kotlin
    private fun removeTab(position: Int) {
        ...
        val tab = tabList.removeAt(position)
        tabMap.remove(tab.id)
        iRecentTabs.remove(tab)
        if (currentTab == tab) {
            currentTab = null
        }
        tab.destroy()
    }
```
However, the manager also keeps a reference to the previously active tab in `currentTabFromPresenter` to capture its preview when switching away. This reference is **never** cleared when a tab is removed. As a result, when switching tabs after a deletion, `currentTabFromPresenter` points to the destroyed tab, attempting to capture its preview. This results in empty/black previews, memory leaks, or recycling glitches where stale graphics buffers show wrong screenshots on other tabs.

---

## Proposed Solutions

### Solution 1: Look up Tab Index by Unique ID at the Moment of Click
Instead of relying on RecyclerView's volatile `adapterPosition` directly, we will use the ViewHolder's bound `tab?.id` (which is unique and stable). When a click occurs, we dynamically query the live tab list from the tab manager to find the correct, up-to-date index:
```kotlin
    override fun onClick(v: View) {
        val tabId = tab?.id ?: return
        val currentPosition = webBrowser.getTabModel().allTabs.indexOfFirst { it.id == tabId }
        if (currentPosition < 0) {
            // Tab is already closed/deleted, ignore click
            return
        }
        if (v === exitButton) {
            webBrowser.tabCloseClicked(currentPosition)
        } else if (v === iCardView) {
            webBrowser.tabClicked(currentPosition)
        }
    }
```

### Solution 2: Clear Outgoing Tab Reference on Removal
In `removeTab(position)` in [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt), we will nullify `currentTabFromPresenter` if the tab being destroyed is the same tab, preventing the manager from calling `capturePreviewSync` on a destroyed tab:
```kotlin
        if (currentTabFromPresenter == tab) {
            currentTabFromPresenter = null
        }
```

---

## Verification Plan

### Automated Tests
- Build and compile the debug APK: `.\gradlew.bat assembleSlionsFullDownloadDebug`
- Run the full suite of JVM unit tests to ensure no regressions: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`

### Manual Verification
- Verify the tab list count and thumbnails update correctly without duplicates or empty states under rapid closings.
