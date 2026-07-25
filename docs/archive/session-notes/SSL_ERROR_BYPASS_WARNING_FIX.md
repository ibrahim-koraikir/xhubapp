# SSL Error Bypass Warning Fix

## Issue
When per-domain SSL error bypass is set to `NoYesAsk.YES`, the browser automatically proceeds past certificate errors with no visual warning to the user. This creates a security concern as users have no indication that they are accessing a site with an untrusted certificate.

## Root Cause
In `WebPageClient.kt`, when `domainPreferences.sslError == NoYesAsk.YES`, the code immediately called `handler.proceed()` without updating the SSL state. This meant the address bar SSL indicator showed no warning about the certificate issue.

## Solution
Modified the `onReceivedSslError` method in `WebPageClient.kt` to set `sslState = SslState.Invalid` before calling `handler.proceed()` when automatically bypassing SSL errors.

## Changes Made

### File: `app/src/main/java/fulguris/view/WebPageClient.kt`

**Before:**
```kotlin
when (domainPreferences.sslError) {
    NoYesAsk.YES -> return handler.proceed()
    // ...
}
```

**After:**
```kotlin
when (domainPreferences.sslError) {
    NoYesAsk.YES -> {
        // Mark SSL state as invalid to show warning in address bar
        sslState = SslState.Invalid
        return handler.proceed()
    }
    // ...
}
```

## Impact

### Security Improvement
- Users now receive a persistent visual warning (invalid SSL icon) when accessing sites with bypassed certificate errors
- The warning remains visible throughout the browsing session on that site
- Provides continuous reminder that the connection is not fully trusted

### User Experience
- The SSL indicator in the address bar will show the invalid/warning state
- Users can still access the site (as configured via domain preferences)
- Clear visual feedback that certificate validation was bypassed

## Testing Recommendations

1. **Enable per-domain SSL bypass:**
   - Visit a site with a self-signed or invalid certificate
   - Choose "Yes" and check "Don't ask again" in the SSL error dialog
   - Verify the domain preference is saved

2. **Verify warning display:**
   - Revisit the same site
   - Confirm the page loads (auto-proceeding through the SSL error)
   - **Check that the SSL indicator shows a warning/invalid state**

3. **Compare with normal sites:**
   - Visit a site with a valid certificate
   - Verify the SSL indicator shows the valid state
   - Navigate back to the bypassed site
   - Confirm the warning state persists

## Technical Details

- The fix leverages the existing `SslState.Invalid` state
- No new SSL states were needed
- The SSL state is already tracked per tab via `webPageTab.sslState`
- The UI already responds to `SslState.Invalid` to display warning indicators

## Related Files
- `app/src/main/java/fulguris/view/WebPageClient.kt` - Main fix location
- `app/src/main/java/fulguris/ssl/SslState.kt` - SSL state enum definition

## Build Verification
```
.\gradlew.bat assembleSlionsFullDownloadDebug
```
✅ Build successful - verified on June 10, 2026
