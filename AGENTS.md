# Repository Guidelines

## Project Structure & Module Organization

**XHub** is an Android WebView browser (rebrand of Fulguris). Package namespace: `com.xhub.browser`.

- `app/src/main/java/com/xhub/browser/` — app code: `activity/` (esp. `WebBrowserActivity`), `browser/` (tabs/sessions), `view/` (WebView clients/tabs), `adblock/`, `download/` (incl. yt-dlp), `settings/`, `database/`, `di/` (Hilt)
- `app/src/main/res/` — layouts, strings (~40 locales), themes/tokens
- `app/src/test/java/com/xhub/browser/` — JVM unit tests (Robolectric); no `androidTest` suite yet
- `app/src/{download,playstore,fdroid}/` — flavor-specific sources
- `subs/Preference/` — Preference library (Gradle module); `subs/l10n/` — translation CLI
- `docs/superpowers/` — agent skills, specs, and implementation plans
- `docs/archive/` — historical session notes and crash logs (not day-to-day docs)

Architecture is View-based (not Compose): a large `WebBrowserActivity` orchestrates tabs, home, and chrome; `TabsManager` owns tab lifecycle; `WebPageTab` wraps each WebView.

## Build, Test, and Development Commands

```powershell
# Avoid Windows file-lock errors before heavy builds
taskkill /F /IM java.exe
timeout /t 3

# Debug APK (primary local flavor)
.\gradlew.bat assembleXhubFullDownloadDebug

# Clean rebuild
.\gradlew.bat clean assembleXhubFullDownloadDebug

# All unit tests for that flavor
.\gradlew.bat testXhubFullDownloadDebugUnitTest

# Single test class
.\gradlew.bat testXhubFullDownloadDebugUnitTest --tests "com.xhub.browser.view.HomeThemeAdaptiveColorTest"
```

Product flavors (dimensions BRAND × VERSION × PUBLISHER): `xhubFullDownload` (sideload, ads on), `xhubFullPlaystore` (Play, tab entitlement), `xhubFullFdroid` (no ads/telemetry). Stack: Kotlin 2.2.10, AGP 8.13.1, Hilt 2.57.1, KSP, compileSdk 35, minSdk 21.

## Coding Style & Naming Conventions

- Kotlin/Java 17; XML layouts + Material components; semantic theme attrs in `values/` (prefer tokens over hard-coded hex)
- Package: `com.xhub.browser.*`; activities/fragments/settings in their packages
- DI via Hilt (`@HiltAndroidApp`, modules in `di/`); logging via Timber
- Async: RxJava schedulers remain the dominant model (coroutines not the default yet)
- L10N: edit English `strings.xml` first; use `python subs\l10n\android\strings.py` (see `L10N.md` / `.github/copilot-instructions.md`). Placeholder mismatches crash at runtime.

## Testing Guidelines

JUnit 4 + Robolectric + Mockito; coverage enabled on debug. Prefer unit tests under `app/src/test/`. Theme/token regressions use adaptive color tests (`HomeThemeAdaptiveColorTest`, etc.). No instrumented UI tests in-tree yet.

## Commit & Pull Request Guidelines

Recent history uses Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:` with optional scopes (`theme`, `tabs`, `home`, `chrome`, `motion`). Keep commits focused. No PR template; describe flavor impact and risk for WebView/tab/download changes.

## Agent Skills (Superpowers — MANDATORY)

Skills live in `docs/superpowers/skills/`. Read the matching `SKILL.md` before acting.

| Skill | When |
|-------|------|
| **brainstorming** | New feature / UI / behavior — design + user approval before code |
| **writing-plans** | After design approval, multi-step work → `docs/superpowers/plans/` |
| **test-driven-development** | Features/fixes — failing test first |
| **systematic-debugging** | Bugs/crashes — root cause before fix |
| **verification-before-completion** | No success claims without fresh build/test evidence |
| **finishing-a-development-branch** | Integrate/wrap up — verify build, present merge/PR/keep/discard |

Key gates: YAGNI; one clarifying question at a time; no production code without a failing test; no fix without root-cause investigation; prove claims with `assembleXhubFullDownloadDebug` / unit tests.
