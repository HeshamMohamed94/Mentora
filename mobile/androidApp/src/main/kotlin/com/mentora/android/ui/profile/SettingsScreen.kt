package com.mentora.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.ThemeController
import com.mentora.android.ui.components.MentoraSelect
import com.mentora.android.ui.components.MentoraSelectOption
import com.mentora.shared.MentoraSdk
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.ThemePreference

/**
 * T18 — the real Settings screen, replacing `ui/screens/PlaceholderScreens.kt`'s placeholder. Built
 * from `ux/SCREEN_UX_SPECS.md § 17` directly (see [ProfileViewModel]'s own kdoc for the two disclosed
 * scope resolutions this screen and [ProfileScreen] share — no account/password fields, no Logout
 * control here). Language selection is genuinely functional end to end for the first time in this
 * phase — see `com.mentora.android.locale.LocalizedContent`'s own kdoc for the mechanism that makes
 * selecting a language here actually change every `stringResource` in the app, not just persist an
 * unread preference.
 *
 * **[themeController] is a real parameter, not resolved via `LocalContext.current.applicationContext
 * as MentoraApplication` (review fix, HIGH).** This module's `testInstrumentationRunner` is globally
 * `NoOpApplicationTestRunner` (see that class's own kdoc) — every instrumented test substitutes a
 * plain `Application`, so that cast throws a real `ClassCastException` the moment ANY test composes
 * this screen, confirmed by an actual `NavigationShellTest` failure this exact way. Threaded down
 * from [com.mentora.android.navigation.MentoraNavHost]'s own `themeController` parameter instead
 * (`MainActivity`'s `app.themeController`, the one process-lifetime instance), the same way `sdk`
 * already is.
 */
@Composable
fun SettingsScreen(sdk: MentoraSdk, themeController: ThemeController, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(sdk, themeController))
    val locale by viewModel.locale.collectAsState()
    val theme by viewModel.theme.collectAsState()

    SettingsContent(
        locale = locale,
        theme = theme,
        onLanguageSelected = viewModel::onLanguageSelected,
        onThemeSelected = viewModel::onThemeSelected,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    locale: AppLocale,
    theme: ThemePreference,
    onLanguageSelected: (AppLocale) -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(MentoraDimens.spacing.space4).testTag(SettingsScreenTestTag),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space6),
    ) {
        // Option order is a fixed convention (English, then العربية) per § 17's own accessibility
        // note — never re-sorted by the CURRENT language, since that would make the list itself
        // silently reorder every time a student switches languages.
        MentoraSelect(
            label = stringResource(R.string.settings_language_field_label),
            options = listOf(
                MentoraSelectOption(value = AppLocale.English, label = stringResource(R.string.settings_language_option_english)),
                MentoraSelectOption(value = AppLocale.Arabic, label = stringResource(R.string.settings_language_option_arabic)),
            ),
            selected = locale,
            onSelect = onLanguageSelected,
            modifier = Modifier.fillMaxWidth().testTag(SettingsLanguageSelectTestTag),
        )

        MentoraSelect(
            label = stringResource(R.string.settings_theme_field_label),
            options = listOf(
                MentoraSelectOption(value = ThemePreference.Light, label = stringResource(R.string.settings_theme_option_light)),
                MentoraSelectOption(value = ThemePreference.Dark, label = stringResource(R.string.settings_theme_option_dark)),
                MentoraSelectOption(value = ThemePreference.System, label = stringResource(R.string.settings_theme_option_system)),
            ),
            selected = theme,
            onSelect = onThemeSelected,
            modifier = Modifier.fillMaxWidth().testTag(SettingsThemeSelectTestTag),
        )
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val SettingsScreenTestTag = "settings-screen"
const val SettingsLanguageSelectTestTag = "settings-language-select"
const val SettingsThemeSelectTestTag = "settings-theme-select"
