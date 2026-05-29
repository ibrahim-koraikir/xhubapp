---
name: android-rendering-perf
description: "You MUST use this skill when optimizing drawing routines, handling animations, or working with layouts and views to ensure the app maintains a silky-smooth 60/120 FPS and avoids memory thrashing."
---

Enforces strict Android rendering optimization, GPU usage guidelines, layout flatting, and memory-conscious custom drawing passes.

## Core Checklist

You MUST verify each of these items for any layout modification, custom view implementation, or graphics drawing pass:

1. **Avoid Layout Thrashing & Nested Layouts**
   - Keep layout hierarchies as flat as possible. Prefer `ConstraintLayout` over heavily nested `LinearLayout`s or `FrameLayout`s.
   - Avoid nesting elements with `layout_weight` inside nested linear layouts, as this forces the system to perform multiple measurement and layout passes per frame.

2. **Garbage-Free Drawing Passes**
   - Never instantiate new objects (e.g., `Paint`, `Rect`, `Path`, `Bitmap`, `Canvas`, or temporary Strings) inside custom view `onDraw` methods.
   - All drawing-related objects MUST be pre-allocated inside the view's constructor or initialization methods and re-used.

3. **Overdraw Prevention**
   - Ensure the GPU does not waste time drawing pixels that are immediately covered by other elements.
   - Avoid specifying unnecessary `android:background` attributes on root layouts if child views will cover them completely.

4. **Efficient View Snapshotting & Rendering**
   - When snapshotting or capturing views (like web pages or thumbnails), draw directly to target-sized software `Canvas` bitmaps to bypass heavy hardware cache allocations and prevent OOMs (Out Of Memory errors).
   - Clean up and recycle bitmaps using `bitmap.recycle()` or central cache eviction policies when they are no longer in memory.

## Verification & Guardrails

- Run code inspection for custom views and look for `new` allocations or local instantiation inside `onDraw` or `onLayout`.
- Verify CPU and memory footprint during UI transitions or tab switcher openings to ensure no memory leakage.
