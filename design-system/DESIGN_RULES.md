# Mentora Design System — Rules for Future Development

This file governs how Mentora's UI is built going forward, on Web, Android, and iOS alike. It applies to every contributor — human or AI (Claude, Codex, or any other assistant). **Read this before implementing any screen.**

---

## The Rules

1. **Never hardcode colors inside UI components.** Every color comes from a semantic token in [`design-tokens.json`](./design-tokens.json) / [`theme-light.json`](./themes/theme-light.json) / [`theme-dark.json`](./themes/theme-dark.json), consumed through the platform binding in [`platform-mapping.md`](./platform-mapping.md). No inline `#hex`, no `rgba()` literals, no raw `Color(0xFF...)`/`UIColor(...)` in component code.

2. **Never hardcode spacing if a token exists.** Use `space.*` tokens exclusively. If a layout seems to need a value between two spacing steps, that's a signal to re-examine the layout, not to introduce `space.7` or an arbitrary pixel value.

3. **Never create arbitrary font sizes.** Use only the tokens in `typography.scale`. If no existing style fits, that is a design-system gap to raise and resolve (see "Proposing a new token" below) — not something to patch locally with a one-off size.

4. **Never create new border radius values without updating the token system.** All radii come from `shape.radius.*`. A new radius requirement gets added to `design-tokens.json` and documented in `DESIGN_SYSTEM.md § 4` before it's used anywhere.

5. **Never create duplicate button/input/card components.** If a "new" button or card variant is needed, check [`COMPONENTS.md`](./COMPONENTS.md) first — it likely already exists as Primary/Secondary/Tonal/Text, or as a card composition. Do not spin up `PrimaryButtonV2`, `CourseCardAlt`, etc.

6. **Reuse existing components before adding new ones.** Compose from what's documented (a `StatCard` + `Badge` + `ProgressBar` covers most dashboard tiles) before proposing a net-new component.

7. **Any new reusable design pattern must be documented.** If a pattern is used more than once, it belongs in `COMPONENTS.md` with the same rigor as existing entries (dimensions, typography, color mapping, radius, border, every state) — not left as implicit convention in one screen's code.

8. **Every screen supports Light and Dark Mode.** No screen ships checked off until both themes are verified — this includes contrast-checking any new token pairing per [`ACCESSIBILITY.md`](./ACCESSIBILITY.md).

9. **Web, Android, and iOS share the same visual identity.** Same color tokens, same type scale, same spacing, same shape language, same motion timing. A Mentora screen should be recognizably Mentora regardless of platform.

10. **Platform-specific UX differences are allowed where they improve usability.** Native navigation patterns (iOS swipe-back, Android system back, web browser history), native share sheets, native date/time pickers, and platform-conventional gesture handling are expected to differ — the visual language (color/type/shape/motion tokens) does not.

11. **Any new token must be added to the source-of-truth design token files.** A token used in exactly one place is still a token — it goes into `design-tokens.json` (and, if it's a color, into both `theme-light.json` and `theme-dark.json`) before it's referenced in code or in `COMPONENTS.md`.

12. **Claude and Codex must always inspect the Design System before implementing UI.** Before writing or generating any screen, component, or style, read the relevant sections of `DESIGN_SYSTEM.md`, `design-tokens.json`, and `COMPONENTS.md`. If a needed value or component isn't there, stop and either (a) compose it from existing primitives, or (b) propose the addition explicitly to the user rather than inventing it silently.

### Added in v1.1

13. **Never reach past the semantic layer to a primitive.** Product/component code consumes `color.semantic.*` (or a `component.*` alias) — never `color.primitive.*`. See [`DESIGN_SYSTEM.md § 0.1 Token Architecture`](./DESIGN_SYSTEM.md).

14. **Follow the naming convention for every new token.** `{category}.{concept}.{property}[.{variant}][.{state}]`, lowerCamelCase, dot-separated — see [`DESIGN_SYSTEM.md § 0.2`](./DESIGN_SYSTEM.md). A token that doesn't fit this shape doesn't get added as-is.

15. **No arbitrary opacity, tint, or "darkened X%" values.** Every state modifier is one of the documented `state.*` opacity tokens (`hoverOpacity`, `pressedOpacity`, `focusOpacity`, `disabledContentOpacity`, `disabledContainerOpacity`) or a distinct named token (e.g. `brand.onSurfaceInverse`, `overlay.chipScrim`) — never an inline `@ 40%` invented for one component. This was retroactively enforced across `COMPONENTS.md` in v1.1; see the changelog.

16. **RTL/logical-layout is not optional groundwork.** Every new component uses logical (`start`/`end`) properties from the first implementation, per [`LOCALIZATION.md`](./LOCALIZATION.md) — not "we'll add RTL later." A component reviewed with hardcoded `left`/`right` fails review.

17. **Every new component ships a content-resilience entry.** Long text, missing images, loading/empty/error states, and text-scaling behavior are specified alongside the component's first version in `COMPONENTS.md`/[`CONTENT_RESILIENCE.md`](./CONTENT_RESILIENCE.md) — not patched in after a real-world content failure is reported.

18. **No hardcoded light/dark branching inside component code.** A component consumes theme-resolved semantic/component tokens and never contains logic like "if dark mode, use `#24252E`." If a component needs to render differently by theme beyond what the token values already encode, that's a sign a token is missing, not a reason to branch — see [`DESIGN_RULES.md § Locked Decisions`](#locked-decisions-2026-09-03-review) below.

19. **Every design-system change bumps the version and updates the changelog.** See § Versioning & Governance below — a token/component/rule change without a corresponding [`CHANGELOG.md`](./CHANGELOG.md) entry is incomplete.

---

## Proposing a New Token or Component

When an existing token/component genuinely doesn't cover a real need:

1. State the gap explicitly (what's missing, and why composing existing tokens doesn't work).
2. Propose the new value using the same naming convention as its neighbors (`color.{group}.{name}`, `typography.{group}.{name}`, etc.).
3. If it's a color, provide both light and dark values and confirm contrast against the surfaces it will sit on (see `ACCESSIBILITY.md § 1`).
4. Add it to `design-tokens.json` (and `theme-light.json`/`theme-dark.json` if a color) — this is the only place values are hand-authored.
5. Document its usage in `DESIGN_SYSTEM.md` and, if it's part of a component, in `COMPONENTS.md`.
6. Only then use it in product code.

Never take a shortcut that skips step 4 — an inline value that "will get tokenized later" is exactly how a design system rots.

---

## Versioning & Governance

The design system versions independently of the product it styles, using semver (`MAJOR.MINOR.PATCH`), tracked in `design-tokens.json → meta.version` and itemized in [`CHANGELOG.md`](./CHANGELOG.md).

| Bump | When |
|---|---|
| **MAJOR** | A breaking change: a token is renamed or removed (not just added), a component's structure changes in a way existing usages must be updated for, or the token architecture itself changes (e.g. a 4th layer added). Requires an explicit migration note in `CHANGELOG.md`. |
| **MINOR** | Backward-compatible additions: new tokens, new components, new documented rules, corrections to existing values that fix a defect (e.g. a contrast failure) without renaming anything. This patch is a MINOR bump (1.0.0 → 1.1.0). |
| **PATCH** | Documentation-only fixes, typo corrections, clarified wording with no value or rule change. |

**Process for any change:**
1. Land the change in `design-tokens.json` (and `theme-light.json`/`theme-dark.json` if color) first — the hand-authored source.
2. Update every markdown file that documents the changed value/rule (do not let `DESIGN_SYSTEM.md`/`COMPONENTS.md`/etc. drift from the JSON).
3. Bump `meta.version` and the `version` field in both theme files to match.
4. Add a dated entry to `CHANGELOG.md`.
5. Re-run the contrast check (§ `ACCESSIBILITY.md § 13`) for anything color-related before calling the change done.

**Deprecation:** a token slated for removal is marked deprecated (a `"deprecated": true` flag or a note in the surrounding markdown) for at least one MINOR release before a MAJOR release removes it, giving implementers a version to migrate off it.

---

## Locked Decisions (2026-09-03 review)

Reviewed and closed per the v1.1 request — each is now a rule, not an open question:

| Decision | Resolution |
|---|---|
| **Per-component state hex values** | Removed. Every interactive state across `COMPONENTS.md` now derives from a documented semantic/component token — either an explicit state-specific semantic token (`brand.primaryHover`, `brand.primaryPressed`, …) for the highest-visibility controls, or a `state.*` opacity overlay for everything else. No component table contains an inline, undocumented opacity/tint (see rule 15 and `CHANGELOG.md` for the specific fixes applied). |
| **WCAG AA on all important color pairs** | Verified computationally, not assumed. Three pairings failed and were corrected (`secondary.default`, `warning.onWarning`, `border.strong` in both themes) — see `DESIGN_SYSTEM.md § 1.1` and the full pass/fail table in `ACCESSIBILITY.md § 13`. All locked values now pass except the WCAG-exempt `text.disabled` pairing, documented as an intentional exemption. |
| **Dark-mode elevation** | Stays subtle, resolved through `surface.elevated`/`elevation.*` tokens (a lighter tone, not a heavier shadow) — unchanged from v1.0, reconfirmed correct on review. Components never compute their own dark-mode shadow. |
| **Icon system** | Material Symbols Rounded remains canonical on all three platforms. Platform-native icon substitution (SF Symbols) stays limited to OS-owned chrome Mentora doesn't draw, and only when visual meaning (silhouette, stroke weight) is preserved — unchanged from v1.0. |
| **CourseCard as one component** | Confirmed: CourseCard is one responsive component whose *container* (grid column count/width) changes per breakpoint, not its internal design. Reconfirmed explicitly in `DESIGN_SYSTEM.md § 9–19`. |
| **`color.secondary.*` vs. `SecondaryButton` naming collision** | Documented, not renamed (`DESIGN_SYSTEM.md § 0.2`) — renaming either would touch more surface area than the ambiguity itself causes, since `SecondaryButton` never actually reads from `color.secondary.*`. Revisit only if this causes a real implementation bug, not preemptively. |

## Locked Decisions (v1.3 patch)

| Decision | Resolution |
|---|---|
| **Dropdown / Select** | Added as a real component (`COMPONENTS.md § Select / Dropdown`, `design-tokens.json → component.select`) — the v1.2 stopgap ("style a native `<select>` to `component.input` tokens") is retired. Every one of its alias references reuses a pre-existing token; zero new colors/spacing/radii/typography/motion values. |
| **Toggle must pair with a visible text label when it conveys a named state** | Locked as both a component usage rule (`COMPONENTS.md`, `design-tokens.json → component.toggle.$note`) and an accessibility requirement (`ACCESSIBILITY.md § 14`) — driven by the Instructor Publish/Unpublish use case, generalized to every future use of Toggle for a named state. |
| **English + Arabic are both locked, functional MVP languages** | Not a design-system decision to make (it's a product requirement, `../product/PRODUCT_SPEC.md § 16`) but confirmed here as **already satisfied** by the RTL/logical-properties architecture in place since v1.0/v1.1 (`LOCALIZATION.md`) — no retroactive rework of existing components was required, only the additions above (Select's RTL notes, the Arabic font-family tokens, locale-aware formatting guidance). This is recorded as a locked decision because it forecloses ever "temporarily" hardcoding `left`/`right` or English-only strings anywhere going forward — the RTL/logical-properties rules (rule 16, `LOCALIZATION.md`) are no longer aspirational. |

---

## Ownership Model

- **Source of truth for values:** `design-tokens.json` (hand-authored).
- **Generated, not hand-edited:** any per-platform token file (`web/tokens.css`, `android/MentoraTokens.kt`, `ios/MentoraTokens.swift`) per the structure in `platform-mapping.md § 9`. If a platform token file and `design-tokens.json` ever disagree, `design-tokens.json` wins and the generated file is regenerated — never patched by hand to "fix" a mismatch.
- **Source of truth for component behavior/states:** `COMPONENTS.md`.
- **Source of truth for accessibility minimums:** `ACCESSIBILITY.md`.

---

## Before Any Screen Is Designed or Implemented

Checklist:

- [ ] Every color used maps to a `color.*` semantic token, present in both themes.
- [ ] Every text style maps to a `typography.*` token.
- [ ] Every spacing value maps to a `space.*` token.
- [ ] Every radius maps to a `shape.radius.*` token.
- [ ] Every shadow/elevation maps to an `elevation.*` token.
- [ ] Every component used is either an existing entry in `COMPONENTS.md` or a newly documented one (rule 7).
- [ ] Light and dark mode both verified — resolved through semantic tokens, no hardcoded theme branch in the component.
- [ ] Contrast, focus states, touch targets, and screen reader labels checked against `ACCESSIBILITY.md`, including zoom/font-scaling/Dynamic Type (`ACCESSIBILITY.md § 10–11`).
- [ ] Any new token was added to `design-tokens.json` first, not invented inline, and follows the naming convention (`DESIGN_SYSTEM.md § 0.2`).
- [ ] Layout uses logical (`start`/`end`) properties throughout — no `left`/`right` — per `LOCALIZATION.md`.
- [ ] Long text, missing images, loading/empty/error, and translation-expansion behavior are specified per `CONTENT_RESILIENCE.md § 1`.
- [ ] No arbitrary opacity/tint value — every state modifier is a `state.*` token or a named semantic/component token.
