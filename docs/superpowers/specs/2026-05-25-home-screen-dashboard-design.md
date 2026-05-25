# Premium Dashboard Grid - Home Screen Design Spec

## 1. Overview
The goal is to transform the `layout_home_screen.xml` from a plain, static scroll view into a dynamic, premium dashboard utilizing Material 3 components, `CoordinatorLayout`, and widget-style shortcut tiles.

## 2. Architecture & Layout Structure

### 2.1 Root Layout (`CoordinatorLayout`)
The entire home screen will be wrapped in a `androidx.coordinatorlayout.widget.CoordinatorLayout`. This allows the header to react to the scroll position of the shortcuts below it.

### 2.2 Collapsing Header (`AppBarLayout` + `CollapsingToolbarLayout`)
- **Behavior:** The header will start large and expanded, showing a massive greeting and user avatar. As the user scrolls down through their shortcuts, it will smoothly collapse into a minimal top bar.
- **Expanded State:** 
  - Large, bold gradient typography for the greeting (e.g., "Good morning").
  - Date and time display.
  - Profile Avatar positioned elegantly on the right or center.
- **Collapsed State:** 
  - The greeting shrinks to a standard 18sp title in the toolbar.
  - The settings and profile buttons remain accessible in the collapsed toolbar.

### 2.3 Dashboard Grid (`NestedScrollView`)
- **Container:** The existing `ScrollView` will be upgraded to a `androidx.core.widget.NestedScrollView` with `app:layout_behavior="@string/appbar_scrolling_view_behavior"`. This links its scroll state to the `CollapsingToolbarLayout`.
- **Dynamic Content:** The "Website Shortcuts" section will live inside this scrollable area.

## 3. Tile Aesthetics (Widget Style)
Instead of tiny 64dp icons, we will restructure how `WebBrowserActivity` inflates shortcut tiles:
- **Component:** We will use `com.google.android.material.card.MaterialCardView` for each shortcut.
- **Layout:** The grid will be changed from 4 columns to **3 columns** to allow for larger, more premium "widget-style" tiles.
- **Styling:** 
  - 16dp rounded corners (`app:cardCornerRadius="16dp"`).
  - Subtle elevation (`app:cardElevation="2dp"`) with a solid, slightly contrasting surface color.
  - The favicon will be centered and larger.
  - The site title will be bolded and placed neatly at the bottom of the card with proper padding.

## 4. Color Palette & Typography
- **Colors:** We will rely heavily on `?attr/colorSurface`, `?attr/colorPrimary`, and `?attr/colorOnSurface` from the existing `Theme.Material3` setup to ensure it looks perfectly native in both Light and Dark modes.
- **Typography:** We will leverage `sans-serif-medium` and `sans-serif-black` for headers to give it that punchy, modern Apple/Google aesthetic.
