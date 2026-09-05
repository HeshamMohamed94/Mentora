# Mentora — API Contract

REST, per [ADR-007](./adr/ADR-007-api-style.md). This file defines conventions and a conceptual endpoint inventory — no implementation code.

---

## 1. Versioning

All routes under `/api/v1/`. A breaking change ships as `/api/v2/...` alongside the still-running `/v1` until every client (especially mobile, which can't be force-upgraded) has migrated. Non-breaking additions (new optional field, new endpoint) never bump the version.

## 2. Transport & Auth

- **Web:** requests go through a same-origin Next.js rewrite/proxy to the backend, so the httpOnly auth cookies set by the backend are first-party to the browser (see [`WEB_ARCHITECTURE.md`](./WEB_ARCHITECTURE.md) and [ADR-006](./adr/ADR-006-authentication-strategy.md)). State-changing requests must include the CSRF header (§ [`AUTH_SECURITY.md`](./AUTH_SECURITY.md)).
- **Mobile:** `Authorization: Bearer <accessToken>` header, refreshed via `POST /api/v1/auth/refresh` using the securely-stored refresh token.
- Every authenticated route resolves a `Principal` (`userId`, `role`) from the verified token before the route handler runs (see [`BACKEND_ARCHITECTURE.md § 3`](./BACKEND_ARCHITECTURE.md)).

## 3. Response Envelope

**Success:**
```json
{ "data": { ... }, "meta": { "requestId": "..." } }
```
List endpoints:
```json
{ "data": [ ... ], "meta": { "requestId": "...", "nextCursor": "..." } }
```
(`nextCursor` omitted/`null` when there is no further page.)

**Error:**
```json
{ "error": { "code": "AUTH_INVALID_CREDENTIALS", "message": "Invalid email or password.", "fields": { "email": "REQUIRED" } }, "meta": { "requestId": "..." } }
```

- `code` is the stable, machine-readable identifier every client's i18n layer maps to a localized string — see [`LOCALIZATION_ARCHITECTURE.md § 6`](./LOCALIZATION_ARCHITECTURE.md) and the cross-cutting rule in [`ARCHITECTURE.md § 4.5`](./ARCHITECTURE.md).
- `message` is an English, developer-facing fallback — logged and shown only if a client somehow has no mapping for `code` (should never happen for a known code; exists as a safety net, not a UI string).
- `fields` (optional) — per-field validation errors, keyed by field name, for form-level display.
- A `5xx` error's `message` is always a generic "Something went wrong" equivalent — the real exception/stack trace is logged server-side with the `requestId`, never serialized to the client, per the brief's hard requirement.

## 4. Error Code Taxonomy

| HTTP status | Code family | Example codes |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `VALIDATION_ERROR` (with `fields`) |
| 401 | Unauthorized | `AUTH_INVALID_CREDENTIALS`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID` |
| 403 | Forbidden | `FORBIDDEN_ROLE`, `FORBIDDEN_NOT_OWNER`, `FORBIDDEN_NOT_ENROLLED` |
| 404 | Not Found | `COURSE_NOT_FOUND`, `LESSON_NOT_FOUND`, `CERTIFICATE_NOT_FOUND` |
| 409 | Conflict | `EMAIL_ALREADY_REGISTERED`, `ALREADY_ENROLLED`, `CATEGORY_IN_USE` |
| 429 | Rate Limited | `RATE_LIMITED_AUTH`, `RATE_LIMITED_AI_TUTOR` |
| 500 | Server Error | `INTERNAL_ERROR` |

Network/offline is a **client-local** state, never a server response — handled entirely per [`../ux/UX_STATES.md § 4`](../ux/UX_STATES.md) (detected via platform connectivity APIs, not an HTTP code).

## 5. Pagination, Filtering, Search, Sorting

- **Pagination:** cursor-based on every list endpoint — `?cursor=<opaque>&limit=20`, response includes `meta.nextCursor`. Chosen over offset/limit to avoid MongoDB's skip/limit performance cliff on large collections and to stay stable under concurrent inserts (per [ADR-007](./adr/ADR-007-api-style.md)).
- **Filtering:** query params scoped per resource — e.g. `GET /courses?category=<id>&level=beginner&status=published`.
- **Search:** `GET /courses?q=<keyword>` — backed by MongoDB's `$text` index on `courses.title`/`description` (§ [`DATABASE_MODEL.md § 4`](./DATABASE_MODEL.md)).
- **Sorting:** `?sort=field:asc|desc` where supported (Admin's `DataTable` columns).

## 6. Idempotency

The demo-checkout completion endpoint (`POST /courses/{id}/checkout/complete`) is idempotent by construction: the service layer checks for an existing `Enrollment` for (`userId`, `courseId`) inside the same transaction before creating one — a retried request (e.g. a flaky network causing a client-side resend) can never create a duplicate enrollment or a duplicate `DemoPurchase` record. No client-supplied idempotency key is required (§ [ADR-007](./adr/ADR-007-api-style.md)).

## 7. Conceptual Endpoint Inventory (grouped by domain)

No request/response bodies specified here — this is the surface map an implementer builds against, detailed alongside [`DATABASE_MODEL.md`](./DATABASE_MODEL.md)'s field lists at implementation time.

### Auth
```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/logout
POST   /api/v1/auth/refresh
```

### Users / Profile
```
GET    /api/v1/users/me
PATCH  /api/v1/users/me                    (name, avatar, preferredLocale)
```

### Courses & Categories
```
GET    /api/v1/courses                     (browse/search/filter — public + enrollment-aware when authenticated)
GET    /api/v1/courses/{id}
POST   /api/v1/courses                     (Instructor)
PATCH  /api/v1/courses/{id}                (Instructor, owner-only)
POST   /api/v1/courses/{id}/sections
PATCH  /api/v1/courses/{id}/sections/{sectionId}
DELETE /api/v1/courses/{id}/sections/{sectionId}
PATCH  /api/v1/courses/{id}/sections/reorder
POST   /api/v1/courses/{id}/sections/{sectionId}/lessons
PATCH  /api/v1/courses/{id}/sections/{sectionId}/lessons/{lessonId}
DELETE /api/v1/courses/{id}/sections/{sectionId}/lessons/{lessonId}
PATCH  /api/v1/courses/{id}/sections/{sectionId}/lessons/reorder
POST   /api/v1/courses/{id}/publish
POST   /api/v1/courses/{id}/unpublish      (Instructor owner, or Admin moderation)
GET    /api/v1/categories
POST   /api/v1/categories                  (Admin)
PATCH  /api/v1/categories/{id}             (Admin)
DELETE /api/v1/categories/{id}             (Admin, blocked if in use)
```

### Enrollment / Demo Checkout
```
GET    /api/v1/courses/{id}/checkout       (order summary preview)
POST   /api/v1/courses/{id}/checkout/complete   (idempotent — see § 6)
GET    /api/v1/enrollments                 (My Learning)
```

### Progress
```
GET    /api/v1/courses/{id}/progress
POST   /api/v1/courses/{id}/lessons/{lessonId}/complete
POST   /api/v1/courses/{id}/lessons/{lessonId}/position    (playback-position heartbeat)
```

### Quiz
```
GET    /api/v1/courses/{id}/quiz           (student-facing — isCorrect stripped, see DATABASE_MODEL.md § 7)
GET    /api/v1/courses/{id}/quiz/editor    (Instructor-facing — full content incl. isCorrect)
PUT    /api/v1/courses/{id}/quiz/editor    (Instructor — replace question set)
POST   /api/v1/courses/{id}/quiz/attempts  (submit — grades server-side, returns score/breakdown)
GET    /api/v1/courses/{id}/quiz/attempts/latest
```

### Learning Paths
```
GET    /api/v1/learning-paths
GET    /api/v1/learning-paths/{id}
POST   /api/v1/learning-paths/{id}/follow
DELETE /api/v1/learning-paths/{id}/follow
```

### Certificates
```
GET    /api/v1/certificates                (Certificates List)
GET    /api/v1/certificates/{id}
```

### AI Tutor
```
GET    /api/v1/ai-tutor/conversation                        (fetch/create the student's single conversation, cursor-paginated messages)
POST   /api/v1/ai-tutor/conversation/messages                (send a message; optional lessonContextId; streams the response)
```

### Instructor (aggregation views)
```
GET    /api/v1/instructor/dashboard        (owned courses + stats)
```

### Admin (aggregation + moderation views)
```
GET    /api/v1/admin/dashboard             (platform counts)
GET    /api/v1/admin/courses               (all courses, DataTable)
GET    /api/v1/admin/users                 (DataTable)
GET    /api/v1/admin/instructors           (DataTable)
```

### Media
```
POST   /api/v1/media/uploads               (issues a presigned upload URL — see MEDIA_ARCHITECTURE.md)
POST   /api/v1/media/uploads/{id}/complete
GET    /api/v1/media/{id}/playback-url     (issues a short-lived signed GET URL, enrollment-checked)
```

## 8. Localization Considerations

- No endpoint accepts or returns pre-translated UI copy — the API is locale-agnostic except for the two content fields that are genuinely locale-shaped: `courses.contentLanguage` (metadata, § [`DATABASE_MODEL.md § 4`](./DATABASE_MODEL.md)) and `users.preferredLocale` (the account's own UI-language sync value, § [`LOCALIZATION_ARCHITECTURE.md § 4`](./LOCALIZATION_ARCHITECTURE.md)).
- Numeric/date/price values are returned as **raw, unformatted values** (ISO 8601 timestamps, integer minor-unit prices, plain numbers) — formatting for the active UI locale happens entirely client-side (§ [`LOCALIZATION_ARCHITECTURE.md § 5`](./LOCALIZATION_ARCHITECTURE.md)), so the backend never needs to know or care which locale is rendering a response.
