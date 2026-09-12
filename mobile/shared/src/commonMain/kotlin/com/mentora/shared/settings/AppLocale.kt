package com.mentora.shared.settings

/**
 * The two UI locales the mobile app supports, per `product/MVP_SCOPE.md`/
 * `architecture/LOCALIZATION_ARCHITECTURE.md`. [wireValue] is the exact string used both by
 * `PreferenceStore`'s persisted value and by the backend's `?language=`/`preferredLocale` wire
 * values (`"en"`/`"ar"`) — never re-derive it from `name`/`ordinal`.
 *
 * This is a UI-locale type only — never conflated with a course's `contentLanguage` (a separate
 * concept, per `execution/PHASE_3_KMP_PLAN.md` Task 7/C3).
 */
enum class AppLocale(val wireValue: String) {
    English("en"),
    Arabic("ar"),
    ;

    companion object {
        /** Falls back to [English] for any value that isn't a recognized wire value. */
        fun fromWireValue(value: String): AppLocale = entries.firstOrNull { it.wireValue == value } ?: English
    }
}
