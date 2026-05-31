# Home Screen UI Audit Refactor — Design Spec

Date: 2026-05-29
Skill Gates: android-m3-baseline, android-accessibility, android-rendering-perf

## Goal
Incrementally refactor `layout_home_screen.xml` using the newly installed UI skills to resolve all
audit findings from Section 1 (AppBar Header), Section 2 (Pinned Toolbar & Buttons), and Section 3
(Scrollable Shortcuts Area). No view IDs are changed. No Kotlin code changes required.

---

## Section 1: AppBar & Expanded Header

### Spacing (8dp Grid)
- `android:layout_marginEnd` on logo FrameLayout: `12dp` → `16dp`
- `android:layout_marginTop` on homeGreeting: `4dp` → `8dp`

### Colors (M3 Adaptive Tokens)
- homeTitle textColor: `@color/home_foreground` → `?attr/colorOnBackground`
- homeGreeting textColor: `@color/home_muted_foreground` → `?attr/colorOnSurfaceVariant`

### Typography (M3 System)
- homeTitle: add `style="@style/TextAppearance.Material3.HeadlineLarge"` (keep textSize=36sp and fontFamily)
- homeGreeting: add `style="@style/TextAppearance.Material3.BodyLarge"` (remove hardcoded textSize/fontFamily)

### Accessibility
- Logo ImageView: add `android:contentDescription="@null"` and `android:importantForAccessibility="no"`

---

## Section 2: Pinned Toolbar Buttons

### Touch Targets (WCAG 48dp minimum)
- `homeSettingsBtnContainer` & `homeProfileButton`: `40dp x 40dp` → `48dp x 48dp`
- `homeSettingsBtn` inner padding: `8dp` → `12dp`

### Colors (M3 Adaptive)
- homeSettingsBtn tint: `@color/home_foreground` → `?attr/colorOnSurface`

### Accessibility
- homeProfileImage: add `android:contentDescription="User profile and settings"`

---

## Section 3: Scrollable Shortcuts Content

### Performance (Flatten Layout)
- Replace nested 3-level LinearLayout header row with a flat `ConstraintLayout`

### Touch Targets (WCAG)
- `btnEditShortcuts` height: `34dp` → `48dp`
- `btnEditShortcuts`: add `android:contentDescription="Edit website shortcuts"`

### Spacing (8dp Grid)
- Edit icon margin start: `6dp` → `8dp`
- `shortcutsDynamicContainer` top margin: `20dp` → `24dp`

### Typography (M3 System)
- Shortcuts title: remove hardcoded textSize/style → `style="@style/TextAppearance.Material3.TitleMedium"`, color → `?attr/colorOnBackground`
- Shortcuts subtitle: remove hardcoded textSize → `style="@style/TextAppearance.Material3.BodySmall"`, color → `?attr/colorOnSurfaceVariant`
- Edit button label: remove hardcoded textSize/style → `style="@style/TextAppearance.Material3.LabelMedium"`, color → `?attr/colorOnSurface`

---

## Verification
- Visual inspect in both Light and Dark theme
- Compile: `.\gradlew.bat assembleSlionsFullDownloadDebug` → must be BUILD SUCCESSFUL
