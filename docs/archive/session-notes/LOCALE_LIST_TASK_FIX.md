# Locale List Task Dependency Fix

## Overview
Fixed the `generateLocaleList` task dependency configuration that was incorrectly narrowed to only cover compile tasks, missing KSP (Kotlin Symbol Processing) and KAPT (Kotlin Annotation Processing Tool) tasks.

---

## Problem

The task dependency guard was simplified to only check for `"compile"` tasks:

```groovy
// INCORRECT - Too narrow
tasks.configureEach { task ->
    if (name.contains("compile")) {  // ❌ Missing task.name reference
        task.dependsOn generateLocaleList  // ❌ Only covers compile tasks
    }
}
```

**Issues:**
1. ❌ Used `name` instead of `task.name` (incorrect scope)
2. ❌ Only checked for "compile" tasks
3. ❌ Missing "ksp" task coverage (Kotlin Symbol Processing)
4. ❌ Missing "kapt" task coverage (Kotlin Annotation Processing)
5. ❌ No explicit guard against circular self-dependency

**Impact:**
- KSP tasks could run before locale list generation
- KAPT tasks could run before locale list generation
- Potential build failures with "file not found" errors for `LocaleList.java`
- Hilt/Dagger annotation processing might fail if it needs locale list

---

## Solution

Restored the complete three-condition form with explicit guards:

```groovy
// CORRECT - Complete coverage
tasks.configureEach { task ->
    if (task.name != "generateLocaleList" && 
        (task.name.contains("compile") || 
         task.name.contains("ksp") || 
         task.name.contains("kapt"))) {
        task.dependsOn generateLocaleList
    }
}
```

**Improvements:**
1. ✅ Uses `task.name` explicitly (correct scope)
2. ✅ Checks for "compile" tasks
3. ✅ Checks for "ksp" tasks (Kotlin Symbol Processing)
4. ✅ Checks for "kapt" tasks (Kotlin Annotation Processing)
5. ✅ Explicitly prevents circular self-dependency with `task.name != "generateLocaleList"`

---

## What is generateLocaleList?

### Purpose
Generates `LocaleList.java` at build time, containing a list of all enabled locales in the app.

### Generated File Location
```
app/build/generated/source/locale/com/xhub/browser/locale/LocaleList.java
```

### Generated Content Example
```java
package com.xhub.browser.locale;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LocaleList {
    public static final List<String> BUNDLED_LOCALES = Collections.unmodifiableList(
        Arrays.asList(new String[] { 
            "af-rZA", "ar-rSA", "bs-rBA", "ca-rES", "cs-rCZ", "da-rDK", 
            "de-rDE", "el-rGR", "en-rGB", "en-rUS", "es-rES", "fi-rFI",
            // ... more locales ...
        })
    );
}
```

### Usage in Code
The generated class is used by the app to:
- Enumerate available translations
- Display language selection UI
- Validate locale settings
- Provide locale metadata

---

## Why All Three Task Types Matter

### 1. Compile Tasks
**Examples:** `compileXhubFullDownloadDebugKotlin`, `compileXhubFullDownloadDebugJavaWithJavac`

**Why:** Standard Kotlin/Java compilation needs `LocaleList.java` to exist if any source file references it.

### 2. KSP Tasks (Kotlin Symbol Processing)
**Examples:** `kspXhubFullDownloadDebugKotlin`

**Why:** KSP is used for code generation (replacing some KAPT usage). Modern annotation processors run via KSP.

**Used by:**
- Mezzanine (file-to-string code generation)
- Modern Hilt/Dagger processors (migrating from KAPT to KSP)
- Other KSP-based code generators

### 3. KAPT Tasks (Kotlin Annotation Processing)
**Examples:** `kaptGenerateStubsXhubFullDownloadDebugKotlin`, `kaptXhubFullDownloadDebugKotlin`

**Why:** KAPT runs annotation processors for Kotlin code.

**Used by:**
- Hilt/Dagger dependency injection (generates component code)
- Room database (generates DAO implementations)
- Other annotation processors

**Critical:** KAPT runs in two phases:
1. `kaptGenerateStubs*` - Creates Java stubs from Kotlin code
2. `kapt*` - Runs annotation processors

Both phases may reference `LocaleList.java`, so both need it generated first.

---

## Task Dependency Flow

### With Correct Configuration ✅
```
generateLocaleList
    ↓
    ├─→ compileXhubFullDownloadDebugKotlin
    ├─→ kspXhubFullDownloadDebugKotlin  
    └─→ kaptXhubFullDownloadDebugKotlin
```

### With Broken Configuration ❌
```
generateLocaleList (might run late)
    ↓
compileXhubFullDownloadDebugKotlin ✅
    ↓
kspXhubFullDownloadDebugKotlin ❌ (runs too early - LocaleList.java missing!)
    ↓  
kaptXhubFullDownloadDebugKotlin ❌ (runs too early - LocaleList.java missing!)
```

---

## Circular Dependency Prevention

### The Guard: `task.name != "generateLocaleList"`

**Why needed:**
Without this guard, the configuration would try to make `generateLocaleList` depend on itself:

```groovy
// WITHOUT GUARD - CIRCULAR DEPENDENCY!
tasks.configureEach { task ->
    if (task.name.contains("generate")) {  // ❌ Matches "generateLocaleList"
        task.dependsOn generateLocaleList  // ❌ Makes task depend on itself!
    }
}

// Result: "Circular dependency between tasks" error
```

**With guard:**
```groovy
// WITH GUARD - NO CIRCULAR DEPENDENCY ✅
tasks.configureEach { task ->
    if (task.name != "generateLocaleList" && task.name.contains("generate")) {
        task.dependsOn generateLocaleList  // ✅ Skip self
    }
}
```

---

## Build.gradle Change Details

**File:** `app/build.gradle`  
**Lines:** ~498-502

### Before (Broken)
```groovy
tasks.configureEach { task ->
    if (name.contains("compile")) {
        task.dependsOn generateLocaleList
    }
}
```

**Issues:**
- Uses `name` instead of `task.name` (wrong scope)
- Only covers compile tasks
- Missing ksp coverage
- Missing kapt coverage
- No self-dependency guard

### After (Fixed)
```groovy
tasks.configureEach { task ->
    if (task.name != "generateLocaleList" && (task.name.contains("compile") || task.name.contains("ksp") || task.name.contains("kapt"))) {
        task.dependsOn generateLocaleList
    }
}
```

**Improvements:**
- Uses `task.name` explicitly ✅
- Covers compile tasks ✅
- Covers ksp tasks ✅
- Covers kapt tasks ✅
- Prevents self-dependency ✅

---

## Testing the Fix

### Verify Task Dependencies
```powershell
# List all task dependencies for a specific task
.\gradlew.bat :app:kspXhubFullDownloadDebugKotlin --dry-run

# Should show generateLocaleList runs first
```

### Build from Clean
```powershell
# Clean build to ensure generateLocaleList runs
.\gradlew.bat clean
.\gradlew.bat assembleXhubFullDownloadDebug

# Should complete without errors
```

### Check Generated File
```powershell
# Verify LocaleList.java was generated
Test-Path "app\build\generated\source\locale\com\xhub\browser\locale\LocaleList.java"
# Should return: True
```

---

## Related Build Tasks

### This project uses multiple code generation tools:

1. **generateLocaleList** (custom task)
   - Generates locale list from enabled translations
   - Output: `LocaleList.java`

2. **mezzanine** (Gradle plugin)
   - Converts resource files to Java string constants
   - Used for: JS files, HTML templates, CSS
   - Runs via KSP

3. **Hilt/Dagger** (annotation processing)
   - Generates dependency injection code
   - Runs via KAPT (transitioning to KSP)

4. **Room** (if used - annotation processing)
   - Generates database implementations
   - Runs via KAPT

All of these need proper task ordering to avoid "file not found" errors.

---

## Common Build Errors This Fixes

### Error 1: Missing LocaleList.java
```
error: cannot find symbol
import com.xhub.browser.locale.LocaleList;
                                ^
  symbol:   class LocaleList
  location: package com.xhub.browser.locale
```

**Cause:** KSP or KAPT ran before `generateLocaleList`  
**Fixed by:** This change ensures locale list generates first

### Error 2: Circular Dependency
```
FAILURE: Build failed with an exception.

* What went wrong:
Circular dependency between the following tasks:
:app:generateLocaleList
\--- :app:generateLocaleList (*)
```

**Cause:** Task depends on itself  
**Fixed by:** `task.name != "generateLocaleList"` guard

---

## Summary

✅ **Fixed:** Task dependency configuration for `generateLocaleList`  
✅ **Coverage:** Now properly covers compile, KSP, and KAPT tasks  
✅ **Safety:** Explicit self-dependency prevention guard  
✅ **Scope:** Uses correct `task.name` reference  
✅ **Result:** Locale list always generates before any processing tasks

---

**Date:** 2026-06-12  
**Status:** ✅ FIXED  
**Impact:** Prevents build failures from missing LocaleList.java  
**Lines Changed:** 1 condition statement (498-502)
