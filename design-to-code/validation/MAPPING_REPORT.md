# Design-to-Code — Mapping Report

How the shared JSON source maps onto the Web implementation, what was actually migrated this phase, and Android/iOS future-readiness.

---

## 1. Shared token → Web mapping

Unchanged and NOT touched by this phase — `tools/token-pipeline/generate.js` (pre-existing) continues to be the sole generator of raw CSS custom properties (`web/styles/tokens.css`, `web/styles/tailwind-theme.css`) and low-level JS constants (`web/src/lib/design-tokens.generated.ts`) directly from `design-system/design-tokens.json` + `themes/*.json`. `design-to-code/shared/tokens.json` et al. are a **normalized re-shape of the same source** for cross-referencing and future Android/iOS generators — they do not feed back into the CSS generator, avoiding any duplicate/conflicting pipeline (per the explicit "do not create a redundant conflicting pipeline" instruction).

## 2. Shared component → React component mapping

`design-to-code/shared/components.json` documents the recipe every existing React component in `web/src/components/ui/` already implements (Button, TextField, Select, Toggle, FileUpload, ReorderableList, DataTable, VideoPlayer, Checkout, SuccessState, Card variants, etc.) — this phase did **not** rewrite any of these components' internals, since they were already built correctly against the same `COMPONENTS.md` source in Tasks 1-11. The mapping here is documentary/normative (a machine-readable mirror of `COMPONENTS.md`), not a new code-generation target for component internals.

## 3. Shared screen spec → route mapping

New this phase: `web/src/lib/design-to-code.generated.ts`'s `screenRoutes` export — a generated `screenId -> {routeIntent, referenceType, screenNumber}` table for all 24 screens, generated from `design-to-code/screens/*.json`. Not yet consumed by application code (no existing code needed a route-metadata table), but available for future tooling (e.g. a documentation page, an e2e test matrix, or a future admin nav generator) without re-deriving it by hand.

## 4. Hardcoded visual values removed this phase

Two concrete, verified removals — both confirmed byte-identical output via live browser re-verification (Dashboard/Explore/Instructor Dashboard, EN+AR, before/after):

1. **Sidebar nav-item arrays.** `web/src/components/navigation/app-shell.tsx`'s `NAV_ITEMS` (8 entries) and `web/src/components/navigation/instructor-shell.tsx`'s `INSTRUCTOR_NAV_ITEMS` (3 entries) were hand-written arrays duplicating what `design-to-code/shared/navigation.json#/shells/authenticatedStudent` and `#/shells/instructorWeb` already define as the locked shell contract. Both files now import `studentNavItems`/`instructorNavItems` from the generated `design-to-code.generated.ts` instead of hardcoding the list — `shared/navigation.json` is now the single source, with the pre-migration array preserved verbatim inside it as `itemsStructured` (and the file it was copied from noted as `itemsStructuredSource`, since generation direction here is source-controls-code, not code-controls-source).
2. **Course-artwork motif array.** `web/src/components/ui/course-thumbnail.tsx`'s `ARTWORK_MOTIFS` (5 gradient+icon pairs, ~2KB of literal CSS gradient strings) and its `motifFor()` hash function are now generated from `design-to-code/shared/artwork.json#/motifSystem` into `artworkMotifs`/`artworkMotifFor()` in `design-to-code.generated.ts` — `course-thumbnail.tsx` imports and calls the generated function instead of hand-defining the array and hash logic inline.

## 5. Remaining legitimate screen-specific values (NOT touched, correctly)

- Every CSS value in `web/src/app/components.css` that already resolves through a `var(--...)` custom property (the vast majority, established across Tasks 1-11 and the D45-D49 fidelity passes) — these already consume the token-pipeline's generated output correctly and are out of this phase's scope to re-migrate.
- Screen-specific layout numbers with no governed equivalent (e.g. `.mtx-editor-panel`'s `max-width: calc(var(--space-16) * 13)` — a screen-specific composed value built FROM tokens, not a raw magic number) were left as-is; they are legitimate content/layout decisions, not duplicated design-system values.
- AI Tutor's `QUICK_ACTION_KEYS` array (`ai-tutor-screen.tsx`) was deliberately NOT migrated — it is a list of **i18n translation keys** driving real, localized copy (`en.json`/`ar.json`), not a visual/layout constant. Migrating it would violate Step 17's explicit rule against encoding translated copy into design specs; `shared/components.json#/aiTutor/quickAction/defaultActions` already documents the English-default label set for design reference only, correctly separate from the app's actual localization resource.

## 6. Android future-readiness

`design-to-code/shared/platform-contract.json#/android` is unblocked to start from day one of a future Android effort: token dot-paths already match `platform-mapping.md`'s documented camelCase convention 1:1, `shared/components.json`'s recipes require no Android-specific re-authoring (only a new Compose renderer), and `shared/navigation.json`'s `itemsStructured` shape (key/href/icon) is a trivial transform away from a Compose `NavHost` route table. **Not started this phase** — no `android/` directory exists, none was created.

## 7. iOS future-readiness

`design-to-code/shared/platform-contract.json#/ios` is similarly unblocked: `shared/tokens.json`'s typography scale carries the base fontSize/lineHeight numbers a SwiftUI Dynamic-Type wrapper needs, `shared/components.json#/select` already documents its Menu/Picker-style native mapping, and `shared/artwork.json`'s motif system requires no iOS-specific restructuring (gradient + icon + chip composition is platform-agnostic). **Not started this phase** — no `ios/` directory exists, none was created.

## 8. Validation summary

`tools/design-to-code/validate.js` (run before every generation, and independently before this report): 24 screens, 6 patterns, 11 shared files checked — **0 errors, 0 warnings** on the final build (the bare-color-reference spot-check inside `shared/components.json` found zero unresolved references).
