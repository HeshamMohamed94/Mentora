# Mentora — Localization Architecture

Implements [`../product/PRODUCT_SPEC.md § 16`](../product/PRODUCT_SPEC.md) (locked MVP requirement) and [`../design-system/LOCALIZATION.md`](../design-system/LOCALIZATION.md) (locked RTL/logical-layout rules) across all three clients. This file resolves the technical questions those documents explicitly deferred: which formatting API, which persistence mechanism, which detection mechanism, per platform.

---

## 1. Two Independent Concepts, Restated as an Architecture Boundary

- **UI language** (`en`/`ar`) — controls interface strings, text direction, layout mirroring, and formatting locale. Lives as: a route prefix + cookie (Web), a `PreferenceStore` value (mobile, via `shared`), and optionally `users.preferredLocale` (account-synced).
- **Course content language** (`courses.contentLanguage`, `en`/`ar`) — pure metadata on a `Course` document ([`DATABASE_MODEL.md § 4`](./DATABASE_MODEL.md)), displayed/filterable, **never read by any localization/formatting/translation code path.** A component rendering a course's content language badge and a component rendering the UI's own translated strings never share code or state — this is enforced by the two concepts having entirely separate data sources (an API field vs. an i18n runtime), not by convention alone.

## 2. UI String Resources Per Platform

| Platform | Mechanism | Why this one (native, tooling-integrated — see ADR-002 § "What Is NOT Shared") |
|---|---|---|
| Web | `next-intl`, ICU message format, `messages/en.json` + `messages/ar.json` | App Router-native, supports ICU plurals/interpolation, ships ESLint rule support for catching unused/missing keys |
| Android | `res/values/strings.xml` (default, English) + `res/values-ar/strings.xml` | Android's own resource-qualifier system — Android Studio's translation editor and lint (`MissingTranslation` check) validate completeness natively |
| iOS | String Catalog (`Localizable.xcstrings`, Xcode 15+) | Xcode's built-in per-locale editor, pluralization support, and a build-time check for missing translations |

No platform's UI strings are generated from a shared KMP source — each platform's own tooling is the source of truth for its translations, per [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md). A shared **key list** (which string keys must exist, in English, as the canonical reference) can be tracked in `architecture/` documentation or a lightweight shared JSON reference file if translation-completeness auditing across platforms becomes useful at implementation time — not a runtime dependency, purely a bookkeeping aid.

**Backend-originated strings:** none exist. Per [`API_CONTRACT.md § 3`](./API_CONTRACT.md), the backend returns error `code`s, never localized prose — every client's own string resources map each `code` to a localized message. This is the one clean answer to "Localization must happen at an appropriate layer": the layer is the client's i18n resource file, uniformly, for every string in the system including error messages.

## 3. Text Direction & Layout

- **Web:** root layout sets `<html lang={locale} dir={locale === 'ar' ? 'rtl' : 'ltr'}>`. Every layout primitive uses CSS logical properties (§ [`WEB_ARCHITECTURE.md § 4`](./WEB_ARCHITECTURE.md)), so this one attribute flip mirrors the entire application — no component contains `if (isRtl)` branching for layout, per [`../design-system/LOCALIZATION.md § 8`](../design-system/LOCALIZATION.md)'s locked rule.
- **Android:** the system locale (set by the in-app language switch, § 4) drives `LocalLayoutDirection` automatically for `ar`; Compose layout code uses `Modifier.padding(start=, end=)`/`Arrangement` exclusively (already mandated by [`../design-system/platform-mapping.md § 3.1`](../design-system/platform-mapping.md)), so no additional architecture is needed beyond ensuring the app's active locale is actually set correctly (§ 4) — the mirroring is Compose's own built-in behavior once that's true.
- **iOS:** same reasoning — SwiftUI's `\.layoutDirection` environment value follows the app's active locale automatically when `leading`/`trailing` (never `.left`/`.right`) are used throughout, per [`../design-system/platform-mapping.md § 3.1`](../design-system/platform-mapping.md).
- **Fixed exceptions** (video scrubber, Mentora wordmark) are implemented identically regardless of platform locale — these are component-level rules already locked in [`../design-system/LOCALIZATION.md § 2`](../design-system/LOCALIZATION.md), not a technical-architecture decision; this file just confirms no platform mechanism (Android's `supportsRtl`, iOS's mirroring) is allowed to override them — both platforms provide explicit opt-out mechanisms (`android:autoMirrored="false"` equivalent handling, `.flipsForRightToLeftLayoutDirection(false)`) applied specifically to the scrubber and wordmark components.

## 4. Locale Switching, Detection & Persistence

**Switching (in-place, no restart, no logout — per [`../product/USER_FLOWS.md § 28`](../product/USER_FLOWS.md)):**

| Platform | Mechanism |
|---|---|
| Web | Changing the Settings language `Select` navigates to the same route under the other locale prefix (`/en/app/settings` → `/ar/app/settings`) via `next-intl`'s router — a full but instant client-side transition, not a hard page reload; `<html dir>` updates as part of the same render. |
| Android | Sets the app's per-app locale via `AppCompatDelegate.setApplicationLocales(...)` (the modern Android 13+ API, back-compatible via AppCompat for older versions) — triggers an automatic Activity recreation with the new locale/layout direction applied, without restarting the process or losing navigation state (Compose's saved-state mechanisms survive this). |
| iOS | Since iOS has no fully-supported in-app-only locale override API as clean as Android's, the app manages its **own** active-locale state (a `PreferenceStore` value in `shared`, read by every SwiftUI view via a custom `EnvironmentKey` rather than relying on `Locale.current`) and every localized string lookup/`Font`/formatter goes through this app-level locale explicitly, not the system's. This is a deliberate, documented iOS-specific implementation detail — not a compromise on the requirement, since the *product* requirement (switch in place, no restart) is still fully met; it's *how* iOS achieves it that differs slightly from Android's OS-level mechanism. |

**Persistence:**

| User state | Where stored |
|---|---|
| Guest | Web: cookie (`NEXT_LOCALE`, read by `middleware.ts` on every request, so SSR renders the right locale with no flash-of-wrong-language) + `localStorage` fallback. Mobile: `PreferenceStore` (`multiplatform-settings`, § [`KMP_ARCHITECTURE.md § 1`](./KMP_ARCHITECTURE.md)) — device-local only, per [`../product/USER_ROLES.md`](../product/USER_ROLES.md). |
| Signed-in | Same local mechanism as Guest **plus** `users.preferredLocale` synced via `PATCH /api/v1/users/me` on every change, and read back on login to apply across devices (§ [`AUTH_SECURITY.md § 2`](./AUTH_SECURITY.md): login response includes `preferredLocale`). |
| Guest → registers/logs in | The locally-persisted preference becomes the new account's initial `preferredLocale` (set once, at the registration/first-login call) — never overwritten by a stale server value on that specific transition, per [`../product/USER_FLOWS.md § 28`](../product/USER_FLOWS.md): "local preference is honored as the starting value." On every subsequent login on any device, the **account's** `preferredLocale` is authoritative and wins over that device's local value. |

**First-launch/first-visit detection** (per [`../product/PRODUCT_SPEC.md § 16`](../product/PRODUCT_SPEC.md)'s locked rule: Arabic if supported+preferred, else English, English fallback always):

| Platform | Detection source |
|---|---|
| Web | `middleware.ts` reads the `Accept-Language` header on the first request with no locale cookie set, negotiates against the two supported locales, redirects to the resolved prefix, and sets the cookie |
| Android | `LocaleListCompat.getDefault()` (system locale list) checked against supported locales at first app launch (no stored preference yet) |
| iOS | `Locale.preferredLanguages` checked the same way, at first launch |

## 5. Locale-Aware Formatting — API Per Platform

Per [`../design-system/LOCALIZATION.md § 7`](../design-system/LOCALIZATION.md): dates, numbers, durations, and prices format per active UI locale, using **Western Arabic numerals in both `en` and `ar`** (a locked design decision, not a technical one — this section only supplies the mechanism).

| Platform | API | Forcing Western numerals |
|---|---|---|
| Web | `Intl.DateTimeFormat`, `Intl.NumberFormat` | Pass `{ numberingSystem: 'latn' }` (or append `-u-nu-latn` to the locale tag, e.g. `'ar-u-nu-latn'`) — both are standard `Intl` options; without this, `ar` locale formatting defaults to Eastern Arabic-Indic digits, which the design system explicitly does not want. |
| Android | `android.icu.text.DateFormat`/`NumberFormat` (or `java.text` equivalents) with `Locale.forLanguageTag("ar-EG-u-nu-latn")` | Same Unicode locale extension mechanism, ICU-backed on Android. |
| iOS | `DateFormatter`/`NumberFormatter` with `.locale = Locale(identifier: "ar_EG@numbers=latn")` (or the modern `Locale(identifier:).numberingSystem` override) | Foundation's `Locale` supports the same `numbers=latn`/`-u-nu-latn` extension, since it's built on ICU underneath. |

This is implemented once per platform as a small formatting-utility wrapper (`lib/i18n/format.ts` on Web, an `expect`/`actual` bridge in `shared/settings` for the *locale object construction* if useful, though the actual `NumberFormat`/`DateFormatter` calls stay in each platform's UI layer since they're UI-framework-adjacent) — not re-implemented ad hoc at every call site, so the "always force Latin numerals" rule can't be forgotten in one screen and not another.

**All raw values (dates, prices, counts) arrive from the backend unformatted** (§ [`API_CONTRACT.md § 8`](./API_CONTRACT.md)) — formatting is 100% client-side, so the backend has zero locale awareness to build or maintain.

## 6. Error Message Localization

Restated from [`API_CONTRACT.md § 3`](./API_CONTRACT.md) since it's a genuine localization-architecture decision: every backend error carries a stable `code`; each client's own string-resource file (§ 2) maps `code` → localized message, in the exact same resource file as every other UI string, using the exact same review/translation-completeness tooling. A new error code added to the backend requires a corresponding key added to all three clients' resource files — a lint/CI check (comparing the backend's known error-code enum against each client's resource-file keys) is a reasonable implementation-time addition to prevent an untranslated code silently falling back to `message`'s English default.

## 7. Accessibility Labels

Per [`../design-system/ACCESSIBILITY.md § 12`](../design-system/ACCESSIBILITY.md): `aria-label`/`contentDescription`/`accessibilityLabel` values are themselves entries in the same per-platform string-resource files (§ 2), translated alongside every other UI string — not a separate accessibility-strings file, so they can never silently fall out of sync with the visible-string translation effort.
