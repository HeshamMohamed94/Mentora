# Mentora — Authentication, Authorization & Security

Combines authentication flow detail, RBAC, and the full MVP security requirement set. Token strategy decision: [ADR-006](./adr/ADR-006-authentication-strategy.md).

---

## 1. Registration

`POST /api/v1/auth/register` — email, password, name. Server validates: email format + uniqueness (`409 EMAIL_ALREADY_REGISTERED` if taken), password strength (minimum length + basic complexity, mirrored client-side for immediate feedback but authoritative server-side). Password hashed with **BCrypt**, cost factor **12**, before storage — plaintext password never persisted, never logged. Role is always set to `student` at registration — there is no self-service Instructor/Admin signup (those accounts are provisioned separately, out of MVP's user-facing scope; see § 6). On success: access + refresh tokens issued immediately (auto-login, per [`../product/USER_FLOWS.md § 1`](../product/USER_FLOWS.md)).

## 2. Login

`POST /api/v1/auth/login` — email + password. On failure: a single generic `401 AUTH_INVALID_CREDENTIALS` regardless of whether the email exists or the password was wrong (never reveal which, per [`../product/USER_FLOWS.md § 2`](../product/USER_FLOWS.md): "no field-specific hint (security)"). Rate-limited per IP and per email (§ 7) to slow credential-stuffing/brute-force attempts. On success: access + refresh tokens issued; `users.preferredLocale`, if set, is returned so the client can immediately apply the account's language preference (see [`LOCALIZATION_ARCHITECTURE.md § 4`](./LOCALIZATION_ARCHITECTURE.md)).

## 3. Logout

`POST /api/v1/auth/logout` — revokes the presented refresh token (marks it `revokedAt`, and by extension its whole rotation family — see § 4) and clears the auth cookies (Web) / instructs the client to clear secure storage (mobile). Access tokens are not individually revocable (they're short-lived by design, per § 4) — logout's real effect is preventing any *future* refresh, and the current access token simply expires within 15 minutes if somehow still held.

## 4. Token Strategy

- **Access token:** JWT, 15-minute expiry, HS256, carries `userId` and `role` as claims. Verified in-process on every request — no database round-trip.
- **Refresh token:** opaque random value, 30-day expiry, stored **hashed** in `refreshTokens` (§ [`DATABASE_MODEL.md § 2`](./DATABASE_MODEL.md)), grouped into a `familyId`. **Rotation:** every successful `/auth/refresh` call issues a new refresh token, marks the presented one `revokedAt`, and keeps the same `familyId`. **Reuse detection:** if a refresh token that's already been marked `revokedAt` is presented again (a strong signal of theft — the legitimate client already rotated past it), the **entire token family** is revoked, forcing re-authentication on every device using that family. This is the standard refresh-token-rotation breach-detection pattern and is the concrete mechanism behind "token rotation/revocation," not just a stated intent.
- **Web delivery:** both tokens as **httpOnly, Secure, SameSite=Lax** cookies, set by the backend, via a same-origin path (Next.js rewrites `/api/*` to the backend — see [`WEB_ARCHITECTURE.md`](./WEB_ARCHITECTURE.md)) so no cross-site cookie configuration or `SameSite=None` is ever needed.
- **Mobile delivery:** both tokens returned in the JSON response body once, then stored by the client in platform secure storage (Android: `EncryptedSharedPreferences`/Keystore-backed DataStore; iOS: Keychain) via the `shared` KMP module's `expect`/`actual` boundary — never in plain `SharedPreferences`/`UserDefaults`, and never logged.

## 5. Session Lifecycle

Access token silently refreshed in the background (client-side interceptor: on a `401 AUTH_TOKEN_EXPIRED`, call `/auth/refresh` once, retry the original request; if refresh also fails, force logout) — a user is never abruptly logged out mid-session just because 15 minutes passed. Refresh token's 30-day window resets on each successful use (sliding expiry) — an active user stays logged in indefinitely; an inactive one is logged out after 30 days of no app opens.

## 6. Authorization / RBAC

Role (`student`|`instructor`|`admin`) is a JWT claim, checked at the **route level** for coarse gating (e.g. `/instructor/*` routes reject any non-`instructor` principal outright) and **re-verified with ownership at the service level** for anything resource-specific:

| Check | Enforced where | Example |
|---|---|---|
| Role gate | Ktor route-level `authorize` guard | Only `instructor` role may hit `POST /courses` |
| Ownership | Service layer, re-checked against the database on every request — **never inferred from the JWT alone** | An Instructor may only `PATCH /courses/{id}` if `course.instructorId == principal.userId` |
| Enrollment gate | Service layer | A Student may only read a lesson's video URL or take a quiz if an `Enrollment` exists for that (`userId`, `courseId`) |
| Admin override | Service layer, explicit | Admin may `unpublish` any course regardless of ownership, but may never `PATCH` its content (§ [`../product/USER_ROLES.md`](../product/USER_ROLES.md): "can unpublish, not rewrite") |

**The backend is authoritative — full stop.** No endpoint trusts a client-supplied role, ownership claim, or completion state. A hidden button on a Student's UI is never the only thing preventing them from calling an Instructor endpoint; the same role/ownership check runs regardless of which client (Web, Android, iOS, or a raw HTTP client) makes the request.

**Instructor/Admin account provisioning:** since there's no self-service signup for these roles (§ 1), MVP provisions them via a seed script / direct database insert during setup (documented in [`DEPLOYMENT.md`](./DEPLOYMENT.md)) — appropriate for a small, known set of demo accounts; a real admin-invite flow is a Post-MVP concern if the platform ever needs self-service instructor onboarding.

## 7. Rate Limiting

Ktor's `RateLimit` plugin, scoped per route group:

| Route group | Limit (illustrative — tunable) | Purpose |
|---|---|---|
| `/auth/login`, `/auth/register` | Strict, per-IP and per-email | Brute-force/credential-stuffing resistance |
| `/ai-tutor/*` | Per-user, e.g. N messages/minute and a daily cap | Cost control (§ [`AI_TUTOR_ARCHITECTURE.md § 6`](./AI_TUTOR_ARCHITECTURE.md)) |
| Everything else | Lenient, per-user | General abuse resistance without hindering normal use |

A `429` response uses code `RATE_LIMITED_AUTH` / `RATE_LIMITED_AI_TUTOR` per [`API_CONTRACT.md § 4`](./API_CONTRACT.md).

## 8. Input Validation & Injection Safety

- Ktor's `RequestValidation` plugin rejects malformed request bodies (wrong types, missing required fields) before they reach service-layer logic.
- **MongoDB query safety:** all queries use the official Kotlin driver's typed builders (`Filters.eq(...)`, `Updates.set(...)`) — never string-concatenated or dynamically-constructed query documents built from raw user input. This eliminates NoSQL injection by construction, not by sanitization discipline.
- File uploads: content-type allowlist (`video/mp4`, `video/webm`, `image/jpeg`, `image/png`, `image/webp`) and max-size limits enforced **server-side**, before any local-filesystem write (§ [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md)) — a client-side check alone is not authoritative and is trivially bypassable.

## 9. Secrets Management

**CURRENT MVP (local only, per [ADR-012](./adr/ADR-012-local-demo-scope.md)):** a `.env` file, gitignored, loaded locally (directly by the config loader, or via docker-compose if that convenience path is used) — this is the active mechanism for the JWT signing secret and any AI provider dev key. No secret (JWT signing key, MongoDB URI, AI provider key) is ever committed to the repository, baked into a client build, or logged at any level — see [`BACKEND_ARCHITECTURE.md § 6`](./BACKEND_ARCHITECTURE.md)'s config loading and the logging redaction rule in § 10 below.

**OPTIONAL FUTURE EVOLUTION — NOT PART OF CURRENT MVP IMPLEMENTATION:** if a hosted staging/production environment is ever pursued ([`DEPLOYMENT.md § 6`](./DEPLOYMENT.md)), secrets would move to a platform-native secret store (Railway/Fly.io environment variables) and, at further scale, a dedicated secrets manager (AWS Secrets Manager / HashiCorp Vault) with a rotation policy.

## 10. Transport, CORS, CSRF

- **Transport:** plain HTTP over `localhost`/LAN is the active MVP setup — there is no public network path to protect with TLS at local-demo scope. HTTPS (terminated at a hosting platform's edge/load balancer) is noted only as part of the optional future production-evolution path ([`DEPLOYMENT.md § 6`](./DEPLOYMENT.md)).
- **CORS:** explicit origin allowlist — `http://localhost:3000` plus, if the Web app is reached from a device/emulator on the same LAN, that origin's LAN address (§ [`DEPLOYMENT.md § 4`](./DEPLOYMENT.md)) — never a wildcard `*`, which is additionally disallowed by browsers whenever `Access-Control-Allow-Credentials` is set (required here, since auth relies on cookies for Web).
- **CSRF:** `SameSite=Lax` cookies + a required custom header (e.g. `X-Requested-With: mentora-web`) on every state-changing (`POST`/`PATCH`/`PUT`/`DELETE`) request — a cross-site attacker's form-POST or `<img>`-tag-based CSRF attempt cannot set custom headers, so a request missing it is rejected regardless of a valid cookie being attached by the browser automatically. Mobile's Bearer-header auth is not cookie-based and is not CSRF-exposed.

## 11. AI Endpoint Protection

Every `/ai-tutor/*` route requires authentication, enforces the per-user rate limit (§ 7), verifies lesson-context enrollment before injecting any lesson content (§ [`AI_TUTOR_ARCHITECTURE.md § 4`](./AI_TUTOR_ARCHITECTURE.md)), and caps request/response size — full detail in [`AI_TUTOR_ARCHITECTURE.md`](./AI_TUTOR_ARCHITECTURE.md).

## 12. Logging Redaction

Structured logs never include: passwords (hashed or plain), full JWT/refresh token values (only a truncated/hashed reference for correlation, if needed at all), full AI conversation content at INFO level, or any field not on an explicit per-log-statement allowlist. The `requestId` (§ [`BACKEND_ARCHITECTURE.md § 7`](./BACKEND_ARCHITECTURE.md)) is what ties a user-reported issue to server logs — not verbose body logging.

## 13. What Is Deliberately Not Built (and why that's acceptable here)

- No WAF, no DDoS-mitigation layer — the MVP has no public network exposure at all (local-only, per [ADR-012](./adr/ADR-012-local-demo-scope.md)), so this class of concern doesn't apply; it would only become relevant under the optional future production-evolution path ([`DEPLOYMENT.md § 6`](./DEPLOYMENT.md)).
- No malware/virus scanning of uploaded media in MVP — flagged as a known gap in [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md) and [`DEPLOYMENT.md`](./DEPLOYMENT.md)'s risk section, acceptable because uploads are restricted to authenticated Instructors (a small, known set of accounts, not open public upload) and content-type/size are still validated.
- No SOC2/compliance-grade audit logging — explicitly out of scope per the brief's "avoid overbuilding enterprise administration."
