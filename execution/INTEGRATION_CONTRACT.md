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

`VALIDATION_ERROR` (400) · `AUTH_INVALID_CREDENTIALS` / `AUTH_TOKEN_EXPIRED` / `AUTH_TOKEN_INVALID` (401) · `FORBIDDEN_ROLE` / `FORBIDDEN_NOT_OWNER` / `FORBIDDEN_NOT_ENROLLED` (403) · `COURSE_NOT_FOUND` / `LESSON_NOT_FOUND` / `CERTIFICATE_NOT_FOUND` (404) · `EMAIL_ALREADY_REGISTERED` / `ALREADY_ENROLLED` / `CATEGORY_IN_USE` (409) · `RATE_LIMITED_AUTH` / `RATE_LIMITED_AI_TUTOR` (429) · `INTERNAL_ERROR` (500).

## 5. Roles

`student` | `instructor` | `admin` — exactly one per account, JWT claim. Guest = unauthenticated. Instructor/Admin are Web-only surfaces (no mobile UI ever calls their write endpoints).

## 6. Endpoint Inventory

See `architecture/API_CONTRACT.md § 7` for the full conceptual list (auth, users, courses/categories, enrollment, progress, quiz, learning-paths, certificates, ai-tutor, instructor, admin, media). This file will append the **as-built** request/response DTO shapes per module here as Phase 1 implements each one — placeholder until then.

### As-Built Module Contracts
*(Appended during Phase 1 implementation — empty until a module is complete and reviewed.)*

## 7. Media / Playback

- Thumbnails/avatars: `GET /api/v1/media/{mediaId}/file` — public read, no auth.
- Lesson videos: never publicly addressable. Client must call `GET /api/v1/media/{mediaId}/playback-url` (enrollment-checked) and use the returned short-lived signed reference directly in the platform's native video element. Re-request if the player session outlives the token.
- Upload: `POST /api/v1/media/uploads` (multipart: file + `{ kind, ownerRefId, contentType }`) → `{ mediaId, storageKey }`, then a separate ordinary PATCH attaches `mediaId` to the owning course/lesson/profile field.

## 8. AI Tutor (Phase 1 boundary; Phase 6 completes it)

- `GET /api/v1/ai-tutor/conversation` — fetch/create the student's single conversation, cursor-paginated messages.
- `POST /api/v1/ai-tutor/conversation/messages` — `{ content, lessonContextId? }`, streamed response. **Phase 1: streams a stub/placeholder response, not a real LLM completion** (see DECISIONS_LOG D4). Contract (request/response shape, streaming mechanism, enrollment gate, rate limit) does not change in Phase 6 — only the `AiProvider` binding does.
- No AI provider key, SDK, or network call ever exists in any client codebase — enforced structurally, not just by convention.

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
