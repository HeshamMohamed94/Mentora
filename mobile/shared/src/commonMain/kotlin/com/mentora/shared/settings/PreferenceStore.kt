package com.mentora.shared.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Non-secret app preferences ([AppLocale], [ThemePreference]) — over `multiplatform-settings`.
 * Never used for [com.mentora.shared.auth.AuthTokens] or any other secret; see
 * [com.mentora.shared.auth.TokenStorage]'s kdoc for that boundary.
 *
 * A plain `interface` for the same reason [com.mentora.shared.auth.TokenStorage] is: Android's
 * implementation needs a `Context`, iOS's needs an `NSUserDefaults` reference (or none, using the
 * standard one) — an `expect`/`actual` class pair would force an identical constructor across
 * both, which this asymmetry rules out. [FakePreferenceStore] (`commonTest`) implements this
 * interface directly.
 *
 * [locale] is observable ([StateFlow], not just a synchronous getter) so a platform UI layer can
 * react live to a locale change from anywhere in the app (e.g. Task 6's `SetLocaleUseCase`)
 * without polling. See [AndroidPreferenceStore]/[IosPreferenceStore]'s kdoc for why this is a
 * plain `MutableStateFlow` wrapper updated alongside the `multiplatform-settings` write, rather
 * than `multiplatform-settings-coroutines`'s `ObservableSettings`/`FlowSettings` support.
 */
interface PreferenceStore {
    val locale: StateFlow<AppLocale>

    fun setLocale(locale: AppLocale)

    fun getTheme(): ThemePreference

    fun setTheme(theme: ThemePreference)
}
