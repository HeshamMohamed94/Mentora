package com.mentora.shared.settings

/**
 * Local-only display theme preference — never sent to the backend (no wire field exists for it;
 * see `execution/PHASE_3_KMP_PLAN.md` Task 6's `SetThemeUseCase` note).
 */
enum class ThemePreference(val wireValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        /** Falls back to [System] for any value that isn't a recognized wire value. */
        fun fromWireValue(value: String): ThemePreference = entries.firstOrNull { it.wireValue == value } ?: System
    }
}
