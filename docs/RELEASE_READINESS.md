# XHub release readiness (Phase 3)

Last verified: 2026-07-12 (automated checks only).

## P0 — Blockers before public store / wide sideload

| Item | Status | Action for you |
|------|--------|----------------|
| Update check / third-party API keys | **Done** — no `slions_*` code paths; leave disabled | Optional: build your own endpoint later |
| Discord / sponsors / Crowdin prefs | **Hidden** until real URLs live | Unhide after URLs return HTTP 200 |
| Contact email | **Hidden** (`mailto:contact@example.com`) | Set real `url_contact_us` in `strings.xml` |
| Privacy policy URL | **Placeholder** `https://xub.site/privacy` | Host policy + update `donottranslate.xml` |
| Terms URL | **Placeholder** `https://xub.site/terms` | Host terms + update `donottranslate.xml` |
| CPAL license GitHub link | `REPLACE_WITH_YOUR_GITHUB_USERNAME` | Set real org/repo in `preference_about.xml` |
| Release signing | Template only (`keystore.properties.template`) | Copy → `keystore.properties`, never commit |
| Device smoke (tabs / popups) | **Manual** | See checklist below |

### URL slots (paste real values, then re-enable prefs)

```xml
<!-- app/src/main/res/values/donottranslate.xml -->
url_privacy_policy
url_terms_and_conditions
url_discord
url_github_sponsors
url_crowdin_project
url_app_home_page
```

```xml
<!-- app/src/main/res/values/strings.xml -->
url_contact_us  <!-- mailto:you@yourdomain.com -->
```

## P1 — Stability (in code)

| Item | Status |
|------|--------|
| Frozen-tab `webPageClient` guards | Done (Phase 1) |
| Popup return policy + tests | Done (`PopupWindowPolicy`) |
| Tab map / entitlement tests | Done |
| Download progress bus tests | Done |
| Field crash reporting | **Timber-only** (Firebase removed). Opt-in/self-host later if needed |

## P2 — Product (download hub)

`DownloadsFragment` already supports:

- System DownloadManager items + yt-dlp items  
- Live progress / cancel via `DownloadProgressBus`  
- Open / share / remove / orphan cleanup  

**No large download-hub rewrite required.** Next product work is polish only (retry UX, empty states) after release blockers.

## Manual device smoke (before release)

1. Open / close tabs rapidly during cold start  
2. Swipe-dismiss a brand-new tab  
3. `target=_blank` with popups ON and OFF  
4. Hit max tabs on Play flavor (TIN = 20) then trigger a popup  
5. Download a file + a video; open/share from Downloads  
6. Settings → About: only working links should be visible  

## Build commands

```powershell
.\gradlew.bat testXhubFullDownloadDebugUnitTest
.\gradlew.bat assembleXhubFullDownloadDebug
# Release (after keystore.properties exists):
.\gradlew.bat assembleXhubFullDownloadRelease
```

See also: `TODO_XHUB_INFRASTRUCTURE.md`, `RELEASE_SIGNING_SETUP.md`, `RELEASE_CHECKLIST.md`.
