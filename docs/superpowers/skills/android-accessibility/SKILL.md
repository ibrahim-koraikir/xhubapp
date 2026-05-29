---
name: android-accessibility
description: "You MUST use this skill when creating or modifying interactive views, components, or screens in this project to ensure strict WCAG compliance and optimal Android screen-reader compatibility."
---

Enforces strict Web Content Accessibility Guidelines (WCAG) and Android Accessibility Standards in the Fulguris codebase.

## Core Checklist

You MUST verify each of these items for all interactive elements in your UI modifications:

1. **Adequate Touch Target Size**
   - Every clickable, touchable, or focusable element (e.g., toolbar buttons, list items, search inputs) MUST have a minimum physical size of `48dp x 48dp`.
   - If the visible component size is smaller (e.g., a `24dp` icon), you must add padding or wrap it in a larger container to ensure the touch hit area is at least `48dp`.

2. **Descriptive Content Labels**
   - Every `ImageView`, `ImageButton`, or other non-text interactive control MUST have an explicit `android:contentDescription` attribute providing an accurate, localized description.
   - For purely decorative elements, explicitly set `android:contentDescription="@null"` and set `android:importantForAccessibility="no"` to prevent screen readers from announcing them.

3. **Text Scaling Compatibility**
   - Never use `dp` for `android:textSize`.
   - All text sizes MUST be declared in `sp` (scalable pixels) to respect the user's system font size preference.
   - Test layout scaling when system font is set to "Large" or "Extra Large" to ensure no text wraps into invisibility or overflows its container.

4. **Logical Focus Order & Labeling**
   - Ensure a logical focus traversal path (`android:nextFocusDown`, `android:nextFocusForward`, etc.) for users navigating with external keyboards or d-pads.
   - Ensure all input fields are properly linked to their corresponding labels or provide a descriptive hint.

## Verification & Guardrails

- Scan layout XML changes for any `ImageView` or `ImageButton` lacking a `contentDescription`.
- Verify that touchable icons have `padding` or dimension overrides ensuring a `48dp` target size.
