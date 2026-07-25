# Premium Starfield Home Screen Restoration Implementation Plan

Goal: Revert the home screen from the minimalist OLED design back to the premium space starfield design.
Architecture: Restore full-screen background image and transparent overlay view in the home screen layout, apply warm gradient outline to profile/settings containers, use Tinder flame icon in the collapsed toolbar, and modify the shortcuts section to have a glassmorphic background and 64dp squircle white-favicon tiles.
Tech Stack: Kotlin, XML Layouts, JUnit/Robolectric, Android SDK.

---

### Task 1: Revert/Add background drawables and styles

**Files:**
- [MODIFY] [colors_home.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/values/colors_home.xml)
- [MODIFY] [dimens_home.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/values/dimens_home.xml)
- [MODIFY] [bg_home_shortcut_card.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_shortcut_card.xml)
- [MODIFY] [bg_shortcut_tile_ripple.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_shortcut_tile_ripple.xml)

- [ ] **Step 1: Add new color and update initial text color in colors_home.xml**
  Modify `app/src/main/res/values/colors_home.xml` around line 17 and 32:
  ```xml
      <!-- Standard hairline divider (#333333) -->
      <color name="home_stroke">#FF333333</color>
      <!-- Faint white border (alpha-based) -->
      <color name="home_border">#22FFFFFF</color>
      <color name="home_tile_stroke">#1AFFFFFF</color>

      <!-- ── Text ─────────────────────────────────────────────────────── -->
      <!-- Primary text — pure white -->
      <color name="home_foreground">#FFFFFFFF</color>
      <!-- Secondary / muted text — medium grey -->
      <color name="home_muted_foreground">#FF888888</color>
      <!-- Dim text — 80 % white -->
      <color name="home_dim_foreground">#CCFFFFFF</color>
      <!-- Subtle text — 47 % white -->
      <color name="home_subtle_foreground">#77FFFFFF</color>
      <!-- Icon / overlay tint — 50 % white -->
      <color name="home_icon_tint">#80FFFFFF</color>
      <!-- Initial letter inside tile — light grey -->
      <color name="home_initial_text">#FFFFFFFF</color>
  ```

- [ ] **Step 2: Update dimensions in dimens_home.xml**
  Modify `app/src/main/res/values/dimens_home.xml` around line 43:
  ```xml
      <!-- ── Shortcut tiles ───────────────────────────────────────────── -->
      <!-- Tile frame (icon circle) size — was hardcoded dp72 in code -->
      <dimen name="home_tile_frame_size">64dp</dimen>
      <!-- Tile fully-circular radius = half of frame size -->
      <dimen name="home_tile_radius">18dp</dimen>
      <dimen name="home_tile_elevation">3dp</dimen>
  ```

- [ ] **Step 3: Update shortcut card background in bg_home_shortcut_card.xml**
  Modify `app/src/main/res/drawable/bg_home_shortcut_card.xml` entirely:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <shape xmlns:android="http://schemas.android.com/apk/res/android"
      android:shape="rectangle">
      <solid android:color="#0FFFFFFF" />
      <stroke android:width="1dp" android:color="#18FFFFFF" />
      <corners android:radius="20dp" />
  </shape>
  ```

- [ ] **Step 4: Update shortcut ripple background in bg_shortcut_tile_ripple.xml**
  Modify `app/src/main/res/drawable/bg_shortcut_tile_ripple.xml` entirely to match the new 18dp squircle radius:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <!-- Rounded ripple for shortcut tiles — dark ripple on the tile surface -->
  <ripple xmlns:android="http://schemas.android.com/apk/res/android"
      android:color="@color/home_icon_tint">
      <item android:id="@android:id/mask">
          <shape android:shape="rectangle">
              <solid android:color="@android:color/white" />
              <corners android:radius="@dimen/home_tile_radius" />
          </shape>
      </item>
      <item>
          <shape android:shape="rectangle">
              <solid android:color="@color/home_tile_surface" />
              <stroke android:width="@dimen/home_tile_stroke_width" android:color="@color/home_tile_stroke" />
              <corners android:radius="@dimen/home_tile_radius" />
          </shape>
      </item>
  </ripple>
  ```

---

### Task 2: Restore layout_home_screen.xml layout structure

**Files:**
- [MODIFY] [layout_home_screen.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)

- [ ] **Step 1: Modify layout_home_screen.xml to include background, overlay and correct resources**
  Make the following contiguous replacements in `app/src/main/res/layout/layout_home_screen.xml`:

  **Replacement 1 (Root CoordinatorLayout & Backgrounds):**
  From:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <androidx.coordinatorlayout.widget.CoordinatorLayout
      xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:app="http://schemas.android.com/apk/res-auto"
      xmlns:tools="http://schemas.android.com/tools"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:background="@color/home_background"
      android:visibility="gone"
      tools:visibility="visible">
  ```
  To:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <androidx.coordinatorlayout.widget.CoordinatorLayout
      xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:app="http://schemas.android.com/apk/res-auto"
      xmlns:tools="http://schemas.android.com/tools"
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      android:visibility="gone"
      tools:visibility="visible">

      <!-- Full-screen Starfield Background Image -->
      <ImageView
          android:id="@+id/homeScreenBackground"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:scaleType="centerCrop"
          android:src="@drawable/bg_home_starfield"
          android:contentDescription="@null"
          android:importantForAccessibility="no" />

      <!-- 40% Transparent Dark Overlay for Text/Icon Contrast -->
      <View
          android:id="@+id/homeScreenBackgroundOverlay"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:background="#66000000"
          android:importantForAccessibility="no" />
  ```

  **Replacement 2 (Header Logo Background):**
  From:
  ```xml
                      <FrameLayout
                          android:layout_width="@dimen/home_logo_badge_size"
                          android:layout_height="@dimen/home_logo_badge_size"
                          android:background="@drawable/bg_home_logo_minimalist"
                          android:layout_marginEnd="14dp"
                          android:padding="1dp">
  ```
  To:
  ```xml
                      <FrameLayout
                          android:layout_width="@dimen/home_logo_badge_size"
                          android:layout_height="@dimen/home_logo_badge_size"
                          android:background="@drawable/bg_home_logo_gradient"
                          android:layout_marginEnd="14dp"
                          android:padding="1dp">
  ```

  **Replacement 3 (Settings Button Container Background):**
  From:
  ```xml
                      <!-- Settings button (left) -->
                      <FrameLayout
                          android:id="@+id/homeSettingsBtnContainer"
                          android:layout_width="@dimen/home_avatar_size"
                          android:layout_height="@dimen/home_avatar_size"
                          android:background="@drawable/bg_home_profile_ring_minimalist"
                          android:padding="1dp"
                          app:layout_constraintLeft_toLeftOf="parent"
                          app:layout_constraintTop_toTopOf="parent"
                          app:layout_constraintBottom_toBottomOf="parent">
  ```
  To:
  ```xml
                      <!-- Settings button (left) -->
                      <FrameLayout
                          android:id="@+id/homeSettingsBtnContainer"
                          android:layout_width="@dimen/home_avatar_size"
                          android:layout_height="@dimen/home_avatar_size"
                          android:background="@drawable/bg_home_profile_ring"
                          android:padding="1dp"
                          app:layout_constraintLeft_toLeftOf="parent"
                          app:layout_constraintTop_toTopOf="parent"
                          app:layout_constraintBottom_toBottomOf="parent">
  ```

  **Replacement 4 (Bookmarks Button Container & Icon):**
  From:
  ```xml
                      <!-- Bookmarks button (right) -->
                      <FrameLayout
                          android:id="@+id/homeBookmarksButton"
                          android:layout_width="@dimen/home_avatar_size"
                          android:layout_height="@dimen/home_avatar_size"
                          android:background="@drawable/bg_home_profile_ring_minimalist"
                          android:padding="1dp"
                          app:layout_constraintRight_toRightOf="parent"
                          app:layout_constraintTop_toTopOf="parent"
                          app:layout_constraintBottom_toBottomOf="parent">

                          <com.google.android.material.imageview.ShapeableImageView
                              android:id="@+id/homeBookmarksIcon"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:scaleType="fitCenter"
                              android:padding="10dp"
                              android:contentDescription="@string/home_bookmarks_desc"
                              app:shapeAppearanceOverlay="@style/ShapeAppearanceOverlay.App.CornerSize50Percent"
                              app:tint="@color/home_foreground"
                              android:src="@drawable/ic_bookmarks" />
                      </FrameLayout>
  ```
  To:
  ```xml
                      <!-- Bookmarks button (right) -->
                      <FrameLayout
                          android:id="@+id/homeBookmarksButton"
                          android:layout_width="@dimen/home_avatar_size"
                          android:layout_height="@dimen/home_avatar_size"
                          android:background="@drawable/bg_home_profile_ring"
                          android:padding="1dp"
                          app:layout_constraintRight_toRightOf="parent"
                          app:layout_constraintTop_toTopOf="parent"
                          app:layout_constraintBottom_toBottomOf="parent">

                          <com.google.android.material.imageview.ShapeableImageView
                              android:id="@+id/homeBookmarksIcon"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:scaleType="fitCenter"
                              android:padding="10dp"
                              android:contentDescription="@string/home_bookmarks_desc"
                              app:shapeAppearanceOverlay="@style/ShapeAppearanceOverlay.App.CornerSize50Percent"
                              android:src="@drawable/ic_launcher_foreground" />
                      </FrameLayout>
  ```

---

### Task 3: Implement WebBrowserActivity.kt text gradient and shortcut tile upgrades

**Files:**
- [MODIFY] [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt)

- [ ] **Step 1: Modify WebBrowserActivity.kt to add brand title text shader and dynamic tile styling**
  Apply the following modifications to `app/src/main/java/com/xhub/browser/activity/WebBrowserActivity.kt`:

  **Modification 1 (Brand title shader inside buildDynamicShortcuts):**
  Around lines 1761-1770:
  From:
  ```kotlin
          // ── Time-aware greeting (string resources) ────────────────────────────
          val homeGreeting = iBinding.homeScreenOverlay.findViewById<TextView>(R.id.homeGreeting)
  ```
  To:
  ```kotlin
          // ── Brand text gradient shader ────────────────────────────────────────
          val homeTitle = iBinding.homeScreenOverlay.findViewById<TextView>(R.id.homeTitle)
          homeTitle?.post {
              val paint = homeTitle.paint
              val width = paint.measureText(homeTitle.text.toString())
              if (width > 0f) {
                  val shader = android.graphics.LinearGradient(
                      0f, 0f, width, homeTitle.textSize,
                      intArrayOf(android.graphics.Color.parseColor("#ffd600"), android.graphics.Color.parseColor("#ff007a")),
                      null, android.graphics.Shader.TileMode.CLAMP
                  )
                  homeTitle.paint.shader = shader
                  homeTitle.invalidate()
              }
          }

          // ── Time-aware greeting (string resources) ────────────────────────────
          val homeGreeting = iBinding.homeScreenOverlay.findViewById<TextView>(R.id.homeGreeting)
  ```

  **Modification 2 (Update colorStroke assignment to use home_tile_stroke):**
  Around lines 1795-1796:
  From:
  ```kotlin
                  val colorTileSurface  = androidx.core.content.ContextCompat.getColor(this, R.color.home_tile_surface)
                  val colorStroke       = androidx.core.content.ContextCompat.getColor(this, R.color.home_stroke)
  ```
  To:
  ```kotlin
                  val colorTileSurface  = androidx.core.content.ContextCompat.getColor(this, R.color.home_tile_surface)
                  val colorStroke       = androidx.core.content.ContextCompat.getColor(this, R.color.home_tile_stroke)
  ```

  **Modification 3 (Update frame elevation):**
  Around lines 1953-1956:
  From:
  ```kotlin
                             radius = tileRadius
                             cardElevation = 0f
                             strokeWidth = strokePx
  ```
  To:
  ```kotlin
                             radius = tileRadius
                             cardElevation = resources.getDimension(R.dimen.home_tile_elevation)
                             strokeWidth = strokePx
  ```

  **Modification 4 (Update favicon loading callback to set frame background to white):**
  Around lines 1993-2003:
  From:
  ```kotlin
                         // Async favicon fetch
                         faviconModel.realFaviconForUrl(site.url, true)
                             .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                             .observeOn(mainScheduler)
                             .subscribeBy(
                                 onSuccess = { bmp ->
                                     faviconIv.setImageBitmap(bmp)
                                     faviconIv.isVisible = true
                                     initial.isVisible  = false
                                 },
  ```
  To:
  ```kotlin
                         // Async favicon fetch
                         faviconModel.realFaviconForUrl(site.url, true)
                             .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                             .observeOn(mainScheduler)
                             .subscribeBy(
                                 onSuccess = { bmp ->
                                     faviconIv.setImageBitmap(bmp)
                                     faviconIv.isVisible = true
                                     initial.isVisible  = false
                                     frame.setCardBackgroundColor(android.graphics.Color.WHITE)
                                 },
  ```

---

### Task 4: Update unit test and verify layout assertions

**Files:**
- [MODIFY] [HomeScreenLayoutTest.kt](file:///C:/Users/w/Desktop/Fulguris-main/app/src/test/java/com/xhub/browser/view/HomeScreenLayoutTest.kt)

- [ ] **Step 1: Update assertions in HomeScreenLayoutTest.kt**
  Replace lines 29-37 in `app/src/test/java/com/xhub/browser/view/HomeScreenLayoutTest.kt`:
  From:
  ```kotlin
          // Background and overlay views should be removed (their resource IDs should either not exist or return null)
          val bgId = context.resources.getIdentifier("homeScreenBackground", "id", context.packageName)
          if (bgId != 0) {
              assertThat(view.findViewById<android.view.View>(bgId)).isNull()
          }
          val overlayId = context.resources.getIdentifier("homeScreenBackgroundOverlay", "id", context.packageName)
          if (overlayId != 0) {
              assertThat(view.findViewById<android.view.View>(overlayId)).isNull()
          }
  ```
  To:
  ```kotlin
          // Background and overlay views should exist
          val bgId = context.resources.getIdentifier("homeScreenBackground", "id", context.packageName)
          assertThat(bgId).isNotEqualTo(0)
          assertThat(view.findViewById<android.view.View>(bgId)).isNotNull()
  
          val overlayId = context.resources.getIdentifier("homeScreenBackgroundOverlay", "id", context.packageName)
          assertThat(overlayId).isNotEqualTo(0)
          assertThat(view.findViewById<android.view.View>(overlayId)).isNotNull()
  ```

- [ ] **Step 2: Run layout test to verify it passes**
  Run: `.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.view.HomeScreenLayoutTest"`
  Expected output: `BUILD SUCCESSFUL`

- [ ] **Step 3: Build the application to verify compilation**
  Run: `.\gradlew.bat assembleXhubFullDownloadDebug`
  Expected output: `BUILD SUCCESSFUL`
