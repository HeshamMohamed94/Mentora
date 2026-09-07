# Mentora Design-to-Code

A structured, machine-readable source of truth derived from the **locked** Mentora Design System v1.3.2, the locked Mentora Showcase, and the locked Product/UX specs — built so future work (Web now, Android/iOS later) can be driven from data rather than developers re-approximating screenshots.

**This is not a redesign.** Nothing under `design-system/`, `product/`, `ux/`, or `architecture/` was modified to build this — everything here is a normalized, cross-referenced re-shape of what those directories already say. See `SOURCE_MANIFEST.json` for the exact precedence rule used whenever two sources disagree.

## Layout

```
design-to-code/
  README.md                  — this file
  SOURCE_MANIFEST.json        — authoritative source registry + precedence rule
  shared/                     — platform-neutral token/component/navigation/artwork layer
    tokens.json                — color/border/motion/icon/breakpoint/grid (normalized design-tokens.json)
    typography.json            — type scale + responsive steps
    spacing.json                — spacing scale + logical-property application rules
    shape.json                  — radius scale
    elevation.json              — elevation levels
    components.json             — every named component's recipe (sizing/states/tokens), no business logic
    navigation.json             — shell rules per role/surface, incl. the LOCKED Course Player exception
    artwork.json                — the governed course-artwork motif/gradient/scrim system
    responsive.json             — breakpoints + per-surface responsive exceptions
    localization.json           — EN/AR + RTL rules, content-role references (never hardcoded copy)
    platform-contract.json      — the Web/Android/iOS generator contract
  screens/                    — one JSON spec per implemented MVP screen (24 files, Tasks 1-11 scope)
  patterns/                   — reusable layout patterns for screens with no exact showcase mockup
  generated/web/              — non-code generated artifacts (JSON reports) from this pipeline
  validation/
    EXTRACTION_REPORT.md        — what was extracted from the showcase vs. inferred, conflicts found
    COVERAGE_REPORT.md          — per-screen reference-type + component/token coverage
    MAPPING_REPORT.md           — shared -> Web mapping, hardcoded values removed, Android/iOS readiness
```

## How this connects to the Web app

The **actual code-generation** happens in `tools/design-to-code/` (parallel to the pre-existing `tools/token-pipeline/`, which this pipeline does NOT replace or duplicate):

- `tools/token-pipeline/generate.js` (pre-existing) — reads `design-system/design-tokens.json` + `themes/*.json` directly, writes raw CSS custom properties (`web/styles/tokens.css`) and a few JS constants (`web/src/lib/design-tokens.generated.ts`).
- `tools/design-to-code/generate.js` (new, this phase) — reads `design-to-code/shared/*.json` and `design-to-code/screens/*.json`, writes the layer ABOVE raw tokens: named component-recipe constants, navigation/shell rules, artwork motif mapping, and a screen-id → route table, into `web/src/lib/design-to-code.generated.ts`.
- `tools/design-to-code/validate.js` — structural validation (token references resolve, component names exist, no duplicate screen ids, no circular pattern references). Generation fails loudly rather than emit partial output on any violation.

Run both from `web/`:

```
npm run validate:design-to-code
npm run generate:design-to-code
```

## Screen coverage (24 screens — Tasks 1-11 scope; Task 12/Admin excluded)

| referenceType | count | meaning |
|---|---|---|
| `exact-showcase` | 7 | A genuine assembled desktop mockup exists in the locked showcase for this exact screen. |
| `approved-pattern` | 5 | Only a mobile mockup exists — desktop composition follows `patterns/mobile-to-desktop-adaptation.json`. |
| `ux-only` | 12 | No mockup of any kind exists — judged against `ux/SCREEN_UX_SPECS.md` + `design-system/COMPONENTS.md` alone. |

Admin screens (25-29) are **not** specced this phase, even though the locked `ux/`/`product/` docs already define their shape — see `validation/COVERAGE_REPORT.md` for why, and to avoid any appearance of starting Task 12.

## Conflict discipline

Every time the showcase's visual composition disagrees with a locked Product/UX decision, the conflict is recorded — never silently resolved — in the affected screen's own `conflicts` array AND in `validation/EXTRACTION_REPORT.md`. Three such conflicts are currently open, all pre-existing and re-confirmed (not newly discovered) from `execution/DECISIONS_LOG.md` D47-D49:

1. Instructor Dashboard's 4th stat card / table columns (showcase vs. `ux/INSTRUCTOR_ADMIN_UX.md`).
2. Course Editor's Media tab / persistent Curriculum rail (showcase vs. `product/SCREEN_INVENTORY.md §C`).
3. Course Player's "no sidebar at all" showcase caption vs. the locked "sidebar collapses" rule (`ux/SCREEN_UX_SPECS.md § 10`) — resolved in favor of collapse, encoded as the locked exception in `shared/navigation.json#/shells/coursePlayerShell`.
