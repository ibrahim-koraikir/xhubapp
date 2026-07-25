# Toolbar Implementation Guide

## Design Source

The toolbar design is based on the Banani HTML/CSS export with the following specifications:

### Original Design (from HTML/CSS)

```css
#bottom-toolbar-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  gap: 8px;
  padding: 0 8px 4px;
}

.toolbar-action {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--foreground); /* #f3f3f3 */
}

.toolbar-icon {
  width: 28px;
  height: 28px;
}
```

### Android Implementation

The design has been converted to Android XML with the following mappings:

| CSS Property | Android Equivalent |
|-------------|-------------------|
| `display: grid` | `LinearLayout` with `weightSum="5"` |
| `grid-template-columns: repeat(5, 1fr)` | Each child has `layout_weight="1"` |
| `gap: 8px` | Implicit spacing via equal weight distribution |
| `padding: 0 8px 4px` | `paddingStart="8dp"` `paddingEnd="8dp"` `paddingBottom="4dp"` |
| `height: 44px` | `layout_height="44dp"` |
| `color: var(--foreground)` | `app:tint="@color/bottom_toolbar_text"` |

## Button Mapping

| Position | Design Icon | Android Icon | Action | ID |
|----------|------------|--------------|--------|-----|
| 1 | `lucide:arrow-left` | `ic_arrow_left.xml` | Navigate back | `button_action_back` |
| 2 | `lucide:arrow-right` | `ic_action_forward.xml` | Navigate forward | `button_action_forward` |
| 3 | `lucide:square-stack` | TabCountView | Show tabs | `tabs_button` |
| 4 | `lucide:copy` | `ic_content_copy.xml` | Copy URL | `button_copy_url` |
| 5 | `lucide:plus` | `ic_action_plus.xml` | New tab | `button_new_tab` |

## Color Variables

| Design Variable | Value | Android Color |
|----------------|-------|---------------|
| `--background` | #0b0b0b | `tab_grid_background` |
| `--foreground` | #f3f3f3 | `bottom_toolbar_text` |
| `--muted-foreground` | #9a9a9a | `bottom_toolbar_hint` |
| `--primary` | #26C6DA | `bottom_toolbar_primary` |
| `--primary-foreground` | #111111 | `bottom_toolbar_primary_foreground` |
| `--accent` | #232323 | `bottom_toolbar_accent` |

## Layout Structure

```
LinearLayout (root, vertical)
├── LinearLayout (assistant pill)
│   ├── ImageView (comet logo)
│   ├── include (search bar)
│   ├── ImageButton (mic)
│   └── FrameLayout (audio button)
└── LinearLayout (navigation row, horizontal, weightSum=5)
    ├── FrameLayout (weight=1)
    │   └── ImageButton (back)
    ├── FrameLayout (weight=1)
    │   └── ImageButton (forward)
    ├── FrameLayout (weight=1)
    │   └── TabCountView (tabs)
    ├── FrameLayout (weight=1)
    │   └── ImageButton (copy)
    └── FrameLayout (weight=1)
        └── ImageButton (new tab)
```

## Key Design Decisions

1. **FrameLayout Wrappers**: Each button is wrapped in a FrameLayout to ensure proper centering and spacing within the grid system.

2. **Equal Weight Distribution**: Using `weightSum="5"` and `layout_weight="1"` ensures each button gets exactly 20% of the available width.

3. **Consistent Sizing**: All buttons maintain 44dp height for comfortable touch targets (Material Design minimum is 48dp, but 44dp is acceptable for toolbar buttons).

4. **Icon Tinting**: All icons use `app:tint` instead of hardcoded colors for theme compatibility.

5. **Accessibility**: Each button has proper `contentDescription` and `tooltipText` for screen readers.

## Responsive Behavior

The toolbar adapts to different screen sizes:
- On narrow screens, buttons may be hidden based on `setupToolBar()` logic
- The threshold is calculated as 10× button width
- Back/Forward buttons are hidden first if space is limited

## Integration Points

### WebBrowserActivity.kt

Button click handlers are set up in `createToolbar()`:

```kotlin
iBindingToolbarContent.buttonActionBack.setOnClickListener { 
    executeAction(R.id.action_back) 
}
iBindingToolbarContent.buttonActionForward.setOnClickListener { 
    executeAction(R.id.action_forward) 
}
iBindingToolbarContent.tabsButton.setOnClickListener(this)
iBindingToolbarContent.buttonCopyUrl?.setOnClickListener { 
    executeAction(R.id.action_copy) 
}
iBindingToolbarContent.buttonNewTab?.setOnClickListener { 
    executeAction(R.id.action_new_tab) 
}
```

### Action Handlers

All actions are implemented in `executeAction()`:
- `R.id.action_back`: Navigate to previous page
- `R.id.action_forward`: Navigate to next page
- `R.id.action_copy`: Copy URL to clipboard with toast
- `R.id.action_new_tab`: Create new tab (respects incognito mode)

## Testing Checklist

- [ ] All 5 buttons are visible on standard screen sizes
- [ ] Back button navigates to previous page
- [ ] Forward button navigates to next page
- [ ] Tabs button opens tabs view or menu (based on settings)
- [ ] Copy button copies URL and shows toast
- [ ] New tab button creates appropriate tab type
- [ ] Long press on back/forward shows page history
- [ ] Icons are properly tinted
- [ ] Touch targets are comfortable (44dp height)
- [ ] Buttons are evenly spaced
- [ ] Design matches Banani mockup

## Future Enhancements

Potential improvements to consider:
1. Add ripple effects for better touch feedback
2. Implement icon animations on state changes
3. Add haptic feedback on button press
4. Support for custom icon sets
5. Theme-aware icon variants (light/dark)
