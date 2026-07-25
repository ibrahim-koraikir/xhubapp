# Toolbar Visual Design Guide

## Complete Bottom Toolbar Layout

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  ┌──────────────────────────────────────────────────┐    │
│  │  🌟  Ask assistant or navigate...    🎤  [🔊]   │    │ ← Assistant Pill
│  └──────────────────────────────────────────────────┘    │   (58dp height)
│                                                            │
│                    ↕ 18dp gap                              │
│                                                            │
│  ┌──────┬──────┬──────┬──────┬──────┐                    │
│  │  ←   │  →   │  ⊞   │  ⎘   │  +   │                    │ ← Navigation Row
│  │ Back │ Fwd  │ Tabs │ Copy │ New  │                    │   (44dp height)
│  └──────┴──────┴──────┴──────┴──────┘                    │
│  ↕ 4dp                                                     │
└────────────────────────────────────────────────────────────┘
```

## Detailed Measurements

### Assistant Pill
```
┌─────────────────────────────────────────────────────────┐
│ 18dp │ 🌟 │ 12dp │ [Search Bar] │ 14dp │ 🎤 │ 14dp │ [🔊] │ 8dp │
│      │24dp│      │   (flex)     │      │22dp│      │ 46dp │     │
└─────────────────────────────────────────────────────────┘
Height: 58dp
Border radius: 29dp (fully rounded)
Background: #232323 (accent)
```

### Navigation Row
```
┌─────────────────────────────────────────────────────────┐
│ 8dp │ [←] │ [→] │ [⊞] │ [⎘] │ [+] │ 8dp │
│     │ 20% │ 20% │ 20% │ 20% │ 20% │     │
│     │44dp │44dp │44dp │44dp │44dp │     │
└─────────────────────────────────────────────────────────┘
Height: 44dp
Each button: 20% width (equal distribution)
Padding: 8dp horizontal, 4dp bottom
```

## Color Specifications

### Dark Theme (Default)
```
Background:     #0b0b0b  ████████  (Very dark gray)
Foreground:     #f3f3f3  ████████  (Light gray)
Muted:          #9a9a9a  ████████  (Medium gray)
Primary:        #26C6DA  ████████  (Comet Teal)
Primary FG:     #111111  ████████  (Almost black)
Accent:         #232323  ████████  (Dark gray)
Input:          #171717  ████████  (Very dark gray)
```

### Button States
```
Normal:    Icon color #f3f3f3 (foreground)
Pressed:   Ripple effect (selectableItemBackgroundBorderless)
Disabled:  Icon color #9a9a9a (muted) + reduced opacity
```

## Icon Specifications

### Icon Sizes
- Navigation icons: 28dp × 28dp (visual size)
- Container: 44dp × 44dp (touch target)
- Padding: 8dp (except new tab: 7dp)

### Icon Mapping
```
Position 1: ic_arrow_left.xml        (←)
Position 2: ic_action_forward.xml    (→)
Position 3: TabCountView             (⊞ with number)
Position 4: ic_content_copy.xml      (⎘)
Position 5: ic_action_plus.xml       (+)
```

## Typography

### Assistant Pill Text
```
Font: Geist (system default fallback)
Size: 15sp
Weight: 500 (Medium)
Color: #9a9a9a (muted foreground)
```

## Spacing System

### Vertical Spacing
```
Top of screen
    ↓
[Other content]
    ↓
Assistant Pill (58dp)
    ↓ 18dp gap
Navigation Row (44dp)
    ↓ 4dp padding
Bottom of screen
```

### Horizontal Spacing
```
Screen edge
    → 16dp (assistant pill margin)
    → 8dp (navigation row padding)
    → Buttons (equal width distribution)
    → 8dp (navigation row padding)
Screen edge
```

## Touch Targets

All buttons meet accessibility guidelines:
- Minimum: 44dp × 44dp ✓
- Recommended: 48dp × 48dp (44dp is acceptable for toolbar)
- Spacing: Adequate (equal distribution prevents accidental taps)

## Responsive Behavior

### Standard Screens (≥ 360dp width)
- All 5 buttons visible
- Equal spacing
- Full functionality

### Narrow Screens (< 360dp width)
- Buttons may be hidden based on available space
- Priority order (kept visible):
  1. Tabs
  2. New Tab
  3. Copy
  4. Forward
  5. Back

### Wide Screens (≥ 600dp width)
- All buttons visible
- More generous spacing
- Potential for additional buttons

## Animation & Feedback

### Touch Feedback
- Ripple effect on press (Material Design)
- Color: Semi-transparent white (#22FFFFFF)
- Duration: 300ms

### State Changes
- Tab count updates immediately
- Icon tint changes for disabled state
- Smooth transitions (no animation currently)

## Accessibility

### Screen Reader Support
```
Back button:     "Navigate back"
Forward button:  "Navigate forward"
Tabs button:     "Tabs" + count
Copy button:     "Copy URL"
New Tab button:  "New tab"
```

### Tooltips (Long Press)
- Enabled for all buttons
- Shows action description
- Follows Material Design guidelines

## Implementation Notes

### XML Structure
```xml
<LinearLayout orientation="vertical">
    <!-- Assistant Pill -->
    <LinearLayout height="58dp" />
    
    <!-- Navigation Row -->
    <LinearLayout 
        orientation="horizontal"
        weightSum="5">
        <FrameLayout weight="1">
            <ImageButton />
        </FrameLayout>
        <!-- Repeat 4 more times -->
    </LinearLayout>
</LinearLayout>
```

### Key Attributes
- `weightSum="5"`: Ensures equal distribution
- `layout_weight="1"`: Each button gets 20%
- `FrameLayout`: Proper centering within grid cell
- `selectableItemBackgroundBorderless`: Material ripple

## Design Principles

1. **Consistency**: All buttons same size and spacing
2. **Clarity**: Clear, recognizable icons
3. **Accessibility**: Proper touch targets and labels
4. **Simplicity**: Clean, uncluttered layout
5. **Functionality**: Most-used actions readily available

## Comparison with Design

| Aspect | Banani Design | Android Implementation | Match |
|--------|---------------|----------------------|-------|
| Layout | CSS Grid (5 columns) | LinearLayout (weightSum=5) | ✓ |
| Height | 44px | 44dp | ✓ |
| Spacing | 8px padding | 8dp padding | ✓ |
| Colors | CSS variables | Android colors | ✓ |
| Icons | Lucide icons | Material icons | ✓ |
| Gaps | 8px | Implicit (equal weights) | ✓ |

## Future Enhancements

Potential improvements:
1. Icon animations on state change
2. Haptic feedback on press
3. Customizable button order
4. Theme variants (light mode)
5. Icon size preferences
6. Badge notifications on tabs button
