# Phase 2: UI & UX Polish Implementation Plan
Goal: Ensure consistency in modern Material 3 aesthetics across the home screen, overlays, and dialogs.
Architecture: XML Layout constraints and Material Components updates.
Tech Stack: Android XML, Material Components (MaterialAlertDialogBuilder, TextInputLayout).

## Tasks

### 1. Fix Home Screen Title Constraints
The center `LinearLayout` containing the app title and greeting in `layout_home_screen.xml` currently has `wrap_content` width and is constrained to the parent's edges. On smaller screens, this can overlap the settings and profile buttons. We will constrain it to the buttons and set width to `0dp`.

**File:** `app/src/main/res/layout/layout_home_screen.xml`
**Action:** Update constraints for the center title `LinearLayout` (approx line 143):
```xml
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:layout_marginHorizontal="12dp"
            app:layout_constraintLeft_toRightOf="@id/homeSettingsBtn"
            app:layout_constraintRight_toLeftOf="@id/homeProfileButton"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent">
```

### 2. Upgrade dialog_add_site.xml to Material 3
The `dialog_add_site.xml` uses basic legacy `EditText` views. We will upgrade these to `com.google.android.material.textfield.TextInputLayout` and `TextInputEditText` for premium styling.

**File:** `app/src/main/res/layout/dialog_add_site.xml`
**Action:** Completely replace the `EditText` elements with `TextInputLayout` wrappers using `style="@style/Widget.Material3.TextInputLayout.OutlinedBox"`.

### 3. Upgrade AlertDialog to MaterialAlertDialogBuilder
`ManageShortcutsActivity` uses the legacy `androidx.appcompat.app.AlertDialog.Builder`. We will upgrade all 4 dialog instances to `com.google.android.material.dialog.MaterialAlertDialogBuilder`. Because the app uses `Theme.Material3`, this will automatically give the dialogs proper rounded corners and elevations.

**File:** `app/src/main/java/fulguris/activity/ManageShortcutsActivity.kt`
**Action:** Replace all `AlertDialog.Builder(this)` with `com.google.android.material.dialog.MaterialAlertDialogBuilder(this)`.

## Verification Plan
1. `.\gradlew.bat assembleSlionsFullDownloadDebug` to ensure compilation.
2. Review the layout changes locally.
