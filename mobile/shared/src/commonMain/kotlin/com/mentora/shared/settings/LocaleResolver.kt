package com.mentora.shared.settings

/**
 * Resolves the app's initial [AppLocale] from a user's preferred system locale list — [Arabic]
 * if any entry's language subtag (the part before the first `-`, e.g. `"ar"` in `"ar-EG"`) is
 * `"ar"` (case-insensitively), else [English].
 *
 * A pure function, deliberately: `commonMain` must never read a platform locale API directly
 * (`Locale.current`/`NSLocale.currentLocale` — see `execution/PHASE_3_KMP_PLAN.md` Task 4's "Must
 * NOT" list). The platform layer (Android `LocaleListCompat`/`Locale.getDefault()`, iOS
 * `NSLocale.preferredLanguages`) is responsible for building [systemLocales] and calling this —
 * that platform-side call is out of scope for Task 4 (Phase 4/5 work).
 */
fun resolveInitialLocale(systemLocales: List<String>): AppLocale {
    val hasArabicTag = systemLocales.any { tag ->
        tag.substringBefore('-').trim().equals(AppLocale.Arabic.wireValue, ignoreCase = true)
    }
    return if (hasArabicTag) AppLocale.Arabic else AppLocale.English
}
