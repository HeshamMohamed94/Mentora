# Mentora Design System — Changelog

Version tracked in `design-tokens.json → meta.version`, policy in [`DESIGN_RULES.md § Versioning & Governance`](./DESIGN_RULES.md).

---

## 1.3.2 — 2026-09-04

One final targeted defect fix, triggered by the Mentora Pre-Architecture Context Check's remaining flagged item. No product screens, no visual redesign, no scope change. **PATCH bump** per explicit product-owner direction, consistent with v1.3.1's precedent.

**Fixed — Primary Button disabled background did not match its own documented spec:**
- `component.button.primary.background.disabled` (`design-tokens.json`): `{color.brand.primary}` → `{color.surface.default}+{color.brand.primary}@{state.disabledContainerOpacity}`. Previously this value was identical to `component.button.primary.background.default`, so a disabled Primary Button rendered with the exact same solid background as an enabled one, relying on `text.disabled` alone to signal the disabled state.
- The fix reuses the pre-existing `state.disabledContainerOpacity` token (0.12 light / 0.16 dark — already used by `component.toggle.track.disabled`) and the pre-existing base+overlay@opacity compositing syntax already established by `component.button.tonal.background.hover`/`.pressed` (e.g. `{color.brand.primaryContainer}+{color.brand.onPrimaryContainer}@{state.hoverOpacity}`). No new token, no new color, no raw `@N%` value.
- This now matches `COMPONENTS.md`'s PrimaryButton Disabled row exactly, which already documented "`color.brand.primary` @ `state.disabledContainerOpacity` over `surface.default`" — no `COMPONENTS.md` edit was required, only the JSON needed to catch up to the doc.
- `component.button.primary.text.disabled` (`color.text.disabled`) is unchanged — the disabled label continues to use the existing disabled-content token.
- Result: in Light Mode, a disabled Primary Button now shows a faint purple-tinted container (`brand.primary` #6558D3 at 12% over `surface.default` #FFFFFF) instead of the full-strength brand purple; in Dark Mode, a faint tinted container (`brand.primary` #C8C2FF at 16% over `surface.default` #191A20) instead of full-strength. Both are clearly distinguishable from the enabled state's solid background while remaining recognizably Mentora-purple, on Web, Android, and iOS alike (all three consume the same semantic/component token resolution — see `platform-mapping.md`'s state-layer-opacity mapping section for how `state.*Opacity` composites per platform).

**Verified before closing this patch:** JSON validity; the one changed alias resolves (`color.surface.default`, `color.brand.primary`, and `state.disabledContainerOpacity` all exist in both `color.semantic.light` and `color.semantic.dark`); no circular reference introduced; zero raw arbitrary opacity modifiers anywhere in `component.*` (still zero, as of 1.3.1); `color.semantic.light`/`dark` key-count parity unchanged (51/51); `COMPONENTS.md` required no edit, since it already documented the now-correct behavior; disabled vs. default backgrounds for Primary Button are now visually distinct in both themes; no other `component.*` entry touched; no MVP screen count, user role, product feature, or UX behavior changed; no production code implemented; no Codex involvement.

**Not done (explicitly out of scope for this patch):**
- No visual redesign of any component, in either theme.
- No new component, token, or design pattern added.
- No product/UX scope, flow, or screen change.
- No Technical Architecture work.

---

## 1.3.1 — 2026-09-04

Non-visual consistency/defect patch, triggered by the Mentora Pre-Architecture Context Check (a source-of-truth audit run before Technical Architecture). No product screens, no visual redesign, no scope change. **PATCH bump** per explicit product-owner direction for this release; note this is one tier narrower than `DESIGN_RULES.md § Versioning & Governance`'s own table would suggest for a value-level defect fix (that table calls a "correction to an existing value that fixes a defect" a MINOR bump) — recorded here for governance traceability, not corrected, since no renaming, removal, or additive surface accompanies this patch.

**Fixed — raw arbitrary opacity remaining in `design-tokens.json`:**
- `component.input.border.disabled`: `{color.border.default}@50%` → `{color.border.default}`. This was already documented as fixed in `COMPONENTS.md` and in this changelog's own 1.1.0 entry ("`TextField` disabled border: undocumented `border.default @ 50%` → `border.default`"), but the literal value in `design-tokens.json` had never actually been updated to match — a drift between the hand-authored JSON (source of truth) and the prose it was supposed to mirror. Also brings `component.input` in line with `component.select.border.disabled` (`{color.border.default}`, no opacity), which v1.3's Select was documented as copying "exactly" from Input.
- `component.chip.background.disabled`: `{color.surface.variant}@50%` → `{color.surface.variant}`, matching `COMPONENTS.md`'s existing Chip Disabled row ("no opacity modifier — background already reads as inactive against `text.disabled`").
- Full `component.*` layer re-scanned for any other raw `@N%`/`@N` modifier not backed by a `state.*` token: **none found**. The only other `@` usages in the component layer were already `@{state.hoverOpacity}` / `@{state.pressedOpacity}` / `@{state.disabledContentOpacity}` / `@{state.disabledContainerOpacity}` — all compliant with `DESIGN_RULES.md` rule 15.
- No new token added. No hex value invented. No primitive consumed directly. No per-component theme branching introduced. `color.semantic.light`/`dark` key parity unchanged (51/51). All 63 distinct `component.*` aliases still resolve to a real token path (same count as v1.2/v1.3 — zero new alias targets).

**Fixed — stale Design System version references** (documentation-currency only, no content rewritten):
- `../product/PRODUCT_SPEC.md § 10`: "Mentora Design System v1.1" → "Mentora Design System v1.3.1" (the shared-design-system-across-clients claim was citing a three-versions-old label; v1.3.0's historical citation elsewhere in the same file, "`CHANGELOG.md` v1.3.0", is a valid dated reference and was left as-is).
- `../ux/SCREEN_UX_SPECS.md § intro`: "Mentora Design System v1.2" → "Mentora Design System v1.3.1".
- `../ux/RESPONSIVE_BEHAVIOR.md § intro`: "Mentora Design System v1.2" → "Mentora Design System v1.3.1" (§ 59's "(v1.2)" citation, marking when `DataTable` was added, is a valid historical note and was left as-is).
- `../ux/MOBILE_UX.md § 15`: "Mentora Design System v1.2" → "Mentora Design System v1.3.1".
- `../ux/UX_STATES.md § intro`: already correctly read "Mentora Design System v1.3" — bumped to "v1.3.1" for consistency with the other current-version pointers; its § "SuccessState (v1.2 — …)" historical note was left as-is.
- `../ux/INSTRUCTOR_ADMIN_UX.md`'s two version mentions ("the four v1.2 components", "resolved in v1.3") are dated/historical, not current-version claims, and were left unchanged.

**Verified before closing this patch:** JSON validity of all `design-system/*.json` files; every `{alias}` reference in `component.*` resolves (63 distinct, unchanged count); zero circular alias references; zero raw `@N%`-style opacity modifiers remain anywhere in `component.*`; `color.semantic.light`/`dark` key-count parity unchanged (51/51); `COMPONENTS.md` prose already matched the corrected token values (no COMPONENTS.md edit was needed); no MVP screen count, user role, product feature, or UX behavior changed; no production code implemented; no Codex involvement.

**Not done (explicitly out of scope for this patch):**
- No visual redesign of any component, in either theme.
- No new component, token, or design pattern added.
- No product/UX scope, flow, or screen change.
- No Technical Architecture work.

---

## 1.3.0 — 2026-09-03

Pre-implementation patch, triggered by two approved product/UX decisions: (1) UX planning proved the v1.2 Dropdown/Select stopgap was insufficient — a real component was needed for Course Editor's Category/Level/Content-Language fields and Settings' Language selector; (2) English + Arabic were locked as functional (not Post-MVP) MVP languages (`../product/PRODUCT_SPEC.md § 16`). No file was restarted; all v1.0–v1.2 content is preserved. MINOR bump (additive only) per `DESIGN_RULES.md § Versioning & Governance`.

**Added — Dropdown / Select component** (`COMPONENTS.md § Select / Dropdown`, `design-tokens.json → component.select`):
- Full structure: label, placeholder, selected value, optional leading icon, trailing `expand_more`/`expand_less` indicator, option list/menu, helper text, error text.
- Full states: Default, Hover, Focused, Open, Selected (has a value), Error, Disabled — plus per-option Default/Hover/Selected/Disabled states within the menu.
- Field styling reuses `component.input`'s tokens exactly (height 52, `radius.medium`, same border/background/text/typography tokens); the menu reuses the popover convention already established by `videoPlayer.speedControl` and `dataTable.rowActions` (`elevation.3`, `surface.elevated`, `radius.medium`) — **all 20 of its alias references resolve to tokens that already existed before v1.3**, verified programmatically (63 distinct aliases total across the whole component layer, unchanged in count from v1.2 — every Select alias reused an existing target). Zero new colors, spacing steps, radii, typography styles, or motion values.
- Keyboard behavior (Tab/Enter/Space/Arrow keys/Escape/typeahead), touch behavior, native Android (`ExposedDropdownMenuBox`)/iOS (`Menu`/`Picker`) mapping, long-label truncation (`CONTENT_RESILIENCE.md`), and a "Loading options…" state are all specified. Web maps to an ARIA `combobox`/`listbox` pattern rather than a bare native `<select>`, since the native element's menu chrome can't be styled to the Mentora menu surface across browsers (`platform-mapping.md`) — the one v1.3 component whose Web mapping doesn't delegate to the plain native element.
- Retires the v1.2 stopgap: Course Editor's category field (and, new, Level and Content-Language fields) now specify the real component instead of a native `<select>` styled to `component.input` tokens as a placeholder.

**Added — Toggle text-pairing rule:** whenever a `Toggle` conveys a named state (e.g. Instructor Course Editor's Published/Draft), it must be paired with a visible text label naming that state — color and switch position are never the sole signal. Recorded in `design-tokens.json → component.toggle.$note`, `COMPONENTS.md`, and `ACCESSIBILITY.md § 14`. Driven by the approved UX decision to use `Toggle` (not a button pair) for Instructor course publishing.

**Added — Arabic font-family tokens** (`design-tokens.json → typography.fontFamily.{webArabic,androidArabic,iosArabic}`, `typography.arabicAdjustments`): formalizes into the token layer what `LOCALIZATION.md § 4` already specified in prose since v1.1 (IBM Plex Sans Arabic/Noto Sans Arabic on web, Noto Sans Arabic on Android, SF Arabic on iOS; zero letter-spacing and a 1.1× body line-height for Arabic text runs). **No font choice changed** — this is a formalization, not a redesign, done because Arabic is now a locked functional requirement rather than documented readiness. Existing Latin `typography.fontFamily.{web,android,ios}` keys are unchanged.

**Added — Locale-Aware Formatting** (`LOCALIZATION.md § 7`, new): product-level requirement that dates, numbers, durations, and prices format per active UI locale via each platform's locale-aware formatting API — never a hardcoded English presentation. Confirms the existing locked decision (Western Arabic numerals in both UI languages) still applies within locale-aware formatting, rather than being contradicted by it.

**Extended (not restarted):**
- `ACCESSIBILITY.md § 14` — Select/Dropdown accessibility contract (ARIA combobox/listbox on Web, native pickers on Android/iOS); Toggle's accessible-name requirement for named states. `§ 12` gained a confirmation note that all existing accessibility rules already apply identically to Arabic UI (no rule needed to change).
- `LOCALIZATION.md` — status banner confirming Arabic is now locked/functional, not aspirational; Forms section extended to cover Select; new DataTable and ReorderableList subsections in § 3 (a genuine documentation gap found during the Arabic/RTL review — these v1.2 components had no explicit RTL rule on record); new § 7 Locale-Aware Formatting (renumbers the former § 7 "Locked Decisions" to § 8, content preserved and extended with the Arabic-locked confirmation).
- `CONTENT_RESILIENCE.md § 1` — 2 new rows (Select's selected-value/placeholder truncation, option-row truncation).
- `platform-mapping.md § 7.1` — Select's native-control mapping table, with the ARIA-combobox rationale explained.
- `DESIGN_RULES.md` — new "Locked Decisions (v1.3 patch)" table recording the Select addition, the Toggle text-pairing rule, and the Arabic-locked confirmation.
- `COMPONENTS.md` — Select/Dropdown section added under § Inputs; Interaction State Matrix gained a Select row; Toggle's spec gained the text-pairing rule; component-token-layer intro updated to list `select`.

**Verified before closing this patch:** JSON validity (all `design-system/*.json`), every `{alias}` reference in `component.*` resolves (63 distinct, same count as v1.2 — confirming zero new alias targets), `color.semantic.light`/`dark` key-count parity unchanged (51/51), zero raw hex or arbitrary `@N%` opacity leaked into `COMPONENTS.md`, all internal markdown cross-references resolve, Demo Checkout's field set still contains zero financial-input fields.

**Not done (explicitly out of scope for this patch):**
- No product screens designed or redesigned.
- No production code implemented.
- No localization files, translation strings, or i18n library integration — that's Technical Architecture/Implementation.
- No Codex involvement.
- No change to the Purple/Indigo brand identity, Light/Dark mode resolution, or any existing component's visual identity beyond the Toggle text-pairing usage rule.

---

## 1.2.0 — 2026-09-03

Controlled amendment, triggered by product planning (`../product/SCREEN_INVENTORY.md`, `../product/DEMO_PAYMENT_FLOW.md`) identifying seven concrete MVP screens/flows that had no corresponding design-system component. No file was restarted; all v1.1 content is preserved. This is a MINOR bump (additive only, nothing renamed or removed) per `DESIGN_RULES.md § Versioning & Governance`.

**Added — 7 new components** (`COMPONENTS.md`, `design-tokens.json → component.*`):
- **VideoPlayer / PlaybackControls** — Course Player's video control contract (play/pause, scrubber, buffered range, volume, playback speed, fullscreen, time labels). Control-surface spec only, no streaming implementation. Deliberately theme-invariant (dark control chrome in both Light and Dark theme, since it always renders over arbitrary video pixels) — resolved via existing tokens (`color.overlay.scrim`, `color.text.inverse`), not a new "dark mode exception." Scrubber is a locked LTR-always exception per `LOCALIZATION.md § 2–3` (unchanged rule, now also stated at the component level).
- **Checkout / OrderSummary** — order-summary shell for the existing simulated Demo Payment flow (`../product/DEMO_PAYMENT_FLOW.md`). Closed field set (course summary, demo price, demo-payment notice, confirm action) — explicitly documented as never accepting card/CVV/expiry/billing/bank fields.
- **SuccessState** — new sibling of `EmptyState`/`ErrorState`/`LoadingState` in § State Patterns, for the Purchase Success moment and any future positive-confirmation screen. Uses existing `success.*` semantic tokens and existing motion tokens; respects `motion.reducedMotion`.
- **Toggle / Switch** — added to § Inputs. Specifies using the platform's *native* switch control (not a custom-built one) for built-in accessibility. All dimensions derive from the existing `space.*` scale (no new size tokens).
- **File & Media Upload** — new top-level section. Visual states only (Idle, Drag-over, Uploading, Success, Error) for Instructor thumbnail/video/resource attachment; no upload backend. Reuses `ProgressBar` for the uploading state and `TextField`'s error pattern for the error state.
- **ReorderableList / DragHandle** — new § Instructor & Admin Components, for Instructor section/lesson ordering. **Requires** always-visible Move Up/Move Down controls as a first-class accessible alternative to drag — not an optional fallback.
- **DataTable** — same new section, for Admin/Instructor list management. Responsive: collapses to stacked cards below the `tablet` breakpoint rather than forcing a cramped table onto a narrow viewport.

**Token accounting — zero new colors:** `color.semantic.light`/`color.semantic.dark` remain at 51 keys each, unchanged from v1.1. Every new `component.*` entry is a `{...}` alias into an existing `color.semantic.*`, `typography.scale.*`, `spacing.scale.*`, `shape.radius.*`, `elevation.*`, `border.*`, or `motion.*` value — verified programmatically (all 63 distinct aliases across the full component layer resolve to a real token path). `themes/theme-light.json`/`theme-dark.json` had no color values changed; only their `version` field was bumped to `1.2.0` for traceability.

**Added — icon directionality classifications** (`design-tokens.json → icon.directional.neverMirror`): `volume_up`, `volume_off`, `fullscreen`, `fullscreen_exit`, `speed`, `cloud_upload`, `insert_drive_file`, `drag_indicator`, `arrow_upward`, `arrow_downward`, `more_vert` — all newly-introduced icons from the seven components above, classified per `LOCALIZATION.md § 5`'s rule that no icon ships without a directionality classification. No new `mirrorInRtl` entries were needed.

**Added — `shape.componentUsage` entries:** `checkoutSummary`, `toggle`, `fileUpload`, `dataTableCollapsedCard` (radius documentation, reusing existing radius tokens — `radius.large`/`radius.full`/`radius.medium`, no new radius values).

**Extended (not restarted):**
- `ACCESSIBILITY.md` — new § 14 "v1.2 Component-Specific Accessibility Notes" (native Toggle semantics, ReorderableList's non-drag requirement, DataTable's collapsed-card semantic parity, VideoPlayer control labels, Checkout notice association, SuccessState live-region announcement, FileUpload's native-picker requirement). Existing § 14 renumbered to § 15.
- `CONTENT_RESILIENCE.md § 1` — 5 new rows (SuccessState title/description, VideoPlayer lesson title, Checkout line-item title, FileUpload filename [middle-truncation, a documented exception to the default end-ellipsis rule], DataTable cell text).
- `platform-mapping.md § 7.1` — native-control mapping notes for `toggle` and `fileUpload`'s Browse action; `dataTable`'s responsive-collapse and Web-primary status noted.
- `DESIGN_SYSTEM.md` — title bumped to v1.2, new v1.2 summary callout, § 9–19 component list updated to reference the seven additions.
- `COMPONENTS.md` — Interaction State Matrix extended with 7 new rows; component-token-layer intro updated; RTL/content-resilience footer updated.

**Verified before closing this patch:** JSON validity (all `design-system/*.json`), every `{alias}` reference in `component.*` resolves to a real token, `color.semantic.light`/`dark` key-count parity unchanged (51/51), zero raw hex or arbitrary `@N%` opacity leaked into `COMPONENTS.md`, all internal markdown cross-references resolve.

**Not done (explicitly out of scope for this patch):**
- No product screens (Home, Login, Course, Dashboard, etc.) designed.
- No production code implemented.
- No Codex involvement.

---

## 1.1.0 — 2026-09-03

Extension of v1.0 per the post-review pass. No file was restarted; all v1.0 content is preserved except the specific value corrections listed below.

**Added**
- 3-layer token architecture made explicit: `primitive → semantic → component`, documented in `DESIGN_SYSTEM.md § 0.1`, with a new `component.*` object in `design-tokens.json` (button, input, card, chip, badge, progressBar, snackbar, navItem aliases).
- Formal naming convention (`DESIGN_SYSTEM.md § 0.2`): `{category}.{concept}.{property}[.{variant}][.{state}]`.
- Explicit unit mapping (`design-tokens.json → units`, `DESIGN_SYSTEM.md § 2.1`, `platform-mapping.md § Units`): web rem/px, Android sp/dp, iOS Dynamic-Type-pt/pt, with the "semantically mapped, not physically identical" caveat.
- Responsive typography (`design-tokens.json → typography.responsive`, `DESIGN_SYSTEM.md § 2.2`): `display.*`/`heading.h1`/`h2` scale down below desktop; all other type tokens are constant across breakpoints.
- New files: `LOCALIZATION.md` (RTL/Arabic readiness — logical properties, directional icons, per-area mirroring rules, Arabic typography), `CONTENT_RESILIENCE.md` (long text, missing images, loading/error/empty data, localization expansion, text-scaling — with a per-component wrap/truncate reference table), `CHANGELOG.md` (this file).
- `ACCESSIBILITY.md`: browser zoom / Android font scaling / iOS Dynamic Type (§ 10), high-text-scaling structural rules (§ 11), long-translated-content accessibility framing (§ 12), a computed Verified Contrast Ratios appendix (§ 13).
- `COMPONENTS.md`: an Interaction State Matrix (Default/Hover/Focused/Pressed/Selected/Loading/Disabled/Error/Success applicability per component category) and a component-token-layer explainer.
- `DESIGN_RULES.md`: rules 13–19 (primitive-layer restriction, naming convention, no-arbitrary-opacity, RTL-by-default, content-resilience-by-default, no-hardcoded-theme-branching, versioning requirement), a Versioning & Governance section, and a Locked Decisions section.
- New color tokens: `color.semantic.{light,dark}.brand.onSurfaceInverse`, `color.semantic.{light,dark}.overlay.chipScrim`.
- New icon metadata: `icon.directional.{mirrorInRtl,neverMirror}`.

**Changed (value corrections — WCAG AA verification)**

Three v1.0 color values were computed to fail WCAG AA and were corrected; nothing else in the starting palette changed. Full before/after and rationale in `DESIGN_SYSTEM.md § 1.1` and `ACCESSIBILITY.md § 13`.

| Token | Before | After | Reason |
|---|---|---|---|
| `color.semantic.light.secondary.default` | `#536DFE` | `#4A62F0` | White `onSecondary` text was 4.21:1, below the 4.5:1 minimum. |
| `color.semantic.light.warning.onWarning` | `#FFFFFF` | `#3A2200` | White text on `warning.default` was 2.53:1. Switched to dark text, matching the convention dark theme already used. |
| `color.semantic.light.border.strong` | `#C7C8D1` | `#8B8C96` | 1.67:1 against typical surface, below the 3:1 UI-component minimum. |
| `color.semantic.dark.border.strong` | `#4A4B54` | `#71727D` | 2.00:1 against typical surface, below the 3:1 UI-component minimum. |

**Fixed (arbitrary values removed from `COMPONENTS.md`)**

Replaced with documented tokens; no visual intent changed beyond what's noted:
- `SecondaryButton` hover/pressed: undocumented `primaryContainer @ 40%/60%` → `brand.primary @ state.hoverOpacity/pressedOpacity`.
- `TonalButton` hover/pressed: cleaned up redundant "darkened 8%" phrasing → pure `state.hoverOpacity/pressedOpacity` state-layer description.
- `TextField` disabled border: undocumented `border.default @ 50%` → `border.default` (background/text dimming already signal disabled).
- `LearningPathCard` description/meta text: undocumented `onPrimaryContainer @ 80%/70%` → full-opacity `onPrimaryContainer`, hierarchy via type scale instead (consistent with design principle 4).
- `CategoryChip` on-image overlay: undocumented `surface.inverse @ 70%` → new named token `color.overlay.chipScrim`.
- `CertificateCard` border: undocumented `brand.primary @ 30%` tint → standard `color.border.default` (dropped the special-cased flourish).
- `Snackbar` action color: vague prose ("lighter brand tone for sufficient contrast") → new named, contrast-verified token `color.brand.onSurfaceInverse`.

**Verified, unchanged**
- Dark-mode elevation approach (subtle, `surface.elevated`-driven, not heavy shadow) — reconfirmed correct.
- Material Symbols Rounded as the sole icon language — reconfirmed, with the SF Symbols OS-chrome exception now explicitly bounded to "equivalent visual meaning only."
- CourseCard as a single responsive component — reconfirmed, cross-referenced from three files instead of one.
- All 30 components in `COMPONENTS.md` re-audited: every token reference resolves to an entry in `design-tokens.json`; no duplicate/conflicting tokens found beyond the pre-existing, now-documented `color.secondary.*` vs. `SecondaryButton` naming overlap (kept, documented, not renamed).

**Not done (out of scope, unchanged from v1.0 status)**
- No product screens (Home, Login, Course, Dashboard, etc.) designed.
- No Web, Android, iOS, backend, database, API, or auth implementation.
- No Codex involvement in this revision.

---

## 1.0.0 — 2026-09-03

Initial release. `DESIGN_SYSTEM.md`, `design-tokens.json`, `themes/theme-light.json`, `themes/theme-dark.json`, `platform-mapping.md`, `COMPONENTS.md`, `ACCESSIBILITY.md`, `DESIGN_RULES.md` created from the founding brief (Material 3 Expressive-inspired direction, purple/indigo brand, Web/Android/iOS scope).
