# Firefox-Style Tab Grid Design Spec

## Goal
Replace the existing tab switcher with a full-screen, 2-column grid layout similar to Firefox Android, and implement a reliable "snapshot-on-leave" thumbnail mechanism to fix broken image previews.

## Architecture

### 1. Reliable Snapshot Capture Logic
- **Current Issue:** Previews fail because they try to capture WebViews while they are in the background or not fully rendered.
- **Solution:** Capture a snapshot of the active page *synchronously* at the exact moment the user presses the "Tab Switcher" button or switches away from the tab.
- **Storage:** Save scaled-down bitmaps (to prevent OutOfMemory errors) into a memory cache linked to each tab's ID. 

### 2. Tab Switcher Overlay (`layout/fragment_tab_grid.xml`)
- A full-screen view/fragment that overlays the browser when the tab button is pressed.
- Contains a `RecyclerView` configured with a `GridLayoutManager(2)` to create the 2-column grid.
- A bottom app bar with a prominent "+" (New Tab) button and standard tab management options.

### 3. Tab Grid Item (`layout/item_tab_grid_card.xml`)
- A `MaterialCardView` to give each tab a distinct, modern card shape with rounded corners and slight elevation.
- **Header Section:** Displays the website's Favicon, the Page Title (ellipsized), and a clickable Close (X) button.
- **Preview Section:** A large `ImageView` filling the rest of the card, displaying the snapshot captured when the user left the tab.

## Trade-offs & Considerations
- **Memory vs. Disk:** The initial implementation will keep scaled-down thumbnails in a memory cache to ensure fast scrolling. If memory usage becomes an issue for power users with 100+ tabs, we will add a disk-caching layer later.
- **Animations:** We will start with a clean slide-up/fade transition. The complex "expanding card" transition seen in Firefox is out of scope for this initial implementation to ensure stability.
