# Animation Overhaul Implementation Plan
Goal: Replace jarring sliding/scaling animations with premium subtle crossfades for new tabs and external intents.
Architecture: Use standard Android alpha animations and replace `overridePendingTransition` calls in activity lifecycle methods.
Tech Stack: Android Animation Framework, Kotlin

---

### Task 1: Write failing test for new animations
**File:** `app/src/test/java/fulguris/animation/AnimationResourceTest.kt`
**Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.animation.AnimationResourceTest"`
**Expected Output:** Compilation error (Unresolved reference: premium_fade_in)
**Code:**
```kotlin
package fulguris.animation

import org.junit.Assert.assertNotEquals
import org.junit.Test
import fulguris.R

class AnimationResourceTest {
    @Test
    fun verifyPremiumFadesExist() {
        assertNotEquals(0, R.anim.premium_fade_in)
        assertNotEquals(0, R.anim.premium_fade_out)
    }
}
```

### Task 2: Create animation resources
**File:** `app/src/main/res/anim/premium_fade_in.xml`
**Code:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:interpolator="@android:anim/accelerate_decelerate_interpolator"
    android:fromAlpha="0.0"
    android:toAlpha="1.0"
    android:duration="200" />
```

**File:** `app/src/main/res/anim/premium_fade_out.xml`
**Code:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<alpha xmlns:android="http://schemas.android.com/apk/res/android"
    android:interpolator="@android:anim/accelerate_decelerate_interpolator"
    android:fromAlpha="1.0"
    android:toAlpha="0.0"
    android:duration="200" />
```
**Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.animation.AnimationResourceTest"`
**Expected Output:** Test passes ✅

### Task 3: Replace transitions in WebBrowserActivity
**File:** `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`
**Code changes (using replace_file_content):**
Replace: `overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)` (Lines 2741, 5041)
With: `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`
Replace: `overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_down_out)` (Line 3796)
With: `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`

### Task 4: Replace transitions in ReadingActivity and MainActivity
**File:** `app/src/main/java/fulguris/activity/ReadingActivity.kt`
Replace: `overridePendingTransition(R.anim.slide_in_from_right, R.anim.fade_out_scale)` (Line 110)
With: `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`
Replace: `overridePendingTransition(R.anim.fade_in_scale, R.anim.slide_out_to_right)` (Line 307)
With: `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`

**File:** `app/src/main/java/fulguris/activity/MainActivity.kt`
Replace: `overridePendingTransition(R.anim.slide_up_in, R.anim.fade_out_scale)` (Line 43)
With: `overridePendingTransition(R.anim.premium_fade_in, R.anim.premium_fade_out)`

### Task 5: Adjust tab click delay
**File:** `app/src/main/java/fulguris/activity/WebBrowserActivity.kt`
Replace: `mainHandler.postDelayed({ closePanels() }, 350)` (Line 3664)
With: `mainHandler.postDelayed({ closePanels() }, 200)`
Replace: `}, 300)` (Line 3684)
With: `}, 150)`

### Task 6: Final Verification
**Command:** `.\gradlew.bat assembleSlionsFullDownloadDebug`
**Expected Output:** BUILD SUCCESSFUL ✅
