# Onboarding + Unified Messages Implementation Plan

**Goal:** First-run product onboarding (5 slides) and themed XHub message banners replacing plain system Toasts.
**Architecture:** `XHubMessage` wraps Snackbar (Activity) / themed Toast (Context); `OnboardingActivity` + `ViewPager2` gated from `SplashActivity` via `UserPreferences.onboardingCompleted`.
**Tech Stack:** Kotlin, Material Snackbar, ViewPager2, SharedPreferences delegates, theme tokens.

---

### Task 1: MessageStyle + pure gravity helper tests
### Task 2: XHubMessage + layout + wrap toast/snackbar
### Task 3: Migrate high-traffic WebBrowserActivity toasts
### Task 4: onboardingCompleted pref + OnboardingActivity UI
### Task 5: Splash gate + Settings re-entry
### Task 6: Verify unit tests + assemble

Spec: `docs/superpowers/specs/2026-07-12-onboarding-and-messages-design.md`
