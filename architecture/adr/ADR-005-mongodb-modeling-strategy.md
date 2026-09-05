# ADR-005: MongoDB Document Modeling Strategy

**Status:** Locked
**Date:** 2026-09-04

## Decision

Every MongoDB collection in Mentora is designed against one explicit rule, applied uniformly: **embed only bounded, co-authored, co-read data; reference everything that grows over the life of the product or is written by a different actor/at a different cadence than its parent.** The full per-collection application of this rule is in [`DATABASE_MODEL.md`](../DATABASE_MODEL.md); this ADR records the *rule itself* as a locked decision, since it's the single most consequential MongoDB-specific judgment call in the whole schema and the brief explicitly calls out avoiding "unbounded arrays inside frequently growing documents."

## The Rule, Precisely

| Embed when... | Reference when... |
|---|---|
| The child data has a hard, small, product-defined upper bound (a course's sections/lessons; a quiz's questions/options; a learning path's member-course list; an order's line item) | The child data can grow indefinitely over time (quiz attempts, AI messages, progress events, certificates issued) |
| The child is always read together with the parent (you never fetch a `Lesson` without its `Course` context) | The child is frequently read/queried independently of any single parent (a `QuizAttempt` is queried by `userId` across many quizzes for a student's history) |
| The child is written by the same actor, in the same edit session, as the parent (an Instructor edits a whole curriculum) | The child is written by a different actor/cadence than the parent (a `Progress` document is updated by the *student's own activity*, continuously, long after the `Course` was last edited by its Instructor) |
| Losing the child alongside the parent (e.g. deleting a course deletes its sections) is the correct lifecycle | The child must survive or be queryable independent of the parent's lifecycle (a `Certificate` must remain valid even if its course is later edited) |

## Concrete Application (see `DATABASE_MODEL.md` for full field-level detail)

- **Embedded:** `Course.sections[].lessons[]` (bounded by realistic curriculum size, always authored/read together); `Quiz.questions[].options[]` (bounded, authored together); `LearningPath.courseRefs[]` (a curated, admin-authored, small ordered list).
- **Referenced, own collection:** `Enrollment` (one per user×course, but the collection itself grows with every purchase — never embedded in `User`); `Progress` (updated far more frequently than any parent document, one per enrollment); `QuizAttempt` (unboundedly growing — every retry is a new document); `Certificate` (must outlive edits to its source course); `AiConversation` + `Message` (Message is its own collection — a long-running chat history must never be embedded in `AiConversation` or `User`, both of which are read on unrelated requests); `DemoPurchase` (an audit-style event record, append-only, never embedded).

## Options Considered

### Option A — The bounded-embed / unbounded-reference rule above (chosen)

### Option B — Embed aggressively everywhere (e.g. a student's quiz attempts embedded in their `User` document, or lesson progress embedded in `Enrollment` as a growing array with no bound)

**Why not:** this is precisely the anti-pattern the brief warned against — a `User` document that accumulates every quiz attempt or AI message over years of activity eventually approaches MongoDB's 16MB document size limit, and, well before that, every read of the `User` document (which happens on nearly every authenticated request, for authorization) gets slower as an unrelated, unboundedly-growing field bloats it. This is a correctness *and* performance defect, not just a style preference.

### Option C — Reference everything, embed nothing (fully normalized, JOIN-everything via `$lookup`)

**Why not:** would require a `$lookup` aggregation (MongoDB's JOIN-equivalent, notably more expensive than a relational JOIN) just to render a course's curriculum outline — the single most frequently-read piece of content in the product — for data that's always authored and read as one unit and has a small, known bound. This throws away MongoDB's actual strength for the one place it's clearly the right tool, for the sake of dogmatic consistency.

## Consequences

- Reviewers/implementers reading [`DATABASE_MODEL.md`](../DATABASE_MODEL.md) never have to guess why a given field is embedded vs. referenced — every collection entry states which side of this rule it falls on and why.
- Progress calculation reads one bounded `Progress` document per enrollment (not a growing event log) — see [`DATABASE_MODEL.md § Progress`](../DATABASE_MODEL.md) for how per-lesson completion is stored as a bounded map keyed by lesson ID (bounded by the course's own lesson count) rather than an ever-growing event array.
- Any future collection added to Mentora is designed by applying this same table, not by ad hoc judgment per feature.

## Migration Path

If a specific embedded array ever threatens to grow unbounded in practice (e.g. a course somehow needs hundreds of sections — unlikely given the product's curated-course positioning, but not impossible), that specific field is the one to extract into its own referenced collection at that time — the rule already tells you which direction that extraction goes.
