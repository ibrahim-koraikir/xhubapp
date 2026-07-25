# OLED Minimalist Home Screen Implementation Plan

**Goal:** Redesign the browser home screen to have a clean, high-contrast, pure-black OLED minimalist look by removing the starfield/gradients and setting flat dark backgrounds.
**Architecture:** Create minimalist solid-color/border-only drawables, update the layout file `layout_home_screen.xml` to use them, remove background images/overlays, and update the dynamic view styling in `WebBrowserActivity.kt`.
**Tech Stack:** Android XML layouts, custom drawables, Kotlin, Robolectric for testing.

---

### Task 1: Create the failing unit test first

**Files:**
- [NEW] [HomeScreenLayoutTest.kt](file:///C:/Users/w/Desktop/Fulguris-main/app/src/test/java/fulguris/view/HomeScreenLayoutTest.kt)

- [ ] **Step 1: Write the failing test**
  Create the unit test file `app/src/test/java/fulguris/view/HomeScreenLayoutTest.kt`:
  ```kotlin
  package fulguris.view

  import android.view.LayoutInflater
  import android.widget.TextView
  import androidx.coordinatorlayout.widget.CoordinatorLayout
  import fulguris.R
  import fulguris.SDK_VERSION
  import fulguris.TestApplication
  import org.assertj.core.api.Assertions.assertThat
  import org.junit.Test
  import org.junit.runner.RunWith
  import org.robolectric.RobolectricTestRunner
  import org.robolectric.RuntimeEnvironment
  import org.robolectric.annotation.Config

  @RunWith(RobolectricTestRunner::class)
  @Config(application = TestApplication::class, sdk = [SDK_VERSION])
  class HomeScreenLayoutTest {

      @Test
      fun `home screen layout inflates successfully and contains correct views`() {
          val context = RuntimeEnvironment.getApplication()
          val inflater = LayoutInflater.from(context)
          val view = inflater.inflate(R.layout.layout_home_screen, null) as CoordinatorLayout

          assertThat(view).isNotNull()
          
          // Background and overlay views should be removed (findViewById should return null)
          assertThat(view.findViewById<android.view.View>(R.id.homeScreenBackground)).isNull()
          assertThat(view.findViewById<android.view.View>(R.id.homeScreenBackgroundOverlay)).isNull()

          // Critical search card and shortcut container views must exist
          assertThat(view.findViewById<android.view.View>(R.id.homeSearchCard)).isNotNull()
          assertThat(view.findViewById<android.view.View>(R.id.shortcutsDynamicContainer)).isNotNull()
          assertThat(view.findViewById<TextView>(R.id.homeTitle)).isNotNull()
      }
  }
  ```

- [ ] **Step 2: Run test to verify it fails**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.view.HomeScreenLayoutTest"`
  Expected: FAIL (because `homeScreenBackground` and `homeScreenBackgroundOverlay` are still present in the layout)

---

### Task 2: Create/Modify Drawable Resources

**Files:**
- [NEW] [bg_home_logo_minimalist.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_logo_minimalist.xml)
- [NEW] [bg_home_profile_ring_minimalist.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_profile_ring_minimalist.xml)
- [MODIFY] [bg_home_search_card.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_search_card.xml)
- [MODIFY] [bg_home_shortcut_card.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_shortcut_card.xml)

- [ ] **Step 1: Create `bg_home_logo_minimalist.xml`**
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Minimalist brand logo badge background: flat dark grey with thin grey outline -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#1A1A1A" />
      <stroke android:width="1dp" android:color="#333333" />
      <corners android:radius="10dp" />
  </shape>
  ```

- [ ] **Step 2: Create `bg_home_profile_ring_minimalist.xml`**
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Minimalist profile ring: simple dark grey circle outline -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="oval">
      <stroke android:width="1dp" android:color="#333333" />
      <solid android:color="@android:color/transparent" />
  </shape>
  ```

- [ ] **Step 3: Modify `bg_home_search_card.xml`**
  Change:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Premium frosted-glass search card for Home screen -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#18FFFFFF" />
      <stroke android:width="1dp" android:color="#22FFFFFF" />
      <corners android:radius="28dp" />
  </shape>
  ```
  To:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Minimalist home search card: solid dark grey with thin outline -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#0D0D0D" />
      <stroke android:width="1dp" android:color="#333333" />
      <corners android:radius="28dp" />
  </shape>
  ```

- [ ] **Step 4: Modify `bg_home_shortcut_card.xml`**
  Change:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Premium glassmorphic card for shortcut tiles section -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#0FFFFFFF" />
      <stroke android:width="1dp" android:color="#18FFFFFF" />
      <corners android:radius="20dp" />
  </shape>
  ```
  To:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Minimalist shortcuts container: transparent background, flat style -->
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="@android:color/transparent" />
  </shape>
  ```

- [ ] **Step 5: Commit**
  Run: `git add app/src/main/res/drawable/; git commit -m "style: create and restyle minimalist drawables"`

---

### Task 3: Restyle Home Screen Layout XML

**Files:**
- [MODIFY] [layout_home_screen.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)

- [ ] **Step 1: Apply OLED background, remove image background, gradient, and overlay**
  In [layout_home_screen.xml](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml), modify the layout:
  1. Add `android:background="#000000"` to the root `CoordinatorLayout` (around line 2).
  2. Delete the `ImageView` (`homeScreenBackground`) (lines 11-19).
  3. Delete the `View` (`homeScreenBackgroundOverlay`) (lines 21-27).
  4. Delete the top gradient header `View` (lines 29-34).
  5. Change `app:contentScrim="@color/home_background"` in `CollapsingToolbarLayout` to `app:contentScrim="#000000"` (around line 48).
  6. Change the logo badge `FrameLayout` background from `@drawable/bg_home_logo_gradient` to `@drawable/bg_home_logo_minimalist` (around line 72) and remove `android:elevation="4dp"`.
  7. Change the settings and profile ring `FrameLayout` backgrounds from `@drawable/bg_home_profile_ring` to `@drawable/bg_home_profile_ring_minimalist` (around lines 131 and 165).
  8. Remove padding from the website shortcuts wrapper container `LinearLayout` (lines 250-255), so it sits flat in the layout.

- [ ] **Step 2: Run test to verify it passes**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.view.HomeScreenLayoutTest"`
  Expected: PASS

- [ ] **Step 3: Commit**
  Run: `git add app/src/main/res/layout/layout_home_screen.xml; git commit -m "style: update layout_home_screen.xml for OLED minimalist style"`

---

### Task 4: Simplify Dynamic Styling in WebBrowserActivity.kt

**Files:**
- [MODIFY] [WebBrowserActivity.kt](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)

- [ ] **Step 1: Remove home title gradient shader**
  In [WebBrowserActivity.kt](file:///C:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt), locate and remove the `LinearGradient` logic applied to `homeTitle` (around lines 1747-1762):
  ```kotlin
          // ── Title gradient shader (gold → magenta) ────────────────────────────
          val homeTitle = iBinding.homeScreenOverlay.findViewById<TextView>(R.id.homeTitle)
          homeTitle?.post {
              val w = homeTitle.paint.measureText(homeTitle.text.toString())
              homeTitle.paint.shader = android.graphics.LinearGradient(
                  0f, 0f, w, homeTitle.textSize,
                  intArrayOf(
                      android.graphics.Color.parseColor("#FFD600"),
                      android.graphics.Color.parseColor("#FF007A"),
                      android.graphics.Color.parseColor("#9C00FF")
                  ),
                  floatArrayOf(0f, 0.55f, 1f),
                  android.graphics.Shader.TileMode.CLAMP
              )
              homeTitle.invalidate()
          }
  ```

- [ ] **Step 2: Restyle shortcut initial/icons to be flat circular solid `#161616` badges**
  1. Remove `tileGradients` list definition (around lines 1785-1792) and references to it (like `val grad = ...` on line 1841).
  2. Remove custom `gradBg` initialization and addition to `frame` (lines 1882-1899).
  3. In `MaterialCardView` setup (lines 1869-1880):
     - Change `radius` to `36 * density` (so it's perfectly circular since size is `dp72`).
     - Set `cardElevation = 0f` (flat design).
     - Set stroke color to `#FF333333.toInt()`.
     - Set background color to `#FF161616.toInt()`.
  4. In `initial` `TextView` setup (lines 1902-1914):
     - Set text color to `#FFCCCCCC.toInt()`.
     - Remove `setShadowLayer`.
     - Set `setTypeface(null, android.graphics.Typeface.NORMAL)`.
  5. In `faviconIv` setup (line 1920):
     - Set background color to `android.graphics.Color.TRANSPARENT`.
  6. Clean up line 1938 (`gradBg.isVisible = false`).

- [ ] **Step 3: Build and verify**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
  Run: `git add app/src/main/java/fulguris/activity/WebBrowserActivity.kt; git commit -m "style: remove vibrant colors and gradients from shortcut rendering"`

---

### Task 5: Final Verification

- [ ] **Step 1: Run all unit tests**
  Run: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
  Expected: BUILD SUCCESSFUL & ALL TESTS PASSED
