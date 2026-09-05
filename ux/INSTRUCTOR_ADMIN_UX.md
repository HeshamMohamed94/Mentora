# Mentora UX — Instructor & Admin Experience

Web-first management surfaces, per `../product/USER_ROLES.md`. Full per-screen field-by-field specs live in [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md). This file defines how the four v1.2 components — `DataTable`, `ReorderableList`, `FileUpload`, `Toggle` — compose into the Instructor and Admin workflows, and keeps this surface deliberately simple: an internal tool for a small number of power users, not a public-facing product surface, and explicitly **not** an enterprise admin suite (no audit log, no granular permission editor, no bulk operations — `../design-system/DESIGN_RULES.md` review already ruled these out at the design-system level, and product planning ruled them Post-MVP at the feature level).

---

## 1. Instructor

### Dashboard

- **Layout:** Sidebar (Dashboard, My Courses, Profile — `../product/INFORMATION_ARCHITECTURE.md § 4`) + content column.
- **Content order:** page title "Dashboard" → a `StatCard` row (total courses, published count, total enrollments — 3 cards max, matching the Dashboard-simplicity principle from `WEB_UX.md § 5`) → "Create Course" primary action → course list.
- **Course list rendering:** `DataTable` (title, status `Badge` Draft/Published, enrollment count, completion rate, row action to open the editor) — collapses to cards below Desktop per `RESPONSIVE_BEHAVIOR.md § 6`.

### Create / Edit Course (Course Editor — Overview)

- **Layout:** Tabs at the top of the Course Editor screen-group — **Overview** (active) | **Curriculum**.
- **Content order:** title `TextField` → description (multiline `TextField`) → category `Select` (DS v1.3) → **Level `Select`** (Beginner/Intermediate/Advanced, v1.3 new field) → **Content Language `Select`** (the language the course content itself is authored/recorded in — independent of UI language, v1.3 new field, `../product/PRODUCT_SPEC.md § 16`) → price `TextField` (numeric, demo pricing per `../product/DEMO_PAYMENT_FLOW.md § 5`) → thumbnail `FileUpload` → **Publish readiness checklist** (a simple list of met/unmet requirements, e.g. "✓ Title set," "✗ At least one lesson with video") → Publish/Unpublish `Toggle` (approved UX decision — see § below), disabled until the checklist passes.
- **FileUpload usage:** thumbnail image, Idle/Drag-over/Uploading/Success/Error states exactly as specced in `../design-system/COMPONENTS.md § File & Media Upload` — no Instructor-specific variant.

### Sections and Lessons (Course Editor — Curriculum)

- **Layout:** ordered list of sections, each expandable to its ordered lessons, rendered with `ReorderableList`.
- **Content order:** "Add Section" primary action at the top → section rows (each with its own "Add Lesson" action and its nested `ReorderableList` of lesson rows) → "Add Quiz" action at the bottom of the curriculum (course-level, one quiz per course per `../product/PRODUCT_SPEC.md § 14`).
- **Reordering:** drag handle **and** the always-visible Move Up/Move Down `IconButton`s per `../design-system/COMPONENTS.md § ReorderableList` — both sections (top level) and lessons (within a section) are reorderable this way; a lesson cannot be dragged across sections in MVP (moving a lesson to a different section, if ever needed, is a delete-and-recreate action — kept simple deliberately, not a missing feature).
- **Lesson row content:** title, video-attached indicator (`Badge`), duration (if known), reorder/delete controls.

### Lesson Editor

- **Content order:** title `TextField` → description (multiline `TextField`) → video `FileUpload` (Idle → Uploading with `ProgressBar` → Success with a thumbnail/duration preview) → optional resource links (simple repeatable `TextField` list, "Add Resource") → Save/Delete/Cancel.

### Quiz Editor

- **Content order:** ordered list of questions (each: prompt `TextField`, 2–4 option `TextField`s, a radio-style "mark correct" control — reusing the Quiz Answer Option selection pattern conceptually, not the exact student-facing component) → "Add Question" → Save.
- **Reordering:** questions use the same `ReorderableList` pattern as sections/lessons — one consistent reordering interaction across the entire Instructor surface, not a bespoke one per editor.

### Publish / Unpublish

- **Approved UX Decision 2:** a `Toggle` — not a button pair — on Course Editor — Overview, **disabled** until the publish-readiness checklist passes (§ above). The `Toggle` directly represents the binary Draft⇄Published state and is paired with a visible text label naming the current state ("Draft" / "Published") at all times, per `../design-system/design-tokens.json → component.toggle.$note` and `../design-system/COMPONENTS.md`'s v1.3 locked rule — the switch's color and position are never the sole signal. The disabled state additionally communicates *why* via the visible checklist, not a bare disabled control with no explanation (`../design-system/ACCESSIBILITY.md`, `UX_STATES.md § 7`).
- Unpublishing a live course keeps existing enrolled students' access intact (only removes it from Explore/search) — `../product/USER_FLOWS.md § 26`.

---

## 2. Admin

### Overview (Admin Dashboard)

- **Layout:** Sidebar (Dashboard, Courses, Users, Instructors, Categories — `../product/INFORMATION_ARCHITECTURE.md § 4`) + content column.
- **Content order:** page title "Dashboard" → a `StatCard` grid (total courses, total students, total instructors, published vs. draft split — 4 cards, one row) → nothing else. This screen is a jumping-off point, not a dense analytics view.

### Courses

- **Layout:** page title + `SearchField` + `DataTable` (title, instructor, status `Badge`, enrollment count, row action "View"/"Unpublish").
- **Interaction:** "View" opens the course as a student would see it (read-only preview of Course Details, not the Instructor's editor); "Unpublish" is available directly from the row action without opening the course, for fast moderation.

### Users

- **Layout:** page title + `SearchField` + `DataTable` (name, email, join date, enrollment count) — **view-only** in MVP (`../product/USER_ROLES.md`), so no row actions beyond perhaps "View" (opens a read-only detail, if useful; not required).

### Instructors

- **Layout:** page title + `SearchField` + `DataTable` (name, email, course count, published count) — row action "View Courses" navigates to **Courses**, pre-filtered to that instructor (reuses the Courses screen with a filter applied, not a separate screen — consistent with `../product/SCREEN_INVENTORY.md`'s existing definition).

### Categories

- **Layout:** page title + "Add Category" primary action + a simple list (not a full `DataTable` — categories are few, flat, and don't need sorting/pagination; a plain list with inline edit/remove per row is proportionate) — each row: category name, course count, edit/remove actions.
- **Guardrail:** removing a category that still has courses assigned to it is blocked or requires confirmation with a clear explanation (not a silent failure) — a small, deliberate piece of data-integrity UX, not "enterprise" scope creep.

---

## 3. Consistent Interaction Patterns Across Instructor & Admin

To keep this surface coherent as *one* tool rather than five disconnected screens:

- **Every list is either a `DataTable` (Courses, Users, Instructors — sortable, filterable, potentially many rows) or a plain list (Categories — few, simple rows).** No third pattern is introduced.
- **Every reorderable collection (sections, lessons, quiz questions) uses `ReorderableList` identically** — drag handle + Move Up/Down, same visual weight, same touch targets, everywhere.
- **Every file attachment (thumbnail, lesson video) uses `FileUpload` identically** — same five states (Idle/Drag-over/Uploading/Success/Error), same copy pattern for errors ("Try again"), regardless of what's being uploaded.
- **Every destructive or state-changing action** (delete section, remove category with courses, unpublish) uses the same lightweight `AppDialog` confirmation pattern (`NAVIGATION_SPEC.md § 6`) — not a mix of inline confirms in some places and modals in others.

---

## 4. Select / Dropdown Usage *(resolved in v1.3 — was previously a flagged component gap)*

Course Editor's **category field** (a single required selection from a fixed, Admin-managed list), its new **Level** and **Content Language** fields, and Instructor/Admin's own **Language** setting all use the real **`Select`** component added in [Mentora Design System v1.3](../design-system/COMPONENTS.md § Select / Dropdown) — not `CategoryChip` (a multi-select filter/tag pattern, wrong fit for a single required value) and not a bare `TextField` (free text, wrong for a constrained list). No stopgap native `<select>` remains anywhere in this surface.

### Language Setting *(v1.3, locked MVP)*

Instructor and Admin each get the same functional Language `Select` (English/العربية) as the Student Settings screen, placed inside their own "Profile / Account" screen rather than a dedicated Settings screen of their own (`../product/INFORMATION_ARCHITECTURE.md § 4`, `../product/PRODUCT_SPEC.md § 16`) — no new screen was added for this. Behavior is identical to `SCREEN_UX_SPECS.md § 17`: selecting a language applies immediately, flips layout direction app-wide, and persists to the account (conceptual only — not implemented at this planning stage).
