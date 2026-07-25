# WebView File Access Security Fix

**Date:** June 14, 2026  
**Status:** ✅ COMPLETED

## Summary

Reverted accidental security vulnerability where `allowFileAccess` was incorrectly set to `true`, contradicting the explicit security comment and reintroducing cross-app file/JavaScript attack vectors.

## Problem Identified

### Accidental Security Regression
**Issue:** In `WebPageTab.createWebView()`, the WebView setting was changed from:
```kotlin
allowFileAccess = false  // BEFORE - SECURE
```
to:
```kotlin
allowFileAccess = true   // AFTER - VULNERABLE
```

**Location:** `app/src/main/java/com/xhub/browser/view/WebPageTab.kt` line ~1120

### Security Comment Contradiction
The code explicitly states:
```kotlin
// Disable file and content access for security (prevents cross-app file/JavaScript attacks)
// File URLs can still be loaded internally if needed, but not from external intents
allowContentAccess = false
allowFileAccess = true    // ← CONTRADICTS the comment above!
```

## Security Risks of `allowFileAccess = true`

### 1. File System Access
- WebView can load `file://` URLs
- Malicious pages can read app-private files
- JavaScript can access local filesystem
- Potential data exfiltration

### 2. Cross-App Attacks
- Malicious apps can craft intents with `file://` URLs
- Can read other apps' private data (if accessible)
- Broader attack surface for exploits

### 3. JavaScript Exploits
- JavaScript can enumerate local files
- Combined with other vulnerabilities, can lead to:
  - Private data leakage
  - Session token theft
  - Cookie exfiltration

### Example Attack Scenario
```
1. User visits malicious website
2. Website redirects to: file:///data/data/com.xhub.browser/databases/
3. JavaScript reads database files
4. Exfiltrates user data to attacker server
```

## Fix Applied

Reverted `allowFileAccess` back to `false`:

```kotlin
// BEFORE (VULNERABLE)
allowFileAccess = true

// AFTER (SECURE)
allowFileAccess = false
```

## Why This Setting Was Disabled

### Android Security Best Practices
From Android documentation:
> "For maximum security, you should disable file access by setting `allowFileAccess` to false."

### Browser Security
Modern browsers have strict file access policies:
- Chrome: Disabled by default
- Firefox: Disabled by default  
- Safari: Disabled by default

Fulguris follows these standards.

### No Impact on yt-dlp
**Important:** This setting does NOT affect yt-dlp functionality:
- yt-dlp runs as a **separate subprocess** (not in WebView)
- Downloads use native Android file system APIs
- WebView file access is unrelated to download functionality

## Confirmation This Was Accidental

### Git Diff Evidence
```diff
- allowFileAccess = false
+ allowFileAccess = true
```

No related commit message or justification found. This appears to be an accidental edit during development.

### No Functional Need
- No code in the codebase requires `file://` URL loading in WebView
- Homepage uses `data:` URIs, not `file://`
- All internal resources loaded via assets or resources
- yt-dlp uses separate process, not WebView

## Verification

Build completed successfully:
```
BUILD SUCCESSFUL in 1m 18s
76 actionable tasks: 9 executed, 67 up-to-date
```

## Testing Recommendations

Test that the app still functions correctly with `allowFileAccess = false`:

1. **Browse Websites:**
   - Visit various websites
   - Verify normal browsing works
   - Check JavaScript functionality

2. **Internal Pages:**
   - Open homepage (should use `data:` URI)
   - Open bookmarks page
   - Open history page

3. **Downloads:**
   - Download regular files (DownloadManager)
   - Download videos with yt-dlp
   - Verify FileProvider URIs work for opening files

4. **File Uploads:**
   - Test file input (`<input type="file">`)
   - Should use Storage Access Framework (SAF)
   - Should NOT use `file://` URLs

All functionality should work as before since no legitimate code path requires `allowFileAccess = true`.

## Related Security Settings

Current WebView security configuration in `WebPageTab.kt`:

```kotlin
// File/Content Access (Security)
allowContentAccess = false   ✅ Disabled
allowFileAccess = false      ✅ Disabled (FIXED)

// JavaScript
javaScriptEnabled = true     ⚠️ Required for modern web
javaScriptCanOpenWindowsAutomatically = false  ✅ Limited

// Storage
databaseEnabled = true       ⚠️ Required for IndexedDB/WebSQL
domStorageEnabled = true     ⚠️ Required for localStorage

// Geo/Camera/etc
// Controlled via permission prompts ✅
```

## Best Practices Going Forward

### Code Review Checklist
- ✅ Security settings changes require explicit justification
- ✅ Comments and code must match
- ✅ Security regressions flagged in review

### If File Access Is Ever Needed
If a legitimate use case requires file access:

1. **Don't enable globally** - Gate it narrowly
2. **Use allowFileAccessFromFileURLs = false** - Prevent cross-file access
3. **Use allowUniversalAccessFromFileURLs = false** - Prevent universal access
4. **Document the reason** - Explain why it's needed
5. **Consider alternatives** - Use `data:` URIs or ContentProvider instead

## Related Documents

- `YT_DLP_CRITICAL_FIX_SUMMARY.md` - Android 10+ W^X fix
- `YT_DLP_DATABASE_FIX.md` - RxJava subscription fix
- `YT_DLP_THREAD_SAFETY_FIX.md` - ConcurrentHashMap fix
- `YT_DLP_SCOPED_STORAGE_FIX.md` - Scoped storage fix
- `YT_DLP_BROADCAST_CLEANUP.md` - Broadcast code removal

## Code References

**File:** `app/src/main/java/com/xhub/browser/view/WebPageTab.kt`
**Line:** ~1120 (`allowFileAccess = false`)

**Comment:** Lines 1117-1119:
```kotlin
// Disable file and content access for security (prevents cross-app file/JavaScript attacks)
// File URLs can still be loaded internally if needed, but not from external intents
```
