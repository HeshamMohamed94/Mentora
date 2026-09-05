# Mentora Design System — Component Specifications

Every value below is a **semantic** token reference (layer 2 of [`DESIGN_SYSTEM.md § 0.1 Token Architecture`](./DESIGN_SYSTEM.md)) into [`design-tokens.json`](./design-tokens.json). No raw hex/px, and no arbitrary/undocumented opacity percentage, appears here — every modifier used (`@ state.hoverOpacity`, `@ state.disabledContainerOpacity`, etc.) is itself a token. If a screen needs a variant not listed, propose a token/component addition per [`DESIGN_RULES.md`](./DESIGN_RULES.md) — do not improvise inline.

Legend: **h** = height, **radius** = corner radius token, **type** = typography token. Colors resolve automatically per active theme — a component table never branches on "if dark mode, use X" (see [`DESIGN_RULES.md` rule 10 / § Locked Decisions](./DESIGN_RULES.md)).

**Component token layer:** the tables below name the semantic token a component uses (e.g. PrimaryButton's default background is `color.brand.primary`). Each of these has a corresponding **component-layer alias** in `design-tokens.json → component.*` (layer 3) — e.g. `component.button.primary.background.default → {color.brand.primary}` — generated into platform theme objects per [`platform-mapping.md`](./platform-mapping.md). Consume the semantic name directly unless a component's styling must be swappable independent of the semantic token behind it, in which case consume the `component.*` alias instead. Buttons, Inputs, Card, Chip, Badge, ProgressBar, Snackbar, NavItem, VideoPlayer, Checkout, SuccessState, Toggle, FileUpload, ReorderableList, DataTable, and Select currently have explicit `component.*` aliases; extend that object (never hardcode) before any other component needs the same decoupling.

**v1.2 addition:** VideoPlayer/PlaybackControls, Checkout/OrderSummary, SuccessState, Toggle/Switch, File/Media Upload, ReorderableList/DragHandle, and DataTable were added because [product planning](../product/SCREEN_INVENTORY.md) identified concrete MVP screens that needed them — not speculatively. Every new token used by these seven is a **reuse** of an existing primitive/semantic value (color, spacing, radius, typography, motion) via the component-layer alias mechanism above; **zero new colors, spacing steps, radii, typography styles, or motion values were introduced** — see [`CHANGELOG.md`](./CHANGELOG.md) for the full accounting.

**v1.3 addition:** Select/Dropdown was added because UX planning surfaced a genuine, recurring need (Course Editor's Category/Level/Content-Language fields, Settings' Language selector) that `CategoryChip` (multi-select tags) and `TextField` (free text) don't correctly serve. Its field styling reuses `component.input`'s tokens outright and its menu reuses the popover convention already established by `VideoPlayer`/`DataTable` — **every one of its 20 alias references resolves to a token that already existed before v1.3**, verified programmatically; zero new colors, spacing, radii, typography, or motion values were introduced for it either.

For RTL/logical-layout behavior (start/end, mirrored icons, nav direction) and content-resilience behavior (long strings, truncation, missing data) that apply across every component below, see [`LOCALIZATION.md`](./LOCALIZATION.md) and [`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md) — both are referenced again at the end of this file rather than repeated per-component.

---

## Interaction State Matrix

Not every state applies to every component category. This table is the map; token values for each cell are in that component's own section below.

| Component category | Default | Hover | Focused | Pressed | Selected | Loading | Disabled | Error | Success |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Buttons (Primary/Secondary/Tonal/Text/Icon) | ✓ | ✓ | ✓ | ✓ | — | Primary only | ✓ | — | — |
| Inputs (Text/Password/Search) | ✓ | ✓ | ✓ | — | — | — | ✓ | ✓ | ✓ |
| Cards (Course/Progress/Path/Instructor/Stat/Certificate) | ✓ | ✓ (web) | ✓ | ✓ | — | skeleton (§ LoadingState) | — | — | — |
| CategoryChip / filter chips | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | — | — |
| Badge | ✓ (static) | — | — | — | — | — | — | ✓ variant | ✓ variant |
| Avatar | ✓ (static) | — | — | — | — | ✓ (skeleton circle) | — | — | — |
| ProgressBar | ✓ (Active) | — | — | — | — | indeterminate | ✓ (Paused) | — | ✓ (Complete) |
| AppDialog / BottomSheet | ✓ | — | ✓ (trapped) | — | — | — | — | — | — |
| Snackbar | ✓ | — | ✓ (action) | ✓ (action) | — | — | — | — | — |
| Navbar / Sidebar / MobileBottomNav / Tabs items | ✓ | ✓ (web) | ✓ | ✓ | ✓ (active) | — | ✓ (rare) | — | — |
| Quiz Answer Option | ✓ | ✓ (web, pre-submit) | ✓ | ✓ | ✓ | — | ✓ (post-submit) | ✓ (incorrect) | ✓ (correct) |
| AITutorBubble / QuickAction | ✓ | ✓ (QuickAction) | ✓ (QuickAction) | ✓ (QuickAction) | — | ✓ (AI "thinking") | — | — | — |
| Loading/Empty/Error State patterns | — | — | — | — | — | ✓ (is one) | — | ✓ (is one) | — |
| SuccessState *(v1.2)* | — | — | — | — | — | — | — | — | ✓ (is one) |
| VideoPlayer controls *(v1.2)* | ✓ | ✓ | ✓ | ✓ | ✓ (play/pause toggle) | ✓ (buffering) | ✓ (control disabled pre-load) | ✓ (playback error) | — |
| Checkout / OrderSummary *(v1.2)* | ✓ | ✓ (confirm button) | ✓ | ✓ (confirm button) | — | ✓ (processing) | ✓ (confirm disabled until valid) | ✓ (simulated failure) | ✓ (→ SuccessState) |
| Toggle / Switch *(v1.2)* | ✓ | ✓ | ✓ | ✓ | ✓ (on/off *is* the selected state) | — | ✓ | — | — |
| File/Media Upload *(v1.2)* | ✓ (idle) | ✓ (drag-over) | ✓ | ✓ | — | ✓ (uploading) | ✓ | ✓ | ✓ (uploaded) |
| ReorderableList / DragHandle *(v1.2)* | ✓ | ✓ | ✓ (row + non-drag controls) | ✓ (dragging) | ✓ (active drag target) | — | ✓ | — | — |
| DataTable *(v1.2)* | ✓ | ✓ (row) | ✓ (row/cell, keyboard nav) | ✓ (row action) | ✓ (row selection, if enabled) | ✓ (skeleton rows) | ✓ (row action disabled) | ✓ (load error) | — |
| Select / Dropdown *(v1.3)* | ✓ | ✓ (field + option rows) | ✓ | ✓ (option tap/click) | ✓ (has a value / chosen option) | ✓ ("Loading options…") | ✓ (field and/or individual options) | ✓ | — |

"—" means the state is not meaningful for that category (e.g. a static Badge has no hover because it isn't interactive). Where a cell says "✓ variant", the state is expressed as a color *variant* of the component (e.g. `Badge` success/error/warning/info) rather than an interaction state.

---

## Buttons

### PrimaryButton

| Property | Value |
|---|---|
| Height | 48 |
| Radius | `radius.medium` (12) |
| Horizontal padding | `space.6` (24) |
| Min width | 88 |
| Icon size (optional leading/trailing) | `icon.medium` (20), gap `space.2` (8) |
| Typography | `typography.label.large` |
| Border | none |

| State | Background | Text/Icon | Notes |
|---|---|---|---|
| Default | `color.brand.primary` | `color.brand.onPrimary` | |
| Hover (pointer platforms) | `color.brand.primaryHover` | `color.brand.onPrimary` | 150ms `motion.easing.standard` |
| Pressed | `color.brand.primaryPressed` | `color.brand.onPrimary` | |
| Focused | `color.brand.primary` | `color.brand.onPrimary` | + `border.width.focus` outline in `color.border.focus`, 2px offset |
| Disabled | `color.brand.primary` @ `state.disabledContainerOpacity` over `surface.default` | `color.text.disabled` | no shadow, not interactive |
| Loading | `color.brand.primary` | inline spinner in `color.brand.onPrimary`, label hidden or dimmed to 0 opacity (width preserved) | button non-interactive while loading |

### SecondaryButton

Same dimensions as PrimaryButton. Outlined style.

| State | Background | Border | Text |
|---|---|---|---|
| Default | transparent | `border.width.default` `color.brand.primary` | `color.brand.primary` |
| Hover | `color.brand.primary` @ `state.hoverOpacity` | `color.brand.primary` | `color.brand.primary` |
| Pressed | `color.brand.primary` @ `state.pressedOpacity` | `color.brand.primary` | `color.brand.primary` |
| Focused | transparent | `border.width.focus` `color.border.focus` | `color.brand.primary` |
| Disabled | transparent | `color.border.default` | `color.text.disabled` |

### TonalButton

Same dimensions. Filled with the container tone — the "second-most prominent" action.

| State | Background | Text |
|---|---|---|
| Default | `color.brand.primaryContainer` | `color.brand.onPrimaryContainer` |
| Hover | `color.brand.primaryContainer` + `color.brand.onPrimaryContainer` @ `state.hoverOpacity` state layer | `color.brand.onPrimaryContainer` |
| Pressed | `color.brand.primaryContainer` + `color.brand.onPrimaryContainer` @ `state.pressedOpacity` state layer | `color.brand.onPrimaryContainer` |
| Focused | `color.brand.primaryContainer` | + focus outline `color.border.focus` |
| Disabled | `color.surface.variant` | `color.text.disabled` |

### TextButton

| Property | Value |
|---|---|
| Height | 40 |
| Horizontal padding | `space.3` (12) |
| Radius | `radius.medium` (12) (hit/hover area only, no visible fill by default) |
| Typography | `typography.label.large` |

| State | Background | Text |
|---|---|---|
| Default | transparent | `color.brand.primary` |
| Hover | `color.brand.primary` @ `state.hoverOpacity` | `color.brand.primary` |
| Pressed | `color.brand.primary` @ `state.pressedOpacity` | `color.brand.primary` |
| Focused | transparent | + focus outline |
| Disabled | transparent | `color.text.disabled` |

### IconButton

| Property | Value |
|---|---|
| Size | 40×40 touch area (icon rendered at `icon.default`, 24) |
| Radius | `radius.full` |
| Colors | icon `color.text.secondary` default, `color.brand.primary` when representing an active/selected toggle |

| State | Background | Icon |
|---|---|---|
| Default | transparent | `color.text.secondary` |
| Hover | `color.text.primary` @ `state.hoverOpacity` | `color.text.primary` |
| Pressed | `color.text.primary` @ `state.pressedOpacity` | `color.text.primary` |
| Focused | transparent | + focus outline, `color.text.primary` |
| Disabled | transparent | `color.text.disabled` |

---

## Inputs

### TextField (base for TextField / PasswordField / SearchField)

| Property | Value |
|---|---|
| Height | 52 |
| Radius | `radius.medium` (12) |
| Padding | horizontal `space.4` (16), vertical `space.3` (12) |
| Border width | `border.width.default` (1), `border.width.focus` (2) when focused |
| Label typography | `typography.label.medium`, `color.text.secondary` |
| Input typography | `typography.body.medium`, `color.text.primary` |
| Placeholder | `typography.body.medium`, `color.text.disabled` |
| Helper text | `typography.caption`, `color.text.secondary` |
| Error text | `typography.caption`, `color.error.default` |
| Leading/trailing icon | `icon.medium` (20), `color.text.secondary` |

| State | Border | Background | Notes |
|---|---|---|---|
| Default | `color.border.default` | `color.surface.default` | |
| Hover | `color.border.strong` | `color.surface.default` | pointer platforms only |
| Focused | `color.border.focus`, 2px | `color.surface.default` | label shrinks/floats above field |
| Filled (has value, not focused) | `color.border.default` | `color.surface.default` | label stays floated |
| Error | `color.border.error` | `color.surface.default` | error text + error icon shown below; error state overrides focus color |
| Success | `color.success.default` border | `color.surface.default` | optional trailing check icon in `color.success.default` |
| Disabled | `color.border.default` | `color.surface.variant` | text `color.text.disabled`, not interactive |

### PasswordField

Extends TextField. Trailing `IconButton` (visibility toggle, `icon.medium`) switches between "visibility" / "visibility_off" Material Symbols. Masked characters use the same `typography.body.medium` token (rendered as `•`). Must always ship the toggle — never a password field with no reveal option.

### SearchField

Extends TextField. Leading icon fixed to "search" (`icon.medium`, `color.text.secondary`). Trailing "close" `IconButton` appears only when the field has a value, clearing it on tap. Default resting background may use `color.surface.variant` instead of `color.surface.default` when embedded in a navbar (see Navbar below) to read as recessed.

### Toggle / Switch *(v1.2)*

**Implement using the platform's native switch control** (Web `<input type="checkbox" role="switch">`, Android Compose `Switch`, iOS SwiftUI `Toggle`) styled to these tokens, rather than a custom-built control — this inherits correct keyboard/screen-reader semantics for free instead of having to reproduce them (see [`ACCESSIBILITY.md § 15`](./ACCESSIBILITY.md)).

| Property | Value |
|---|---|
| Track width × height | `space.10` (40) × `space.6` (24) |
| Track radius | `radius.full` |
| Thumb size | `space.5` (20) |
| Thumb inset | `border.width.focus` (2) |
| Thumb travel distance | `space.4` (16) |

| State | Track | Thumb |
|---|---|---|
| On | `color.brand.primary` | `color.surface.default`, `elevation.1` |
| Off | `color.border.strong` | `color.surface.default`, `elevation.1` |
| Focused | as above + `color.border.focus` outline | — |
| Disabled (on or off) | `color.border.default` @ `state.disabledContainerOpacity` | `color.surface.default` |

Selection state (on/off) *is* this component's "Selected" state in the Interaction State Matrix — there is no separate visual "selected" beyond the on-position.

**Locked rule (v1.3):** whenever a Toggle conveys a meaningful named state — e.g. a course's Published/Draft status — it **must** be paired with a visible text label naming that state ("Published" / "Draft"), placed adjacent to the control (`typography.label.large`, `space.2` gap). Color and switch position are never the only signal, consistent with the design system's global "never color alone" rule (`ACCESSIBILITY.md § 8`). A bare Toggle with no state label is only acceptable for controls whose meaning is self-evident and unnamed (e.g. a generic on/off preference where the adjacent form label already states what's being toggled).

### Select / Dropdown *(v1.3)*

Field styling is identical to `TextField`'s (same height, radius, padding, typography, border/background/text tokens) — Select is an input variant, not a visually distinct control. What differs is the trailing indicator and the menu it opens.

| Property | Value |
|---|---|
| Height | 52 |
| Radius | `radius.medium` (12) |
| Padding | horizontal `space.4` (16) |
| Label typography | `typography.label.medium`, `color.text.secondary` |
| Selected-value / placeholder typography | `typography.body.medium` — `color.text.primary` (has a value) / `color.text.disabled` (placeholder, no selection yet) |
| Helper text | `typography.caption`, `color.text.secondary` |
| Error text | `typography.caption`, `color.error.default` |
| Leading icon (optional) | `icon.medium` (20), `color.text.secondary` |
| Trailing indicator | `icon.medium` (20) — `expand_more` closed / `expand_less` open (both non-directional, never mirror) |

| State | Border | Background | Notes |
|---|---|---|---|
| Default | `color.border.default` | `color.surface.default` | placeholder shown if no selection |
| Hover | `color.border.strong` | `color.surface.default` | pointer platforms only |
| Focused | `color.border.focus`, 2px | `color.surface.default` | not yet open — reached via Tab, not yet activated |
| Open | `color.border.focus`, 2px | `color.surface.default` | menu visible; trailing indicator flips to `expand_less` |
| Selected (has a value) | `color.border.default` | `color.surface.default` | selected value replaces the placeholder, same treatment as `TextField`'s Filled state |
| Error | `color.border.error` | `color.surface.default` | error text + icon shown below, same pattern as `TextField` |
| Disabled | `color.border.default` | `color.surface.variant` | text `color.text.disabled`, not interactive |

**Option list (menu):**

| Property | Value |
|---|---|
| Surface | `color.surface.elevated`, `elevation.3`, `radius.medium` — the same popover/menu convention already used by `VideoPlayer`'s speed selector and `DataTable`'s row-action menu (`COMPONENTS.md § Media & Playback`, `§ Instructor & Admin Components`), not a new floating-surface pattern |
| Option row height | 44 (`touchTarget.web_px`) |
| Option row padding | horizontal `space.4` (16) |
| Option typography | `typography.body.medium` |

| Option state | Background | Text |
|---|---|---|
| Default | transparent | `color.text.primary` |
| Hover | `color.text.primary` @ `state.hoverOpacity` | `color.text.primary` |
| Selected (currently chosen) | `color.brand.primaryContainer` | `color.brand.onPrimaryContainer` |
| Disabled option | transparent | `color.text.disabled`, not selectable, not focusable |

**Keyboard behavior (Web):** `Tab`/`Shift+Tab` moves focus to/from the closed field; `Enter`/`Space` opens the menu; `↓`/`↑` moves the highlighted option (opens the menu if closed and starts from the current selection); `Enter`/`Space` on a highlighted option selects it and closes the menu; `Escape` closes the menu without changing the selection and returns focus to the field. Typeahead (typing a letter jumps to the first matching option) is expected standard behavior, not a bespoke Mentora pattern.

**Touch behavior:** tap the field to open the menu; tap an option to select and close; tap outside the menu (scrim/backdrop) to close without changing the selection.

**Native mapping (Android/iOS):** Android maps to an `ExposedDropdownMenuBox`-style Compose control; iOS maps to a `Menu`/`Picker`-style control — both styled to the tokens above, preserving the Mentora visual contract while inheriting each platform's native accessible interaction model (see `platform-mapping.md`).

**Long-label behavior:** the selected-value/placeholder text truncates to 1 line with ellipsis if it doesn't fit (matching `TextField`'s existing pattern); option rows in the menu likewise truncate to 1 line — the option list itself scrolls (not the individual row) once options exceed the menu's available height. Full text for both the selected value and any option is always available via the accessible name, per `CONTENT_RESILIENCE.md`.

**Loading options (if fetched asynchronously):** the field shows a disabled-looking state with a small inline spinner replacing the trailing indicator (reusing `LoadingState`'s spinner treatment) until options resolve; if opened before options are ready, the menu shows a single "Loading options…" row (`typography.body.small`, `color.text.secondary`) instead of a skeleton list — options lists are typically short enough that a skeleton would be unnecessary overhead.

**Disabled options:** an individual option can be non-selectable within an otherwise open/enabled Select (e.g. a category with no capacity) — rendered in `color.text.disabled`, excluded from keyboard arrow traversal and typeahead matching, and not tappable/clickable.

---

## File & Media Upload *(v1.2)*

### FileUpload

**Visual states only — no upload/storage backend implementation.** Built for Instructor course thumbnails, lesson videos, and lesson resources (`../product/USER_FLOWS.md § 24`).

| Property | Value |
|---|---|
| Radius | `radius.medium` (12) |
| Padding | `space.6` (32) |
| Instructional icon | `icon.large`, `color.text.secondary` |
| Instructional text | `typography.body.small`, `color.text.secondary` |
| Browse action | `TonalButton` |

| State | Border | Background | Content |
|---|---|---|---|
| Idle | `color.border.default` (dashed stroke) | `color.surface.default` | Upload icon + "Drag a file here or Browse" + `TonalButton` |
| Drag-over | `color.border.focus` | `color.brand.primaryContainer` | Same content, highlighted to confirm the drop target |
| Uploading | `color.border.default` | `color.surface.default` | Filename (`typography.body.medium`) + `ProgressBar` (reused, not redefined) + cancel `IconButton` |
| Success | `color.border.default` | `color.surface.default` | Thumbnail preview (image/video) or file-type icon, filename, file size (`typography.caption`), success icon (`icon.medium`, `color.success.default`), remove `IconButton` |
| Error | `color.border.error` | `color.surface.default` | Error icon (`color.error.default`) + message (`typography.caption`, `color.error.default`) + retry `TextButton` — same icon+text+color pattern as `TextField`'s error state |
| Disabled | `color.border.default` *(no opacity modifier — background/text dimming already signal disabled, same resolution as `TextField`'s disabled border; see `CHANGELOG.md` 1.1.0)* | `color.surface.variant` | Instructional text in `color.text.disabled` |

**Content resilience:** filenames truncate to the middle (`name…ext`) at 1 line rather than end-truncating, so the file extension stays visible — the one deliberate departure from `CONTENT_RESILIENCE.md § 1`'s default end-ellipsis rule, justified because the extension is often the only way to distinguish two similarly-named files; the full filename is always available via the accessible name/tooltip per `CONTENT_RESILIENCE.md § 7`.

---

## Cards

### CourseCard

Core content component — see also DESIGN_SYSTEM.md §14 for grid placement.

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Border | `border.width.default` `color.border.default` |
| Background | `color.surface.default` |
| Elevation | `elevation.1` resting, `elevation.2` on hover (web/desktop only) |
| Padding (content area below thumbnail) | `space.4` (16) |
| Thumbnail aspect ratio | 16:9, top corners clipped to card radius |
| Web width | 280–340px (fluid within grid column) |
| Mobile width | 100% of grid column |

**Content hierarchy (top to bottom):**
1. Thumbnail image (16:9)
2. Category chip (`CategoryChip`, overlaid bottom-left of thumbnail or first line of content — pick one placement and keep it consistent site-wide)
3. Course title — `typography.heading.h4`, `color.text.primary`, max 2 lines, ellipsis
4. Instructor name — `typography.body.small`, `color.text.secondary`
5. Rating + student count + duration — single row, `typography.caption`, `color.text.secondary`, icons at `icon.small` (16)
6. Progress bar (only if enrolled — see `ProgressBar`)
7. Primary action — `TonalButton` ("Continue"/"Enroll") full-width on mobile, auto-width on web

| State | Treatment |
|---|---|
| Default | `elevation.1`, `color.border.default` |
| Hover (web) | `elevation.2`, border `color.border.strong`, 150ms transition |
| Pressed | scale 0.98 (`motion.duration.fast`) or background tint `color.text.primary` @ `state.pressedOpacity` |
| Focused (keyboard) | focus outline `color.border.focus` around whole card |

### CourseProgressCard

Variant of CourseCard for "My Learning" — same shell, always shows `ProgressBar` + "% complete" (`typography.caption`, `color.text.secondary`) + "Resume" `TonalButton`, and drops rating/student-count row.

### LearningPathCard

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Background | `color.brand.primaryContainer` (differentiates from plain CourseCard) |
| Padding | `space.5` (20) |
| Title | `typography.heading.h4`, `color.brand.onPrimaryContainer` |
| Description | `typography.body.small`, `color.brand.onPrimaryContainer` |
| Meta (course count, duration) | `typography.caption`, `color.brand.onPrimaryContainer` |
| Action | `TextButton` in `color.brand.onPrimaryContainer` or a small `PrimaryButton` |

Title/description/meta all use `color.brand.onPrimaryContainer` at full opacity — hierarchy comes from the type-scale step (h4 → body.small → caption), not from fading the text color, per design principle 4 ("hierarchy via type scale and spacing, not extra colors"). This also avoids introducing an undocumented text-opacity token.

### InstructorCard

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Background | `color.surface.default` |
| Border | `color.border.default` |
| Padding | `space.4` (16) |
| Layout | `Avatar` (size `large`, 64) + name (`typography.heading.h4`) + title/expertise (`typography.body.small`, `color.text.secondary`) + stats row (`typography.caption`) |

### QuizCard

See dedicated **Quiz System** section below.

### StatCard

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Background | `color.surface.default` |
| Border | `color.border.default` |
| Padding | `space.4` (16) |
| Value | `typography.heading.h2`, `color.text.primary` |
| Label | `typography.body.small`, `color.text.secondary` |
| Trend indicator (optional) | `typography.caption` + `icon.small`, `color.success.default` (up) / `color.error.default` (down) |

### CertificateCard

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Background | `color.surface.default` |
| Border | `color.border.default` (same as other cards — no special-cased tint; achievement is signaled by the certificate preview image and title, not by border color) |
| Padding | `space.5` (20) |
| Thumbnail/preview | 16:9 or A4-ish preview image of the certificate, `radius.medium` on the preview itself |
| Title (course name) | `typography.heading.h4` |
| Meta (completion date, issuer) | `typography.caption`, `color.text.secondary` |
| Actions | `TonalButton` "View", `TextButton` "Share"/"Download" |

---

## Chips & Badges

### CategoryChip

| Property | Value |
|---|---|
| Height | 28 |
| Radius | `radius.full` |
| Padding | horizontal `space.3` (12) |
| Typography | `typography.label.medium` |

| State | Background | Text |
|---|---|---|
| Default (unselected filter) | `color.surface.variant` | `color.text.secondary` |
| Selected | `color.brand.primaryContainer` | `color.brand.onPrimaryContainer` |
| On image overlay (course thumbnail) | `color.overlay.chipScrim` | `color.text.inverse` |
| Disabled | `color.surface.variant` (no opacity modifier — background already reads as inactive against `text.disabled`) | `color.text.disabled` |

### Badge

| Property | Value |
|---|---|
| Height | 20 (text badge) / 8 (dot badge) |
| Radius | `radius.full` |
| Padding | horizontal `space.2` (8) |
| Typography | `typography.label.medium` |

| Variant | Background | Text |
|---|---|---|
| Neutral | `color.surface.variant` | `color.text.secondary` |
| Success | `color.success.container` | `color.success.onSuccessContainer` |
| Warning | `color.warning.container` | `color.warning.onWarningContainer` |
| Error | `color.error.container` | `color.error.onErrorContainer` |
| Info | `color.info.container` | `color.info.onInfoContainer` |
| Brand | `color.brand.primaryContainer` | `color.brand.onPrimaryContainer` |

---

## Avatar

| Size token | Dimension |
|---|---|
| `avatar.small` | 24 |
| `avatar.medium` | 40 |
| `avatar.large` | 64 |
| `avatar.xlarge` | 96 |

Radius always `radius.full`. Fallback (no image): initials on `color.brand.primaryContainer` background, text `color.brand.onPrimaryContainer`, `typography.label.large` (scaled to avatar size). Online/status dot (optional): 25% of avatar diameter, `color.success.default`, `border.width.default` ring in `color.surface.default` to separate from the image.

---

## ProgressBar

| Property | Value |
|---|---|
| Height | 8 |
| Radius | `radius.full` |
| Track background | `color.surface.variant` |
| Fill | `color.brand.primary` |

| State | Fill color | Notes |
|---|---|---|
| Default/Active | `color.brand.primary` | animates width change over `motion.duration.normal`, `motion.easing.standard` |
| Complete | `color.success.default` | swap fill color at 100%, optional checkmark icon at end |
| Paused | `color.text.disabled` | static, no animation |

Indeterminate variant (e.g. AI Tutor "thinking"): looping linear sweep, `motion.easing` linear, never uses `easing.standard` (that's for state changes, not loops).

---

## Media & Playback *(v1.2)*

### VideoPlayer / PlaybackControls

**Contract only** — this specifies the UI/control surface for the Course Player's video area; it does not define or depend on any streaming/video-infrastructure implementation.

**A deliberate, documented exception to normal theming:** the control bar always sits on top of arbitrary video pixels, not an app surface — so its tokens are the same in both Light and Dark theme (this is *not* a hardcoded-color violation of `DESIGN_RULES.md` rule 18: `color.overlay.scrim` and `color.text.inverse` are still semantic tokens, they simply already resolve to a dark-chrome-appropriate value in both themes by design — see `DESIGN_SYSTEM.md § 1.2/1.3`).

| Property | Value |
|---|---|
| Video area aspect ratio | 16:9 (same convention as `CourseCard` thumbnail) |
| Control bar background | `color.overlay.scrim` |
| Control bar padding | `space.3` (12) horizontal, `space.2` (8) vertical |
| Control button touch target | 44×44 (`touchTarget.web_px`, platform-equivalent) |
| Control icon size | `icon.default` (24) |
| Control icon color | `color.text.inverse` (both themes — see note above) |
| Time labels | `typography.caption`, `color.text.inverse` |

**Controls:** play/pause, timeline/scrubber with current time and duration, volume (mute toggle + level), playback speed selector, fullscreen toggle. Lesson progress is the existing `ProgressBar` token set, reused (not redefined) for the Course Player's overall-progress indicator outside the video frame itself.

| Control button state | Background | Icon |
|---|---|---|
| Default | transparent | `color.text.inverse` |
| Hover | `color.text.inverse` @ `state.hoverOpacity` | `color.text.inverse` |
| Pressed | `color.text.inverse` @ `state.pressedOpacity` | `color.text.inverse` |
| Focused | transparent | `color.text.inverse` + focus outline `color.border.focus` |
| Disabled (e.g. before video metadata loads) | transparent | `color.text.inverse` @ `state.disabledContentOpacity` |

**Scrubber/timeline:**

| Property | Value |
|---|---|
| Track height (resting) | `space.1` (4) |
| Track height (hover/active) | 8 (matches `ProgressBar` height — thickens on interaction, a standard scrubber affordance) |
| Radius | `radius.full` |
| Track (unplayed) | `color.text.inverse` @ `state.hoverOpacity` |
| Buffered range | `color.text.inverse` @ `state.pressedOpacity` |
| Played/fill | `color.brand.primary` |
| Thumb size (resting / active-drag) | `space.3` (12) / `space.4` (16) |
| Thumb color | `color.brand.primary` |

**RTL rule — locked exception:** the scrubber/timeline **always renders left-to-right**, regardless of the app's layout direction, per [`LOCALIZATION.md § 2–3`](./LOCALIZATION.md) — this is the one place in Mentora that deliberately does not mirror. Every other control (play/pause, volume, fullscreen, speed) is non-directional and never mirrors either (`design-tokens.json → icon.directional.neverMirror`); no control in this component mirrors in RTL.

**Playback speed selector:** a compact trigger (`typography.label.medium`, `color.text.inverse`, e.g. "1×") opening a small popover list (`elevation.3`, `color.surface.elevated` background, `color.text.primary` options) — reuses the existing popover/menu elevation convention (`DESIGN_SYSTEM.md § 5`), not a new floating-surface pattern.

**Content resilience:** the current lesson title shown in/near the control bar truncates to 1 line with ellipsis (matches `CONTENT_RESILIENCE.md § 1`'s pattern for secondary metadata); the video area itself shows a `LoadingState` skeleton (matching the 16:9 aspect ratio) while loading and an inline error affordance (reusing `ErrorState`'s icon+title+retry pattern, sized for the video frame) on playback failure — see `CONTENT_RESILIENCE.md`.

---

## Checkout *(v1.2)*

### Checkout / OrderSummary

Built specifically for Mentora's simulated **Demo Payment** flow — see [`../product/DEMO_PAYMENT_FLOW.md`](../product/DEMO_PAYMENT_FLOW.md). **This component's field set is closed and exhaustive: course summary, demo price, a demo-payment notice, and a primary confirmation action. It must never contain a card number, CVV, expiry date, billing address, or bank-account field — see `DESIGN_RULES.md` rule 15 (no arbitrary/undocumented fields) and the product team's strict payment rules.**

| Property | Value |
|---|---|
| Radius | `radius.large` (16) |
| Padding | `space.5` (20) |
| Background | `color.surface.default` |
| Border | `color.border.default` |
| Course line item | thumbnail + title + instructor, reusing `CourseCard`'s compact content pattern |
| Price label(s) | `typography.body.medium`, `color.text.secondary` |
| Total price | `typography.heading.h3`, `color.text.primary` |
| Demo-payment notice | `color.info.container` background, `color.info.onInfoContainer` text, `radius.medium`, `space.3`/`space.2` padding, `typography.body.small` — copy: *"Demo Payment — no real charges or payment information required."* |
| Confirm action | `PrimaryButton` — *"Complete Demo Purchase"* |
| Cancel action | `TextButton` — back to Course Details |

| State | Notes |
|---|---|
| Default | order summary + notice shown, confirm action enabled |
| Processing | in-place (not a route change) — confirm action shows `PrimaryButton`'s existing Loading state (inline spinner); rest of the summary stays visible and static |
| Error (optional, simulated) | `color.error.default` inline message ("Demo checkout could not be completed. Try again.") + `TextButton` "Try Again" — reuses `TextField`'s error-messaging pattern (icon+text+color), not a new error style |
| → Success | routes to `SuccessState` (below), not a state of this component itself |

**Content resilience:** course title in the line item follows the same 2-line-clamp rule as `CourseCard` (`CONTENT_RESILIENCE.md § 1`); price values never truncate (same rule as `StatCard`'s value — reformat/shrink one type step before ever clipping a number).

---

## Dialogs, Sheets, Feedback

### AppDialog

| Property | Value |
|---|---|
| Radius | `radius.xlarge` (24) |
| Background | `color.surface.elevated` |
| Elevation | `elevation.4` |
| Padding | `space.6` (24) |
| Scrim | `color.overlay.scrim` behind dialog |
| Title | `typography.heading.h3` |
| Body | `typography.body.medium`, `color.text.secondary` |
| Actions | right-aligned (web/tablet+) row of `TextButton`/`PrimaryButton`, gap `space.2` (8); full-width stacked on mobile |
| Motion | open: `motion.duration.normal` + `easing.decelerate`, scale 0.95→1 + fade; close: `motion.duration.fast` + `easing.accelerate` |

### BottomSheet

| Property | Value |
|---|---|
| Radius | `radius.xlarge` (24), top corners only |
| Background | `color.surface.elevated` |
| Elevation | `elevation.4` |
| Padding | `space.5` (20), plus safe-area inset on iOS/Android |
| Drag handle | 32×4, `radius.full`, `color.border.strong`, centered, `space.2` from top |
| Motion | open: slide-up `motion.duration.normal` + `easing.decelerate`; close: slide-down `motion.duration.fast` + `easing.accelerate` |

### Snackbar

| Property | Value |
|---|---|
| Radius | `radius.medium` (12) |
| Background | `color.surface.inverse` |
| Text | `typography.body.small`, `color.text.inverse` |
| Padding | `space.4` (16) horizontal, `space.3` (12) vertical |
| Action (optional) | `TextButton` in `color.brand.onSurfaceInverse` — a token defined specifically for a brand-colored action on `surface.inverse` (verified ≥4.5:1 in both themes, see `DESIGN_SYSTEM.md § 1.1`) |
| Elevation | `elevation.3` |
| Motion | slide-up + fade, `motion.duration.normal` + `easing.decelerate` in; fade out `motion.duration.fast` |
| Auto-dismiss | 4s default, paused on hover/focus/touch |

---

## State Patterns

### LoadingState

Skeleton loaders preferred over spinners for content areas (cards, lists, text blocks). Spinners reserved for buttons/inline actions and full-screen initial load.

| Property | Value |
|---|---|
| Skeleton background | `color.surface.variant` |
| Skeleton shimmer | animated gradient sweep, `color.surface.variant` → `color.border.default` → `color.surface.variant`, `motion.duration.slow` loop |
| Skeleton radius | matches the component it stands in for (e.g. `radius.large` for a card skeleton) |
| Spinner | `color.brand.primary` stroke, indeterminate rotation |

### SuccessState *(v1.2)*

Sibling of `EmptyState`/`ErrorState` — a celebratory confirmation moment, structurally identical in composition (icon → title → description → primary action) but using the success semantic identity instead of neutral/error. Built for Purchase Success (`../product/DEMO_PAYMENT_FLOW.md`) and reusable anywhere else a completed, positive action deserves a dedicated confirmation screen (e.g. a future "course completed" moment).

| Property | Value |
|---|---|
| Icon | `icon.large` (32) or a custom illustration, `color.success.default` |
| Title | `typography.heading.h3`, `color.text.primary` |
| Description | `typography.body.small`, `color.text.secondary`, max ~2 lines |
| Primary action | `PrimaryButton` |
| Container padding | `space.10` (40) vertical |
| Entrance motion | `motion.duration.slow` + `motion.easing.decelerate` — scale/fade the icon and content in |

**Reduced motion:** per `design-tokens.json → motion.reducedMotion` and [`ACCESSIBILITY.md § 9`](./ACCESSIBILITY.md), when the platform's reduced-motion preference is on, the entrance plays as an opacity cross-fade at `motion.duration.fast` (or an instant appearance) instead of the scale/fade sequence — no exception for this component just because it's a celebratory moment; the accessibility rule is unconditional.

**Content resilience:** title/description follow the same wrap rules as `EmptyState`/`ErrorState` (`CONTENT_RESILIENCE.md § 1`) — wraps freely up to the soft 1–2/2–3 line guidance, never truncated.

### EmptyState

| Element | Spec |
|---|---|
| Illustration/Icon | centered, `icon.large` (32) or larger custom illustration, `color.text.secondary` if icon |
| Title | `typography.heading.h4`, `color.text.primary` |
| Description | `typography.body.small`, `color.text.secondary`, max ~2 lines |
| Primary action | `PrimaryButton` or `TonalButton` |
| Container padding | `space.10` (40) vertical |

### ErrorState

| Element | Spec |
|---|---|
| Icon | `icon.large`, `color.error.default` |
| Title | `typography.heading.h4`, "Something went wrong" style friendly copy — never raw backend error text |
| Description | `typography.body.small`, `color.text.secondary` — plain-language explanation |
| Retry action | `PrimaryButton` or `TonalButton` labeled "Try again" |
| Container padding | `space.10` (40) vertical |

Raw backend/server error messages, stack traces, or status codes must never render to the end user — map every known error to a friendly message; unknown errors fall back to a generic "Something went wrong" copy.

---

## Navigation

### Navbar (public web)

| Property | Value |
|---|---|
| Height | 64 |
| Background | `color.surface.default` |
| Border | `border.width.default` bottom, `color.border.default` |
| Padding | horizontal per breakpoint page padding |
| Logo | left-aligned |
| Nav links | `typography.label.large`, `color.text.secondary` default / `color.text.primary` active, active indicator = 2px underline `color.brand.primary` |
| Search | `SearchField`, `color.surface.variant` background |
| Auth actions | "Login" as `TextButton`, "Get Started" as `PrimaryButton` |

### Sidebar (authenticated web)

| Property | Value |
|---|---|
| Width | 264 expanded / 72 collapsed (icon-only) |
| Background | `color.surface.default` |
| Border | `border.width.default` right, `color.border.default` |
| Item height | 44 |
| Item padding | horizontal `space.4` (16) |
| Item typography | `typography.label.large` |
| Item icon | `icon.default` (24) |

| State | Background | Text/Icon |
|---|---|---|
| Default | transparent | `color.text.secondary` |
| Hover | `color.text.primary` @ `state.hoverOpacity` | `color.text.primary` |
| Active/selected | `color.brand.primaryContainer` | `color.brand.onPrimaryContainer` |
| Focused | transparent | + focus outline |

Items: Dashboard, Explore, My Learning, Learning Paths, AI Tutor, Certificates, Profile, Settings.

### MobileBottomNavigation

| Property | Value |
|---|---|
| Height | 64 + safe-area inset |
| Background | `color.surface.default` |
| Border | `border.width.default` top, `color.border.default` |
| Max items | 5 — Home, Explore, My Learning, AI Tutor, Profile |
| Item | icon `icon.default` (24) + label `typography.caption` |

| State | Icon/Label color |
|---|---|
| Default | `color.text.secondary` |
| Active | `color.brand.primary` |

### Tabs

| Property | Value |
|---|---|
| Height | 44 |
| Typography | `typography.label.large` |
| Indicator | 2px underline, `color.brand.primary`, animates position over `motion.duration.normal` + `easing.standard` |

| State | Text |
|---|---|
| Default | `color.text.secondary` |
| Active | `color.text.primary` + indicator |
| Disabled | `color.text.disabled` |

---

## AI Tutor

### AITutorBubble

| Property | AI message | User message |
|---|---|---|
| Background | `color.surface.variant` | `color.brand.primaryContainer` |
| Text | `color.text.primary` | `color.brand.onPrimaryContainer` |
| Radius | `radius.large` (16), tail corner (bottom-left for AI / bottom-right for user) reduced to `radius.small` (8) | same, mirrored |
| Padding | `space.3` (12) vertical, `space.4` (16) horizontal | same |
| Typography | `typography.body.medium` | same |
| Max width | 80% of chat container | same |

The AI Tutor uses the exact same surfaces, radii, and type scale as the rest of the product — it must read as a Mentora feature, not a bolted-on chat widget.

### AITutorQuickAction

Rendered as a horizontally scrollable row of chip-style buttons above the input.

| Property | Value |
|---|---|
| Height | 36 |
| Radius | `radius.full` |
| Padding | horizontal `space.4` (16) |
| Background | `color.surface.default` |
| Border | `border.width.default` `color.border.default` |
| Typography | `typography.label.medium` |
| Text | `color.brand.primary` |

Default quick actions: "Explain this lesson", "Summarize", "Give me an example", "Quiz me", "What should I learn next?"

| State | Background | Border |
|---|---|---|
| Default | `color.surface.default` | `color.border.default` |
| Hover/Pressed | `color.brand.primaryContainer` | `color.brand.primary` |

---

## Quiz System

### QuestionCard

Same shell as a `StatCard` (radius `radius.large`, border `color.border.default`, padding `space.5`). Contains:
- Progress indicator ("Question 3 of 10") — `typography.caption`, `color.text.secondary`, paired with a slim `ProgressBar`
- Question text — `typography.heading.h4`

### Answer Options

Each option is a full-width row, height 52, radius `radius.medium`, border `border.width.default`, padding `space.4` horizontal.

| State | Background | Border | Text/Icon | Icon |
|---|---|---|---|---|
| Default | `color.surface.default` | `color.border.default` | `color.text.primary` | none |
| Selected (unsubmitted) | `color.brand.primaryContainer` | `color.brand.primary` | `color.brand.onPrimaryContainer` | radio/check filled, `color.brand.primary` |
| Correct (after submit) | `color.success.container` | `color.success.default` | `color.success.onSuccessContainer` | check_circle icon, `color.success.default` |
| Incorrect (after submit) | `color.error.container` | `color.error.default` | `color.error.onErrorContainer` | cancel icon, `color.error.default` |
| Disabled (post-submit, unselected) | `color.surface.variant` | `color.border.default` | `color.text.disabled` | none |

**Rule:** correctness is never color-only — always pair the state color with an icon (`check_circle`/`cancel`) and, where space allows, text ("Correct"/"Incorrect"). This satisfies both the brief's explicit requirement and WCAG 1.4.1 (color not sole means of conveying information).

### Result screen

Uses `StatCard`s for the score summary (`typography.display.medium` for the score number), `success`/`error` semantic colors per-question breakdown, and a `PrimaryButton` for "Continue"/"Retry".

---

## Instructor & Admin Components *(v1.2)*

Web-only, per [`../product/USER_ROLES.md`](../product/USER_ROLES.md) — built for the Instructor course-editing surfaces and Admin management lists.

### ReorderableList / DragHandle

For Instructor sections and lessons (`../product/USER_FLOWS.md § 23–24`). **Drag-and-drop MUST ship with a fully equivalent non-drag alternative — this is not optional.** A row that can only be reordered by dragging fails keyboard and screen-reader users; the Move Up/Move Down controls below are not a fallback afterthought, they are the accessible path and are always present (not hidden behind a hover/focus reveal that a screen-reader user might miss).

| Property | Value |
|---|---|
| Row min-height | `space.12` (48) — meets touch-target minimum directly |
| Row padding | `space.4` (16) horizontal |
| Row internal gap | `space.3` (12) |
| Row background / border | `color.surface.default` / `color.border.default` |
| Drag handle icon | `icon.medium` (20), `color.text.secondary` default / `color.text.primary` hover, 44×44 touch target |
| Non-drag controls | "Move Up" / "Move Down" `IconButton`s (`arrow_upward`/`arrow_downward` — non-directional, never mirror in RTL), always visible per-row |

| State | Treatment |
|---|---|
| Default | `elevation.0` (flat, border only) |
| Hover (web) | `color.text.primary` @ `state.hoverOpacity` background tint |
| Focused | focus outline `color.border.focus` around the row or the specific control |
| Dragging | `elevation.3`, background lifts to `color.surface.elevated` |
| Drop target indicator | a `border.width.focus`-thick line in `color.brand.primary` between the two rows the dragged item would land between |
| Disabled | `color.text.disabled` content, drag handle and Move controls inert |

**Content resilience:** section/lesson title in each row follows `CONTENT_RESILIENCE.md § 1`'s 1-line-clamp rule for list-item text.

### DataTable

Primarily for Web Instructor/Admin lists (`../product/SCREEN_INVENTORY.md` § Admin screens). **Responsive by breakpoint, not forced onto mobile:** at `desktop`/`largeDesktop` (`design-tokens.json → breakpoints`) it renders as a true table; below that (`tablet`/`mobile` — i.e. a narrow browser window, since Instructor/Admin is a Web-only surface that can still be resized narrow) each row **collapses to a stacked card** of label:value pairs rather than a horizontally-scrolling or cramped table. This is the same responsive philosophy as every other Mentora layout (`DESIGN_SYSTEM.md § 9`), applied to tabular data specifically.

| Property | Value |
|---|---|
| Header background | `color.surface.variant` |
| Header typography/color | `typography.label.medium`, `color.text.secondary` |
| Sort indicator | `arrow_upward`/`arrow_downward` (`icon.small`), non-directional |
| Row typography/color | `typography.body.medium`, `color.text.primary` |
| Row divider | `color.border.default` |
| Row hover (web) | `color.text.primary` @ `state.hoverOpacity` |
| Row actions | `more_vert` trigger → `IconButton`, opening a row-action menu (reuses the popover/menu convention from `VideoPlayer`'s speed selector — `elevation.3`, `color.surface.elevated`) |
| Collapsed card (narrow viewport) | `radius.medium`, `color.surface.default` background, `color.border.default` border, `space.4` padding; each column renders as a `label.medium` (`color.text.secondary`) : `body.medium` (`color.text.primary`) pair, stacked |
| Pagination | `TextButton`s + page label (`typography.body.small`) |

**Content resilience:** cell text (course titles, user names, emails) follows a 1-line-clamp-with-ellipsis rule (`CONTENT_RESILIENCE.md § 1`); the full value is always available via the row's detail view (e.g. opening the course) or an accessible title/tooltip, never silently lost. Loading state uses skeleton rows (`LoadingState`); an empty result set uses `EmptyState` ("No courses match your filters," etc.); a failed load uses `ErrorState` with retry — no bespoke table-specific empty/error treatment.

---

## RTL and Content Resilience — cross-references

Every component above is specified for the *default* (LTR, ideal-content) case. Two documents extend it without duplicating it here:

- **[`LOCALIZATION.md`](./LOCALIZATION.md)** — how each of Navbar/Sidebar/MobileBottomNavigation, forms (TextField family), CourseCard/all cards, AppDialog/BottomSheet, and the course player behave when the layout direction is RTL (Arabic first), including which icons mirror (§ Icon System above) and which don't. **VideoPlayer's scrubber/timeline is the one component that deliberately never mirrors** (§ Media & Playback above) — everything else added in v1.2, including ReorderableList's drag direction and DataTable's sort-icon placement, follows the same logical-properties rule as every v1.1 component.
- **[`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md)** — exact wrapping/truncation rules per component when course/instructor names run long, images fail to load, data is empty, a network call fails, or translated/accessibility-scaled text is longer than the English baseline. CourseCard's "max 2 lines, ellipsis" title rule (§ Cards above) is one instance of the general rule defined there — check that file before inventing a new truncation behavior for a new component. v1.2 additions (`FileUpload` filename, `DataTable` cell text) extend that same table rather than inventing new rules.
