package com.mentora.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.mentora.shared.MentoraSdk
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * T4 Part C — theme observable (G1 resolution, `PHASE_4_ANDROID_PLAN.md` § 6).
 *
 * `UserFacade.setTheme` is write-only — `shared` exposes no `observeTheme`/notification of any
 * kind. This class owns BOTH sides of every write: [setTheme] calls `sdk.user.setTheme(theme)`
 * (which persists through the exact same [AndroidPreferenceStore] instance `MentoraApplication`
 * retains — see its kdoc) and, in that same call, updates [theme]'s backing [MutableStateFlow] so
 * observers stay in sync without ever needing the façade to notify anyone. [androidPreferenceStore]
 * is read directly (a plain synchronous [AndroidPreferenceStore.getTheme] getter, not a `Flow`) only
 * once, at construction, to seed the initial value with whatever was already persisted from a prior
 * app run.
 */
class ThemeController(androidPreferenceStore: AndroidPreferenceStore, private val sdk: MentoraSdk) {
    private val _theme = MutableStateFlow(androidPreferenceStore.getTheme())
    val theme: StateFlow<ThemePreference> = _theme.asStateFlow()

    fun setTheme(preference: ThemePreference) {
        sdk.user.setTheme(preference)
        _theme.value = preference
    }
}

/** [ThemePreference.Light]/[ThemePreference.Dark] map directly; [ThemePreference.System] defers to
 * the device's current system setting via [isSystemInDarkTheme]. The one place `MentoraTheme`'s
 * `darkTheme` boolean is computed from a real preference source instead of a hardcoded default. */
@Composable
fun ThemePreference.resolveDarkTheme(): Boolean = when (this) {
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
    ThemePreference.System -> isSystemInDarkTheme()
}
