# Phase 3: Performance Audit Implementation Plan
Goal: Eliminate main-thread blocking operations caused by synchronous database/SharedPreferences I/O.
Architecture: Offload heavy file reads and JSON parsing to background threads using RxJava.
Tech Stack: Kotlin, RxJava (Single, Schedulers).

## Tasks

### 1. Offload Shortcut Loading in WebBrowserActivity
The home screen initialization currently parses JSON directly on the UI thread in `setupHomeScreen()`. We will wrap this in a reactive flow.

**File:** `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`
**Action:** Wrap the `ShortcutRepository.loadGroups(this)` call inside an RxJava `Single`.
```kotlin
        io.reactivex.Single.fromCallable {
            fulguris.shortcuts.ShortcutRepository.loadGroups(this@WebBrowserActivity)
        }
        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
        .subscribe { groups ->
            // Original layout inflation code goes here
            // ...
        }
```

### 2. Offload Shortcut Loading in ManageShortcutsActivity
The shortcut manager activity also loads the JSON on the main thread in `onCreate()`.

**File:** `app/src/main/java/fulguris/activity/ManageShortcutsActivity.kt`
**Action:** Wrap the data loading in RxJava and notify the adapter when it completes.
```kotlin
        io.reactivex.Single.fromCallable { ShortcutRepository.loadGroups(this) }
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe { loadedGroups ->
                groups.clear()
                groups.addAll(loadedGroups)
                adapter.notifyDataSetChanged()
            }
```

## Verification Plan
1. Build the app using `.\gradlew.bat assembleSlionsFullDownloadDebug`.
2. Ensure there are no StrictMode warnings for SharedPreferences reads on the main thread.
