# Premium Starfield Background Implementation Plan
Goal: Implement a stunning, immersive full-screen starry dark background on the browser home screen.
Architecture: Add the starry background image and a dark transparent scrim view as base layers inside layout_home_screen.xml, rendering behind all other elements. Remove the background color from the root CoordinatorLayout.
Tech Stack: Android XML layout, ImageView
---

## Tasks

### Task 1: Copy Background Image Asset
- **File to create:** `app/src/main/res/drawable/bg_home_starfield.jpg`
- **Command:** `Copy-Item -Path "C:\Users\w\.gemini\antigravity\brain\c2025dd7-271a-4118-92d6-5598aa26b212\media__1780142981228.jpg" -Destination "c:\Users\w\Desktop\Fulguris-main\app\src\main\res\drawable\bg_home_starfield.jpg"`
- **Expected Output:** File copied successfully.

### Task 2: Refactor `layout_home_screen.xml`
- **File to modify:** [layout_home_screen.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/layout/layout_home_screen.xml)
- **Modifications:**
  - Remove `android:background="@color/home_background"` from the root `<androidx.coordinatorlayout.widget.CoordinatorLayout>` starting at line 2.
  - Insert the full-screen `<ImageView>` and dim `<View>` overlay right after the root tag opens.
- **Target Replacement:**
```xml
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
- **Replacement Content:**
```xml
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

### Task 3: Build Verification
- **Command:** `.\gradlew.bat assembleSlionsFullDownloadDebug`
- **Expected Output:** `BUILD SUCCESSFUL`

### Task 4: Unit Test Verification
- **Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
- **Expected Output:** `BUILD SUCCESSFUL`
