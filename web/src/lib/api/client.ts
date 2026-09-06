/**
 * Typed fetch client for the Ktor backend, reached same-origin via next.config.ts's
 * `/api/*` rewrite (architecture/WEB_ARCHITECTURE.md § 3). Implements the response envelope,
 * CSRF header, and 401-refresh-retry rules from execution/INTEGRATION_CONTRACT.md §§ 2-4.
 */

export interface ApiMeta {
  requestId?: string;
  nextCursor?: string;
}

export interface ApiEnvelope<T> {
  data?: T;
  error?: { code: string; message: string; fields?: Record<string, string> };
  meta?: ApiMeta;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields?: Record<string, string>;

  constructor(status: number, code: string, message: string, fields?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function buildHeaders(init: RequestInit, method: string): Headers {
  const headers = new Headers(init.headers);
  // CSRF defense (AUTH_SECURITY.md § 10) — required on every state-changing request; a
  // cross-site attacker's form-POST/<img> CSRF attempt cannot set a custom header.
  if (MUTATING_METHODS.has(method)) {
    headers.set("X-Requested-With", "mentora-web");
  }
  if (init.body && typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  return headers;
}

// Server Components/route handlers run outside the browser, so a relative fetch URL has
// nothing to resolve against — they must hit the backend directly. This only matters for
// unauthenticated, server-fetched data (e.g. Landing's featured courses); every
// authenticated screen fetches client-side via hooks, where the relative `/api/v1` path
// correctly goes through next.config.ts's same-origin proxy (WEB_ARCHITECTURE.md § 3).
const SERVER_API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

async function rawRequest(path: string, init: RequestInit): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const base = typeof window === "undefined" ? `${SERVER_API_BASE_URL}/api/v1` : "/api/v1";
  return fetch(`${base}${path}`, {
    ...init,
    method,
    headers: buildHeaders(init, method),
    credentials: "same-origin",
  });
}

// Coalesce concurrent refresh attempts into one in-flight request.
let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = rawRequest("/auth/refresh", { method: "POST" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

function emitForceLogout() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event("mentora:force-logout"));
  }
}

/** Full envelope (data + meta) — use for cursor-paginated list endpoints that need `nextCursor`. */
export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<{ data: T; meta: ApiMeta }> {
  let response = await rawRequest(path, init);

  if (response.status === 401) {
    const body = (await response
      .clone()
      .json()
      .catch(() => null)) as ApiEnvelope<T> | null;

    if (body?.error?.code === "AUTH_TOKEN_EXPIRED") {
      const refreshed = await refreshSession();
      if (refreshed) {
        response = await rawRequest(path, init);
      } else {
        emitForceLogout();
      }
    } else if (body?.error?.code && body.error.code !== "AUTH_INVALID_CREDENTIALS") {
      emitForceLogout();
    }
  }

  const envelope = (await response.json().catch(() => null)) as ApiEnvelope<T> | null;

  if (!response.ok || !envelope || envelope.error) {
    const code = envelope?.error?.code ?? "INTERNAL_ERROR";
    // Per INTEGRATION_CONTRACT.md § 3: `message` is an English developer fallback only —
    // never render it in production UI. Callers map `code` to a localized string.
    const message = envelope?.error?.message ?? "Request failed.";
    throw new ApiError(response.status, code, message, envelope?.error?.fields);
  }

  return { data: envelope.data as T, meta: envelope.meta ?? {} };
}

/** Convenience wrapper for single-resource endpoints that don't need `meta`. */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const { data } = await apiRequest<T>(path, init);
  return data;
}
