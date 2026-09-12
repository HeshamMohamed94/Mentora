package com.mentora.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class FakePreferenceStoreTest {

    @Test
    fun `defaults to English locale and System theme`() {
        val store = FakePreferenceStore()

        assertEquals(AppLocale.English, store.locale.value)
        assertEquals(ThemePreference.System, store.getTheme())
    }

    @Test
    fun `setLocale then reading the flow's value round-trips what was set`() {
        val store = FakePreferenceStore()

        store.setLocale(AppLocale.Arabic)

        assertEquals(AppLocale.Arabic, store.locale.value)
    }

    @Test
    fun `setLocale emits the new value on the observable locale flow`() {
        val store = FakePreferenceStore(initialLocale = AppLocale.English)
        val emissions = mutableListOf<AppLocale>()
        emissions += store.locale.value

        store.setLocale(AppLocale.Arabic)
        emissions += store.locale.value

        assertEquals(listOf(AppLocale.English, AppLocale.Arabic), emissions)
    }

    @Test
    fun `setTheme then getTheme round-trips what was set`() {
        val store = FakePreferenceStore()

        store.setTheme(ThemePreference.Dark)

        assertEquals(ThemePreference.Dark, store.getTheme())
    }

    @Test
    fun `constructing with an initial locale and theme seeds both`() {
        val store = FakePreferenceStore(initialLocale = AppLocale.Arabic, initialTheme = ThemePreference.Light)

        assertEquals(AppLocale.Arabic, store.locale.value)
        assertEquals(ThemePreference.Light, store.getTheme())
    }
}
