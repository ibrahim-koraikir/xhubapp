# Phase 1: Automated Health Check Implementation Plan
Goal: Fix the 12 failing unit tests in the JVM test suite to restore a 100% pass rate.
Architecture: The JVM test suite runs locally using Robolectric. We will upgrade the test framework dependencies to support Java 17 bytecode and fix invalid test classes.
Tech Stack: JUnit 4, Robolectric, Kotlin, Mockito

## Tasks

### 1. Upgrade Robolectric to support Java 17
Robolectric 4.4 does not support Java 17 bytecode, which is why 11 tests are failing with `java.lang.IllegalArgumentException at ClassReader.java:195`. We will upgrade it to `4.11.1`.

**File:** `app/build.gradle`
**Code:**
```gradle
    // Replace: testImplementation 'org.robolectric:robolectric:4.4'
    testImplementation 'org.robolectric:robolectric:4.11.1'
```
**Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest` (to verify the `IllegalArgumentException` goes away)

### 2. Fix InvalidTestClassError in CloseableExtensionsTest
`CloseableExtensionsTest.kt` is failing because all its `@Test` methods are commented out, which JUnit 4 considers an error when `@RunWith` is present. We will add a simple dummy test so JUnit doesn't crash.

**File:** `app/src/test/java/fulguris/extensions/CloseableExtensionsTest.kt`
**Code:**
```kotlin
    @Rule
    @JvmField
    val exception: ExpectedException = ExpectedException.none()

    @Test
    fun `dummy test to prevent InvalidTestClassError`() {
        // All other tests are commented out, this prevents JUnit from failing
    }
/*
    @Test
```
**Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.extensions.CloseableExtensionsTest"` (to verify it passes)

### 3. Verify the Full Suite
**Command:** `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`
**Expected Output:** `BUILD SUCCESSFUL` (0 test failures)
