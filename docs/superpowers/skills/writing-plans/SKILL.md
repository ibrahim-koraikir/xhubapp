---
name: writing-plans
description: Use when you have a spec or requirements for a multi-step task, before touching code
---

**Announce at start:** "I'm using the writing-plans skill to create the implementation plan."

**Save plans to:** `docs/superpowers/plans/YYYY-MM-DD-<feature-name>.md`

## Plan Document Header
Every plan MUST start with:
```markdown
# [Feature Name] Implementation Plan

**Goal:** [One sentence describing what this builds]
**Architecture:** [2-3 sentences about approach]
**Tech Stack:** [Key technologies/libraries]

---
```

## Task Structure
Each task must have: exact file paths, complete code (no placeholders), exact commands, expected output.

```markdown
### Task N: [Component Name]

**Files:**
- Modify: `exact/path/to/file.kt:123-145`

- [ ] **Step 1: Write the failing test**
  ```kotlin
  // complete test code here
  ```
- [ ] **Step 2: Run test to verify it fails**
  Run: `./gradlew test --tests "ClassName.testName"`
  Expected: FAIL
- [ ] **Step 3: Write minimal implementation**
  ```kotlin
  // complete implementation code here
  ```
- [ ] **Step 4: Run test to verify it passes**
  Expected: PASS
- [ ] **Step 5: Build and verify**
  Run: `.\gradlew.bat assembleSlionsFullDownloadDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 6: Commit**
  `git commit -m "feat: description"`
```

## No Placeholders — Ever
These are plan failures:
- "TBD", "TODO", "implement later"
- "Add appropriate error handling"
- "Similar to Task N" (repeat the code)
- Steps that describe what to do without showing how

## Bite-Sized Tasks
Each step is 2-5 minutes. Write test → Run (fail) → Implement → Run (pass) → Commit.

## After Writing
Offer execution options:
1. **Inline Execution** — execute tasks in this session with checkpoints
2. **Step-by-step** — execute one task at a time with your review between each
