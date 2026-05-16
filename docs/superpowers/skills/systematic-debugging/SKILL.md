---
name: systematic-debugging
description: Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes
---

## The Iron Law
```
NO FIXES WITHOUT ROOT CAUSE INVESTIGATION FIRST
```

## The Four Phases
You MUST complete each phase before proceeding to the next.

### Phase 1: Root Cause Investigation
1. Read full error messages and stack traces completely
2. Reproduce consistently — what are the exact steps?
3. Check recent changes — git diff, recent commits
4. Gather evidence — add diagnostic logging at component boundaries
5. Trace data flow — where does the bad value originate? Keep tracing up until you find the source

### Phase 2: Pattern Analysis
1. Find working examples of similar code in the codebase
2. Compare against broken code — list EVERY difference, however small
3. Understand all dependencies and assumptions

### Phase 3: Hypothesis and Testing
1. State ONE clear hypothesis: "I think X is the root cause because Y"
2. Make the SMALLEST possible change to test it — one variable at a time
3. Verify before continuing — if it didn't work, form a NEW hypothesis. DON'T stack more fixes.

### Phase 4: Implementation
1. Fix at the root cause, not the symptom
2. Verify the fix actually works (build, run, test)
3. **If 3+ fixes have failed → STOP. Question the architecture. Discuss with user before another attempt.**

## Red Flags — STOP and Return to Phase 1
- "Just try changing X and see if it works"
- "It's probably X, let me fix that"
- "One more fix attempt" after 2+ failures
- Proposing fixes before understanding root cause
