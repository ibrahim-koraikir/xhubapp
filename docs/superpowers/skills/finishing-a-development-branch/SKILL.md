---
name: finishing-a-development-branch
description: Use when implementation is complete, all tests pass, and you need to integrate work
---

**Announce at start:** "I'm using the finishing-a-development-branch skill to complete this work."

## Step 1: Verify Build Passes
```powershell
.\gradlew.bat assembleSlionsFullDownloadDebug
```
If build fails: fix before proceeding. Cannot offer integration options until build passes.

## Step 2: Detect Environment
```bash
git status
git log --oneline -5
```
Determine if on a feature branch or main.

## Step 3: Present Options
```
Implementation complete. What would you like to do?

1. Merge back to main locally
2. Push and create a Pull Request
3. Keep the branch as-is (I'll handle it later)
4. Discard this work

Which option?
```

## Step 4: Execute Choice

### Option 1: Merge Locally
```bash
git checkout main
git pull
git merge <feature-branch>
```
Verify build passes on merged result. Then delete branch:
```bash
git branch -d <feature-branch>
```

### Option 2: Push and Create PR
```bash
git push -u origin <feature-branch>
```
Then open PR with summary of changes.

### Option 4: Discard
Confirm first: "This will permanently delete branch `<name>` and all commits. Type 'discard' to confirm."

## Red Flags — Never
- Proceed with a failing build
- Merge without verifying build on result
- Delete work without confirmation
- Force-push without explicit request
