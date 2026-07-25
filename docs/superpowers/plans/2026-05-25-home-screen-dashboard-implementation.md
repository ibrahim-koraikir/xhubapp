# Home Screen Premium Dashboard Grid Implementation Plan
Goal: Modernize the home screen layout with a CoordinatorLayout collapsing header and a premium 3-column MaterialCardView widget-style shortcuts grid.
Architecture: Migrating FrameLayout to CoordinatorLayout + CollapsingToolbarLayout, restructuring dynamically-loaded shortcut items into MaterialCardView widgets in a 3-column dynamic flow.
Tech Stack: Android SDK, Material Components (Material 3), RxJava, Hilt DI.
---

## User Review Required
No breaking changes are introduced; all existing IDs are retained and all transitions are kept consistent.

## Proposed Changes

### Component: Home Screen UI

#### [MODIFY] [layout_home_screen.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)
Modify the layout to use CoordinatorLayout, AppBarLayout, CollapsingToolbarLayout, and NestedScrollView.

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

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/homeAppBarLayout"
        android:layout_width="match_parent"
        android:layout_height="180dp"
        android:background="@android:color/transparent"
        app:elevation="0dp">

        <com.google.android.material.appbar.CollapsingToolbarLayout
            android:id="@+id/homeCollapsingToolbar"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:layout_scrollFlags="scroll|exitUntilCollapsed"
            app:contentScrim="@color/home_background"
            app:titleEnabled="false">

            <!-- Expanded Header Layout -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:gravity="bottom"
                android:paddingStart="24dp"
                android:paddingEnd="24dp"
                android:paddingBottom="24dp"
                app:layout_collapseMode="parallax">

                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <FrameLayout
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:background="@drawable/bg_home_logo_gradient"
                        android:layout_marginEnd="12dp">

                        <ImageView
                            android:layout_width="22dp"
                            android:layout_height="22dp"
                            android:layout_gravity="center"
                            android:src="@drawable/ic_comet_logo"
                            app:tint="@color/white" />
                    </FrameLayout>

                    <TextView
                        android:id="@+id/homeTitle"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="xbuh"
                        android:textColor="@color/home_foreground"
                        android:textSize="36sp"
                        android:textStyle="bold"
                        android:fontFamily="sans-serif-black"
                        android:includeFontPadding="false"
                        android:letterSpacing="0.02" />
                </LinearLayout>

                <TextView
                    android:id="@+id/homeGreeting"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Good morning"
                    android:textColor="@color/home_muted_foreground"
                    android:textSize="16sp"
                    android:fontFamily="sans-serif-medium"
                    android:layout_marginTop="4dp" />

            </LinearLayout>

            <!-- Collapsed Toolbar -->
            <androidx.appcompat.widget.Toolbar
                android:id="@+id/homeToolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                app:layout_collapseMode="pin"
                app:contentInsetStart="0dp"
                app:contentInsetStartWithNavigation="0dp">

                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:paddingHorizontal="16dp">

                    <!-- Settings button -->
                    <FrameLayout
                        android:id="@+id/homeSettingsBtnContainer"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:background="@drawable/bg_home_profile_ring"
                        android:padding="2dp"
                        app:layout_constraintLeft_toLeftOf="parent"
                        app:layout_constraintTop_toTopOf="parent"
                        app:layout_constraintBottom_toBottomOf="parent">

                        <ImageButton
                            android:id="@+id/homeSettingsBtn"
                              android:layout_width="match_parent"
                              android:layout_height="match_parent"
                              android:background="?attr/selectableItemBackgroundBorderless"
                              android:src="@drawable/ic_settings"
                              android:padding="8dp"
                              app:tint="@color/home_foreground"
                              android:contentDescription="Settings" />
                    </FrameLayout>

                    <!-- Profile avatar -->
                    <FrameLayout
                        android:id="@+id/homeProfileButton"
                        android:layout_width="40dp"
                        android:layout_height="40dp"
                        android:background="@drawable/bg_home_profile_ring"
                        android:padding="2dp"
                        app:layout_constraintRight_toRightOf="parent"
                        app:layout_constraintTop_toTopOf="parent"
                        app:layout_constraintBottom_toBottomOf="parent">

                        <com.google.android.material.imageview.ShapeableImageView
                            android:id="@+id/homeProfileImage"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:scaleType="centerCrop"
                            app:shapeAppearanceOverlay="@style/ShapeAppearanceOverlay.App.CornerSize50Percent"
                            android:src="@drawable/ic_launcher_foreground" />
                    </FrameLayout>

                </androidx.constraintlayout.widget.ConstraintLayout>

            </androidx.appcompat.widget.Toolbar>

        </com.google.android.material.appbar.CollapsingToolbarLayout>

    </com.google.android.material.appbar.AppBarLayout>

    <!-- Scrollable body content -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:scrollbars="none"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingHorizontal="24dp"
            android:paddingTop="16dp"
            android:paddingBottom="32dp">

            <!-- Website Shortcuts section -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical">

                <!-- Header Row with Edit Button -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Website Shortcuts"
                            android:textColor="@color/home_foreground"
                            android:textSize="18sp"
                            android:textStyle="bold" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Quick access to your favourite sites"
                            android:textColor="@color/home_muted_foreground"
                            android:textSize="13sp"
                            android:layout_marginTop="2dp" />

                    </LinearLayout>

                    <!-- Edit button -->
                    <LinearLayout
                        android:id="@+id/btnEditShortcuts"
                        android:layout_width="wrap_content"
                        android:layout_height="34dp"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:paddingHorizontal="12dp"
                        android:background="@drawable/bg_shortcut_manager_icon_btn"
                        android:clickable="true"
                        android:focusable="true">

                        <ImageView
                            android:layout_width="14dp"
                            android:layout_height="14dp"
                            android:src="@drawable/ic_edit_outline"
                            android:tint="@color/home_foreground" />

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="6dp"
                            android:text="Edit"
                            android:textColor="@color/home_foreground"
                            android:textSize="13sp"
                            android:textStyle="bold" />

                    </LinearLayout>

                </LinearLayout>

                <!-- Dynamic shortcut groups - populated from ShortcutRepository in code -->
                <LinearLayout
                    android:id="@+id/shortcutsDynamicContainer"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:layout_marginTop="20dp" />

            </LinearLayout>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

#### [MODIFY] [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)
Modify `buildDynamicShortcuts()` to construct 3-column MaterialCardView grids for shortcut groups.

```kotlin
        io.reactivex.Single.fromCallable { fulguris.shortcuts.ShortcutRepository.loadGroups(this) }
            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
            .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe { groups ->
                val inflater = layoutInflater
                val dp6  = (6  * resources.displayMetrics.density).toInt()
                val dp8  = (8  * resources.displayMetrics.density).toInt()
                val dp12 = (12 * resources.displayMetrics.density).toInt()
                val dp24 = (24 * resources.displayMetrics.density).toInt()
                val dp48 = (48 * resources.displayMetrics.density).toInt()

                groups.forEach { group ->
                    // Group label
                    val label = TextView(this).apply {
                        text = group.name
                        setTextColor(ContextCompat.getColor(context, R.color.home_foreground))
                        textSize = 16f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        val lp = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.bottomMargin = dp12
                        layoutParams = lp
                    }
                    container.addView(label)

                    // Row of tiles (3 per row)
                    var row: android.widget.LinearLayout? = null
                    group.sites.forEachIndexed { idx, site ->
                        if (idx % 3 == 0) {
                            row = android.widget.LinearLayout(this).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                val lp = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                lp.bottomMargin = dp24
                                layoutParams = lp
                            }
                            container.addView(row)
                        }

                        // Premium MaterialCardView shortcut tile
                        val tile = com.google.android.material.card.MaterialCardView(this).apply {
                            radius = 16 * resources.displayMetrics.density
                            cardElevation = 2 * resources.displayMetrics.density
                            strokeWidth = 0
                            val typedValue = android.util.TypedValue()
                            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
                            setCardBackgroundColor(typedValue.data)

                            isClickable = true
                            isFocusable = true
                            tag = site.url
                            setOnClickListener { onHomeScreenShortcutClick(it) }

                            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                setMargins(dp6, 0, dp6, 0)
                            }
                            layoutParams = lp
                        }

                        val cardInner = android.widget.LinearLayout(this).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            gravity = android.view.Gravity.CENTER
                            setPadding(dp12, dp12, dp12, dp12)
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                        }

                        // Icon frame
                        val frame = android.widget.FrameLayout(this).apply {
                            val lp = android.widget.LinearLayout.LayoutParams(dp48, dp48)
                            layoutParams = lp
                            background = ContextCompat.getDrawable(context, R.drawable.bg_shortcut_tile)
                            clipToOutline = true
                            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                        }

                        // Initial letter
                        val initial = TextView(this).apply {
                            text = site.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                            setTextColor(ContextCompat.getColor(context, R.color.home_foreground))
                            textSize = 18f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            gravity = android.view.Gravity.CENTER
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        frame.addView(initial)

                        // Favicon overlay
                        val faviconIv = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            val pad = (8 * resources.displayMetrics.density).toInt()
                            setPadding(pad, pad, pad, pad)
                            setBackgroundColor(android.graphics.Color.WHITE)
                            isVisible = false
                            layoutParams = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                        frame.addView(faviconIv)

                        // Try to load a real favicon; keep letter visible if nothing found
                        faviconModel.realFaviconForUrl(site.url, true)
                            .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                            .observeOn(mainScheduler)
                            .subscribeBy(
                                onSuccess = { bitmap ->
                                    faviconIv.setImageBitmap(bitmap)
                                    faviconIv.isVisible = true
                                    initial.isVisible = false
                                },
                                onError = {}
                            )

                        cardInner.addView(frame)

                        // Site name label below icon
                        val nameLabel = TextView(this).apply {
                            text = site.name
                            setTextColor(ContextCompat.getColor(context, R.color.home_foreground))
                            textSize = 13f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            gravity = android.view.Gravity.CENTER
                            val lp = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            lp.topMargin = dp8
                            layoutParams = lp
                        }
                        cardInner.addView(nameLabel)

                        tile.addView(cardInner)
                        row?.addView(tile)
                    }

                    // Pad empty slots in last row so tiles stay even-sized
                    val remainder = group.sites.size % 3
                    if (remainder != 0) {
                        repeat(3 - remainder) {
                            val spacer = android.widget.Space(this).apply {
                                val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                    setMargins(dp6, 0, dp6, 0)
                                }
                                layoutParams = lp
                            }
                            row?.addView(spacer)
                        }
                    }
                }
            }
```

---

## Verification Plan

### Automated Tests
- Run unit tests to verify compile/build correctness:
  `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
- Assemble Debug APK:
  `.\gradlew.bat assembleSlionsFullDownloadDebug`
