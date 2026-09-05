# Mentora Design System v1.3.2

**Product:** Mentora — *Learn. Build. Grow.*
**Direction:** Material 3 Expressive-inspired · Modern SaaS · Educational · Clean · Professional · Minimal · Friendly
**Scope:** Web, Android (Jetpack Compose), iOS (SwiftUI)
**Status:** Source of truth. Created before any product screen. All future UI must derive from this document and `design-tokens.json`.

This is the master narrative document. Machine-readable values live in [`design-tokens.json`](./design-tokens.json) and [`themes/theme-light.json`](./themes/theme-light.json) / [`themes/theme-dark.json`](./themes/theme-dark.json). Component-level specs live in [`COMPONENTS.md`](./COMPONENTS.md). Token → platform code mapping (incl. unit mapping) lives in [`platform-mapping.md`](./platform-mapping.md). Accessibility rules live in [`ACCESSIBILITY.md`](./ACCESSIBILITY.md). RTL/localization rules live in [`LOCALIZATION.md`](./LOCALIZATION.md). Content-resilience rules (long text, missing data, failure states) live in [`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md). Governance and version history live in [`DESIGN_RULES.md`](./DESIGN_RULES.md) and [`CHANGELOG.md`](./CHANGELOG.md).

> **v1.1 note:** this revision adds the explicit 3-layer token architecture, a formal naming convention, unit mapping, responsive typography, RTL/localization rules, extended accessibility requirements, a full interaction-state matrix, content-resilience rules, and locks the decisions flagged after v1.0. It also corrects three color values that failed WCAG AA on verification (§ 1.1). Nothing in v1.0 was restarted — see [`CHANGELOG.md`](./CHANGELOG.md) for the itemized diff.

> **v1.2 note:** a controlled amendment adding exactly seven components that [product planning](../product/SCREEN_INVENTORY.md) proved were necessary — VideoPlayer/PlaybackControls, Checkout/OrderSummary, SuccessState, Toggle/Switch, File/Media Upload, ReorderableList/DragHandle, and DataTable (see `COMPONENTS.md`). Every new value is a `component.*` alias into an **existing** primitive/semantic token — v1.2 adds **zero** new colors, spacing steps, radii, typography styles, or motion values. The Primitive → Semantic → Component architecture (§ 0.1), naming convention (§ 0.2), light/dark resolution model, RTL rules, and accessibility requirements are unchanged and were verified to still hold for all seven additions. Nothing in v1.0/v1.1 was restarted — see [`CHANGELOG.md`](./CHANGELOG.md).

> **v1.3 note:** a pre-implementation patch adding the **Dropdown/Select** component (retiring the v1.2 native-`<select>` stopgap) and formalizing what's needed to support **English + Arabic as locked, functional MVP languages** — not new RTL groundwork, since `LOCALIZATION.md`'s logical-properties architecture has been correct since v1.0/v1.1, but a small set of formalizations: Arabic font-family tokens (previously prose-only), locale-aware formatting guidance, and Select's own RTL notes. Zero new colors, spacing steps, radii, or motion values; the Latin type scale, brand palette, Light/Dark resolution model, and every existing component's visual identity are unchanged. See [`CHANGELOG.md`](./CHANGELOG.md).

> **v1.3.1 note:** a non-visual consistency patch, no redesign. Removes two raw `@50%` opacity modifiers that had drifted back into `design-tokens.json → component.input.border.disabled` and `component.chip.background.disabled` — both now resolve to the plain semantic token (`color.border.default`, `color.surface.variant`) exactly as `COMPONENTS.md` and `CHANGELOG.md` 1.1.0 already documented, and consistent with `component.select.border.disabled`'s existing pattern. Also corrects stale "Design System v1.1/v1.2" current-version references in `../product/PRODUCT_SPEC.md` and three UX files to v1.3.1. No token added, no color/spacing/radius/typography/motion value changed, no MVP scope or screen count changed. See [`CHANGELOG.md`](./CHANGELOG.md).

> **v1.3.2 note:** one final targeted defect fix, no redesign. `design-tokens.json → component.button.primary.background.disabled` was a solid `{color.brand.primary}` — identical to its own `default` state, so a disabled Primary Button rendered visually indistinguishable from an enabled one by background. Corrected to `{color.surface.default}+{color.brand.primary}@{state.disabledContainerOpacity}`, using the same base+overlay@opacity compositing syntax already established by `component.button.tonal.background.hover/pressed`, and matching what `COMPONENTS.md`'s PrimaryButton Disabled row already documented ("`color.brand.primary` @ `state.disabledContainerOpacity` over `surface.default`"). No new color or opacity value introduced — `state.disabledContainerOpacity` (0.12 light / 0.16 dark) already existed and is used elsewhere (`Toggle`'s disabled track). `PrimaryButton`'s disabled text stays `color.text.disabled`, unchanged. No other token touched. See [`CHANGELOG.md`](./CHANGELOG.md).

---

## 0. Design Principles

1. **Semantic before literal.** No screen references a raw hex, px, or arbitrary style — only tokens.
2. **One visual language, three platforms.** Web, Android, and iOS look like the same product wearing different (native) shoes. Platform conventions (nav patterns, gestures) may differ; color, type, spacing, and shape do not.
3. **Soft, not flat, not heavy.** Rounded surfaces (12–24px radii), borders over shadow, shadow used sparingly for true elevation (menus, modals) not decoration.
4. **Strong hierarchy, minimal noise.** Type scale and spacing carry hierarchy — not extra colors or borders.
5. **Calm motion.** Motion confirms state changes; it never entertains.
6. **AA by default.** Every token pairing documented here meets WCAG AA at time of writing (see [ACCESSIBILITY.md](./ACCESSIBILITY.md)).

### 0.1 Token Architecture — Primitive → Semantic → Component

Every value in Mentora flows through exactly three layers. Nothing above layer 1 is allowed to hold a literal value that isn't itself an alias of the layer below it.

| Layer | What it is | Example | Who consumes it |
|---|---|---|---|
| **1. Primitive** | Raw, context-free values with no meaning attached — a color swatch, a number. Lives under `color.primitive.*` in `design-tokens.json`. | `purple.500 = #6558D3` | Nobody, directly. Primitives exist only so semantic tokens can be generated/audited from one palette. |
| **2. Semantic** | Purpose-named tokens that resolve differently per theme. This is where "what is this color/size *for*" is answered. Lives under `color.semantic.{light,dark}.*`, `typography.scale.*`, `spacing.scale.*`, `shape.radius.*`, `elevation.*`, `border.*`, `motion.*`, `icon.*`. | `brand.primary` → `#6558D3` (light) / `#C8C2FF` (dark), both aliasing `purple.500` / `purple.200` | Screens and components reference these directly. This is the **default** consumption layer, and what `COMPONENTS.md` documents in its tables. |
| **3. Component** | Component-scoped names that alias a semantic token, giving a component's own vocabulary a stable identity independent of which semantic token backs it today. Lives under `component.*` in `design-tokens.json`, written as `{path.to.semantic.token}` aliases. | `component.button.primary.background.default → {color.brand.primary}` | Platform code generation (Style Dictionary or equivalent) targets this layer when a component's styling needs to be swappable without touching every call site — e.g. if PrimaryButton's background is later re-themed to `brand.accent` instead of `brand.primary`, only the layer-3 alias changes, not every screen. |

**Resolution chain, worked example:** `purple.500` (primitive) → `color.semantic.light.brand.primary` (semantic, theme-resolved) → `component.button.primary.background.default` (component alias) → consumed on Web as `--btn-primary-bg: var(--color-brand-primary)`, on Android as `MentoraTheme.component.button.primary.background`, on iOS as `Color.mentoraComponentButtonPrimaryBackground`.

**Rule of thumb:** most component work only ever needs to reach into the **semantic** layer (that's what `COMPONENTS.md` shows). Reach for the **component** layer only when a component genuinely needs a name decoupled from the semantic token behind it. Never reach for **primitive** values from product/component code — see [`DESIGN_RULES.md`](./DESIGN_RULES.md) rule 1.

### 0.2 Naming Convention

One convention, used identically across color, typography, spacing, shape, elevation, border, motion, and icon tokens:

```
{category}.{concept}.{property}[.{variant}][.{state}]
```

- **category** — the top-level domain: `color`, `typography`, `spacing`, `shape`, `elevation`, `border`, `motion`, `icon`, `component`.
- **concept** — the semantic group: `brand`, `text`, `surface`, `border`, `success`, `heading`, `body`, `button`, `input`…
- **property** — the specific facet: `primary`, `secondary`, `large`, `medium`, `radius`, `duration`…
- **variant** *(optional)* — a named variation of the same concept: `primary` vs `tonal` vs `text` button, `h1` vs `h2` heading.
- **state** *(optional)* — an interaction/status suffix: `default`, `hover`, `pressed`, `focused`, `disabled`, `error`.

Segments are `lowerCamelCase`, dot-separated, and never abbreviated beyond what's already established (`bg` is never used in a token name — write `background`). Examples across categories:

| Token | Reads as |
|---|---|
| `color.brand.primaryHover` | color → brand concept → primary property → hover state |
| `color.success.onSuccessContainer` | color → success concept → text-on-container property |
| `typography.heading.h3` | typography → heading concept → h3 variant |
| `spacing.scale.space.4` | spacing → scale concept → step 4 |
| `shape.radius.large` | shape → radius concept → large property |
| `component.button.primary.background.hover` | component → button concept → primary variant → background property → hover state |

**Documentation shorthand vs. JSON path:** `COMPONENTS.md`/`DESIGN_SYSTEM.md` reference tokens by a readable shorthand rather than the literal JSON key path, to avoid repeating a category name that's already obvious from context. The mapping is fixed and mechanical:

| Doc shorthand | Full JSON path |
|---|---|
| `space.4` | `spacing.scale["space.4"]` |
| `radius.large` | `shape.radius.large` |
| `typography.heading.h4` | `typography.scale["heading.h4"]` |
| `color.brand.primary` | `color.semantic.{light,dark}["brand.primary"]` |
| `state.hoverOpacity` | `color.semantic.{light,dark}["state.hoverOpacity"]` (referenced without the `color.` prefix since it's a modifier, not a color itself) |
| `elevation.2` | `elevation["2"]` |
| `motion.duration.fast` | `motion.duration.fast` (already 1:1) |

`design-tokens.json` is authoritative for values; this table exists so the shorthand never reads as a typo or a missing token.

**Platform translation is mechanical, not creative:** the dot-path becomes kebab-case for a CSS custom property, camelCase for a Kotlin/Swift accessor — see [`platform-mapping.md`](./platform-mapping.md) for the exact transform per category. No platform binding introduces its own name for a concept the token system already names.

**One documented naming ambiguity:** `color.secondary.*` (the indigo/blue secondary brand hue, e.g. `secondary.default = #4A62F0`) is unrelated to the `SecondaryButton` **component** (which is an outlined style of the *primary* brand color, not a use of `color.secondary.*`). This is a naming collision inherited from the brief's own vocabulary ("Secondary" as both a brand color and a button variant). It is called out explicitly here rather than silently renamed, to avoid unnecessary churn — see [`DESIGN_RULES.md § Locked Decisions`](./DESIGN_RULES.md).

---

## 1. Color Tokens

Raw values live under `color.primitive.*` in `design-tokens.json`. **Product code must only reference `color.semantic.*` (or the flattened `theme-light.json` / `theme-dark.json`).** Primitives exist purely so both themes can be generated from one palette.

### 1.1 Refinements vs. the brief

The brief's starting palette is used as-given, with the following additions/documented decisions (everything else is verbatim):

| Addition | Value (Light / Dark) | Reason |
|---|---|---|
| `background.secondary` | `#F1F2F7` / `#15161C` | Brief only specified one background; a second, slightly deeper tone was needed to separate stacked page sections without adding a border. |
| `surface.elevated` | `#FFFFFF` / `#24252E` | Light mode elevates via shadow only (surface color doesn't need to shift on white). Dark mode elevates via a lighter tone (`#24252E`, between `surface.default` and `surface.variant`), following M3's tonal-elevation convention — shadows barely read on dark backgrounds. |
| `border.strong` | `#C7C8D1` / `#4A4B54` | Not specified; derived as a darker step of the neutral scale for dividers/emphasis borders that need more contrast than the default border. |
| `*.container` for success/warning/error/info | see table below | Only the "default" tone was specified; container tones (for badges, banners, tinted backgrounds) were derived at the same lightness relationship M3 uses between a color and its container. |
| `*.onX` / `*.onXContainer` text pairs | see table below | Required so text placed on any container/brand surface is contrast-checked, not guessed per-screen. |
| `brand.primaryPressed` | `#4A3EB0` (light) / `#A29BE0` (dark) | Brief gave hover only; pressed is one step further along the same darken/lighten curve used for hover, consistent with the state-layer model below. |
| `secondary.hover` / dark secondary tones | `#4058E0`, container `#3B4278` | Brief gave default only; hover derived the same way as primary's hover, container derived at M3's tonal offset. |
| `brand.onSurfaceInverse` | `#C8C2FF` (light) / `#6558D3` (dark) | New in v1.1. Needed a brand-colored accent that stays legible on `surface.inverse` (which flips lightness relative to the active theme) — e.g. a `Snackbar` action link. Resolved by borrowing the *other* theme's `brand.primary`, since `surface.inverse` is by definition "what the other theme's background looks like." Both pairings reuse contrast ratios already relied on elsewhere (see § 1.4). |
| `overlay.chipScrim` | `rgba(17,18,23,0.72)` (both themes) | New in v1.1. Formalizes the translucent scrim behind a `CategoryChip` rendered over a photo thumbnail (`COMPONENTS.md → CourseCard`), replacing an undocumented ad hoc opacity. Same value in both themes since it composites over photographic content, not app surfaces. |

**v1.1 contrast corrections** — three v1.0 values failed WCAG AA on formal verification (§ 1.4) and were corrected. Nothing else in the starting palette changed:

| Token | v1.0 value | v1.1 value | Measured contrast | Reason |
|---|---|---|---|---|
| `color.semantic.light.secondary.default` | `#536DFE` | `#4A62F0` | White text 4.21:1 → **4.89:1** | `secondary.onSecondary` (white) on `secondary.default` fell short of the 4.5:1 text minimum. Darkened one step; `secondary.hover`/`container`/`onSecondaryContainer` were already compliant and are unchanged. |
| `color.semantic.light.warning.onWarning` | `#FFFFFF` | `#3A2200` | White text 2.53:1 → dark text **5.89:1** | White-on-`warning.default` (#ED8B00) badly failed AA. Switched to dark text, matching the convention dark theme's `warning.onWarning` already used — this also makes the two themes internally consistent with each other. |
| `color.semantic.{light,dark}.border.strong` | `#C7C8D1` / `#4A4B54` | `#8B8C96` / `#71727D` | vs. `surface.default`, UI 3:1: 1.67:1 / 2.00:1 → **3.34:1 / 3.64:1** | `border.strong` backs hover/emphasis outlines and is treated as a meaningful UI-component boundary (WCAG 1.4.11, 3:1 minimum), not decoration. Darkened (light) / lightened (dark) enough to clear 3:1 against their typical surface while staying visually "quieter" than `color.border.focus`. |

State changes (hover/pressed/focus) are modeled two ways depending on the token:
- **Brand/secondary buttons:** explicit hex per state (table below) — these are the highest-frequency, highest-visibility controls and deserve hand-tuned values.
- **Everything else** (chips, list rows, icon buttons, cards): a **state layer** — the semantic `state.hoverOpacity` / `pressedOpacity` / `focusOpacity` token applied as an overlay of `text.primary` (light) or `text.inverse`-equivalent (dark) over the resting surface, per M3 convention. See `design-tokens.json → color.semantic.{light,dark}.state.*`.

### 1.2 Light Theme

| Token | Value | Usage |
|---|---|---|
| `color.background.primary` | `#F8F9FC` | App/page background |
| `color.background.secondary` | `#F1F2F7` | Alternating sections, secondary backgrounds |
| `color.surface.default` | `#FFFFFF` | Cards, sheets, dialogs, inputs |
| `color.surface.elevated` | `#FFFFFF` | Same as default; elevation communicated via `elevation.*` shadow |
| `color.surface.variant` | `#F0F1F6` | Nested surfaces, table stripes, chip backgrounds |
| `color.surface.inverse` | `#1A1B20` | Tooltips, dark-on-light snackbars |
| `color.text.primary` | `#1A1B20` | Headings, body text |
| `color.text.secondary` | `#5F6069` | Supporting text, metadata |
| `color.text.disabled` | `#9A9BA3` | Disabled labels |
| `color.text.inverse` | `#FFFFFF` | Text on brand/dark surfaces |
| `color.text.link` | `#6558D3` | Inline links |
| `color.border.default` | `#E1E2E8` | Card/input borders, dividers |
| `color.border.strong` | `#8B8C96` | Emphasis dividers, selected outlines, hover borders (v1.1: darkened for 3:1 UI contrast, was `#C7C8D1`) |
| `color.border.focus` | `#6558D3` | Focus ring |
| `color.border.error` | `#BA1A1A` | Error field outline |
| `color.brand.primary` | `#6558D3` | Primary actions, active nav, brand accents |
| `color.brand.primaryHover` | `#5548C2` | Primary button hover |
| `color.brand.primaryPressed` | `#4A3EB0` | Primary button pressed |
| `color.brand.primaryContainer` | `#E8E5FF` | Tonal button bg, selected chip bg, highlight surfaces |
| `color.brand.onPrimary` | `#FFFFFF` | Text/icon on `brand.primary` |
| `color.brand.onPrimaryContainer` | `#2B2170` | Text/icon on `brand.primaryContainer` |
| `color.brand.onSurfaceInverse` | `#C8C2FF` | Brand-accent text/action on `surface.inverse` (v1.1, e.g. Snackbar action) |
| `color.secondary.default` | `#4A62F0` | Secondary emphasis, links inside AI features (v1.1: darkened for 4.5:1 with white text, was `#536DFE`) |
| `color.secondary.hover` | `#4058E0` | Secondary hover |
| `color.secondary.container` | `#E2E7FF` | Secondary tonal surfaces |
| `color.secondary.onSecondary` | `#FFFFFF` | Text on secondary |
| `color.secondary.onSecondaryContainer` | `#1C2470` | Text on secondary container |
| `color.accent.default` | `#7C4DFF` | Sparingly: illustrations, highlights, gamification accents — never body UI |
| `color.success.default` / `.container` / `.onSuccess` / `.onSuccessContainer` | `#2E7D32` / `#E3F5E5` / `#FFFFFF` / `#0D3312` | Completed states, positive badges |
| `color.warning.default` / `.container` / `.onWarning` / `.onWarningContainer` | `#ED8B00` / `#FFEDD6` / `#3A2200` / `#4A2C00` | Caution, pending review (v1.1: `onWarning` switched to dark text, was `#FFFFFF` at 2.53:1) |
| `color.error.default` / `.container` / `.onError` / `.onErrorContainer` | `#BA1A1A` / `#FFDAD6` / `#FFFFFF` / `#410002` | Errors, destructive actions |
| `color.info.default` / `.container` / `.onInfo` / `.onInfoContainer` | `#1976D2` / `#DCEBFC` / `#FFFFFF` / `#0B2E4E` | Informational banners/badges |
| `color.overlay.chipScrim` | `rgba(17,18,23,0.72)` | Scrim behind a chip/label rendered on a photo (v1.1) |

### 1.3 Dark Theme

| Token | Value | Usage |
|---|---|---|
| `color.background.primary` | `#111217` | App/page background |
| `color.background.secondary` | `#15161C` | Alternating sections |
| `color.surface.default` | `#191A20` | Cards, sheets, dialogs, inputs |
| `color.surface.elevated` | `#24252E` | Raised surfaces (popovers, dropdown, active card) |
| `color.surface.variant` | `#22232B` | Nested surfaces, chip backgrounds |
| `color.surface.inverse` | `#F2F0F7` | Tooltips shown on dark backgrounds |
| `color.text.primary` | `#F2F0F7` | Headings, body text |
| `color.text.secondary` | `#C7C5CF` | Supporting text |
| `color.text.disabled` | `#777780` | Disabled labels |
| `color.text.inverse` | `#1A1B20` | Text on light-toned dark-mode surfaces (e.g. `brand.primary`) |
| `color.text.link` | `#C8C2FF` | Inline links |
| `color.border.default` | `#383941` | Card/input borders, dividers |
| `color.border.strong` | `#71727D` | Emphasis dividers (v1.1: lightened for 3:1 UI contrast, was `#4A4B54`) |
| `color.border.focus` | `#C8C2FF` | Focus ring |
| `color.border.error` | `#FFB4AB` | Error field outline |
| `color.brand.primary` | `#C8C2FF` | Primary actions, active nav |
| `color.brand.primaryHover` | `#B4AEEF` | Primary button hover |
| `color.brand.primaryPressed` | `#A29BE0` | Primary button pressed |
| `color.brand.primaryContainer` | `#4E449E` | Tonal button bg, selected chip bg |
| `color.brand.onPrimary` | `#2B2170` | Text/icon on `brand.primary` (primary is light-toned in dark mode) |
| `color.brand.onPrimaryContainer` | `#E8E5FF` | Text/icon on `brand.primaryContainer` |
| `color.brand.onSurfaceInverse` | `#6558D3` | Brand-accent text/action on `surface.inverse` (v1.1, e.g. Snackbar action) |
| `color.secondary.default` | `#BBC3FF` | Secondary emphasis |
| `color.secondary.hover` | `#A7B0F5` | Secondary hover |
| `color.secondary.container` | `#3B4278` | Secondary tonal surfaces |
| `color.secondary.onSecondary` | `#1C2470` | Text on secondary |
| `color.secondary.onSecondaryContainer` | `#E2E7FF` | Text on secondary container |
| `color.accent.default` | `#B69CFF` | Sparingly, same rules as light |
| `color.success.default` / `.container` / `.onSuccess` / `.onSuccessContainer` | `#81C784` / `#1E4620` / `#0D3312` / `#E3F5E5` | |
| `color.warning.default` / `.container` / `.onWarning` / `.onWarningContainer` | `#FFB74D` / `#5C3D00` / `#4A2C00` / `#FFEDD6` | |
| `color.error.default` / `.container` / `.onError` / `.onErrorContainer` | `#FFB4AB` / `#93000A` / `#410002` / `#FFDAD6` | |
| `color.info.default` / `.container` / `.onInfo` / `.onInfoContainer` | `#90CAF9` / `#0D47A1` / `#0B2E4E` / `#DCEBFC` | |
| `color.overlay.chipScrim` | `rgba(17,18,23,0.72)` | Scrim behind a chip/label rendered on a photo (v1.1, same in both themes) |

**Rule:** never pair a `.default` status color with `.container` of a *different* status, and never place `text.primary`/`text.secondary` directly on a brand/status surface — always use the matching `onX` token.

### 1.4 Verified Contrast Ratios

Every pairing in § 1.2/1.3 was computed (WCAG relative-luminance formula), not eyeballed. Full pairing list and re-run instructions are in [`ACCESSIBILITY.md § Verified Contrast Ratios`](./ACCESSIBILITY.md) — that table is the audit trail for design principle 6 ("AA by default") and is re-run any time a color token changes.

---

## 2. Typography Tokens

**Platform fonts:** Web → **Inter**, Android → **Roboto**, iOS → **SF Pro**. All three are geometric/grotesque humanist sans faces with near-identical x-heights and metrics at these weights, so the scale below renders visually equivalent across platforms without per-platform size adjustments.

**Weights used, system-wide:** `400` Regular, `500` Medium, `600` SemiBold, `700` Bold. Never introduce Light (300) or Black (800/900) — reserve heavier/lighter registers for illustration/marketing assets, not UI.

| Token | Size | Line height | Weight | Letter spacing | Typical use |
|---|---|---|---|---|---|
| `typography.display.large` | 48 | 56 | 700 | -0.25 | Marketing hero headline |
| `typography.display.medium` | 40 | 48 | 700 | -0.25 | Section hero, landing page |
| `typography.heading.h1` | 32 | 40 | 700 | 0 | Page title |
| `typography.heading.h2` | 28 | 36 | 700 | 0 | Section title |
| `typography.heading.h3` | 24 | 32 | 600 | 0 | Card group title, dialog title |
| `typography.heading.h4` | 20 | 28 | 600 | 0.15 | Card title, subsection |
| `typography.body.large` | 18 | 28 | 400 | 0.15 | Lead paragraph, emphasis body |
| `typography.body.medium` | 16 | 24 | 400 | 0.25 | Default body text |
| `typography.body.small` | 14 | 20 | 400 | 0.25 | Secondary/support text |
| `typography.label.large` | 14 | 20 | 600 | 0.1 | Button labels, tab labels |
| `typography.label.medium` | 12 | 16 | 600 | 0.5 | Chips, badges, overline labels |
| `typography.caption` | 12 | 16 | 400 | 0.4 | Timestamps, helper/error text |

Full values (incl. rationale for line-height/letter-spacing not stated in the brief) are in `design-tokens.json → typography.scale`.

### 2.1 Units Across Platforms

Typography tokens are stored as unitless numbers in `design-tokens.json`; each platform binds them to its **text-scaling** unit, never a fixed one, so the type respects the user's accessibility settings:

| Platform | Unit | Behavior |
|---|---|---|
| Web | **rem** (token px ÷ 16) | Scales with both the root `<html>` font-size and browser zoom. `typography.body.medium` (16px) → `1rem`. |
| Android | **sp** (scale-independent pixel) | Scales with the user's system font-size setting (Settings → Display → Font size), independent of display density. |
| iOS | **pt**, bound through Dynamic Type text styles | Scales with the user's Text Size / Larger Accessibility Sizes setting when the font is requested via `Font`/`UIFont` Dynamic Type APIs rather than a fixed point size. |

**These units are mapped *semantically*, not physically identical.** A `16` in the token scale becomes `1rem` on web, `16sp` on Android, and `16pt`-via-Dynamic-Type on iOS — three different scaling mechanisms that happen to agree at each platform's default system font size, and diverge once the user changes it. See [`platform-mapping.md § Units`](./platform-mapping.md) for the full conversion table and [`ACCESSIBILITY.md`](./ACCESSIBILITY.md) for the zoom/font-scaling/Dynamic-Type testing requirements this enables.

Spacing, radius, icon, border-width, and elevation tokens are the opposite: they use each platform's **fixed layout unit** (px / dp / pt) and deliberately do **not** scale with text size — see § 3–8 below and `design-tokens.json → units`.

### 2.2 Responsive Typography

Only the two largest steps — `display.*` and `heading.h1`/`h2` — resize across breakpoints, so a marketing hero doesn't overwhelm a 360px-wide phone. `heading.h3`/`h4`, all `body.*`, `label.*`, and `caption` are **constant at every breakpoint**; they're already sized for compact contexts and rescaling them would create inconsistent reading rhythm between a card on mobile and the same card on desktop.

| Token | Mobile | Tablet | Desktop | Large Desktop |
|---|---|---|---|---|
| `typography.display.large` | 34 / 40 | 40 / 48 | 48 / 56 | 48 / 56 |
| `typography.display.medium` | 30 / 36 | 34 / 40 | 40 / 48 | 40 / 48 |
| `typography.heading.h1` | 26 / 33 | 28 / 36 | 32 / 40 | 32 / 40 |
| `typography.heading.h2` | 23 / 29 | 25 / 32 | 28 / 36 | 28 / 36 |
| `typography.heading.h3` – `caption` | unchanged across all breakpoints | | | |

*(size / line-height, in the platform's text unit from § 2.1)*. Values are in `design-tokens.json → typography.responsive`. Weight and letter-spacing never change across breakpoints — only size/line-height step down, and only for these four tokens.

---

## 3. Spacing Tokens

4px base unit, expressed as **px (web) / dp (Android) / pt (iOS)** — the fixed layout unit in each platform, not the text-scaling unit (§ 2.1). Spacing intentionally does not grow when the user increases text size; see [`ACCESSIBILITY.md § High Text Scaling`](./ACCESSIBILITY.md) for how components reflow instead. **Only the named steps below are valid tokens — never an interpolated value (no `space.7`, no `18px`).**

| Token | Value (px) |
|---|---|
| `space.0` | 0 |
| `space.1` | 4 |
| `space.2` | 8 |
| `space.3` | 12 |
| `space.4` | 16 |
| `space.5` | 20 |
| `space.6` | 24 |
| `space.8` | 32 |
| `space.10` | 40 |
| `space.12` | 48 |
| `space.16` | 64 |

**Applied usage:**

| Context | Token(s) |
|---|---|
| Page padding — mobile | `space.4` (16) |
| Page padding — tablet | `space.6` (24) |
| Page padding — desktop | `space.8` (32) |
| Page padding — large desktop | `space.12` (48) |
| Card padding | `space.4` (16) |
| Section spacing (mobile / desktop) | `space.10` (40) / `space.12` (48) |
| List item gap | `space.2` (8) compact, `space.3` (12) default |
| Grid gap (mobile / desktop) | `space.4` (16) / `space.6` (24) |
| Component internal gap (icon-to-label, etc.) | `space.2` (8) |

---

## 4. Shape Tokens

| Token | Value (px) |
|---|---|
| `radius.none` | 0 |
| `radius.small` | 8 |
| `radius.medium` | 12 |
| `radius.large` | 16 |
| `radius.xlarge` | 24 |
| `radius.full` | 999 |

**Component usage:**

| Component | Radius |
|---|---|
| Button (all variants) | `radius.medium` (12) |
| Input / TextField | `radius.medium` (12) |
| Course Card | `radius.large` (16) |
| Dashboard Card | `radius.large` (16) |
| Modal / Dialog | `radius.xlarge` (24) |
| Bottom Sheet | `radius.xlarge` (24), top corners only |
| Chip / Badge | `radius.full` |
| Avatar | `radius.full` |
| Snackbar | `radius.medium` (12) |

---

## 5. Elevation Tokens

Mentora prefers **borders + subtle shadow** over heavy Material drop shadows. Shadow is reserved for genuine z-axis separation (menus, modals, floating controls) — resting cards use a 1px border and elevation.1 at most.

| Token | Web (`box-shadow`) | Android (Compose `tonalElevation`/`shadowElevation`) | iOS (`shadowRadius` / `y` / `opacity`) | Usage |
|---|---|---|---|---|
| `elevation.0` | none | 0dp | 0 / 0 / 0 | Flat, border-only elements |
| `elevation.1` | `0 1px 2px rgba(17,18,23,0.06)` | 1dp | 2 / 1 / 0.06 | Resting cards |
| `elevation.2` | `0 2px 8px rgba(17,18,23,0.08)` | 3dp | 6 / 2 / 0.08 | Hovered/raised cards, dropdown triggers |
| `elevation.3` | `0 4px 16px rgba(17,18,23,0.10)` | 6dp | 12 / 4 / 0.10 | Menus, popovers, tooltips |
| `elevation.4` | `0 8px 24px rgba(17,18,23,0.12)` | 12dp | 20 / 8 / 0.12 | Modals, dialogs, bottom sheets |

**Dark mode:** prefer raising `surface.elevated` (a lighter tone) over increasing shadow — shadows barely read against a near-black background. Keep shadow color pure black; the opacities above already assume dark-mode use.

---

## 6. Border Tokens

| Token | Value |
|---|---|
| `border.width.default` | 1px |
| `border.width.focus` | 2px |
| `border.color.default` | `color.border.default` |
| `border.color.strong` | `color.border.strong` |
| `border.color.focus` | `color.border.focus` |
| `border.color.error` | `color.border.error` |

Focus rings render as a 2px outline in `border.color.focus`, offset 2px from the element edge (see [ACCESSIBILITY.md](./ACCESSIBILITY.md)).

---

## 7. Motion Tokens

| Token | Value |
|---|---|
| `motion.duration.fast` | 150ms |
| `motion.duration.normal` | 200ms |
| `motion.duration.slow` | 300ms |
| `motion.easing.standard` | `cubic-bezier(0.4, 0.0, 0.2, 1)` |
| `motion.easing.decelerate` | `cubic-bezier(0.0, 0.0, 0.2, 1)` (entrances) |
| `motion.easing.accelerate` | `cubic-bezier(0.4, 0.0, 1, 1)` (exits) |

| Interaction | Duration | Easing |
|---|---|---|
| Button press feedback | fast (150ms) | standard |
| Dialog open | normal (200ms) | decelerate |
| Dialog close | fast (150ms) | accelerate |
| Bottom sheet open | normal (200ms) | decelerate |
| Bottom sheet close | fast (150ms) | accelerate |
| Navigation transition (route change) | slow (300ms) | standard |
| Page transition (marketing site) | slow (300ms) | standard |
| Progress bar fill | normal (200ms) | standard; indeterminate loops use linear |

**Rule:** motion communicates state change only. No bounce, spring-overshoot, parallax, or auto-playing decorative animation.

---

## 8. Icon System

**Family:** Material Symbols Rounded, everywhere, at `weight 400`, `optical size 24` (adjust optical size only when rendering at `icon.large`).

| Token | Size (px/dp/pt) |
|---|---|
| `icon.small` | 16 |
| `icon.medium` | 20 |
| `icon.default` | 24 |
| `icon.large` | 32 |

**Platform delivery** (full detail in [platform-mapping.md](./platform-mapping.md)):
- **Web:** Material Symbols Rounded via self-hosted variable font or inline SVG sprite (not the Google Fonts CDN, for reliability/perf).
- **Android:** Material Symbols Rounded vector drawables (`ImageVector` / XML), bundled — do not depend on `androidx.compose.material.icons` defaults, which are sharp/filled, not Rounded.
- **iOS:** Material Symbols Rounded exported as PDF/SVG asset catalog entries rendered at the same pt sizes. SF Symbols are used **only** for OS-owned chrome Mentora doesn't draw (share sheet, keyboard accessory, system alerts) — never inside app UI, to avoid mixing icon styles.

**Locked (§ 14 review):** Material Symbols Rounded remains the canonical Mentora icon language on all three platforms. A platform-native substitution (e.g. an SF Symbol) is permitted only where necessary for OS-owned chrome as above, and only when it preserves equivalent visual meaning (same silhouette family — rounded terminals, similar stroke weight) — never as a stylistic swap inside product UI.

**Directional icons (RTL):** a defined subset of icons encode a left/right direction (`chevron_left`/`chevron_right`, `arrow_back`/`arrow_forward`, etc. — full list in `design-tokens.json → icon.directional`) and must mirror automatically when the layout direction is RTL. Icons with no inherent direction (`play_arrow`, `check`, `search`, …) never mirror. See [`LOCALIZATION.md § Directional Icons`](./LOCALIZATION.md).

---

## 9–19. Components, Buttons, Inputs, Course Card, Navigation, Responsive, AI Tutor, Progress, Quiz, States

Full specs for every listed component (dimensions, padding, typography, color mapping, radius, border, and every interaction state) live in **[COMPONENTS.md](./COMPONENTS.md)**. That includes:

- Buttons (Primary, Secondary, Tonal, Text, Icon) — §10 of the brief
- Inputs (Text, Password, Search) — §11
- Course Card + Course Progress Card — §12
- Navigation (Navbar, Sidebar, Bottom Navigation, Tabs) — §13
- AI Tutor bubble + quick actions — §15
- Progress bar — §16
- Quiz card/answer states — §17
- Loading / Empty / Error / **Success** states — §18 (`SuccessState` added v1.2)
- **v1.2:** Media & Playback (VideoPlayer/PlaybackControls), Checkout/OrderSummary, Toggle/Switch, File & Media Upload, and Instructor & Admin Components (ReorderableList/DragHandle, DataTable) — all in `COMPONENTS.md`, added because product planning proved them necessary (`../product/SCREEN_INVENTORY.md`)
- **v1.3:** Select/Dropdown (in `COMPONENTS.md § Inputs`, retiring the v1.2 stopgap), plus Arabic font-family tokens and locale-aware formatting guidance (`LOCALIZATION.md §§ 4, 7`) to support English + Arabic as locked MVP languages (`../product/PRODUCT_SPEC.md § 16`)

Responsive breakpoints, grid, and course-grid column counts (§14 of the brief) are defined once, here, since they're layout rules rather than components:

### Breakpoints

| Name | Range (px) |
|---|---|
| Mobile | 0–599 |
| Tablet | 600–1023 |
| Desktop | 1024–1439 |
| Large Desktop | 1440+ |

### Grid

| Breakpoint | Columns | Gutter |
|---|---|---|
| Mobile | 4 | 16 |
| Tablet | 8 | 24 |
| Desktop | 12 | 24 |
| Large Desktop | 12 | 24 |

Max layout width: **1440px**. Preferred marketing content width: **1200–1280px**.

### Course grid (cards per row)

| Mobile | Tablet | Desktop | Large Desktop |
|---|---|---|---|
| 1 | 2 | 3 | 4 |

The Course Card component itself does not change design between breakpoints — only the grid column count and card width change. See [COMPONENTS.md § Course Card](./COMPONENTS.md) for the single responsive definition.

**Locked (§ 14 review):** CourseCard stays one responsive component, on every platform — never a separate "mobile design" and "web design." Breakpoint/grid changes shift its container, not its own layout rules.

For how these same layouts behave under RTL locales, long/expanding translated strings, missing thumbnails, and failure states, see [`LOCALIZATION.md`](./LOCALIZATION.md) and [`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md) — both apply to every component in `COMPONENTS.md`, not just Course Card.

---

## 10. File Map

| File | Contents |
|---|---|
| `DESIGN_SYSTEM.md` | This document — narrative source of truth |
| `design-tokens.json` | Platform-independent token values (primitive → semantic → component layers) |
| `themes/theme-light.json` | Flattened, resolved light-theme semantic colors |
| `themes/theme-dark.json` | Flattened, resolved dark-theme semantic colors |
| `platform-mapping.md` | Token → Web CSS / Android Compose / iOS SwiftUI mapping tables, unit mapping, suggested repo structure |
| `COMPONENTS.md` | Full component specs (dimensions, states, tokens used, interaction-state matrix) |
| `ACCESSIBILITY.md` | WCAG AA rules, contrast, focus, touch targets, screen reader labels, zoom/font-scaling/Dynamic Type |
| `LOCALIZATION.md` | RTL readiness, logical (start/end) layout, directional icons, nav mirroring, Arabic-specific guidance |
| `CONTENT_RESILIENCE.md` | Long text, missing images, loading/error/empty data, localization expansion, text-scaling wrapping/truncation rules |
| `DESIGN_RULES.md` | Governance: what must never be hand-rolled, how to propose a new token, versioning policy, locked decisions |
| `CHANGELOG.md` | Version history of the design system itself |
