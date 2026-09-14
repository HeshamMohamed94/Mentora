package com.mentora.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.theme.ThemeController
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.ThemePreference
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * T18 — Settings' ViewModel. `ux/SCREEN_UX_SPECS.md § 17`'s only two REAL, functional controls this
 * task builds: Language and Theme. **Two disclosed, deliberate scope resolutions**, both following
 * this phase's already-established precedents rather than the literal spec text:
 * - **No account/password fields** — § 17's content item 1 ("Account/password change fields") has no
 *   backing endpoint at all (`User`'s own kdoc, same D44 precedent Web's Task 10 already disclosed
 *   for this exact gap). "Edit Profile" (name only, the one field that DOES have a real endpoint)
 *   lives on Profile itself per § 16's own content order, not duplicated here.
 * - **No Logout control here** — `ux/MOBILE_UX.md § 13`'s own explicit mobile-specific resolution:
 *   "Logout lives in Settings or directly on Profile (single, consistent placement — Profile...)".
 *   § 17's literal "3. Logout" is the generic cross-platform text this mobile-specific doc overrides;
 *   [ProfileViewModel.onLogoutTapped] is the one real Logout action in this app.
 *
 * **No "Save Changes" button** — § 17's own Language behavior text already requires the Select to
 * "apply immediately... no confirmation dialog"; Theme (§ 4 plan G1) is the same "apply on selection"
 * shape. With no account fields left to batch (the point above), there is nothing left for a Save
 * button to submit — omitted rather than built as a dead control.
 *
 * **[onLanguageSelected] never surfaces a failure to the Select itself.** [setLocale]
 * (`SetLocaleUseCase`'s own kdoc) writes the LOCAL preference unconditionally, synchronously, before
 * any network call — the on-screen language swap `com.mentora.android.locale.LocalizedContent`
 * drives from `sdk.user.observeLocale()` therefore always succeeds regardless of connectivity. Only
 * the OPTIONAL server-side `preferredLocale` sync (authenticated users only, for cross-device
 * consistency) can fail, silently and best-effort, the same "secondary write, no user-facing error
 * surface" treatment this phase already gives `MyLearningViewModel`'s own secondary loads.
 */
class SettingsViewModel(
    val locale: StateFlow<AppLocale>,
    private val setLocale: suspend (AppLocale) -> ApiResult<Unit>,
    val theme: StateFlow<ThemePreference>,
    private val setTheme: (ThemePreference) -> Unit,
) : ViewModel() {

    fun onLanguageSelected(locale: AppLocale) {
        viewModelScope.launch { setLocale(locale) }
    }

    fun onThemeSelected(theme: ThemePreference) {
        setTheme(theme)
    }

    /** Plain [ViewModelProvider.Factory]. [themeController] is [com.mentora.android.MentoraApplication]'s
     *  one process-lifetime [ThemeController] instance (`UserFacade.setTheme` has no read-back path —
     *  see that class's own kdoc), not a fresh one constructed here — the Screen resolves it via
     *  `LocalContext.current.applicationContext`, same pattern [com.mentora.android.ui.aitutor
     *  .AiTutorScreen] already establishes for reaching an [android.app.Application]-scoped
     *  dependency from a NavBackStackEntry-scoped screen. */
    class Factory(private val sdk: MentoraSdk, private val themeController: ThemeController) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
            locale = sdk.user.observeLocale(),
            setLocale = sdk.user.setLocale::invoke,
            theme = themeController.theme,
            setTheme = themeController::setTheme,
        ) as T
    }
}
