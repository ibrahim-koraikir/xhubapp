# Release Checklist — XHub Browser

Review and tick off every item below **before** distributing a new APK.
This is a human-readable checklist; `app/build.gradle` already enforces the
signing-config requirement at build time (see item 3) as a hard fail.

Last released build (fill in before each release):

| Field                  | Value |
|------------------------|-------|
| Previously released `versionCode` | 260 |
| Previously released `versionName` | 2.0.9 |
| This release `versionCode` | _(must be higher)_ |
| This release `versionName` | _(must be updated to match)_ |

---

## 1. Version bump

- [ ] `slions-version-code` in `gradle/libs.versions.toml` is **strictly higher** than the
      previously released `versionCode`. Android uses this to detect upgrades/downgrades —
      reusing or lowering it breaks updates.
- [ ] `slions-version-name` in `gradle/libs.versions.toml` is updated to a matching,
      human-readable value (e.g. `2.1.0`).
- [ ] `versionCode` and `versionName` are consistent with each other and with the
      changelog/release notes.

      ```toml
      # gradle/libs.versions.toml
      slions-version-code = "261"   # <-- bumped
      slions-version-name = "2.1.0" # <-- bumped
      ```

      These are consumed in `app/build.gradle` by the `xhub` product flavor.

## 2. Signing configuration

- [ ] `keystore.properties` exists at the project root **and** contains valid, non-empty
      values for `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, **or**
      the `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` /
      `RELEASE_KEY_PASSWORD` environment variables are set in the build shell.
- [ ] `keystore.properties.template` matches the keys you actually use (no drift).
- [ ] The release keystore is backed up securely off-machine. If it is lost, future
      updates cannot ship under the same package id.
- [ ] Confirmed that `app/build.gradle` now **fails the build loudly** when a release
      task is requested without a signing config (it throws a `GradleException`).
      A clean `gradlew assembleSlionsFullDownloadRelease` must end in
      `BUILD SUCCESSFUL`, not an unsigned APK.

      ```powershell
      .\gradlew.bat clean assembleSlionsFullDownloadRelease
      ```

## 3. Privacy / legal URLs are live

- [ ] `https://xub.site/privacy` loads in a real browser and contains the XHub privacy
      policy. It must cover: browsing history storage, crash log writing, ad impressions
      served via ExoClick, and device/location access.
- [ ] `https://xub.site/terms` loads in a real browser and contains the terms.
- [ ] No remaining `TODO` comments next to `url_privacy_policy` /
      `url_terms_and_conditions` in `app/src/main/res/values/donottranslate.xml`
      (these were removed; do not re-add them).

      ```bash
      curl -I https://xub.site/privacy   # expect HTTP 200
      curl -I https://xub.site/terms     # expect HTTP 200
      ```

## 3b. In-app ads gating (ExoClick)

ExoClick interstitials are controlled per flavor via `buildConfigField` in `app/build.gradle`:

- [ ] `ADS_ENABLED` is **`true` only for the `download` channel**. It must be `false` for
      `playstore` (Google Play forbids this network's adult creatives) and `fdroid`
      (F-Droid prohibits proprietary ad/tracking networks). Verify:

      ```bash
      grep -n "ADS_ENABLED" app/build.gradle
      ```

- [ ] `EXOCLICK_ZONE_ID` and `EXOCLICK_PROVIDER_URL` are populated **only** for the
      `download` flavor and are empty strings for `playstore` / `fdroid`.
      `ExoClickInterstitial` reads these from `BuildConfig` and no-ops when they are empty.
- [ ] `WebBrowserActivity.showInAppAds()` additionally gates on:
      `BuildConfig.ADS_ENABLED`, `Sponsorship.TIN` tier only, not incognito, not first-run
      (`versionCode == 0`), not an external `ACTION_VIEW` launch, a one-time personalized-ad
      consent prompt, and per-day / minimum-interval frequency capping (persisted in
      `UserPreferences`).
- [ ] The personalized-ad consent dialog shows before the first ad and its choice is
      persisted (`adsConsentAsked` / `adsConsentGranted`).
- [ ] The privacy policy (`https://xub.site/privacy`) discloses ExoClick ad impressions
      (see section 3).

## 4. Clean release build

- [ ] Kill any running Java/Gradle daemons to avoid file locks:
      `taskkill /F /IM java.exe`
- [ ] Clean + assemble the release variant you intend to ship:
      ```powershell
      .\gradlew.bat clean assembleSlionsFullDownloadRelease
      ```
- [ ] Output ends in `BUILD SUCCESSFUL`. The signed APK is emitted under
      `app/build/outputs/apk/slionsFullDownload/release/`.
- [ ] If the build produced an *unsigned* APK or only logged a warning, **stop** — the
      GradleException guard did not fire as expected and the signing setup needs fixing.

## 5. Sanity verification on the produced APK

- [ ] Confirm `android:extractNativeLibs="false"` and 16KB page alignment landed in the
      packaged manifest / native libs:
      ```powershell
      aapt dump badging path\to\XHub-vX.Y.Z.apk | findstr /I "extractNativeLibs application-label"
      ```
- [ ] Install the APK on a clean device (or emulator), confirm it launches and the
      version in Settings → About matches the bumped `versionName`.

## 6. Pre-distribution hygiene

- [ ] Root `.md` notes that expose internal dev history are gitignored or pruned
      (see `.gitignore` `*.md` rule with `README.md` / `CHANGELOG.md` allowlist).
- [ ] `SETUP_RELEASE_SIGNING.bat` / `REBUILD.bat` contain no hardcoded keystore paths
      or passwords (verify before any public repo share).
- [ ] No secrets, API keys, or signing credentials are committed. Run:
      ```bash
      git log --all -p -- keystore.properties | head
      ```
      If anything appears, rotate it.
