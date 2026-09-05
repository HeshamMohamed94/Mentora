# Mentora Design System — Localization & RTL Readiness

**Status (v1.3): English and Arabic are both locked, functional MVP languages** (`../product/PRODUCT_SPEC.md § 16`) — Arabic is not a future add-on this design system merely stays "ready" for; it is a language the first shipped product must actually work in, on Web, Android, and iOS alike. Every layout rule in this file was written from v1.0 onward to hold up unmodified under RTL, so the amount of *design-system* change required by this product decision is small (§§ 4 and 8 below, plus `COMPONENTS.md § Select / Dropdown`'s RTL notes) — the rules themselves were already correct; what changed is that they are now load-bearing on day one, not aspirational. This file is the contract: nothing here is optional groundwork for "later," it constrains how [`COMPONENTS.md`](./COMPONENTS.md) and every screen is built.

---

## 1. The Core Rule: Logical, Not Physical

Every layout property in Mentora is expressed in **logical (flow-relative) terms** — `start`/`end` — never physical `left`/`right`. This is a hard requirement, not a style preference: a component built with `margin-left`/`padding-right`/`text-align: left` has to be rewritten for RTL; a component built with `margin-inline-start`/`padding-inline-end`/`text-align: start` mirrors automatically when the document direction flips.

| Physical (never use) | Logical (always use) | Platform equivalents |
|---|---|---|
| `left` / `right` (margin, padding, position) | `inline-start` / `inline-end` | Web: CSS logical properties (`margin-inline-start`, `inset-inline-end`, `border-inline-start`…). Android: Compose `Modifier.padding(start=, end=)`, XML `paddingStart`/`paddingEnd`/`layout_marginStart` (never `Left`/`Right`). iOS: `leading`/`trailing` in Auto Layout and SwiftUI (`.padding(.leading, …)`, `HStack` alignment `.leading`/`.trailing`), never `.left`/`.right`. |
| `text-align: left/right` | `text-align: start/end` | Web: `text-align: start`. Android: `Modifier.fillMaxWidth()` + `TextAlign.Start`. iOS: `.multilineTextAlignment(.leading)`. |
| `float: left/right` | `float: inline-start/inline-end` (or flex/grid, preferred) | Web only; prefer Flexbox/Grid with logical alignment over float entirely. |
| Row direction assumed left-to-right | `flex-direction: row` (which already flips under `dir="rtl"`) — never hardcode a reversed row for LTR | Web: plain `row`, not `row-reverse`, so the browser's own RTL handling applies. Android/iOS: `LayoutDirection`-aware `Row`/`HStack`, not a manually mirrored one. |

**Direction source of truth:** the document/window direction (`dir="rtl"` on `<html>` for web, `LocalLayoutDirection` in Compose, `UITraitCollection.layoutDirection`/SwiftUI environment `\.layoutDirection` on iOS) is set once, at the root, from the active locale. Components never read the locale directly to decide their own mirroring — they respond to the inherited layout direction, the same way they respond to light/dark theme (see [`DESIGN_RULES.md` rule 10](./DESIGN_RULES.md)).

---

## 2. What Mirrors and What Doesn't

Mirroring is not "flip everything." Three categories:

| Always mirrors | Never mirrors | Content-dependent (mirrors if the *content* is directional) |
|---|---|---|
| Layout direction (nav position, sidebar side, card content order, form label/input alignment, dialog action order) | Numerals (Mentora uses Western Arabic numerals `0–9` in all locales, including Arabic UI — a documented decision, see § 6) | Progress/timeline scrubbers in the course player: kept **LTR always**, per the wide cross-product convention that "time moves left-to-right" regardless of UI direction (YouTube, Netflix, etc. all do this) — mirroring a video scrubber reads as broken, not localized |
| Directional icons (`chevron_left`↔`chevron_right`, `arrow_back`↔`arrow_forward`, etc. — full list in `design-tokens.json → icon.directional.mirrorInRtl`) | Non-directional icons (`play_arrow`, `check`, `search`, `star`, `close`… — full list in `icon.directional.neverMirror`). A play triangle never flips; it means "play" regardless of reading direction. | Embedded Latin text/brand names/URLs inside Arabic copy render LTR inline (Unicode bidi algorithm handles this automatically as long as text isn't manually reversed) |
| Swipe/back gesture direction (iOS edge-swipe-back originates from the trailing edge in RTL, not always the physical left) | The **Mentora wordmark/logo** — logotypes are brand identity, not layout, and are never mirrored, same convention as every major localized product | Chart/graph reading direction for trend data (bar charts read start-to-end, so they do mirror) |

---

## 3. Per-Area Rules

### Navigation

- **Navbar (public web):** logo stays at the layout **start** (left in LTR, right in RTL — it moves with the direction, it just isn't itself flipped). Nav links, search, and auth actions reorder to flow start→end exactly as in LTR, just mirrored as a whole.
- **Sidebar (authenticated web):** the sidebar moves to the layout end edge in RTL (right becomes left... no — the sidebar stays semantically "start-anchored" the same way it is in LTR; concretely, since Mentora's sidebar sits at the reading start in LTR, it sits at the reading start in RTL too, which is the visual right). Item icon + label order mirrors (icon at `inline-start` of the label in both directions — this falls out for free from using logical padding).
- **MobileBottomNavigation:** item order reverses to match reading order (Home first in reading-start position in both directions); icons that are directional (none of the 5 default items are) would mirror per § 2.
- **Tabs:** the active-tab underline indicator animates from the tab's `inline-start` edge, and tab order follows content reading order — this falls out automatically from not hardcoding `left`.
- **Breadcrumbs / back button:** `arrow_back`/`chevron_left` mirror to point toward the layout start, which is the visual right in RTL — this is exactly the directional-icon rule in § 2, not a special case.

### Forms (TextField / PasswordField / SearchField / Select)

- Label and helper/error text are `text-align: start`.
- Leading icon (e.g. `SearchField`'s search glyph) sits at `inline-start`; trailing icon (clear button, password toggle) sits at `inline-end`. These positions swap visually in RTL, but no component-level logic changes — only the logical property resolves differently.
- Input text itself is directionally **auto** (`dir="auto"` / platform equivalent) so a user typing an English course title inside an otherwise-Arabic UI isn't forced into RTL character order.
- Numeric/latin-only fields (email, price, course code) are explicitly forced LTR-direction for their input value even inside an RTL form, since email addresses and codes don't mirror.
- **Select/Dropdown (v1.3):** the trailing `expand_more`/`expand_less` indicator is non-directional and never mirrors (`design-tokens.json → icon.directional.neverMirror`); the field's leading icon (if any) follows the same `inline-start` rule as other inputs. The option menu opens below (or above, if there's no room) the field regardless of layout direction — RTL affects horizontal alignment/reading order within the menu (option text `text-align: start`), never the vertical open direction. Long Arabic option labels (e.g. category names) follow the same 1-line clamp as any other Select value (`CONTENT_RESILIENCE.md § 1`) — never a reason to widen the field beyond its token-defined size.

### Cards (CourseCard and the rest of the Cards family)

- All text within a card is `text-align: start`; the whole content stack (title → instructor → meta row → progress → action) keeps its **vertical** order unchanged — RTL affects horizontal flow, not vertical stacking.
- The meta row (rating · student count · duration, each with a small icon) reorders as a unit start-to-end; none of the icons used there (star, people, schedule) are directional, so they don't mirror individually — only their sequence position does, automatically, from flex/row logical direction.
- `CategoryChip` overlaid on the thumbnail sits at the same logical corner (`inline-start`, block-end) in both directions, not a hardcoded "bottom-left."
- The primary action button (`TonalButton`) remains full-width on mobile in both directions (no directional concern) and auto-width, start-anchored on web in both directions.

### Dialogs (AppDialog / BottomSheet)

- Dialog **title and body** are `text-align: start`.
- **Action button order mirrors:** in LTR, the lower-emphasis action (Cancel/Dismiss) sits at `inline-start` and the primary action sits at `inline-end`, closest to where a reading user's eye/hand naturally lands last — in RTL both swap position while keeping the same *semantic* order (secondary-then-primary reading start-to-end). Never hardcode "Cancel on the left."
- The dialog/sheet **close (`✕`) icon** sits at the `inline-end` corner of the header in both directions (a fixed logical position, not a fixed physical one).
- `BottomSheet`'s drag handle stays horizontally centered — direction-neutral, no change needed.

### DataTable *(v1.3 addition — closes a gap found during the Arabic/RTL review)*

- Column order reverses to match reading order (the first logical column reads at the layout start in both directions) — this falls out of using logical row/flex direction, not a per-column mirror rule.
- Cell text is `text-align: start`; numeric columns (counts, rates) stay `text-align: start` too (never a hardcoded right-align), consistent with § 1's "no physical left/right" rule.
- The row-actions affordance (`more_vert`, non-directional, never mirrors) sits at the row's `inline-end`, which is the visual left in RTL.
- Sort indicators and any directional chevrons used for expand/collapse follow the directional-icon rule in § 2 like any other chevron.
- Below `desktop`, DataTable collapses to stacked `label: value` cards (`../ux/RESPONSIVE_BEHAVIOR.md § 6`) — each pair is `text-align: start`, label and value order unchanged by direction (this is a vertical stack, so RTL affects only the text alignment within it, not row order).

### ReorderableList *(v1.3 addition — closes a gap found during the Arabic/RTL review)*

- The drag handle icon (`drag_indicator`, a vertical dot grid) is non-directional and never mirrors, same as the accessible Move Up/Move Down `IconButton`s already documented in [`COMPONENTS.md § ReorderableList`](./COMPONENTS.md) (`arrow_upward`/`arrow_downward`, also non-directional).
- The handle sits at the row's `inline-start`, item label/content follow at `inline-start` of the remaining space, and the Move Up/Down controls sit at `inline-end` — this reorders as a whole in RTL like any other row-based component, with "up"/"down" (vertical reordering) unaffected by horizontal direction since this component never reorders left/right.

### Course Player Layout

- Video **playback scrubber and time labels stay LTR always** (§ 2) — this is the one deliberate exception to full mirroring, and it must be called out in implementation so an engineer doesn't "fix" it into mirroring by default.
- Playback controls surrounding the scrubber (play/pause, volume, settings, fullscreen) lay out start-to-end and mirror as a group — a "skip forward 10s" icon is directional (`forward_10`-style) and is treated as content-dependent: it visually points toward the direction the timeline advances, which stays fixed (pointing right) regardless of UI direction, since it's paired with the non-mirroring scrubber.
- The **lesson/chapter list panel** (typically docked at one side on desktop/tablet) docks at the layout **end** in both directions (i.e., it moves from right in LTR to left in RTL) so it doesn't compete with the reading-start content area.
- **Captions/subtitles** render `text-align: center` (unaffected by direction) but the caption *text itself*, when in Arabic, renders RTL-shaped even while the video chrome around it follows the rule above.
- Chapter markers on the (non-mirrored) scrubber remain positioned by elapsed time, left-to-right, in both directions — consistent with the scrubber exception.

---

## 4. Typography for Arabic

Inter (web), Roboto (Android), and SF Pro (iOS) do not fully cover Arabic script (Inter has none; Roboto's Arabic coverage is inconsistent; SF Pro auto-substitutes). Mentora defines an explicit Arabic-script font stack per platform, matched for x-height/weight-availability to the Latin scale in [`DESIGN_SYSTEM.md § 2`](./DESIGN_SYSTEM.md) so the two scripts feel like one type system, not two bolted together:

| Platform | Latin font | Arabic font | Note |
|---|---|---|---|
| Web | Inter | **IBM Plex Sans Arabic** (fallback: Noto Sans Arabic) | Chosen for its geometric, modern character that pairs visually with Inter's grotesque forms — both read as "clean SaaS," not calligraphic/traditional. |
| Android | Roboto | **Noto Sans Arabic** | Google's own Arabic companion family for Roboto; consistent metrics across Android versions. |
| iOS | SF Pro | **SF Arabic** (automatic) | Apple substitutes SF Arabic automatically for Arabic text runs when using system font APIs — no extra bundling needed, matches the Latin weight scale 1:1. |

The `typography.scale` tokens (size/line-height/weight/letter-spacing) apply unchanged — only the `fontFamily` binding differs per script. **Letter-spacing is disabled for Arabic runs** (Arabic is a cursive/connected script; adding tracking breaks letter joining) — implementations detect the script of the text run and zero out `letterSpacing` for Arabic regardless of which typography token is active. **Line-height gets a +10% bump for Arabic body text** (`typography.body.*`), since Arabic diacritics and ascenders/descenders need more vertical room than the Latin metrics assume — this is the one token value that is intentionally script-dependent, and it's documented here rather than silently baked into the base scale.

---

## 5. Directional Icons — Source of Truth

The canonical mirror/don't-mirror lists live in `design-tokens.json → icon.directional` (`mirrorInRtl` / `neverMirror`), referenced from [`DESIGN_SYSTEM.md § 8`](./DESIGN_SYSTEM.md). When a new icon is introduced, it must be added to exactly one of those two lists before use — an icon with undeclared directionality is treated as `neverMirror` by default (safer default: a wrongly-static icon is a minor polish issue, a wrongly-mirrored icon can invert meaning, e.g. flipping a "next" arrow into "back").

---

## 6. Long/Expanding Translated Strings

- German/French/Spanish UI strings typically run **25–35% longer** than English; Arabic is often **comparable or shorter** in character count but visually taller (diacritics) and requires the line-height bump in § 4.
- No component in [`COMPONENTS.md`](./COMPONENTS.md) uses a fixed pixel width for a text container that isn't also allowed to wrap or grow — see [`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md) for the exact wrap/truncate rule per component. Localization expansion and "long content" are the same underlying resilience problem; that file is the single source for truncation behavior, this file only adds the direction-specific pieces (start/end alignment of wrapped text, RTL-correct ellipsis position).
- Button labels are the one place expansion is checked hardest: a `PrimaryButton` label that would overflow at `space.6` horizontal padding wraps to two lines (button height grows) rather than clipping — a clipped call-to-action is a worse failure than a taller button.

---

## 7. Locale-Aware Formatting *(v1.3)*

Dates, numbers, durations, and prices are **presentation-formatted per active UI locale**, not hardcoded to one English convention. This is a formatting-layer rule, not a new visual token — the underlying `typography.*` tokens rendering these values are unchanged; only the platform locale-formatting API's output differs.

| Value type | English (en) presentation | Arabic (ar) presentation | Notes |
|---|---|---|---|
| Date | e.g. "Sep 3, 2026" | e.g. "٣ سبتمبر ٢٠٢٦" or "3 سبتمبر 2026" depending on the numeral decision below | Use the platform's locale-aware date formatter (`Intl.DateTimeFormat`, `DateFormatter`+locale, Android `DateFormat`) — never a hand-built date string. |
| Numbers / counts | "1,234 students" | "١٬٢٣٤ طالب" or "1,234 طالب" (Western numerals, per § 8's locked decision) | Grouping/decimal separators follow locale convention via the platform formatter; the *numeral glyphs* stay Western Arabic (`0–9`) per the locked decision in § 8 — an intentional, documented divergence from "fully native" Arabic formatting, in favor of consistency and avoiding mixed-numeral confusion. |
| Duration | "2h 30m" | Arabic-labeled equivalent (e.g. "٢ س ٣٠ د" pattern or spelled-out equivalents) — exact string is a copy/localization-file decision, not a design-system one | Component-level: `typography.caption`/`body.small` tokens unchanged; only the string content is locale-aware. |
| Price | "EGP 899" | Arabic-appropriate currency presentation (currency label/symbol placement can follow locale convention; numeral glyphs stay Western per § 8) | `../product/DEMO_PAYMENT_FLOW.md § 5`'s demo prices are formatted the same way in both locales — formatting changes presentation, never the underlying demo price value or the fact that it's a non-real charge. |

**What this file does not decide:** the specific formatting library/API (`Intl`, ICU, platform-native formatters) is a Technical Architecture decision, out of scope here — this section only locks the *product requirement* that formatting is locale-aware, and the *design* constraint that formatted values still fit within each component's existing wrap/truncate rules (`CONTENT_RESILIENCE.md § 1`) regardless of which locale's formatting produced a longer or shorter string.

---

## 8. Locked Decisions (§ 14 review)

- **Numeral system:** Western Arabic numerals (`0–9`) everywhere, including Arabic UI — see § 7 for where this applies (dates, counts, durations, prices). Matches the majority of modern Gulf/Levant SaaS products and avoids mixed-numeral confusion in course counts, prices, and timestamps.
- **Video scrubber stays LTR** in all locales — documented exception to full mirroring, called out explicitly so it isn't "corrected" into mirroring by a future contributor.
- **Wordmark never mirrors.**
- **Arabic is a locked, functional MVP language (v1.3)** — not Post-MVP, not "readiness only." See the status note at the top of this file and `../product/PRODUCT_SPEC.md § 16`.
- Everything else in this file mirrors via **logical properties**, not per-locale conditional code — a screen that hardcodes `if (isRTL) { … }` for anything other than the two exceptions above is doing it wrong; the layout should mirror because the underlying CSS/Compose/SwiftUI direction primitive mirrors it, not because a screen branched on locale.
