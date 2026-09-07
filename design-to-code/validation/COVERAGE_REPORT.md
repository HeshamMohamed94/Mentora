# Design-to-Code — Coverage Report

Per-screen reference-type, component coverage, and token coverage for all 24 MVP Web screens implemented through Phase 2 Task 11. Admin (screens 25-29) is explicitly excluded — see § 4.

---

## 1. Reference-type coverage

| # | Screen | referenceType | Notes |
|---|---|---|---|
| 1 | Landing | exact-showcase | Hero collage proportions approximate, not byte-identical |
| 2 | Explore | exact-showcase | Showcase's own reference frame is itself a mobile-mockup pair — see EXTRACTION_REPORT.md conflict #5 |
| 3 | Course Details | approved-pattern | Mobile mockup only |
| 4 | Learning Paths | ux-only | No mockup |
| 5 | Learning Path Details | ux-only | No mockup, no dedicated component spec either |
| 6 | Login | ux-only | No mockup |
| 7 | Register | ux-only | No mockup |
| 8 | Dashboard | exact-showcase | Open conflict: 4-module layout vs. locked 3-module cap |
| 9 | My Learning | approved-pattern | Mobile mockup only; desktop horizontal-row adaptation is correct, not a defect |
| 10 | Course Player | exact-showcase | LOCKED shell exception (focused learning shell) |
| 11 | Quiz | ux-only | No mockup |
| 12 | Quiz Results | ux-only | No mockup |
| 13 | Certificates List | ux-only | No mockup, no dedicated component spec either |
| 14 | Certificate Detail | ux-only | No mockup, no dedicated component spec either |
| 15 | AI Tutor | approved-pattern | Mobile mockup only |
| 16 | Profile | ux-only | No mockup, no dedicated component spec either |
| 17 | Settings | ux-only | No mockup |
| 18 | Demo Checkout | approved-pattern | Mobile mockup only — highest-matching screen in the original audit (93%) |
| 19 | Purchase Success | approved-pattern | Mobile mockup only — 2nd highest-matching screen (91%) |
| 20 | Instructor Dashboard | exact-showcase | Open conflict: 4th stat card / table columns vs. locked spec |
| 21 | Course Editor — Overview | exact-showcase | Open conflict: Media tab / persistent rail vs. locked spec |
| 22 | Course Editor — Curriculum | exact-showcase | Shares Overview's conflict; own structure (sections/lessons/reorder) unaffected |
| 23 | Lesson Editor | ux-only | No mockup |
| 24 | Quiz Editor | ux-only | No mockup |

**Totals:** exact-showcase 7 (29%), approved-pattern 5 (21%), ux-only 12 (50%).

## 2. Component coverage

Every component named in `product/SCREEN_INVENTORY.md`'s per-screen "Design system components" lists has a corresponding recipe in `design-to-code/shared/components.json`, verified by `tools/design-to-code/validate.js`'s componentSequence resolution check (zero unresolved component references across all 24 screens, confirmed by a clean validation run). Component families covered: Buttons (5 variants), Inputs (TextField/PasswordField/SearchField/Select/Toggle), File & Media Upload, Cards (7 variants), Chips & Badges, Avatar, ProgressBar, Media & Playback (VideoPlayer), Checkout, Dialogs & Sheets, State Patterns (Loading/Empty/Error/Success), Navigation (Navbar/Sidebar/MobileBottomNav/Tabs), AI Tutor, Quiz System, Instructor & Admin (ReorderableList, DataTable).

**Not covered (deliberately):** Admin-specific DataTable usage patterns for screens 25-29 — the component recipe itself (`dataTable`) is fully specified (it's shared with Instructor Dashboard), but no Admin screen spec references it this phase.

## 3. Token coverage

Every token category in `design-tokens.json` has a corresponding `design-to-code/shared/*.json` file: color (tokens.json), typography (typography.json), spacing (spacing.json), shape (shape.json), elevation (elevation.json), border/motion/icon/breakpoints/grid (tokens.json). `tools/design-to-code/validate.js`'s bare-color-reference spot-check found zero unresolved `color.*` references inside `shared/components.json`'s structured state tables (0 warnings on the current build).

**Not independently re-validated:** prose-embedded token references (e.g. inside a `states` object's free-text notes, or a composite `"color.brand.primaryContainer @ state.hoverOpacity"` expression) are not individually parsed — the validator's spot-check is intentionally narrow (see `validate.js`'s own doc comment) rather than claiming a false sense of complete semantic validation.

## 4. Why Admin (screens 25-29) has no design-to-code spec this phase

The locked `product/SCREEN_INVENTORY.md §D` and `ux/SCREEN_UX_SPECS.md §D` already fully define Admin Dashboard, Manage Courses, Manage Users, Manage Instructors, and Manage Categories — the brief for this phase explicitly permitted generating Admin specs "from already-approved design documentation if the reference exists." They were **not** generated, as a deliberate, conservative choice: Task 12 (Admin Web) has explicitly not started, and the brief's repeated, emphatic "DO NOT START TASK 12" instruction was read as extending to avoiding even spec-only artifacts for Admin screens, to remove any ambiguity about whether this phase began Task 12 work. This is a scope decision, not an oversight — the locked source material for Admin specs is fully available and unchanged, so producing `design-to-code/screens/admin-*.json` later (when Task 12 is explicitly authorized) is a small, low-risk addition following the exact same template as the 24 screens already built.
