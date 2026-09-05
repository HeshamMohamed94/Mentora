# Mentora Design System — Accessibility

**Target: WCAG 2.1 AA**, across Web, Android, and iOS. This is a floor, not a ceiling — accessibility is validated per-component before it ships, not retrofitted after a screen is "done."

---

## 1. Contrast

| Content type | Minimum ratio | Applies to |
|---|---|---|
| Normal text (< 18pt / < 14pt bold) | 4.5:1 | `text.primary`/`text.secondary` on `background.*`/`surface.*` |
| Large text (≥ 18pt, or ≥ 14pt bold) | 3:1 | Headings, `display.*`, `heading.*` |
| UI components & graphical objects | 3:1 | Borders on inputs, focus indicators, icon-only controls, chart/graph elements |
| Disabled content | No minimum (WCAG exempts disabled UI) | `text.disabled` — still kept legible (~3:1) as a UX courtesy, not a compliance requirement |

Every semantic color pairing in [`DESIGN_SYSTEM.md § 1`](./DESIGN_SYSTEM.md) (e.g. `brand.onPrimary` on `brand.primary`, `success.onSuccessContainer` on `success.container`) is chosen to clear the relevant ratio above in **both** themes. When a new token pairing is proposed, check contrast before adding it to `design-tokens.json` — do not assume a container/on-container pair is safe by analogy.

**Never** place `text.primary` or `text.secondary` directly on a brand/status surface — always use the matching `onX`/`onXContainer` token, which is the one guaranteed to be contrast-checked.

---

## 2. Focus States

- Every interactive element (button, link, input, chip, card, nav item, tab) has a visible focus indicator when reached via keyboard (web) or focus navigation (Android D-pad/TalkBack, iOS Switch Control/keyboard).
- Focus indicator: `border.width.focus` (2px) outline in `color.border.focus`, offset 2px from the element's edge, radius matching the element's own radius token + 2px.
- Focus is never removed (no `outline: none` without a replacement) and never relies on color alone — the 2px outline geometry is itself the signal.
- Focus order follows visual/reading order (top-to-bottom, left-to-right in LTR). Modals/sheets trap focus while open and return it to the triggering element on close.

---

## 3. Keyboard Navigation (Web)

- All interactive components reachable and operable via `Tab`/`Shift+Tab`, `Enter`/`Space` (activation), `Esc` (dismiss dialogs/sheets/menus), and arrow keys where a native pattern expects them (tabs, radio groups, menus, the quiz answer-option list).
- No keyboard traps outside intentional focus-trapping in modals/sheets (which must always offer an `Esc`/close-button escape).
- Skip-to-content link at the very start of the DOM on every page using `Navbar`/`Sidebar`.

---

## 4. Touch Targets

| Platform | Minimum | Preferred |
|---|---|---|
| Android | 48dp | 48dp (Material minimum, use as default) |
| iOS | 44pt minimum | 44pt |
| Web | 44px recommended for primary/important controls | 44px |

Applies to all tappable controls: buttons, icon buttons, chips, nav items, checkboxes/radios (the *hit area*, not necessarily the visual glyph — e.g. a 24px checkbox icon still gets a 44–48px tap target via padding). `IconButton`'s 40×40 visual size is padded to meet this at the touch-target layer (see [`COMPONENTS.md`](./COMPONENTS.md)); where a design calls for a visually smaller control, the *hit slop* still expands to the platform minimum.

---

## 5. Screen Reader Labels

- Every `IconButton` and icon-only control ships a text label for assistive tech (`aria-label` on web, `contentDescription` in Compose, `accessibilityLabel` in SwiftUI). No icon-only control ships without one.
- Images (course thumbnails, instructor photos, certificate previews) get meaningful alt text; purely decorative images (background flourishes) are marked decorative/hidden from the accessibility tree.
- Form fields (`TextField`/`PasswordField`/`SearchField`) programmatically associate their visible label with the input (`<label for>` / `labelText` / accessibility label), not placeholder-only labeling — placeholders disappear on input and are not a substitute for a label.
- Live regions: async status changes (form submit success/error, AI Tutor new message, quiz submit result) announce via `aria-live="polite"` (web), `LiveRegion`/accessibility announcements (Compose), or `.accessibilityAddTraits(.updatesFrequently)` / `UIAccessibility.post(notification:)` (iOS) — don't rely on sighted-only visual feedback (a color change or toast) for state that matters.
- Custom components built on non-semantic elements (e.g. a `div`-based Tabs or a Compose `Row`-based nav) carry the correct ARIA role/Compose semantics/SwiftUI accessibility traits (`role="tab"`, `Modifier.semantics`, `.accessibilityAddTraits(.isButton)`) so they read correctly instead of as generic containers.

---

## 6. Disabled-State Contrast

Disabled controls are exempt from the 4.5:1/3:1 minimums by WCAG, but Mentora still keeps `text.disabled` legible enough to be read as "present but inactive" rather than invisible. Measured against `background.primary`: **2.63:1 in light theme, 4.22:1 in dark theme** (see § 12 for the full computed table) — below the informal AA text threshold in light theme, which is expected and acceptable *because* WCAG explicitly exempts disabled-component text from contrast minimums (SC 1.4.3 exception). The value is a deliberate legibility choice, not a compliance claim. Disabled controls are also removed from the tab order (or marked `aria-disabled`/`disabled` so assistive tech announces the state rather than silently skipping or, worse, treating them as active).

---

## 7. Error Accessibility

- Every `TextField` error state pairs three signals, never just color: the `color.border.error` outline, an error icon, **and** the error text itself (`typography.caption`, `color.error.default`) — see [`COMPONENTS.md § Inputs`](./COMPONENTS.md).
- The error text is programmatically linked to its field (`aria-describedby` / equivalent) so a screen reader announces it when the field receives focus, not only when visually scanned.
- Form-level submit errors announce via a live region and move focus to the first invalid field (web) or announce via accessibility notification (mobile) — the user is never left to hunt for what went wrong.
- Per [`COMPONENTS.md § ErrorState`](./COMPONENTS.md), raw backend errors/stack traces/status codes are never surfaced to the user — only mapped, friendly copy, which keeps error announcements meaningful to assistive-tech users too.

---

## 8. Color Is Never the Only Signal

Applies system-wide, not just to errors:

- Quiz correctness: icon + text + color (§ Quiz System in `COMPONENTS.md`).
- Progress "Complete" state: color change **plus** a checkmark icon, not color alone.
- Status badges (success/warning/error/info): always paired with a label, not a bare colored dot, when used to convey meaningful state (a bare status dot is acceptable only for low-stakes presence indicators like "online").
- Links inside body text: underlined or otherwise distinguished by more than color alone (weight/underline), since `text.link` may not be distinguishable from `text.primary` for color-blind users at a glance.

---

## 9. Motion & Reduced Motion

- Respect `prefers-reduced-motion` (web), `Settings > Accessibility > Reduce Motion` (iOS), and the system "remove animations" setting (Android): when active, cross-fade or cut instantly instead of playing slide/scale transitions defined in [`DESIGN_SYSTEM.md § 7`](./DESIGN_SYSTEM.md). Functional feedback (e.g. a button's pressed state) may keep a very short opacity change but drops translation/scale.
- No auto-playing animation loops longer than 5 seconds without a pause control (relevant to any future marketing/illustration use).

---

## 10. Browser Zoom, Android Font Scaling, iOS Dynamic Type

Three platform mechanisms let a user scale text/content independent of Mentora's own settings. All three are first-class, tested states — not edge cases.

| Mechanism | Requirement | How Mentora supports it |
|---|---|---|
| **Web browser zoom** | Content and functionality remain usable up to 200% zoom (WCAG 1.4.4) with no loss of content/functionality, and reflows with no horizontal scrolling at 400% zoom equivalent to a 320px CSS-pixel viewport (WCAG 1.4.10, "Reflow") | Typography tokens are bound in `rem` (§ 2.1 of `DESIGN_SYSTEM.md`), so zoom scales type correctly; layout uses the responsive breakpoints/grid (`DESIGN_SYSTEM.md § 9`) so content reflows to the mobile column count rather than requiring horizontal scroll. |
| **Android font scaling** | Settings → Display → Font size, up to the system's largest step, must not clip or overlap text | Typography tokens are bound in `sp` (§ 2.1), which scales automatically; layout containers use intrinsic/wrap sizing rather than fixed pixel heights for anything holding text, per [`CONTENT_RESILIENCE.md § 1`](./CONTENT_RESILIENCE.md). |
| **iOS Dynamic Type** | Text must scale via Dynamic Type text styles, including the accessibility (AX1–AX5) range, without clipping | Typography tokens are bound through Dynamic-Type-aware `Font`/`UIFont` APIs (§ 2.1); components follow the same wrap-before-clip rules as the other platforms. |

**Testing bar:** every screen is checked at 200% browser zoom, the largest standard Android font-scale step, and iOS's largest accessibility Dynamic Type size before being considered done — not just the platform's default text size.

---

## 11. High Text Scaling — Structural Consequences

Scaling text 2–3x can't be absorbed by font-size alone; it changes what layouts are even possible. Rules:

- **Reflow, don't clip.** Every wrap/truncate rule in [`CONTENT_RESILIENCE.md § 1`](./CONTENT_RESILIENCE.md) is the rule at *every* text scale, including maximum — a component that clips at 100% and wraps at 200% has two behaviors to maintain and test; Mentora components have one.
- **Fixed-height text containers are not allowed.** Any container holding user- or CMS-authored text sizes to its content (min-height at most, never a hard max that clips) — see `CONTENT_RESILIENCE.md § 8`'s locked rule.
- **Multi-column layouts collapse before text overlaps.** A `CourseCard` grid at `desktop` (3 columns) that would overlap text at maximum Android font scale drops to fewer columns rather than letting cards visually collide — treated the same as a viewport-width breakpoint change, driven by available inline space per card, not just raw screen width.
- **Icon-only fallback is pre-defined, not improvised.** `MobileBottomNavigation`'s icon-only-at-extreme-scale fallback (`CONTENT_RESILIENCE.md § 1`) is the model: when a component truly cannot accommodate maximum text scale in its normal form, its reduced form is specified in advance, and the reduced form still carries full accessible labeling even when the visible text is hidden.
- **Touch targets don't shrink** to make room for larger text — § 4's 44/48px/pt minimums hold regardless of text scale; layout gives way before touch targets do.

---

## 12. Long Translated Content

Complements § 10–11 for the specific case of localization rather than user text-scale preference — full detail lives in [`LOCALIZATION.md`](./LOCALIZATION.md) (direction/mirroring) and [`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md) (wrap/truncate per component); this section is the accessibility framing of the same problem:

- A translated string that triggers a component's wrap/truncate behavior (`CONTENT_RESILIENCE.md § 1`) must still expose the **full, untruncated** string to assistive technology via the accessible name/label, even where the visible text is clamped — a screen-reader user is never given less information than a sighted user could get by hovering for a tooltip or expanding the card.
- RTL text (§ `LOCALIZATION.md`) is exposed with the correct `lang`/direction metadata (`lang="ar"` + inherited `dir="rtl"` on web, locale-aware accessibility traits on Android/iOS) so assistive tech uses correct pronunciation and reading-order rules, not just correct visual mirroring.
- Mixed-direction strings (e.g. an English course title inside an Arabic sentence) rely on the platform's bidi algorithm rather than manual character reordering, so both visual rendering and screen-reader reading order stay correct together.

**v1.3 — confirmed for the now-locked Arabic UI (`../product/PRODUCT_SPEC.md § 16`, `LOCALIZATION.md § 7`):** every rule in §§ 1–14 of this file already applies identically regardless of active language — contrast ratios, focus indicators, touch targets, screen-reader labeling, browser zoom, Android font scaling, iOS Dynamic Type, and reduced motion are UI-language-agnostic by construction (they're properties of tokens and components, not of the English strings used to demo them). No accessibility rule required a change to support Arabic as a functional MVP language; Arabic content simply exercises the same rules with different text and direction. Accessibility labels (`aria-label`/`contentDescription`/`accessibilityLabel`) are themselves localizable strings and must ship an Arabic equivalent alongside every English one once actual localization files exist (implementation detail, deferred to Technical Architecture per `../product/PRODUCT_SPEC.md`).

---

## 13. Verified Contrast Ratios

Every semantic color pairing referenced from [`DESIGN_SYSTEM.md § 1.4`](./DESIGN_SYSTEM.md) was computed programmatically (WCAG relative-luminance contrast formula), not eyeballed. Re-run whenever a color token in `design-tokens.json → color.semantic` changes.

| Pair | Light | Dark | Requirement |
|---|---|---|---|
| `brand.onPrimary` on `brand.primary` | 5.40:1 | 8.12:1 | 4.5:1 |
| `brand.onPrimary` on `brand.primaryHover` | 6.79:1 | 6.59:1 | 4.5:1 |
| `brand.onPrimary` on `brand.primaryPressed` | 8.05:1 | 5.34:1 | 4.5:1 |
| `brand.onPrimaryContainer` on `brand.primaryContainer` | 10.99:1 | 6.46:1 | 4.5:1 |
| `brand.primary`/`text.link` on `background.primary` | 5.13:1 | 11.24:1 | 4.5:1 |
| `brand.primary`/`text.link` on `surface.default` | 5.40:1 | 10.43:1 | 4.5:1 |
| `secondary.onSecondary` on `secondary.default` | 4.89:1 *(corrected v1.1, was 4.21:1)* | 8.05:1 | 4.5:1 |
| `secondary.onSecondaryContainer` on `secondary.container` | 11.14:1 | 7.65:1 | 4.5:1 |
| `text.primary` on `background.primary` | 16.33:1 | 16.55:1 | 4.5:1 |
| `text.primary` on `surface.default` | 17.19:1 | 15.36:1 | 4.5:1 |
| `text.secondary` on `background.primary` | 5.93:1 | 10.97:1 | 4.5:1 |
| `text.secondary` on `surface.default` | 6.24:1 | 10.18:1 | 4.5:1 |
| `text.secondary`/`onPrimaryContainer` on `brand.primaryContainer` (LearningPathCard) | 5.08:1 | 4.66:1 / 6.46:1 | 4.5:1 |
| `success.onSuccess` on `success.default` | 5.13:1 | 6.96:1 | 4.5:1 |
| `success.onSuccessContainer` on `success.container` | 12.31:1 | 9.46:1 | 4.5:1 |
| `warning.onWarning` on `warning.default` | 5.89:1 *(corrected v1.1, was 2.53:1)* | 7.35:1 | 4.5:1 |
| `warning.onWarningContainer` on `warning.container` | 11.10:1 | 8.64:1 | 4.5:1 |
| `error.onError` on `error.default` | 6.46:1 | 10.09:1 | 4.5:1 |
| `error.onErrorContainer` on `error.container` | 13.26:1 | 7.24:1 | 4.5:1 |
| `info.onInfo` on `info.default` | 4.60:1 | 7.92:1 | 4.5:1 |
| `info.onInfoContainer` on `info.container` | 11.44:1 | 7.12:1 | 4.5:1 |
| `text.disabled` on `background.primary` *(exempt, § 6)* | 2.63:1 | 4.22:1 | n/a |
| `border.focus` vs. surface (UI component) | 5.40:1 | — | 3:1 |
| `border.strong` vs. `surface.default` (UI component) | 3.34:1 *(corrected v1.1, was 1.67:1)* | 3.64:1 *(corrected v1.1, was 2.00:1)* | 3:1 |
| `brand.onSurfaceInverse` on `surface.inverse` | 10.33:1 | 4.78:1 | 4.5:1 |

All rows pass their requirement except the documented, WCAG-exempt disabled-text row. The three "corrected v1.1" rows are the fixes detailed in [`DESIGN_SYSTEM.md § 1.1`](./DESIGN_SYSTEM.md).

---

## 14. v1.2/v1.3 Component-Specific Accessibility Notes

Full state/token specs are in [`COMPONENTS.md`](./COMPONENTS.md) — this section is the accessibility contract for each component added since v1.1, called out explicitly because each has a specific failure mode if built naively.

| Component | Requirement |
|---|---|
| **Toggle / Switch** | Implement with the platform's **native** switch control (Web `role="switch"`, Compose `Switch`, SwiftUI `Toggle`) — inherits correct `aria-checked`/state announcements, keyboard operability (Space/Enter to toggle), and focus handling automatically. A custom-built div/View reproducing the visual without the native role is non-compliant. **v1.3:** when a Toggle conveys a named state (Published/Draft), the accompanying text label is part of the control's accessible name (e.g. "Published, on" / "Draft, off"), not conveyed by `aria-checked` state alone — a screen-reader user must hear *what* is toggled on, not just *that* something is. |
| **Select / Dropdown** *(v1.3)* | Web: implement as `role="combobox"` (trigger) + `role="listbox"`/`role="option"` (menu) with `aria-expanded`, `aria-activedescendant`, and `aria-selected` kept in sync with the visual Open/Selected states — see keyboard behavior in `COMPONENTS.md § Select / Dropdown`. Android/iOS: use the platform's native picker-equivalent control (`ExposedDropdownMenuBox`/`Menu`+`Picker`) so TalkBack/VoiceOver announce option count, position, and selection automatically. Disabled options are excluded from the accessibility tree's interactive traversal, not merely visually dimmed. |
| **ReorderableList / DragHandle** | Drag-and-drop is **never** the only way to reorder. The Move Up/Move Down `IconButton`s are always present (not hover/focus-revealed-only) and fully keyboard-operable, each announcing the item and its new position after the move (a live region or equivalent, per § 5). Drag itself, where implemented, additionally needs platform drag-accessibility support (e.g. Android's accessibility drag actions, iOS's accessibility custom actions) — the non-drag controls are the accessibility baseline regardless of whether that's present. |
| **DataTable** | Uses real table semantics (`<table>`/`<th scope>` on web, equivalent semantics via Compose/SwiftUI accessibility APIs) when rendered as a table, so headers are announced with their cells. The narrow-viewport **collapsed card** layout (`COMPONENTS.md`) carries the same label:value semantic association (e.g. each pair exposed as a labeled group) — collapsing to cards must not collapse the semantic relationship between a column header and its value. |
| **VideoPlayer / PlaybackControls** | Every control button (play/pause, volume, fullscreen, speed, and the scrubber itself) has a text label for assistive tech per § 5 (e.g. "Play", "Mute", "Seek: 3 minutes 12 seconds of 12 minutes"). The scrubber's fixed-LTR behavior (`LOCALIZATION.md § 3`) is exposed with correct `aria-valuenow`/equivalent regardless of app layout direction — RTL affects reading order elsewhere on the page, never the reported playback position. |
| **Checkout / OrderSummary** | The demo-payment notice text is programmatically associated with the confirm action's context (not just visually adjacent) so a screen-reader user hears "Demo Payment — no real charges..." as part of reviewing the order, not as an easily-skipped aside. |
| **SuccessState** | The success announcement (e.g. "Payment Successful, you're now enrolled") fires as a live-region announcement per § 5 when the state appears, not only rendered visually — a screen-reader user gets the same confirmation a sighted user gets from the animation. |
| **File/Media Upload** | Drag-over is a visual-only affordance; every FileUpload also has a real, keyboard-reachable "Browse" `TonalButton` that opens the platform's native file picker — drag-and-drop is additive, never the only path to attach a file. Upload progress and success/error states announce via a live region (§ 5), matching the existing rule for async status changes. |

---

## 15. Platform-Specific Notes

| Platform | Tooling to validate against |
|---|---|
| Web | Axe/Lighthouse accessibility audit, keyboard-only pass, VoiceOver (Safari) / NVDA (Windows) smoke test, 200%/400% browser zoom pass |
| Android | Accessibility Scanner, TalkBack smoke test, Compose semantics tree review, largest system font-scale pass |
| iOS | Accessibility Inspector, VoiceOver smoke test, Dynamic Type stress test (largest accessibility size) |

Every new component in [`COMPONENTS.md`](./COMPONENTS.md) is checked against this file before it's considered implementation-ready — not after a screen ships.
