# Full App Review & Optimization Design
**Date:** 2026-05-24
**Topic:** Comprehensive 3-Phase App Review (Code Health, UI/UX, Performance)

## Goal
Conduct a holistic review of the Fulguris codebase to catch and fix hidden errors, polish recent UI implementations, and ensure optimal performance. This guarantees a stable, premium user experience.

## Phase 1: Automated Health Check (Code & Hidden Bugs)
We will leverage Android's build tools to aggressively hunt for objective errors:
1.  **Test Suite Execution:** Run `.\gradlew.bat testSlionsFullDownloadDebugUnitTest` to uncover any broken logic or failing tests in the backend.
2.  **Linting & Warnings Analysis:** Run `.\gradlew.bat lintSlionsFullDownloadDebug` (or observe build warnings during standard compilation) to find deprecated APIs, unresolved references, and syntax warnings.
3.  **Resolution:** Categorize the findings and systematically fix all critical warnings (e.g., deprecated method replacements, unchecked casts, type mismatches).

## Phase 2: UI & UX Polish
We will manually review the code for the most heavily modified user-facing components:
1.  **Home Screen (`layout_home_screen.xml`):** Verify that the new premium header is perfectly constrained and doesn't overlap inappropriately on smaller screens.
2.  **Dialogs & Overlays (`dialog_add_site.xml`, `search_overlay.xml`):** Ensure we are consistently using modern `BottomSheetDialog` aesthetics and rounded corners everywhere instead of legacy JS alerts.
3.  **Resolution:** Apply layout constraint fixes, remove redundant legacy views, and standardize corner radii and elevations.

## Phase 3: Performance Audit
We will look for obvious bottlenecks that degrade the browsing experience:
1.  **Memory Leaks:** Inspect `MemoryLeakUtils.java` and `AdManager.kt` to ensure WebViews and ad contexts are being properly destroyed.
2.  **Main Thread Work:** Check for heavy I/O operations (like database reads or file accesses) happening on the main thread and move them to Coroutines/RxJava.
3.  **Resolution:** Implement proper lifecycle teardowns and background threading where necessary.

## Spec Self-Review
- [x] No placeholders or "TODO" items.
- [x] Clear scope (broken into 3 manageable phases).
- [x] Specific commands outlined for Phase 1.
