export type UserRole = "student" | "instructor" | "admin";

/**
 * Full profile shape returned by `GET /users/me`. Null-valued fields are omitted from the
 * JSON entirely (backend `explicitNulls = false`, per execution/DECISIONS_LOG.md D20) rather
 * than sent as `null` — hence optional (`?`), not `| null`.
 */
export interface AuthUser {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  avatarMediaId?: string;
  preferredLocale?: string;
  createdAt: string;
}

/**
 * The smaller `user` object embedded in `POST /auth/{login,register}` responses — verified
 * against backend/src/main/kotlin/com/mentora/backend/auth/service/AuthService.kt's
 * `AuthUser` DTO (a different, narrower type than the one above despite the shared name):
 * `{id, email, name, role, preferredLocale?}` — no `avatarMediaId`, no `createdAt`. Not
 * documented as such in execution/INTEGRATION_CONTRACT.md § 8a; see DECISIONS_LOG D36.
 */
export interface AuthSessionUser {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  preferredLocale?: string;
}
