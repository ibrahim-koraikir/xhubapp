# Onboarding + Unified Messages — Design

**Date:** 2026-07-12  
**Status:** Draft for user review  
**Product:** XHub (`com.xhub.browser`)  
**Approved direction:** Custom intro Activity + unified XHub message banners (not raw system Toasts)

---

## 1. Problem

1. **First-run gap:** `SplashActivity` launches `MainActivity` immediately (`// TODO: check if we need onboarding`). Users never learn product features (video download FAB, open-in-background, tabs, home shortcuts).
2. **Legacy intro content is legal/privacy-oriented** (terms, telemetry, ad block strings exist) but the **activity pipeline is gone** — strings orphaned.
3. **Feedback UI is inconsistent and dated:** mix of system `Toast.makeText`, `Context.toast()`, and Material `Snackbar` with default chrome. Looks weak next to the themed home screen.

---

## 2. Goals

| Goal | Success look |
|------|----------------|
| Teach core XHub actions in ≤60s | 4–5 skippable slides, first launch only |
| Re-openable help | Settings → “Introduction” restarts intro |
| One visual language for feedback | Icon + text (+ optional action), theme tokens, rounded |
| Prefer reliable host | Snackbar on Activity; themed Toast only as Context fallback |
| No spam | First-run once; no interstitial every cold start |

## 3. Non-goals (v1)

- Full interactive coach-marks on every toolbar button  
- Rewriting accept-terms / subscription flows  
- Localizing all new strings into 40 languages in v1 (English source first; L10N follow-up)  
- Animating complex Lottie unless assets already exist  

---

## 4. Onboarding design

### 4.1 Entry / exit

```
SplashActivity
  → if !userPreferences.onboardingCompleted
       → OnboardingActivity
            → on Done/Skip: onboardingCompleted = true
            → MainActivity (+ original intent extras if any)
  → else MainActivity
```

- Prefer **new flag** `onboardingCompleted` (bool, default false) over overloading `versionCode` so version updates never re-force intro.
- Settings → About/General: **“Show introduction”** launches `OnboardingActivity` without clearing other prefs; does **not** reset `onboardingCompleted` unless we want “force” — **v1:** launching from settings does not require completion to use app; only first-run gates Splash.

### 4.2 Slides (product-focused)

| # | Title (intent) | Body (intent) | Visual |
|---|----------------|---------------|--------|
| 1 | Welcome to XHub | Fast browsing, home shortcuts, privacy controls | Logo / starfield-friendly illustration |
| 2 | Tabs your way | Multiple tabs; open links in background so you stay on the page | Tab strip / multi-window icon |
| 3 | Download videos | When a video is detected, use the download button; pick quality when offered | Download FAB motif |
| 4 | Stay private | Incognito + ad block keep tracking down; change anytime in Settings | Shield / private chip |
| 5 | You’re ready | Tip: long-press shortcuts for more actions; pull downloads from menu | Check / home |

**Controls:** page dots, **Skip** (top or bottom), **Next**, last page **Get started**.  
**Theme:** use semantic tokens (`appColor*`, night-aware), match home glass/dark language — not AppIntro default purple.

### 4.3 Implementation sketch

| Piece | Path |
|-------|------|
| Activity | `app/src/main/java/com/xhub/browser/activity/OnboardingActivity.kt` |
| Layout | `res/layout/activity_onboarding.xml` + `item_onboarding_page.xml` |
| Adapter | `ViewPager2` + `FragmentStateAdapter` or simple RecyclerView pages |
| Pref | `UserPreferences.onboardingCompleted` via boolPreference |
| Splash wire | `SplashActivity` branch before `MainActivity` |
| Settings | Preference intent or click → `OnboardingActivity` |
| Manifest | Register activity, portrait preferred, no history optional |

**Do not** re-add AppIntro module for v1 (submodule stays unused).

### 4.4 Copy principles

- Short titles (≤5 words), body ≤2 sentences  
- Name real UI: “download button”, “tabs”, “home shortcuts”  
- No legal wall of text on product slides  

---

## 5. Unified messages design

### 5.1 API

```kotlin
// Primary
XHubMessage.show(
  host: Activity,
  text: CharSequence,
  style: MessageStyle = MessageStyle.Info,
  duration: MessageDuration = MessageDuration.Short,
  actionLabel: CharSequence? = null,
  action: (() -> Unit)? = null
)

// Fallback when only Context is available (Application, background)
XHubMessage.showToast(context: Context, text: CharSequence, style: MessageStyle = MessageStyle.Info)
```

| Style | Use | Icon direction |
|-------|-----|----------------|
| Info | Neutral (“Link copied”) | info outline |
| Success | Completed download / export OK | check circle |
| Warning | SSL soft warning, max tabs | warning |
| Error | Failed download / blocked | error |

### 5.2 Visual

- Rounded container (theme radius token), padding 16dp  
- Icon 20–24dp start, text body medium, optional action button end  
- Colors: `colorSurface` / `colorOnSurface` / style-tinted icon via `colorPrimary` or semantic  
- Gravity: respect existing toolbar bottom preference (TOP when toolbars bottom, else BOTTOM) — same rule as `makeSnackbar`  
- Animation: fade (already used)  
- Max 2 lines text; ellipsize  

### 5.3 Migration (v1 call sites)

**Must migrate (high traffic):**

| Current | New style |
|---------|-----------|
| `shortcut_opened_in_background` Toast | Info |
| `message_link_copied` Toast | Success |
| `shortcut_longpress_hint` Toast | Info |
| Tab closed Snackbar (+ Undo if present) | Info + action |
| Max tabs Snackbar | Warning + action if any |
| Download snackbars | Success / Error |

**v1 batch 2 (optional same PR if small):** `Context.toast` text-zoom spam → Info short or remove if redundant; settings fragments later.

**Deprecate path:** keep `Activity.snackbar` / `Context.toast` as thin wrappers calling `XHubMessage` so remaining call sites pick up style without one giant rewrite.

### 5.4 Files

| Piece | Path |
|-------|------|
| API | `ui/message/XHubMessage.kt` + `MessageStyle.kt` |
| Layout | `res/layout/view_xhub_message.xml` |
| Snackbar custom view | inflate into Snackbar or use `Snackbar.SnackbarLayout` addView pattern |
| Tests | style mapping / gravity helper pure tests |

---

## 6. Architecture notes

- **No Hilt requirement** for pure UI helpers; Activity can call object methods.  
- Onboarding state in `UserPreferences` (SharedPreferences) — consistent with rest of app.  
- Avoid blocking first paint forever: intro only when flag false; Splash delay stays ~0.  
- Incognito launches: **skip intro** if intent is clearly external/incognito deep path? **v1:** show intro only from normal Splash → Main path when incomplete; external intents to `WebBrowserActivity` can bypass Splash — **acceptable** (rare first install via VIEW intent: still set flag false until they open launcher path once, or set completed on first MainActivity). **Decision:** set `onboardingCompleted` check only in `SplashActivity`; if user lands via VIEW first, show intro next cold start from launcher — OK.

---

## 7. Risks

| Risk | Mitigation |
|------|------------|
| Too many slides → skip | Cap at 5; Skip always visible |
| Custom Snackbar breaks a11y | Content description on icons; duration ≥ Snackbar.LENGTH_SHORT |
| Double messages during migration | Replace implementations of toast/snackbar wrappers, don’t stack |
| Theme mismatch light/dark | Resolve colors via theme attrs only |

---

## 8. Verification plan

1. Fresh install (clear data): Splash → Onboarding → Main  
2. Skip and Done both set flag; second launch goes Main  
3. Settings reopens intro  
4. Link copy / background tab / tab close show new banner  
5. `assembleXhubFullDownloadDebug` + unit tests for prefs/message helpers  

---

## 9. Implementation order (after design approval)

1. Message system (wrappers) — immediate visual win, low risk  
2. Migrate high-traffic toasts  
3. Onboarding UI + Splash gate  
4. Settings re-entry  
5. Polish copy / icons  

---

## 10. Open for user approval

Please confirm or amend:

1. **Slide list** (section 4.2) — add/remove any topics?  
2. **Message styles** (section 5.1) — OK?  
3. **Order** messages first then onboarding (section 9) — OK?  
4. **Skip AppIntro module** for v1 — OK?  

After approval → writing-plans skill → implementation plan with bite-sized tasks + TDD.
