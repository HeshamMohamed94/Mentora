import createIntlMiddleware from "next-intl/middleware";
import { NextRequest, NextResponse } from "next/server";
import { routing } from "./i18n/routing";

const intlMiddleware = createIntlMiddleware(routing);

// Auth gate (architecture/WEB_ARCHITECTURE.md § 2): an unauthenticated request to the
// authenticated Student ("/app"), Instructor ("/instructor"), or Admin ("/admin") areas
// redirects to Login and returns there after a successful login (product/USER_FLOWS.md § 2,
// ux/NAVIGATION_SPEC.md § 6 — "enroll-gate always returns to intent").
//
// This is a presence check only, not a JWT-validity check — the backend is the sole source
// of authorization truth (architecture/AUTH_SECURITY.md § 6); middleware only avoids
// rendering an authenticated shell for a request that plainly has no session. The refresh
// token (30-day sliding expiry) is the better signal than the 15-minute access token, since
// an expired access token with a live refresh token is still a legitimate session that the
// client-side fetch interceptor will silently renew (execution/INTEGRATION_CONTRACT.md § 2).
const REFRESH_COOKIE = "mentora_refresh_token";
const PROTECTED_PREFIXES = ["/app", "/instructor", "/admin"];

function stripLocalePrefix(pathname: string): string {
  const match = pathname.match(/^\/([a-z]{2})(\/.*|$)/);
  if (match && routing.locales.includes(match[1] as (typeof routing.locales)[number])) {
    return match[2] || "/";
  }
  return pathname;
}

function isProtectedPath(pathname: string): boolean {
  const unprefixed = stripLocalePrefix(pathname);
  return PROTECTED_PREFIXES.some((prefix) => unprefixed === prefix || unprefixed.startsWith(`${prefix}/`));
}

export default function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (isProtectedPath(pathname) && !request.cookies.get(REFRESH_COOKIE)) {
    const localeMatch = pathname.match(/^\/([a-z]{2})(\/|$)/);
    const locale = localeMatch && routing.locales.includes(localeMatch[1] as (typeof routing.locales)[number])
      ? localeMatch[1]
      : routing.defaultLocale;

    const loginUrl = new URL(`/${locale}/login`, request.url);
    loginUrl.searchParams.set("redirect", pathname + request.nextUrl.search);
    return NextResponse.redirect(loginUrl);
  }

  return intlMiddleware(request);
}

export const config = {
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"],
};
