# Video Detection Default Implementation Plan
Goal: Standardize preference namespace attributes to make settings toggles clickable and correct defaults.
Architecture: Standardize XML attributes in `preference_general.xml` to use the `android:` namespace instead of the custom/library `app:` namespace.
Tech Stack: Android XML layout, Jetpack Preference Library.
---

## Proposed Changes

### 1. Modify Layout File: [preference_general.xml](file:///c:/Users/w/Desktop/Fulguris-main/app/src/main/res/xml/preference_general.xml)
Change the namespace prefix from `app:` to `android:` for `key`, `title`, and `defaultValue` attributes for these preferences:
* `pref_key_search_in_new_tab`
* `pref_key_url_in_new_tab`
* `pref_key_homepage_in_new_tab`
* `pref_key_bookmark_in_new_tab`
* `pref_key_video_detection_enabled`

## Tasks

### Task 1: Apply Namespace Changes in `preference_general.xml`
- **File**: `c:\Users\w\Desktop\Fulguris-main\app\src\main\res\xml\preference_general.xml`
- **Action**: Replace `app:key`, `app:title`, and `app:defaultValue` with `android:key`, `android:title`, and `android:defaultValue` for the selected SwitchPreferences.

**Exact Diff**:
```diff
         <x.SwitchPreference
-            app:key="@string/pref_key_search_in_new_tab"
-            app:title="@string/search_in_new_tab"
-            app:defaultValue="@bool/pref_default_search_in_new_tab"
+            android:key="@string/pref_key_search_in_new_tab"
+            android:title="@string/search_in_new_tab"
+            android:defaultValue="@bool/pref_default_search_in_new_tab"
             app:iconSpaceReserved="false"
             app:icon="@drawable/ic_search"
             app:singleLineTitle="false" />

         <x.SwitchPreference
-            app:key="@string/pref_key_url_in_new_tab"
-            app:title="@string/url_in_new_tab"
-            app:defaultValue="@bool/pref_default_url_in_new_tab"
+            android:key="@string/pref_key_url_in_new_tab"
+            android:title="@string/url_in_new_tab"
+            android:defaultValue="@bool/pref_default_url_in_new_tab"
             app:iconSpaceReserved="false"
             app:icon="@drawable/ic_web"
             app:singleLineTitle="false" />

         <x.SwitchPreference
-            app:key="@string/pref_key_homepage_in_new_tab"
-            app:title="@string/homepage_in_new_tab"
-            app:defaultValue="@bool/pref_default_homepage_in_new_tab"
+            android:key="@string/pref_key_homepage_in_new_tab"
+            android:title="@string/homepage_in_new_tab"
+            android:defaultValue="@bool/pref_default_homepage_in_new_tab"
             app:iconSpaceReserved="false"
             app:icon="@drawable/ic_home_outline"
             app:singleLineTitle="false" />

         <x.SwitchPreference
-            app:key="@string/pref_key_bookmark_in_new_tab"
-            app:title="@string/bookmark_in_new_tab"
-            app:defaultValue="@bool/pref_default_bookmark_in_new_tab"
+            android:key="@string/pref_key_bookmark_in_new_tab"
+            android:title="@string/bookmark_in_new_tab"
+            android:defaultValue="@bool/pref_default_bookmark_in_new_tab"
             app:iconSpaceReserved="false"
             app:icon="@drawable/ic_bookmark_border"
             app:singleLineTitle="false" />
```

and:
```diff
         <x.SwitchPreference
-            app:key="@string/pref_key_video_detection_enabled"
-            app:title="@string/pref_title_video_detection"
+            android:key="@string/pref_key_video_detection_enabled"
+            android:title="@string/pref_title_video_detection"
             android:summary="@string/pref_summary_video_detection"
-            app:defaultValue="@bool/pref_default_video_detection_enabled"
+            android:defaultValue="@bool/pref_default_video_detection_enabled"
             app:icon="@drawable/ic_download_outline"
             app:iconSpaceReserved="false"
             app:singleLineTitle="false" />
```

---

### Task 2: Build Verification
- **Command**:
  ```powershell
  taskkill /F /IM java.exe
  timeout /t 3
  .\gradlew.bat assembleSlionsFullDownloadDebug
  ```
- **Expected Output**: `BUILD SUCCESSFUL`

---

### Task 3: Run Unit Tests
- **Command**:
  ```powershell
  .\gradlew.bat testSlionsFullDownloadDebugUnitTest
  ```
- **Expected Output**: `BUILD SUCCESSFUL` and all tests pass.
