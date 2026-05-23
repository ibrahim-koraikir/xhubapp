# Home Screen Header Redesign Implementation Plan
Goal: Redesign the home screen top section to display a horizontal layout with Settings, branding title with a gradient logo box, dynamic greeting, and a profile avatar shortcut to Bookmarks.
Architecture: Integrate a modern ConstraintLayout container to position Settings (left), center branding (logo + title + greeting), and Profile (right) side-by-side. Connect bindings in the main WebBrowserActivity.
Tech Stack: Android XML Layouts, Material Design ShapeableImageView, ConstraintLayout, Kotlin.
---

## Proposed Changes

### 1. Create logo gradient drawable resource
#### [NEW] [bg_home_logo_gradient.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_logo_gradient.xml)
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:startColor="#FF8C00"
        android:endColor="#FF007A"
        android:angle="315" />
    <corners android:radius="8dp" />
</shape>
```

### 2. Create profile ring border drawable resource
#### [NEW] [bg_home_profile_ring.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/drawable/bg_home_profile_ring.xml)
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <stroke
        android:width="2dp"
        android:color="#4DFF7A00" />
    <solid android:color="@android:color/transparent" />
</shape>
```

### 3. Add circular shape overlay style to styles.xml
#### [MODIFY] [styles.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/values/styles.xml)
Insert right after the other Shapes definitions (near line 1014):
```xml
    <style name="ShapeAppearanceOverlay.App.CornerSize50Percent" parent="">
        <item name="cornerSize">50%</item>
    </style>
```

### 4. Update the layout home screen file
#### [MODIFY] [layout_home_screen.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)
Replace lines 26 to 55 with the ConstraintLayout header:
```xml
            <!-- Header Container -->
            <androidx.constraintlayout.widget.ConstraintLayout
                android:id="@+id/homeHeaderContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:paddingBottom="24dp"
                android:layout_marginBottom="20dp">

                <!-- Left Settings Button -->
                <ImageButton
                    android:id="@+id/homeSettingsButton"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
                    android:background="@drawable/bg_shortcut_tile"
                    android:src="@drawable/ic_settings"
                    app:tint="#D0D0D0"
                    app:layout_constraintLeft_toLeftOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent"
                    android:contentDescription="Settings" />

                <!-- Centered Branding Group -->
                <LinearLayout
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:gravity="center_horizontal"
                    app:layout_constraintLeft_toRightOf="@id/homeSettingsButton"
                    app:layout_constraintRight_toLeftOf="@+id/homeProfileButton"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintBottom_toBottomOf="parent">

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical">

                        <!-- Logo with Orange/Pink Gradient Background -->
                        <FrameLayout
                            android:layout_width="28dp"
                            android:layout_height="28dp"
                            android:background="@drawable/bg_home_logo_gradient"
                            android:layout_marginEnd="6dp">

                            <ImageView
                                android:layout_width="16dp"
                                android:layout_height="16dp"
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
                            android:textSize="28sp"
                            android:textStyle="bold"
                            android:fontFamily="sans-serif-medium"
                            android:includeFontPadding="false"
                            android:letterSpacing="0.02" />
                    </LinearLayout>

                    <TextView
                        android:id="@+id/homeGreeting"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Good morning"
                        android:textColor="@color/home_muted_foreground"
                        android:textSize="11sp"
                        android:layout_marginTop="4dp" />
                </LinearLayout>

                <!-- Right Profile Button with Orange Ring border -->
                <FrameLayout
                    android:id="@+id/homeProfileButton"
                    android:layout_width="32dp"
                    android:layout_height="32dp"
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
```

### 5. Wire up the buttons in the main WebBrowserActivity
#### [MODIFY] [WebBrowserActivity.kt](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/java/fulguris/activity/WebBrowserActivity.kt)
Find where greeting text is configured in `initializeHomeScreen()` (around line 1767) and add click listeners:
```kotlin
        val homeSettings = iBinding.homeScreenOverlay.findViewById<View>(R.id.homeSettingsButton)
        homeSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        val homeProfile = iBinding.homeScreenOverlay.findViewById<View>(R.id.homeProfileButton)
        homeProfile?.setOnClickListener {
            openBookmarks()
        }
```

---

## Verification Plan

### Automated Build Verification
- Execute `.\gradlew.bat assembleSlionsFullDownloadDebug` to ensure all layout files and IDs compile cleanly.

### Manual Verification
- Visual inspection of the spacing and alignments of the horizontal ConstraintLayout header elements.
- Verify clicking Settings opens settings.
- Verify clicking Profile opens bookmarks list/drawer.
