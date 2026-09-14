package com.mentora.android.ui.profile

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * T18 — [SettingsViewModel]'s language/theme selection logic, as a plain JVM unit test. Both
 * [SettingsViewModel.locale]/[SettingsViewModel.theme] are plain [MutableStateFlow]s here (not
 * [kotlinx.coroutines.flow.StateFlow] wrapped by production code's own real
 * `sdk.user.observeLocale()`/`ThemeController.theme`) — sufficient to prove this class's own
 * selection-forwarding logic without needing `shared`'s real facade wiring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onLanguageSelected_callsSetLocale_withTheSelectedValue() = runTest(testDispatcher) {
        var capturedLocale: AppLocale? = null
        val viewModel = SettingsViewModel(
            locale = MutableStateFlow(AppLocale.English),
            setLocale = { locale -> capturedLocale = locale; ApiResult.Success(Unit) },
            theme = MutableStateFlow(ThemePreference.System),
            setTheme = {},
        )

        viewModel.onLanguageSelected(AppLocale.Arabic)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AppLocale.Arabic, capturedLocale)
    }

    @Test
    fun onLanguageSelected_networkFailure_neverThrows_theLocalSwitchAlreadyHappenedInSetLocaleItself() = runTest(testDispatcher) {
        // `SetLocaleUseCase`'s own kdoc: the local `PreferenceStore` write happens unconditionally
        // BEFORE any network call, so a `Failure` return here represents only the optional
        // cross-device sync failing — this class must swallow it silently, never crash or retry.
        val viewModel = SettingsViewModel(
            locale = MutableStateFlow(AppLocale.English),
            setLocale = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
            theme = MutableStateFlow(ThemePreference.System),
            setTheme = {},
        )

        viewModel.onLanguageSelected(AppLocale.Arabic)
        testDispatcher.scheduler.advanceUntilIdle()
        // No exception propagated out of `advanceUntilIdle()` is itself the assertion.
    }

    @Test
    fun onThemeSelected_callsSetTheme_withTheSelectedValue() {
        var capturedTheme: ThemePreference? = null
        val viewModel = SettingsViewModel(
            locale = MutableStateFlow(AppLocale.English),
            setLocale = { ApiResult.Success(Unit) },
            theme = MutableStateFlow(ThemePreference.System),
            setTheme = { theme -> capturedTheme = theme },
        )

        viewModel.onThemeSelected(ThemePreference.Dark)

        assertEquals(ThemePreference.Dark, capturedTheme)
    }
}
