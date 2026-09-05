# ADR-006: Authentication & Token Strategy

**Status:** Locked
**Date:** 2026-09-04

## Decision

- Password hashing: **BCrypt**, cost factor 12.
- Access token: short-lived **JWT** (15 minutes), signed HS256.
- Refresh token: **opaque, random, rotating, single-use** token, stored **hashed** server-side, 30-day lifetime.
- **Web:** both tokens delivered as **httpOnly, Secure, SameSite=Lax cookies**, set via a same-origin reverse-proxy path so no cross-site cookie configuration is needed.
- **Mobile (Android/iOS):** tokens stored in platform secure storage (EncryptedSharedPreferences/Keystore-backed DataStore; Keychain) via the `shared` KMP module's `expect`/`actual` boundary, sent as an `Authorization: Bearer` header.
- Full flow, endpoints, and RBAC enforcement detail: [`AUTH_SECURITY.md`](../AUTH_SECURITY.md).

## Context

MVP explicitly excludes Forgot/Reset Password and SSO ([`../product/MVP_SCOPE.md`](../../product/MVP_SCOPE.md)) — this ADR is scoped to register/login/logout + role-based access only. The brief explicitly forbids "insecure localStorage-only auth for Web if a safer practical design is available," and requires backend-authoritative authorization.

## Options Considered

### Option A — JWT access + rotating opaque refresh, httpOnly cookies (Web) / secure storage (mobile) — chosen

Standard, well-understood pattern. httpOnly cookies mean a client-side XSS bug can't read the token (unlike `localStorage`, which is fully exposed to any injected script) — directly satisfies the brief's explicit constraint. Short-lived access tokens limit the blast radius of a leaked token; rotating single-use refresh tokens mean a stolen-and-replayed refresh token is detectable (the legitimate client's next refresh attempt will fail against an already-rotated token, which the backend treats as a signal to revoke the whole token family).

### Option B — JWT in `localStorage`, no refresh rotation

**Why not:** exactly what the brief prohibits — `localStorage` is readable by any script running on the page, so a single XSS vulnerability anywhere in the Web app compromises every logged-in session's tokens. Rejected outright.

### Option C — Server-side session only (opaque session ID cookie, session state in MongoDB/Redis, no JWT at all)

A legitimate, arguably simpler alternative — no token signing/verification logic at all, revocation is trivial (delete the session document).

**Why not chosen as the primary mechanism:** would require a database (or Redis) round-trip on *every* authenticated request to validate the session, whereas a signed JWT access token is verified in-process with no I/O — meaningful for a system where nearly every request (progress checks, course reads) is authenticated. Also doesn't map as cleanly onto mobile clients, which don't have a cookie jar in the same way a browser does and conventionally use bearer tokens. The chosen design gets session's easy-revocation property back for the piece that matters (the refresh token, checked at a much lower frequency than every request) while keeping the access token's per-request cost near zero.

### Option D — RS256 (asymmetric) JWT signing instead of HS256

**Why not (for now):** RS256 matters when multiple independently-deployed services need to *verify* tokens without holding the signing secret — a microservices concern. Since Mentora is a modular monolith (ADR-003), the single process that issues tokens is also the only process that ever verifies them, so a shared HS256 secret is simpler with no real security cost at this architecture's scale.

## CSRF Consideration

Because Web auth relies on cookies, state-changing requests are protected against CSRF via **SameSite=Lax** (blocks the classic cross-site form-POST attack vector for a cookie-authenticated session) plus a **custom-header requirement** (e.g. `X-Requested-With`) enforced server-side on all mutating routes — a request without it is rejected, and a cross-site attacker's form/img-tag-based CSRF attempt cannot set custom headers. Mobile's Bearer-header auth is not cookie-based and is therefore not CSRF-exposed by construction.

## Consequences

- The Web app and backend must be deployed such that cookies are first-party (see [`WEB_ARCHITECTURE.md`](../WEB_ARCHITECTURE.md)'s reverse-proxy note) — this is a deployment-topology consequence of this decision, recorded in [`DEPLOYMENT.md`](../DEPLOYMENT.md).
- A `RefreshToken` collection exists in MongoDB (hashed tokens only, never plaintext), enabling logout-time and breach-detected revocation — see [`DATABASE_MODEL.md`](../DATABASE_MODEL.md).
- Role (`student`/`instructor`/`admin`) is embedded as a claim in the access token but re-verified against the database at the service layer for ownership-sensitive operations (e.g. "is this Instructor the owner of this course") — the JWT claim is trusted for coarse role gating, never for fine-grained resource ownership, per [`AUTH_SECURITY.md`](../AUTH_SECURITY.md).

## Migration Path

If Mentora ever adds SSO (Post-MVP), it plugs into the same access/refresh token issuance at the end of the OAuth callback — no change to how tokens are stored or verified afterward. If Mentora ever needs true multi-service token verification, HS256 → RS256 is a contained change (rotate to an asymmetric key pair, publish the public key) that doesn't change the refresh-token/cookie/RBAC design above it.
