package com.mentora.android.viewmodel

import com.mentora.shared.settings.AppLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T19 — [reloadOnLocaleChange]'s own unit test, isolated from any one ViewModel's real load logic —
 * see that function's own kdoc for the `.drop(1)` rationale this test locks in by name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocaleReloadTest {

    @Test
    fun collectingTheCurrentValueAtSubscriptionTime_doesNotTriggerAReload() = runTest {
        val locale = MutableStateFlow(AppLocale.English)
        var reloadCount = 0

        TestScope(StandardTestDispatcher(testScheduler)).reloadOnLocaleChange(observeLocale = { locale }) { reloadCount++ }
        testScheduler.advanceUntilIdle()

        assertEquals(0, reloadCount)
    }

    @Test
    fun eachSubsequentLocaleChange_triggersExactlyOneReload() = runTest {
        val locale = MutableStateFlow(AppLocale.English)
        var reloadCount = 0

        TestScope(StandardTestDispatcher(testScheduler)).reloadOnLocaleChange(observeLocale = { locale }) { reloadCount++ }
        testScheduler.advanceUntilIdle()

        locale.value = AppLocale.Arabic
        testScheduler.advanceUntilIdle()
        assertEquals(1, reloadCount)

        locale.value = AppLocale.English
        testScheduler.advanceUntilIdle()
        assertEquals(2, reloadCount)
    }

    @Test
    fun settingTheSameLocaleValueAgain_isNotANewEmission_soItDoesNotReload() = runTest {
        // StateFlow's own conflation contract (distinctUntilChanged-by-equality) — asserted here so a
        // future StateFlow -> plain Flow refactor of the locale source would be caught by this test.
        val locale = MutableStateFlow(AppLocale.English)
        var reloadCount = 0

        TestScope(StandardTestDispatcher(testScheduler)).reloadOnLocaleChange(observeLocale = { locale }) { reloadCount++ }
        testScheduler.advanceUntilIdle()

        locale.value = AppLocale.English
        testScheduler.advanceUntilIdle()

        assertEquals(0, reloadCount)
    }
}
