package com.mentora.shared.settings

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_FILE_NAME = "mentora_preferences"

/**
 * Android [PreferenceStore], backed by `multiplatform-settings`' [SharedPreferencesSettings] —
 * plain (unencrypted) `SharedPreferences` is explicitly fine here because [AppLocale]/
 * [ThemePreference] are non-secret. Never used for
 * [com.mentora.shared.auth.AuthTokens] — see [com.mentora.shared.auth.TokenStorage]'s kdoc.
 *
 * [locale] is a plain [MutableStateFlow] that [setLocale] updates alongside the persisted
 * `multiplatform-settings` write, rather than `multiplatform-settings-coroutines`'s
 * `ObservableSettings`/`FlowSettings` support: that mechanism needs a `CoroutineScope` to collect
 * from, and this constructor-injected, DI-agnostic class (built once by Koin's platform module in
 * Task 15, with no lifecycle of its own) has no natural owner for one. The
 * `execution/PHASE_3_KMP_PLAN.md` Task 4 acceptance criteria explicitly sanctions this simpler
 * alternative.
 *
 * [context] is expected to be supplied by Koin's `androidContext()` when the Android platform DI
 * module is wired in Task 15 — this class has no dependency on Koin itself.
 */
class AndroidPreferenceStore(context: Context) : PreferenceStore {
    private val settings: Settings = SharedPreferencesSettings(
        context.applicationContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE),
    )

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
