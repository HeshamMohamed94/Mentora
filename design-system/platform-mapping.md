# Mentora Design System — Platform Mapping

How every token in [`design-tokens.json`](./design-tokens.json) is consumed on Web, Android (Jetpack Compose), and iOS (SwiftUI). Nothing here introduces new values — this file only names the implementation handle for each already-defined token.

---

## 0. Units

Full rationale in [`DESIGN_SYSTEM.md § 2.1`](./DESIGN_SYSTEM.md) and `design-tokens.json → units`. The short version: **a token's numeric value is identical across platforms; the unit it's expressed in is not physically identical**, because each platform's scaling unit behaves differently under the user's own accessibility settings.

| Token category | Web | Android | iOS | Scales with user text-size setting? |
|---|---|---|---|---|
| Typography (`typography.scale.*`) | `rem` (px ÷ 16) | `sp` | `pt` via Dynamic Type text styles | **Yes** — this is required, not optional (see `ACCESSIBILITY.md § 10`) |
| Spacing (`spacing.scale.*`) | `px` | `dp` | `pt` (fixed) | No — spacing stays fixed so layout rhythm doesn't stretch when only text grows |
| Radius (`shape.radius.*`) | `px` | `dp` | `pt` | No |
| Border width (`border.width.*`) | `px` | `dp` | `pt` | No |
| Icon size (`icon.sizes.*`) | `px` (or `1em` if icon font is sized relative to adjacent text) | `dp` | `pt` | No, except where an icon is explicitly sized to match adjacent scaling text (rare, called out per-component) |
| Elevation offsets (`elevation.*`) | `px` | `dp` | `pt` | No |

**Concretely, one example:** `typography.scale.body.medium.fontSize = 16` becomes `1rem` on web (which is `16px` at the browser's default root size but grows if the user has increased their browser's default font size or zoomed), `16sp` on Android (which is `16dp` at the system's default font scale but grows if the user increased Settings → Display → Font size), and a Dynamic-Type-bound `.body` (or nearest matching) style on iOS (which is `16pt` at the default Text Size setting but grows across the Dynamic Type range up to the AX sizes). The number `16` is shared; the three renderings diverge the moment a real user with non-default accessibility settings opens the app — which is the point of using each platform's scaling unit rather than a fixed one.

---

## 1. Colors

Naming convention: dot-path token `color.brand.primary` becomes:

| Platform | Convention | Example |
|---|---|---|
| Web | CSS custom property, kebab-case, theme-scoped via `[data-theme]` or `.dark` on `:root` | `--color-brand-primary` |
| Android | `MentoraTheme.colors.<camelCase>` (a `MentoraColors` class exposed through `CompositionLocal`, swapped by `MaterialTheme`/custom theme based on `isSystemInDarkTheme()`) | `MentoraTheme.colors.brandPrimary` |
| iOS | `Color.mentora<PascalCase>`, defined once via a Color Set in the asset catalog with Any/Dark appearances (so `Color("brandPrimary")` auto-switches) | `Color.mentoraBrandPrimary` |

### Full color table

| Token | Web CSS var | Android | iOS |
|---|---|---|---|
| `color.background.primary` | `--color-background-primary` | `colors.backgroundPrimary` | `Color.mentoraBackgroundPrimary` |
| `color.background.secondary` | `--color-background-secondary` | `colors.backgroundSecondary` | `Color.mentoraBackgroundSecondary` |
| `color.surface.default` | `--color-surface-default` | `colors.surfaceDefault` | `Color.mentoraSurfaceDefault` |
| `color.surface.elevated` | `--color-surface-elevated` | `colors.surfaceElevated` | `Color.mentoraSurfaceElevated` |
| `color.surface.variant` | `--color-surface-variant` | `colors.surfaceVariant` | `Color.mentoraSurfaceVariant` |
| `color.surface.inverse` | `--color-surface-inverse` | `colors.surfaceInverse` | `Color.mentoraSurfaceInverse` |
| `color.text.primary` | `--color-text-primary` | `colors.textPrimary` | `Color.mentoraTextPrimary` |
| `color.text.secondary` | `--color-text-secondary` | `colors.textSecondary` | `Color.mentoraTextSecondary` |
| `color.text.disabled` | `--color-text-disabled` | `colors.textDisabled` | `Color.mentoraTextDisabled` |
| `color.text.inverse` | `--color-text-inverse` | `colors.textInverse` | `Color.mentoraTextInverse` |
| `color.text.link` | `--color-text-link` | `colors.textLink` | `Color.mentoraTextLink` |
| `color.border.default` | `--color-border-default` | `colors.borderDefault` | `Color.mentoraBorderDefault` |
| `color.border.strong` | `--color-border-strong` | `colors.borderStrong` | `Color.mentoraBorderStrong` |
| `color.border.focus` | `--color-border-focus` | `colors.borderFocus` | `Color.mentoraBorderFocus` |
| `color.border.error` | `--color-border-error` | `colors.borderError` | `Color.mentoraBorderError` |
| `color.brand.primary` | `--color-brand-primary` | `colors.brandPrimary` | `Color.mentoraBrandPrimary` |
| `color.brand.primaryHover` | `--color-brand-primary-hover` | `colors.brandPrimaryHover` | `Color.mentoraBrandPrimaryHover` |
| `color.brand.primaryPressed` | `--color-brand-primary-pressed` | `colors.brandPrimaryPressed` | `Color.mentoraBrandPrimaryPressed` |
| `color.brand.primaryContainer` | `--color-brand-primary-container` | `colors.brandPrimaryContainer` | `Color.mentoraBrandPrimaryContainer` |
| `color.brand.onPrimary` | `--color-brand-on-primary` | `colors.brandOnPrimary` | `Color.mentoraBrandOnPrimary` |
| `color.brand.onPrimaryContainer` | `--color-brand-on-primary-container` | `colors.brandOnPrimaryContainer` | `Color.mentoraBrandOnPrimaryContainer` |
| `color.secondary.*` | `--color-secondary-*` | `colors.secondary*` | `Color.mentoraSecondary*` |
| `color.accent.default` | `--color-accent-default` | `colors.accentDefault` | `Color.mentoraAccentDefault` |
| `color.success.*` / `warning.*` / `error.*` / `info.*` | `--color-{status}-*` | `colors.{status}*` | `Color.mentora{Status}*` |
| `color.overlay.scrim` | `--color-overlay-scrim` | `colors.overlayScrim` | `Color.mentoraOverlayScrim` |

State-layer opacities (`state.hoverOpacity`, etc.) map to:
- Web: applied via a pseudo-element or `color-mix()` overlay, or a CSS var `--state-hover-opacity` multiplied into an `rgba()` overlay.
- Android: Compose `Modifier.background(color.copy(alpha = MentoraTheme.state.hoverOpacity))` inside `indication`/`ripple` customization.
- iOS: SwiftUI `.opacity()` overlay view, or a custom `ButtonStyle` reading `configuration.isPressed`.

---

## 2. Typography

| Token | Web | Android (Compose `TextStyle`) | iOS (SwiftUI `Font`) |
|---|---|---|---|
| `typography.display.large` | `.text-display-large` (CSS class or Tailwind-esque utility mapping to `font-size:3rem; line-height:3.5rem; font-weight:700; letter-spacing:-0.25px`) | `MentoraTheme.typography.displayLarge` | `.mentoraFont(.displayLarge)` |
| `typography.heading.h1` … `h4` | `.text-h1` … `.text-h4` | `MentoraTheme.typography.h1` … `h4` | `.mentoraFont(.h1)` … `.h4` |
| `typography.body.large/medium/small` | `.text-body-lg/md/sm` | `MentoraTheme.typography.bodyLarge/Medium/Small` | `.mentoraFont(.bodyLarge/Medium/Small)` |
| `typography.label.large/medium` | `.text-label-lg/md` | `MentoraTheme.typography.labelLarge/Medium` | `.mentoraFont(.labelLarge/Medium)` |
| `typography.caption` | `.text-caption` | `MentoraTheme.typography.caption` | `.mentoraFont(.caption)` |

**Font family binding:**

| Platform | Family | Loading |
|---|---|---|
| Web | `Inter` | Self-hosted variable woff2, `font-display: swap`, `-apple-system`/`Segoe UI` fallback stack |
| Android | `Roboto` | System font (pre-installed); no bundling needed |
| iOS | `SF Pro` | System font via `Font.system` / `UIFont` — do not bundle, use San Francisco directly for correct Dynamic Type behavior |

Compose `TextStyle` and SwiftUI `Font` values each carry `fontSize`, `lineHeight` (Compose) or computed `lineSpacing` (SwiftUI), `fontWeight`, and `letterSpacing`/`tracking` pulled 1:1 from `design-tokens.json → typography.scale`.

**iOS Dynamic Type:** wrap each `mentoraFont` case with `.dynamicTypeSize` scaling relative to the token's base size so Mentora respects the user's system text-size setting without breaking the ratio between scale steps.

---

## 3. Spacing

| Token | Web | Android | iOS |
|---|---|---|---|
| `space.N` | `--space-N` (e.g. `--space-4: 16px`) or Tailwind scale mapped 1:1 | `MentoraTheme.spacing.N.dp` (e.g. `spacing.space4`) | `MentoraSpacing.space4` (a `CGFloat` enum/struct) |

Usage is always via the token, e.g. `padding: var(--space-4)`, `Modifier.padding(MentoraTheme.spacing.space4)`, `.padding(MentoraSpacing.space4)`.

### 3.1 Logical (start/end) Application — RTL

Spacing/shape/border tokens are direction-neutral numbers; **how they're applied** is what must be logical rather than physical, per [`LOCALIZATION.md § 1`](./LOCALIZATION.md):

| Platform | Logical primitive | Never use |
|---|---|---|
| Web | CSS logical properties: `margin-inline-start`, `padding-inline-end`, `inset-inline-start`, `border-inline-end-width`, `text-align: start` | `margin-left`, `padding-right`, `left`/`right`, `text-align: left` |
| Android (Compose) | `Modifier.padding(start = …, end = …)`, `Arrangement`/`Alignment` with `LayoutDirection` from `LocalLayoutDirection.current` | `Modifier.padding(left = …, right = …)`, hardcoded `Alignment.Start`/`End` assumptions that ignore `LayoutDirection` |
| Android (XML, if used for any native chrome) | `paddingStart`/`paddingEnd`, `layout_marginStart`/`End`, `android:supportsRtl="true"` in the manifest | `paddingLeft`/`paddingRight` |
| iOS (SwiftUI) | `.padding(.leading, …)`/`.padding(.trailing, …)`, `HStack(alignment:)` with `.leading`/`.trailing`, environment `\.layoutDirection` | `.padding(.left, …)` (not a real SwiftUI API, but equivalent hardcoded frame math using fixed left/right offsets) |

This is a mechanical substitution, not a design decision each screen makes — every layout primitive above already flips automatically under RTL when the *logical* variant is used, which is the entire reason `DESIGN_RULES.md` rule 16 requires it from the first implementation of any component.

---

## 4. Shape / Radius

| Token | Web | Android (Compose `Shape`) | iOS (SwiftUI) |
|---|---|---|---|
| `radius.small/medium/large/xlarge` | `--radius-small` etc., `border-radius: var(--radius-medium)` | `RoundedCornerShape(MentoraTheme.shape.medium)` | `.cornerRadius(MentoraRadius.medium)` / `RoundedRectangle(cornerRadius:)` |
| `radius.full` | `border-radius: 999px` (or `50%` for circular avatars) | `CircleShape` / `RoundedCornerShape(percent = 50)` | `Capsule()` / `Circle()` |

---

## 5. Elevation

| Token | Web | Android (Compose) | iOS (SwiftUI) |
|---|---|---|---|
| `elevation.N` | `box-shadow: var(--elevation-N)` | `Modifier.shadow(elevation = MentoraTheme.elevation.N)` (paired with `tonalElevation` on `Surface` for dark-mode tone shift) | `.shadow(color: .mentoraShadow, radius: N.radius, x: 0, y: N.y)` with opacity baked into `.mentoraShadow`'s alpha, or a `.mentoraElevation(N)` view modifier |

Compose should additionally set `Surface(tonalElevation = ...)` in dark theme so the surface tone lightens per §5 of DESIGN_SYSTEM.md, not just the shadow.

---

## 6. Motion

| Token | Web (CSS) | Android (Compose) | iOS (SwiftUI) |
|---|---|---|---|
| `motion.duration.fast/normal/slow` | `transition-duration: 150ms/200ms/300ms` | `tween(durationMillis = 150/200/300)` | `.animation(.easeInOut(duration: 0.15/0.2/0.3))` or custom `Animation` |
| `motion.easing.standard` | `cubic-bezier(0.4,0.0,0.2,1)` | `CubicBezierEasing(0.4f,0f,0.2f,1f)` | `.timingCurve(0.4,0.0,0.2,1)` |
| `motion.easing.decelerate` | `cubic-bezier(0.0,0.0,0.2,1)` | `CubicBezierEasing(0f,0f,0.2f,1f)` | `.timingCurve(0.0,0.0,0.2,1)` |
| `motion.easing.accelerate` | `cubic-bezier(0.4,0.0,1,1)` | `CubicBezierEasing(0.4f,0f,1f,1f)` | `.timingCurve(0.4,0.0,1,1)` |

---

## 7. Icons

| Platform | Delivery |
|---|---|
| Web | Material Symbols Rounded self-hosted variable font (`font-variation-settings` for weight/fill/optical size) or inline SVG sprite; sized via `font-size`/`width`+`height` matching `icon.*` tokens |
| Android | Vector drawables / `ImageVector` generated from Material Symbols Rounded SVG exports, bundled in `res/drawable`; sized via `Modifier.size(MentoraTheme.icon.default.dp)` |
| iOS | PDF or SVG assets in the asset catalog (template rendering mode for tinting), sized via frame modifiers matching `icon.*` pt values |

**Directional mirroring** (`icon.directional.mirrorInRtl` in `design-tokens.json`, full rules in [`LOCALIZATION.md § 5`](./LOCALIZATION.md)): Web applies `transform: scaleX(-1)` (or swaps to a pre-mirrored glyph) scoped to `[dir="rtl"]`; Compose wraps the icon in a `Modifier.mirror()`-style conditional on `LocalLayoutDirection`; SwiftUI applies `.flipsForRightToLeftLayoutDirection(true)` on the `Image`. Never applied to icons in `icon.directional.neverMirror`.

---

## 7.1 Component Token Layer

`design-tokens.json → component.*` (layer 3, see [`DESIGN_SYSTEM.md § 0.1`](./DESIGN_SYSTEM.md)) generates into a platform-specific component-theme object alongside the semantic tokens above:

| Platform | Convention | Example |
|---|---|---|
| Web | CSS custom properties namespaced per component | `--btn-primary-bg`, `--input-border-focused` |
| Android | `MentoraTheme.component.<name>.<part>` | `MentoraTheme.component.button.primary.background.default` |
| iOS | `Color.mentoraComponent<Name><Part>` / a `MentoraComponentTokens` namespace | `MentoraComponentTokens.Button.Primary.background(for: .default)` |

These are generated, not hand-written — the alias (`{color.brand.primary}`, etc.) in `design-tokens.json` is the single place the mapping is declared; regenerating from `design-tokens.json` keeps all three platforms' component-layer objects in sync automatically (`platform-mapping.md § 9`'s "generated, never hand-edited" rule applies here too).

**v1.2 additions** (`videoPlayer`, `checkout`, `successState`, `toggle`, `fileUpload`, `reorderableList`, `dataTable`) follow this exact same pattern — no new mapping mechanism was introduced. Two are worth calling out specifically because they map to a **native platform control** rather than a purely custom-styled one (see `ACCESSIBILITY.md § 14` for why):

| Component | Web | Android (Compose) | iOS (SwiftUI) |
|---|---|---|---|
| `toggle` | `<input type="checkbox" role="switch">`, styled via the `component.toggle.*` CSS variables | `Switch(colors = SwitchDefaults.colors(...))` reading `MentoraTheme.component.toggle` | `Toggle(...).tint(MentoraComponentTokens.Toggle.trackOn)` |
| `fileUpload`'s Browse action | `<input type="file">` triggered by a styled `TonalButton`, plus a native-OS drag-and-drop target | `ActivityResultContracts.GetContent` triggered the same way | `UIDocumentPickerViewController`/`PhotosPicker` triggered the same way |

`dataTable`'s responsive collapse (table ↔ stacked card, `COMPONENTS.md`) is implemented per-platform as: Web — a CSS breakpoint swap between `<table>` markup and a card list; this component is Web-primary (Instructor/Admin is Web-only per Mentora's product plan — see `../product/USER_ROLES.md`), so Android/Compose and iOS/SwiftUI mappings are provided here only for design-system completeness, not because Mentora ships a native Instructor/Admin app.

**v1.3 addition — `select`:**

| Platform | Mapping |
|---|---|
| Web | A styled trigger (`component.select` tokens) + an ARIA `listbox`/`combobox` menu (`role="combobox"` on the trigger, `role="listbox"`/`role="option"` in the menu) — not a bare native `<select>`, since that element can't be styled to match `component.select`'s menu surface (`elevation.3`, `surface.elevated`) across browsers; the ARIA pattern preserves native-equivalent keyboard/screen-reader behavior (`COMPONENTS.md § Select / Dropdown`, keyboard behavior) while keeping full visual control. |
| Android | `ExposedDropdownMenuBox` (Compose Material 3), styled via `MentoraTheme.component.select` |
| iOS | A `Menu`-triggered picker or `Picker(.menu)` style, styled via `MentoraComponentTokens.Select` |

This is the one v1.3 component whose Web mapping deliberately does **not** delegate to the plain native element (contrast with `toggle`, which does) — because a native `<select>`'s dropdown chrome cannot be styled to match Mentora's menu surface tokens in every browser, while a checkbox-based switch styles perfectly via CSS. The ARIA `combobox`/`listbox` pattern is the standard, well-supported way to keep full accessibility semantics without that limitation.

---

## 8. Breakpoints & Grid

| Concept | Web | Android | iOS |
|---|---|---|---|
| Breakpoints | CSS media queries / container queries at 600/1024/1440px | `WindowSizeClass` (Compact/Medium/Expanded) mapped to Mentora's four breakpoints | `UITraitCollection`/SwiftUI `HorizontalSizeClass` + explicit width checks via `GeometryReader` for the 4-tier scale (SwiftUI's 2-class system doesn't map 1:1, so Mentora defines its own `MentoraSizeClass` enum from raw width) |
| Grid columns | CSS Grid `grid-template-columns: repeat(N, 1fr)` per breakpoint | `LazyVerticalGrid(columns = GridCells.Fixed(N))` | `LazyVGrid(columns: [GridItem](repeating:, count: N))` |

---

## 9. Suggested Repository Structure

```
design-system/
├── DESIGN_SYSTEM.md          # narrative source of truth
├── design-tokens.json        # platform-independent tokens (primitives + semantic)
├── platform-mapping.md       # this file
├── COMPONENTS.md             # component specs
├── ACCESSIBILITY.md          # a11y rules
├── DESIGN_RULES.md           # governance
├── themes/
│   ├── theme-light.json
│   └── theme-dark.json
├── components/                # cross-platform component *specs* (not code) if broken out per-component
├── web/
│   ├── tokens.css             # generated from design-tokens.json
│   └── components/            # React/Vue/etc. implementations consuming tokens.css
├── android/
│   ├── MentoraTokens.kt        # generated Kotlin objects from design-tokens.json
│   └── components/             # Composables consuming MentoraTheme
└── ios/
    ├── MentoraTokens.swift     # generated Swift enums/structs from design-tokens.json
    ├── MentoraColors.xcassets  # Color Set catalog (light/dark per token)
    └── Components/              # SwiftUI views consuming MentoraTheme
```

`design-tokens.json` is the only file a human edits by hand for values. `web/tokens.css`, `android/MentoraTokens.kt`, and `ios/MentoraTokens.swift` are treated as **generated output** (via a token-transform script, e.g. Style Dictionary) — never hand-edited, so the three platforms cannot drift from the source of truth.
