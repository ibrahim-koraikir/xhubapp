# Tab Index and Preview Race Condition Fix Implementation Plan
Goal: Fix tab list count discrepancies, wrong tab switching, and incorrect previews by shifting to ID-based tab index lookups and clearing references of destroyed tabs.
Architecture: Resolve current tab indexes in click handlers dynamically via the tab manager's live tab list by looking up the unique tab ID. Clear `currentTabFromPresenter` in `TabsManager.removeTab` when the active tab is destroyed to prevent capturing preview of a deleted tab.
Tech Stack: Kotlin, Android SDK, Android RecyclerView.
---

## Proposed Changes

### Component: Tab Switcher Click Handler

#### [MODIFY] [TabViewHolder.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt)
- Update `onClick` implementation to fetch the correct live position from the tab manager using `tab?.id` before triggering click actions.

```kotlin
    override fun onClick(v: View) {
        val tabId = tab?.id ?: return
        val currentPosition = webBrowser.getTabModel().allTabs.indexOfFirst { it.id == tabId }
        if (currentPosition < 0) {
            return
        }
        if (v === exitButton) {
            webBrowser.tabCloseClicked(currentPosition)
        } else if (v === iCardView) {
            webBrowser.tabClicked(currentPosition)
        }
    }
```

### Component: Tab Manager Destruction Lifecycle

#### [MODIFY] [TabsManager.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/browser/TabsManager.kt)
- Clear `currentTabFromPresenter` inside `removeTab(position)` when the tab being deleted matches it.

```kotlin
    private fun removeTab(position: Int) {
        if (position >= tabList.size) {
            return
        }

        val tab = tabList.removeAt(position)
        tabMap.remove(tab.id)
        iRecentTabs.remove(tab)
        if (currentTab == tab) {
            currentTab = null
        }
        if (currentTabFromPresenter == tab) {
            currentTabFromPresenter = null
        }
        tab.destroy()
    }
```

---

## Detailed Tasks

### Task 1: Update click handling in TabViewHolder
- Modify `onClick` in `app/src/main/java/fulguris/browser/tabs/TabViewHolder.kt` to resolve positions dynamically by tab ID.

### Task 2: Update tab cleanup in TabsManager
- Modify `removeTab` in `app/src/main/java/fulguris/browser/TabsManager.kt` to clear the `currentTabFromPresenter` reference.

### Task 3: Verification & Test
- Build the app using `.\gradlew.bat assembleSlionsFullDownloadDebug`
- Run the unit tests using `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
