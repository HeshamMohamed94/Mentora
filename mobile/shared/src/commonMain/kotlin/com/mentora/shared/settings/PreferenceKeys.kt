package com.mentora.shared.settings

/**
 * `multiplatform-settings` key names shared by [AndroidPreferenceStore] and [IosPreferenceStore]
 * so the two platform implementations can never drift apart. `internal` — visible across this
 * Gradle module's source sets, not part of the public `shared` surface.
 */
internal object PreferenceKeys {
    const val LOCALE = "app_locale"
    const val THEME = "theme_preference"
}
