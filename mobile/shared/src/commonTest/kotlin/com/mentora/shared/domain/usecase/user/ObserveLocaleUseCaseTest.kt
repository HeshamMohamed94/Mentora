package com.mentora.shared.domain.usecase.user

import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.FakePreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLocaleUseCaseTest {

    @Test
    fun `invoke exposes PreferenceStore's locale StateFlow, reflecting later writes`() {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val useCase = ObserveLocaleUseCase(preferenceStore)

        val flow = useCase()
        assertEquals(AppLocale.English, flow.value)

        preferenceStore.setLocale(AppLocale.Arabic)

        assertEquals(AppLocale.Arabic, flow.value)
    }
}
