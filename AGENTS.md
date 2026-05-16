# AGENTS.md - Fulguris Web Browser

## Project Type
Android Kotlin application (browser app). Uses Gradle, Hilt DI, KSP.

## Build Commands

```powershell
# Kill any running Java processes before building (avoids file lock errors)
taskkill /F /IM java.exe
timeout /t 3

# Build APK (product flavors: slionsFullDownload, slionsFullPlaystore, slionsFullFdroid)
.\gradlew.bat assembleSlionsFullDownloadDebug

# Clean build
.\gradlew.bat clean assembleSlionsFullDownloadDebug

# Run unit tests
.\gradlew.bat testSlionsFullDownloadDebugUnitTest
```

## Product Flavors
- `slionsFullDownload` - Download channel (unlimited tabs)
- `slionsFullPlaystore` - Google Play (subscription required for >20 tabs)
- `slionsFullFdroid` - F-Droid channel
- Debug/Release build types

## Localization (L10N)

Translation tools are in `subs/l10n/android/`. See `.github/copilot-instructions.md` for detailed workflows.

```powershell
# Check translation status
python subs\l10n\android\strings.py --check th-rTH

# Translate strings
python subs\l10n\android\strings.py --set string_id de-rDE 'Translation'
python subs\l10n\android\strings.py --set-batch th-rTH string1 'คำแปล1' string2 'คำแปล2'
```

## Key Directories
- `app/src/main/java/fulguris/` - Main source code
- `app/src/main/res/` - Android resources (layouts, strings, drawables)
- `app/src/test/` - JVM unit tests (fast, no device needed)
- `app/src/androidTest/` - Instrumented tests (require device)
- `docs/superpowers/` - Superpowers skill files, specs, and plans
- `subs/AppIntro/` - AppIntro library submodule
- `subs/Preference/` - Preference library submodule
- `subs/l10n/` - Localization tools (git submodule)

## Important Files
- `build.gradle` - Root build config (Kotlin 2.2.10, AGP 8.13.1, Hilt 2.57.1)
- `app/build.gradle` - App module config with product flavors
- `gradle.properties` - Build settings (4GB heap, AndroidX, 16KB page support)

## Reference
- Build instructions: `BUILD_INSTRUCTIONS.md`
- Localization: `.github/copilot-instructions.md` and `L10N.md`
- Full L10N docs: `subs/l10n/docs/android/L10N.md`

---

## Agent Skills (Superpowers — MANDATORY, Non-Negotiable)

These skills are installed from https://github.com/obra/superpowers and are **mandatory**.
Full skill files live in `docs/superpowers/skills/`. Read the SKILL.md before acting.

**The agent checks for relevant skills before ANY task. These are mandatory workflows, not suggestions.**

---

### SKILL: brainstorming
**Full skill:** `docs/superpowers/skills/brainstorming/SKILL.md`

**Trigger: ANY new feature, UI change, behavior modification, or significant refactor.**

**HARD GATE: Do NOT write any code until design is presented and user explicitly approves.**

**Process (in order — do not skip steps):**
1. Explore project context (existing files, recent commits, patterns)
2. Ask clarifying questions — **ONE at a time only**
3. Propose **2-3 approaches** with trade-offs and your recommendation
4. Present design in sections, get approval after each
5. Write design doc to `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`
6. Self-review spec for placeholders, contradictions, ambiguity
7. Ask user to review the written spec before proceeding
8. Invoke writing-plans skill

**Key rules:** YAGNI, one question at a time, always get approval before any code.

---

### SKILL: writing-plans
**Full skill:** `docs/superpowers/skills/writing-plans/SKILL.md`

**Trigger: After brainstorming is approved, before touching code for any multi-step task.**

**Announce:** "I'm using the writing-plans skill to create the implementation plan."

**Process:**
1. Map out all files to create/modify with exact paths
2. Break work into bite-sized **2-5 minute tasks** (write test → run → implement → run → commit)
3. Each task must have: exact file paths, **complete code** (no stubs), exact commands + expected output
4. Save plan to `docs/superpowers/plans/YYYY-MM-DD-<feature>.md`
5. Self-review for placeholders, type consistency, spec coverage gaps

**Plan header (required):**
```
# [Feature] Implementation Plan
Goal: [one sentence]
Architecture: [2-3 sentences]
Tech Stack: [key libs]
---
```

**No Placeholders — Ever. These are plan failures:**
- "TBD", "TODO", "implement later"
- "Add appropriate error handling"
- "Similar to Task N" (always repeat the code)
- Steps that describe what to do without showing the code

---

### SKILL: test-driven-development
**Full skill:** `docs/superpowers/skills/test-driven-development/SKILL.md`

**Trigger: When implementing any feature or bugfix, before writing implementation code.**

**IRON LAW: NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST.**
Write code before the test? Delete it. Start over.

**RED-GREEN-REFACTOR:**
1. **RED**: Write one minimal test. One behavior. Clear name. Real code, no mocks if avoidable.
2. **Verify RED**: Run it. Watch it FAIL. Confirm it fails for the right reason (missing feature, not a typo).
3. **GREEN**: Write the minimal code to pass. Don't add anything extra.
4. **Verify GREEN**: Run it. Watch it PASS. Confirm no other tests broke.
5. **REFACTOR**: Clean up. Keep tests green. Don't add behavior.

**Android test commands:**
```powershell
# JVM unit tests (fast, no device)
.\gradlew.bat testSlionsFullDownloadDebugUnitTest

# Run a single test class
.\gradlew.bat testSlionsFullDownloadDebugUnitTest --tests "fulguris.ClassName"
```

**Never skip watching the test fail. A test that passes immediately is not a valid test.**

---

### SKILL: systematic-debugging
**Full skill:** `docs/superpowers/skills/systematic-debugging/SKILL.md`

**Trigger: Any bug, crash, test failure, or unexpected behavior.**

**IRON LAW: NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST.**

**4-Phase Process (must complete each phase before the next):**

1. **Root Cause Investigation**
   - Read the FULL error message and stack trace — don't skim
   - For Android crashes: pull logcat with `adb logcat -b crash -d`
   - Reproduce consistently. What are the exact steps?
   - Check recent changes: `git diff`, `git log -5`
   - Trace data flow to the original source of the bad value

2. **Pattern Analysis**
   - Find working examples of similar code in the codebase
   - Compare against broken code — list EVERY difference, however small
   - Don't assume "that can't matter"

3. **Hypothesis & Test**
   - State ONE clear hypothesis: "I think X is the root cause because Y"
   - Make the **smallest possible change** to test it — one variable at a time
   - If it didn't work: form a NEW hypothesis. Do NOT stack more fixes.

4. **Implement & Verify**
   - Fix at root cause, not symptom
   - Verify with a fresh build: `.\gradlew.bat assembleSlionsFullDownloadDebug`
   - **If 3+ fixes have failed → STOP. Question the architecture. Discuss with user.**

**Red Flags (STOP and return to Phase 1):**
- "Just try changing X and see if it works"
- "It's probably X, let me fix that"
- "One more fix attempt" after 2+ failures
- Proposing fixes before tracing data flow

---

### SKILL: verification-before-completion
**Full skill:** `docs/superpowers/skills/verification-before-completion/SKILL.md`

**Trigger: Before claiming ANY fix is done, before moving to the next task, before any success claim.**

**IRON LAW: NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE.**

**Gate function — required before ANY success claim:**
1. **IDENTIFY** what command proves the claim
2. **RUN** it fresh (e.g., `.\gradlew.bat assembleSlionsFullDownloadDebug`)
3. **READ** the full output and exit code
4. **ONLY THEN** make the claim — with the evidence

**Never say:** "this should fix it", "probably works now", "looks correct"
**Always say:** "Build output: `BUILD SUCCESSFUL` ✅" or "Build failed with: [actual error] ❌"

---

### SKILL: finishing-a-development-branch
**Full skill:** `docs/superpowers/skills/finishing-a-development-branch/SKILL.md`

**Trigger: When implementation is complete and you need to integrate or wrap up work.**

**Announce:** "I'm using the finishing-a-development-branch skill to complete this work."

**Process:**
1. **Verify build passes:** `.\gradlew.bat assembleSlionsFullDownloadDebug` → must show `BUILD SUCCESSFUL`
2. **Detect environment:** `git status`, `git log --oneline -5`
3. **Present exactly these 4 options:**
   - Merge back to main locally
   - Push and create a Pull Request
   - Keep the branch as-is (I'll handle it later)
   - Discard this work
4. **Execute the chosen option**

**Never proceed with a failing build. Always get typed confirmation before discarding work.**