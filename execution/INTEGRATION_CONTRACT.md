# Mentora — Integration Contract

**Purpose:** the condensed, implementation-level contract every later phase (Web, KMP shared, Android, iOS) codes against, so client work never has to re-derive backend behavior from scratch. This is a summary of `architecture/API_CONTRACT.md`, `AUTH_SECURITY.md`, `DATABASE_MODEL.md`, and `MEDIA_ARCHITECTURE.md` — those files remain the source of truth; this file is updated with the **actual, as-built** shape whenever Phase 1 implementation deviates in a non-breaking way (e.g. a concrete field name), and is the first thing later phases must read before writing a single API call.

**Status: to be finalized at Phase 1 COMPLETE.** Until then, treat § 1–6 below as the locked design intent; implementation specifics (exact DTO field names, exact route param names) will be appended per module as each is implemented.

---

## 1. Base URL & Versioning

- All routes: `/api/v1/...`
- Local backend: `http://localhost:8080`
- Health check: `GET /healthz` → `200 { status: "ok", mongo: "ok" }` or `503` naming the failing dependency. No auth required.

## 2. Auth Transport

- **Web:** httpOnly, Secure, `SameSite=Lax` cookies (access + refresh), set by the backend via a same-origin Next.js proxy. State-changing requests (`POST`/`PATCH`/`PUT`/`DELETE`) must include header `X-Requested-With: mentora-web` (CSRF defense) — Web clients must always send this header on writes once Phase 2 begins.
- **Mobile:** `Authorization: Bearer <accessToken>` header. Refresh via `POST /api/v1/auth/refresh` using the securely-stored (Keystore/Keychain) refresh token.
- Access token: JWT, 15 min expiry, claims `userId`, `role`.
- Refresh token: opaque, 30-day sliding expiry, rotated every use, reuse triggers full-family revocation.
- Client-side pattern (all clients): on `401 AUTH_TOKEN_EXPIRED`, call `/auth/refresh` once, retry original request; if refresh also fails, force logout.

## 3. Response Envelope (exact shape, do not deviate)

Success (single resource):
```json
{ "data": { ... }, "meta": { "requestId": "..." } }
```
Success (list):
```json
{ "data": [ ... ], "meta": { "requestId": "...", "nextCursor": "..." } }
```
Error:
```json
{ "error": { "code": "...", "message": "...", "fields": { } }, "meta": { "requestId": "..." } }
```
- `code` — stable, machine-readable; every client maps this to a localized string. **Never render `message` in production UI** — it's an English developer fallback only.
- List pagination is cursor-based: `?cursor=<opaque>&limit=20`. Never assume offset/limit.

## 4. Error Code Taxonomy (client i18n keys must cover all of these)

`VALIDATION_ERROR` (400) · `AUTH_INVALID_CREDENTIALS` / `AUTH_TOKEN_EXPIRED` / `AUTH_TOKEN_INVALID` (401) · `FORBIDDEN_ROLE` / `FORBIDDEN_NOT_OWNER` / `FORBIDDEN_NOT_ENROLLED` / `FORBIDDEN_CSRF` (403) · `COURSE_NOT_FOUND` / `LESSON_NOT_FOUND` / `CERTIFICATE_NOT_FOUND` (404) · `EMAIL_ALREADY_REGISTERED` / `ALREADY_ENROLLED` / `CATEGORY_IN_USE` (409) · `RATE_LIMITED_AUTH` / `RATE_LIMITED_AI_TUTOR` (429) · `INTERNAL_ERROR` (500).

**As-built additions:** `FORBIDDEN_CSRF` (403) — missing/invalid `X-Requested-With: mentora-web` header on a cookie-authenticated state-changing request (`AUTH_SECURITY.md § 10`). `MEDIA_NOT_FOUND` (404) — a media id that doesn't exist, or a `lessonVideo`-kind id requested through the public `/file` route. Neither is in `API_CONTRACT.md`'s original example list; both are added as same-family codes since that list is illustrative, not exhaustive. Web clients (Phase 2) must send the CSRF header on every `POST`/`PATCH`/`PUT`/`DELETE`.

## 5. Roles

`student` | `instructor` | `admin` — exactly one per account, JWT claim. Guest = unauthenticated. Instructor/Admin are Web-only surfaces (no mobile UI ever calls their write endpoints).

## 6. Endpoint Inventory

See `architecture/API_CONTRACT.md § 7` for the full conceptual list (auth, users, courses/categories, enrollment, progress, quiz, learning-paths, certificates, ai-tutor, instructor, admin, media). This file will append the **as-built** request/response DTO shapes per module here as Phase 1 implements each one — placeholder until then.

### As-Built Module Contracts

**Auth** (`/api/v1/auth/*`) — see § 8a below.

**Users** — `GET /users/me` → `{ id, email, name, role, avatarMediaId, preferredLocale, createdAt }` (never `passwordHash`). `PATCH /users/me` body `{ name?, preferredLocale? }` only — no `avatarMediaId` yet (media module doesn't exist; see D10).

**Categories** — `GET /categories` → plain array (no pagination), `{ id, name, slug, courseCount }`. `POST`/`PATCH` Admin-only, `name` only; **slug is server-generated at creation and never changes** on rename. `DELETE` blocked with `409 CATEGORY_IN_USE` while `courseCount > 0`.

**Courses** — `GET /courses` (public list): query params `category`, `level`, `maxPrice`, `q`, `cursor`, `limit` — **`status` is not an accepted filter; the endpoint always forces `published`** (see D9). Returns `CourseSummary` (no embedded curriculum). `GET /courses/{id}`: full `CourseResponse` with `sections[].lessons[]`; a `draft` course 404s (`COURSE_NOT_FOUND`) for anyone but the owning Instructor or an Admin — **404, never 403, to avoid revealing existence**. Write endpoints (`POST /courses`, `PATCH /courses/{id}`, all `sections`/`lessons` CRUD + reorder, `POST .../publish`) are Instructor-owner-only (`403 FORBIDDEN_NOT_OWNER` otherwise); `POST .../unpublish` allows owner-Instructor **or** Admin. `thumbnailMediaId`/`videoMediaId` are ObjectId-shaped strings, format-checked only (see D10) — no `media` collection exists yet to verify against. Publish validation fields: `title`, `description`, `categoryId`, `priceDisplay` → `"REQUIRED"`; `thumbnailMediaId` → `"thumbnail": "REQUIRED"`; no sections → `"curriculum": "NO_SECTIONS"`; an empty section → `"section.<id>.lessons": "NO_LESSONS"`; a lesson missing video → `"lesson.<id>.videoMediaId": "REQUIRED"`. No `isEnrolled` field anywhere yet (see D11 — next task's concern).

**Enrollment** — Student role required on all three routes. `GET /courses/{id}/checkout` → `{ course: { id, title, thumbnailMediaId }, instructorName, priceDisplay }`, 404s the same way for a non-existent or unpublished course. `POST /courses/{id}/checkout/complete` → `{ enrollment: { id, courseId, source, enrolledAt, status }, alreadyEnrolled }`, `201` on first completion, `200` on every idempotent repeat (never an error) — backed by a real MongoDB transaction plus a unique-index duplicate-key fallback (see D14 for a known non-blocking limitation under true concurrent race conditions). `GET /enrollments` → cursor-paginated, student-scoped, minimal shape (no embedded course details — client composes with `GET /courses/{id}` itself).

**Progress** — Student role + enrollment required (`403 FORBIDDEN_NOT_ENROLLED` otherwise) on all three routes. `GET /courses/{id}/progress` → `{ courseId, completedLessonIds: [String], currentLessonId, currentPositionSeconds, quizPassed, completionPercent, courseCompletedAt }`, created lazily (find-or-create) on first call. `POST .../lessons/{lessonId}/complete` — idempotent, integer `completionPercent = completedCount * 100 / totalLessonCount`. `POST .../lessons/{lessonId}/position` — heartbeat, rejects negative `positionSeconds`. **`quizPassed` and `courseCompletedAt` are always `null` in this phase's progress responses** — course-completion detection and certificate issuance are implemented in the Quiz+Certificates task (D15), not here. `enrollment/service/EnrollmentService.kt` gained one small addition for this: `requireEnrollment(userId, courseId)`.

**Quiz** — `GET /courses/{id}/quiz` (Student, enrolled) → `{ courseId, questions: [{ questionId, prompt, order, options: [{ optionId, text }] }] }` — **`isCorrect` is structurally absent** (a distinct DTO type, not an omitted field). `GET /courses/{id}/quiz/editor` (owner-Instructor) → same shape plus `isCorrect` per option; returns an empty `questions: []` if no quiz authored yet (not a 404). `PUT .../quiz/editor` replaces the whole question set; requires exactly one `isCorrect: true` option per question. `POST .../quiz/attempts` (Student, enrolled) grades server-side (`score = correctCount * 100 / totalQuestions`, `passed = score >= 70`, fixed threshold), returns `{ score, passed, breakdown: [{ questionId, selectedOptionId, correctOptionId, isCorrect }] }` (correctness reveal is intentional here — this is the post-submission Results screen), and sets `progress.quizPassed` to *this attempt's* result (latest attempt wins, not sticky-true). `GET .../quiz/attempts/latest` → same shape or `404 ATTEMPT_NOT_FOUND`. **Still no course-completion/certificate wiring** — next task (D16).

**Certificates** — Student role required. `GET /certificates` → cursor-paginated `{ id, courseTitleSnapshot, instructorNameSnapshot, issuedAt }`. `GET /certificates/{id}` → `{ id, studentNameSnapshot, courseTitleSnapshot, instructorNameSnapshot, completionDateSnapshot, issuedAt }`, `404 CERTIFICATE_NOT_FOUND` for a nonexistent id or another student's certificate. **`id` is a public, reversible, human-presentable code** (`MTR-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX`, uppercase hex groups of the underlying ObjectId) — never a raw Mongo id. Snapshots are frozen at issuance (a later course-title edit never changes an already-issued certificate). Issuance is triggered automatically — from `POST .../lessons/{lessonId}/complete`, `POST .../quiz/attempts`, and (as a self-healing check) `GET .../progress` — whichever crosses the eligibility threshold (`all lessons complete && (no quiz || quizPassed)`) first; there is no direct "issue certificate" endpoint. `progress`/`quiz` never depend on `certificates` (route-layer-only wiring) — see D17 for why.

**Media** — see § 7 below for the full as-built shape (upload, public file, gated playback/stream). Route auth: `POST /media/uploads` and `GET /media/{id}/playback-url` require `jwt-auth`; `GET /media/{id}/file` and `GET /media/{id}/stream` are public (no auth possible — a native video element can't attach headers/cookies, and thumbnails are public-read by design).

**Instructor** — `GET /instructor/dashboard` (Instructor role required) → `{ stats: { totalCourses, publishedCount, totalEnrollments }, courses: [{ id, title, status, enrollmentCount, completionRate }] }`, scoped to `principal.userId`-owned courses only (another Instructor's courses never appear), sorted by `_id` ascending. `completionRate` (0-100 integer) is the average of `progress.completionPercent` across that course's progress documents, `0` when there are none — this specific definition isn't spelled out in any locked doc (`product/USER_ROLES.md` just says "completion rate"), so it's recorded here as the as-built contract. This module owns no collection of its own (`architecture/BACKEND_ARCHITECTURE.md § 3`) — it reads `courses`/`enrollments`/`progress` directly rather than going through another module's service, the one documented exception to this codebase's usual cross-module-reuse rule.

**Admin** — all four routes require `Role.admin`. `GET /admin/dashboard` → `{ totalCourses, publishedCourses, draftCourses, totalStudents, totalInstructors }` (plain counts, no pagination). `GET /admin/courses` (optional `q` keyword, reuses the `courses` collection's existing text index) → cursor-paginated `{ id, title, instructorName, status, enrollmentCount }` across **every** status and every Instructor (unlike the public `/courses` list, which is published-only and unauthenticated-safe). `GET /admin/users` (optional `q`, matches `name` OR `email`) → cursor-paginated `{ id, name, email, createdAt, enrollmentCount }`, scoped to `role == student` only. `GET /admin/instructors` (optional `q`, same match rule) → cursor-paginated `{ id, name, email, courseCount, publishedCount }`, scoped to `role == instructor` only. **`users` and `instructors` are disjoint lists — there is no "all accounts" endpoint.** The `q` filter on `users`/`instructors` is a case-insensitive substring match with the query escaped (`Pattern.quote`) before being used as a Mongo regex — any future free-text admin filter should do the same. This module owns no collection of its own (`architecture/BACKEND_ARCHITECTURE.md § 3`) — like `instructor`, it reads `courses`/`users`/`enrollments` directly. Category management and course-unpublish moderation are **not** part of this module — they're served by the pre-existing `categories` module and `POST /courses/{id}/unpublish` (already Admin-capable) respectively.

**AI Tutor** — see § 8 below for the full as-built shape.

## 7. Media / Playback (as-built)

- **Upload:** `POST /api/v1/media/uploads` (Student/Instructor/Admin, `jwt-auth`) — multipart body: a file part plus form fields `kind` (`courseThumbnail`|`lessonVideo`|`avatar`), `ownerRefId`, `contentType` (client-declared, validated server-side against an allowlist per `kind`), `courseId` (**required when `kind=lessonVideo`**, ignored otherwise — needed to resolve upload-time ownership and later playback-time enrollment, since `ownerRefId` for a `lessonVideo` is the lessonId, not a courseId), `durationSeconds` (optional, `lessonVideo` only, client-supplied from local video metadata — no server-side probing). **The client must append the form fields before the file part** — the server resolves metadata from fields seen so far at the moment the file part is streamed, so field-after-file ordering silently yields "missing field" validation errors (see `DECISIONS_LOG.md` D26). Returns `201 { mediaId, storageKey }`. Server-side limits: images (`courseThumbnail`/`avatar`) ≤ 5 MB (`image/jpeg`|`image/png`|`image/webp`); videos (`lessonVideo`) ≤ 500 MB (`video/mp4`|`video/webm`) — enforced while streaming to disk, not after buffering the whole file; an oversized/wrong-type upload leaves no partial file and no `media` document. Ownership: `avatar` requires `ownerRefId == principal.userId`; `courseThumbnail`/`lessonVideo` require the `instructor` role and course ownership (`FORBIDDEN_NOT_OWNER`/`COURSE_NOT_FOUND`); `lessonVideo` additionally requires `ownerRefId` to be a real lesson id on that `courseId` (`404 LESSON_NOT_FOUND` otherwise). A separate, ordinary `PATCH` (already covered by the `courses`/`users` contracts) attaches the resulting `mediaId` to the owning course/lesson/profile field — never implicit in the upload call.
- **Thumbnails/avatars:** `GET /api/v1/media/{mediaId}/file` — public read, no auth, streams the file directly (raw bytes, not the JSON envelope). Returns `404 MEDIA_NOT_FOUND` for a missing id or for a `lessonVideo`-kind id — lesson videos are never reachable through this route, by design.
- **Lesson videos:** never publicly addressable. Client calls `GET /api/v1/media/{mediaId}/playback-url` (`jwt-auth`; owning-Instructor or Admin previews freely, otherwise `enrollment.requireEnrollment` gates it — `403 FORBIDDEN_NOT_ENROLLED`) → `{ url, expiresAt }`, where `url` is `/api/v1/media/{mediaId}/stream?token=<signed>` — a purpose-scoped, 5-minute-lived JWT (distinct from the session access token; verified by hand, not through the `jwt-auth` Authentication provider). Pass `url` directly as the native `<video>`/ExoPlayer/AVPlayer source; `GET .../stream` is itself unauthenticated (a player element can't attach custom headers) and instead validates `?token=` per-request, streaming with HTTP range-request support (Ktor's `PartialContent` plugin) for scrubbing/seeking. Re-request `playback-url` if the player session outlives the token's `expiresAt`.
- `media.ownerRefId` is a `String` on the wire and server-side (not always an `ObjectId`) — a courseId/userId hex string for `courseThumbnail`/`avatar`, but a UUID string for `lessonVideo` (matching how `courses` actually generates lesson ids — see `DECISIONS_LOG.md` D25). Clients should treat it as an opaque string in all cases, never assume ObjectId-hex shape.

## 8. AI Tutor (Phase 1 boundary; Phase 6 completes it) — as-built

- `GET /api/v1/ai-tutor/conversation` (`Role.student`) — lazily creates the student's single conversation on
  first call. Cursor-paginated (`cursor`/`limit`, oldest-first), but the shape doesn't fit the plain list-page
  pattern (it also needs the conversation id), so it responds `{ conversationId, messages: [...], nextCursor }`
  inside `data` rather than a bare array — each message `{ id, role, content, lessonContextId, createdAt }`.
- `POST /api/v1/ai-tutor/conversation/messages` (`Role.student`, CSRF header required) — `{ content, courseId?, lessonContextId? }`.
  **`courseId` and `lessonContextId` are a pair — send both or neither** (`400 VALIDATION_ERROR` otherwise):
  there is no reverse lookup from a lesson id back to its owning course anywhere in this backend, so unlike
  the architecture doc's prose (which only mentions `lessonContextId`), the client must supply the course id
  it already knows it's viewing — the same pairing already required by the `media` module's `lessonVideo`
  upload (§ 7 above, D21). An unenrolled student sending lesson context gets `403 FORBIDDEN_NOT_ENROLLED`; a
  `lessonContextId` that doesn't belong to the given `courseId` gets `404 LESSON_NOT_FOUND`. `content` is
  capped at 4000 characters (`400` if blank or over). **Response is a streamed `text/plain` body** (not the
  JSON envelope, not SSE-framed) — plain incremental chunks as the (Phase 1: stub) provider produces them.
  **Phase 1 streams a fixed placeholder response, not a real LLM completion** (D4/D30) — both the user's and
  the assistant's messages are persisted regardless (the assistant one only after the stream completes
  successfully; a mid-stream failure leaves no partial assistant message). Contract (request/response shape,
  streaming mechanism, enrollment gate, rate limit) does not change in Phase 6 — only the `AiProvider` Koin
  binding does, and Phase 6 is expected to add the "global mode" enrolled-course-list system-prompt content
  that Phase 1 deliberately omits (D30).
- Rate-limited per-user on two independent, configurable caps (`AI_TUTOR_MESSAGES_PER_MINUTE`/`AI_TUTOR_MESSAGES_PER_DAY`
  env vars, defaulting to 20/minute and 200/day) — either one alone returns `429` when exceeded.
- No AI provider key, SDK, or network call ever exists in any client codebase — enforced structurally, not just by convention.

**Phase 1 backend module set is now complete: auth, users, courses, categories, enrollment, progress, quiz, certificates, media, instructor, admin, aitutor.** Remaining Phase 1 work is the backend test/seed/documentation pass.

## 8a. Auth Response Shape (as-built, Phase 1)

`POST /auth/register`, `POST /auth/login`, `POST /auth/refresh` all: set `mentora_access_token` and `mentora_refresh_token` as httpOnly/Secure/SameSite=Lax cookies **and** return `{ accessToken, refreshToken, user }` (register/login) or `{ accessToken, refreshToken }` (refresh) in the JSON body — both mechanisms fire on every response, per `AUTH_SECURITY.md § 4`'s literal per-client delivery description. **Security note for Phase 2 (Web):** the response body's token fields exist for mobile/API-uniformity; the Web app must never persist or re-expose them to client-side JS (no `localStorage`, no passing them to a client component) — the httpOnly cookies are Web's actual auth mechanism. If Phase 2 adds a BFF-style route handler in front of these endpoints (rather than a transparent rewrite), it should drop the body's token fields before they reach the browser, forwarding only `Set-Cookie` + `user`.

`POST /auth/logout` — revokes the presented refresh token (+ family), clears both cookies, returns `{ data: {} }`.

**As-built note (Phase 2, D36):** the `user` object embedded in `login`/`register`'s response body is `{ id, email, name, role, preferredLocale? }` — **not** the same shape as `GET /users/me`'s response (`{ id, email, name, role, avatarMediaId?, preferredLocale?, createdAt }`). No `avatarMediaId`, no `createdAt`. Both DTOs happen to share the name `AuthUser` in the Kotlin source despite living in different modules (`auth` vs. `users`) — don't assume one shape covers both call sites. Both omit null-valued optional fields entirely rather than sending `null` (`explicitNulls = false`, D20).

## 9. Cross-Cutting Rules Every Client Must Respect

1. The backend is authoritative — no client "declares" progress/enrollment/role state; always re-fetch or trust only server responses.
2. Never store or log a raw JWT/refresh token beyond required platform secure storage.
3. UI language and course-content language are independent — never conflate `Accept-Language`/UI locale with `course.contentLanguage`.
4. Numeric/date/price values from the API are raw and unformatted — client formats them per active locale.
5. Every layout uses logical (start/end) properties — never physical left/right.
6. No code path anywhere may resemble a real payment gateway call, card field, or financial credential — the demo-checkout flow is `DemoPurchase`/`Enrollment` only.

## 10. What Later Phases Must NOT Redo

- Do not re-derive the auth/session/token-refresh strategy — implement clients against § 2 above.
- Do not invent new error codes without updating this file and `API_CONTRACT.md`.
- Do not build a second media upload/playback mechanism — use § 7.
- Do not wire a direct AI provider SDK into any client — always go through the backend's `/ai-tutor/*` routes.
