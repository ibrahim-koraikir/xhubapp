---
name: verification-before-completion
description: Use when about to claim work is complete, fixed, or passing. Run verification commands and confirm output before making any success claims. Evidence before assertions always.
---

## The Iron Law
```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

## The Gate Function
BEFORE claiming any status or expressing satisfaction:

1. **IDENTIFY**: What command proves this claim?
2. **RUN**: Execute the FULL command (fresh, complete)
3. **READ**: Full output, check exit code, count failures
4. **VERIFY**: Does output confirm the claim?
5. **ONLY THEN**: Make the claim

For this Android project, the primary verification command is:
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
Expected output confirming success: `BUILD SUCCESSFUL`

## Red Flags — STOP
- Using "should", "probably", "seems to"
- Expressing satisfaction before verification ("Great!", "Perfect!", "Done!")
- About to commit/push without running the build
- ANY wording implying success without having run verification

## Never Say
- "this should fix it"
- "probably works now"
- "looks correct"

## Always Say
- "Build output: `BUILD SUCCESSFUL` ✅"
- "Build failed with: [actual error message] ❌"
