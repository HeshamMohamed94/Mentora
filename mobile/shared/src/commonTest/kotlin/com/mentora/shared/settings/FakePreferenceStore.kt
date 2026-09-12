package com.mentora.shared.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [PreferenceStore] for tests (Task 5+ locale-sync tests). Not `expect`/`actual` —
 * [PreferenceStore] is a plain interface (see its kdoc), so this is just another implementor,
 * same as [AndroidPreferenceStore]/[IosPreferenceStore].
 */
class FakePreferenceStore(
    initialLocale: AppLocale = AppLocale.English,
    initialTheme: ThemePreference = ThemePreference.System,
) : PreferenceStore {
    private val localeState = MutableStateFlow(initialLocale)
    override val locale: StateFlow<AppLocale> = localeState.asStateFlow()

    private var theme: ThemePreference = initialTheme

    override fun setLocale(locale: AppLocale) {
        localeState.value = locale
    }

    override fun getTheme(): ThemePreference = theme

    override fun setTheme(theme: ThemePreference) {
        this.theme = theme
    }
}
