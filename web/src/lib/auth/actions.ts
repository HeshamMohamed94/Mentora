import { apiFetch } from "../api/client";
import type { AuthSessionUser, AuthUser } from "./types";

/**
 * Auth response bodies also carry `accessToken`/`refreshToken` for mobile/API uniformity
 * (execution/INTEGRATION_CONTRACT.md § 8a). Web's actual session is the httpOnly cookie pair
 * the backend sets on the same response — these functions deliberately discard the token
 * fields and never store or re-expose them to client JS. `user` here is the narrower
 * `AuthSessionUser` shape, not the full `AuthUser` profile — see types.ts's doc comment.
 */
interface RawAuthResponse {
  accessToken: string;
  refreshToken: string;
  user: AuthSessionUser;
}

export async function login(input: { email: string; password: string }): Promise<AuthSessionUser> {
  const { user } = await apiFetch<RawAuthResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
  return user;
}

export async function register(input: { name: string; email: string; password: string }): Promise<AuthSessionUser> {
  const { user } = await apiFetch<RawAuthResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
  return user;
}

export async function logout(): Promise<void> {
  await apiFetch<Record<string, never>>("/auth/logout", { method: "POST" });
}

export async function getCurrentUser(): Promise<AuthUser> {
  return apiFetch<AuthUser>("/users/me");
}
