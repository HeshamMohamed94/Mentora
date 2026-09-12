package com.mentora.shared.domain.usecase.user

import com.mentora.shared.settings.FakePreferenceStore
import com.mentora.shared.settings.ThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals

class SetThemeUseCaseTest {

    @Test
    fun `invoke writes the theme to PreferenceStore`() {
        val preferenceStore = FakePreferenceStore(initialTheme = ThemePreference.System)
        val useCase = SetThemeUseCase(preferenceStore)

        useCase(ThemePreference.Dark)

        assertEquals(ThemePreference.Dark, preferenceStore.getTheme())
    }

    // SetThemeUseCase's constructor takes only a PreferenceStore — no ApiClient/repository
    // dependency exists on this use case at all (see its kdoc), so there is no MockEngine/network
    // request count to assert here: a network call is structurally impossible, not merely unused
    // in this test, which is the stronger guarantee `execution/PHASE_3_KMP_PLAN.md` Task 6 asks for.
}
