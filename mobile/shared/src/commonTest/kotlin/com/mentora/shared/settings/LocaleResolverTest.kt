package com.mentora.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleResolverTest {

    @Test
    fun `a single Arabic regional tag resolves to Arabic`() {
        assertEquals(AppLocale.Arabic, resolveInitialLocale(listOf("ar-EG")))
    }

    @Test
    fun `an Arabic tag anywhere in the list resolves to Arabic, even if not first`() {
        assertEquals(AppLocale.Arabic, resolveInitialLocale(listOf("fr", "ar")))
    }

    @Test
    fun `a non-Arabic-only list resolves to English`() {
        assertEquals(AppLocale.English, resolveInitialLocale(listOf("fr")))
    }

    @Test
    fun `an empty list resolves to English`() {
        assertEquals(AppLocale.English, resolveInitialLocale(emptyList()))
    }

    @Test
    fun `multiple regional Arabic and non-Arabic tags still resolve to Arabic`() {
        assertEquals(AppLocale.Arabic, resolveInitialLocale(listOf("ar-SA", "en-US")))
    }

    @Test
    fun `matching is on the language subtag, not an exact string match`() {
        assertEquals(AppLocale.Arabic, resolveInitialLocale(listOf("AR-eg")))
    }
}
