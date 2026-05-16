---
name: test-driven-development
description: Use when implementing any feature or bugfix, before writing implementation code
---

## The Iron Law
```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

Write code before the test? Delete it. Start over.

## Red-Green-Refactor Cycle

### RED — Write Failing Test
Write one minimal test showing what should happen.
- One behavior per test
- Clear descriptive name
- Use real code (no mocks unless unavoidable)

### Verify RED — Watch It Fail (MANDATORY. Never skip.)
Run the test. Confirm:
- Test FAILS (not errors)
- Failure message is expected
- Fails because feature is missing (not a typo)

**Test passes immediately?** You're testing existing behavior. Fix the test.

### GREEN — Write Minimal Code
Write the simplest code that makes the test pass. Don't add features, refactor other code, or "improve" beyond the test.

### Verify GREEN — Watch It Pass (MANDATORY.)
Run the test. Confirm:
- Test passes
- All other tests still pass

### REFACTOR — Clean Up
After green only: remove duplication, improve names, extract helpers. Keep tests green. Don't add behavior.

## Android-Specific Notes
For Android/Kotlin, write unit tests in `app/src/test/` (JVM, fast).
UI/integration tests go in `app/src/androidTest/` (requires device).
Prefer JVM unit tests when possible (faster, no device needed).

Run with: `.\gradlew.bat testSlionsFullDownloadDebugUnitTest`

## Common Rationalizations — All Wrong
| Excuse | Reality |
|--------|---------|
| "Too simple to test" | Simple code breaks. Test takes 30 seconds. |
| "I'll test after" | Tests passing immediately prove nothing. |
| "Already manually tested" | Ad-hoc ≠ systematic. Can't re-run. |
| "Deleting X hours is wasteful" | Sunk cost. Keeping unverified code is technical debt. |
| "TDD will slow me down" | TDD is faster than debugging. |

## Red Flags — STOP and Start Over
- Code written before test
- Test passes immediately without implementation
- Can't explain why test failed
- "I already manually tested it"
- "Tests after achieve the same purpose"
