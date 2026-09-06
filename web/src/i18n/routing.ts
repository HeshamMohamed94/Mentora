import { defineRouting } from "next-intl/routing";

/**
 * Locked MVP languages (product/PRODUCT_SPEC.md § 16): English + Arabic, both fully
 * functional, not a "coming soon" placeholder. `localePrefix: "always"` keeps every route
 * — including the default locale — under /en or /ar, so hreflang alternates
 * (WEB_ARCHITECTURE.md § 7) and locale-scoped middleware matching stay unambiguous.
 */
export const routing = defineRouting({
  locales: ["en", "ar"],
  defaultLocale: "en",
  localePrefix: "always",
});

export type AppLocale = (typeof routing.locales)[number];
