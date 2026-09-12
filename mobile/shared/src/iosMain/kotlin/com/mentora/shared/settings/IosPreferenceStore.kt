package com.mentora.shared.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

// NOT compiled/verified on this Windows machine — see "Disclosed limitation B1" in
// execution/PHASE_3_KMP_PLAN.md. Needs `implementation(libs.multiplatform.settings)` added to
// `iosMain`'s dependencies once a macOS host wires that source set back into
// `shared/build.gradle.kts` (see that file's comment).

/**
 * iOS [PreferenceStore], backed by `multiplatform-settings`' [NSUserDefaultsSettings] over the
 * standard [NSUserDefaults]. Same [locale]-as-`MutableStateFlow` design as
 * [AndroidPreferenceStore] — see that class's kdoc for why.
 */
class IosPreferenceStore : PreferenceStore {
    private val settings: Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)

    private val localeState = MutableStateFlow(
        AppLocale.fromWireValue(settings.getString(PreferenceKeys.LOCALE, AppLocale.English.wireValue)),
    )
    override val locale: StateFlow<AppLocale> = localeState.asStateFlow()

    override fun setLocale(locale: AppLocale) {
        settings.putString(PreferenceKeys.LOCALE, locale.wireValue)
        localeState.value = locale
    }

    override fun getTheme(): ThemePreference =
        ThemePreference.fromWireValue(settings.getString(PreferenceKeys.THEME, ThemePreference.System.wireValue))

    override fun setTheme(theme: ThemePreference) {
        settings.putString(PreferenceKeys.THEME, theme.wireValue)
    }
}
