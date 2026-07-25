# Downloads Fix & UI Redesign — Implementation Plan
Goal: Fix download visibility and replace the preference-style downloads screen with a rich, glassmorphic card list UI.
Architecture: Maintain DownloadManager as the single download provider. Replace PreferenceFragmentCompat with a standard Fragment using a custom RecyclerView, ListAdapter, dynamic status pills, and async video/image thumbnails.
Tech Stack: Android DownloadManager, RecyclerView, ListAdapter with DiffUtil, MediaMetadataRetriever, LruCache, Kotlin Coroutines.
---

## Detailed Tasks

### Task 1: Create bg_status_pill.xml shape drawable
File: `app/src/main/res/drawable/bg_status_pill.xml`
Create a 100dp rounded rectangle shape to serve as the background of status pills.

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="100dp" />
    <solid android:color="@android:color/white" />
</shape>
```

---

### Task 2: Create layout_downloads.xml layout
File: `app/src/main/res/layout/layout_downloads.xml`
Create the main layout for DownloadsFragment containing a header, empty state view, RecyclerView, and bottom action buttons (Clean up, Remove all, Delete all).

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/transparent">

    <!-- Header bar -->
    <LinearLayout
        android:id="@+id/layoutHeader"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/tvDownloadsHeaderTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/downloads"
            android:textSize="20sp"
            android:textStyle="bold"
            android:textColor="?attr/colorOnSurface"/>

        <TextView
            android:id="@+id/tvDownloadsHeaderSummary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:layout_marginTop="2dp"
            tools:text="Loading downloads..."/>

    </LinearLayout>

    <!-- Empty State -->
    <LinearLayout
        android:id="@+id/layoutEmptyState"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="24dp"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/layoutHeader"
        app:layout_constraintBottom_toTopOf="@id/cardActions"
        app:layout_constraintVertical_bias="0.4">

        <ImageView
            android:layout_width="72dp"
            android:layout_height="72dp"
            android:src="@drawable/ic_cloud_download_outline"
            android:tint="?attr/colorOnSurfaceVariant"
            android:alpha="0.6"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/pref_summary_no_downloads"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="?attr/colorOnSurface"
            android:layout_marginTop="16dp"/>

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Downloads you start will appear here"
            android:textSize="12sp"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:layout_marginTop="4dp"
            android:gravity="center"/>

    </LinearLayout>

    <!-- RecyclerView -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvDownloads"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:clipToPadding="false"
        android:paddingBottom="16dp"
        app:layout_constraintTop_toBottomOf="@id/layoutHeader"
        app:layout_constraintBottom_toTopOf="@id/cardActions"/>

    <!-- Bottom Actions Panel -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/cardActions"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:cardCornerRadius="0dp"
        app:cardElevation="4dp"
        app:strokeWidth="0dp"
        app:cardBackgroundColor="?attr/colorSurface"
        app:layout_constraintBottom_toBottomOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center"
            android:padding="8dp">

            <Button
                android:id="@+id/btnClean"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/action_clean"
                android:textColor="?attr/colorPrimary"
                android:icon="@drawable/ic_cleaning_services_outline"/>

            <Button
                android:id="@+id/btnRemoveAll"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/pref_title_downloads_remove_all"
                android:textColor="?attr/colorPrimary"
                android:icon="@drawable/ic_delete_sweep_outline"/>

            <Button
                android:id="@+id/btnDeleteAll"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/pref_title_downloads_delete_all"
                android:textColor="?attr/colorError"
                android:icon="@drawable/ic_delete_forever_outline"/>

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

### Task 3: Modify fragment_downloads.xml and item_download_card.xml
Files:
- `app/src/main/res/layout/fragment_downloads.xml`
- `app/src/main/res/layout/item_download_card.xml`

Update fragment_downloads.xml to use `match_parent` height and width to ensure the sheet covers the correct screen portion.
Update item_download_card.xml to reference the correct `@drawable/ic_more_vertical` drawable for options.

---

### Task 4: Rewrite DownloadsFragment.kt
File: `app/src/main/java/fulguris/settings/fragment/DownloadsFragment.kt`
Replace entire file contents to change class signature to standard `Fragment`, configure the custom ListAdapter, content observer, action buttons, dynamic pills, and cache thumbnail loaders.

---

### Task 5: Build & Run JVM Unit Tests
Verify that the project compiles successfully and all unit tests pass:
```powershell
taskkill /F /IM java.exe
.\gradlew.bat clean assembleSlionsFullDownloadDebug
.\gradlew.bat testSlionsFullDownloadDebugUnitTest
```
