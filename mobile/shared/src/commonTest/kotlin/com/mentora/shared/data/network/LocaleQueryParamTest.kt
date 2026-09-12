package com.mentora.shared.data.network

import com.mentora.shared.settings.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleQueryParamTest {

    @Test
    fun `maps English to the en wire value under the language key`() {
        assertEquals("language" to "en", localeQueryParam(AppLocale.English))
    }

    @Test
    fun `maps Arabic to the ar wire value under the language key`() {
        assertEquals("language" to "ar", localeQueryParam(AppLocale.Arabic))
    }
}
