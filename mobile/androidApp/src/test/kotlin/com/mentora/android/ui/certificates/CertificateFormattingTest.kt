package com.mentora.android.ui.certificates

import java.time.format.DecimalStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T15 — [formatCertificateDate]'s own kdoc: a real locale-aware platform formatter (localized month
 * names) with digit rendering pinned to Western numerals regardless of locale
 * (`design-system/LOCALIZATION.md §§ 7-8`).
 */
class CertificateFormattingTest {

    @Test
    fun formatsARealIso8601Instant_inEnglish() {
        val formatted = formatCertificateDate("2026-01-12T10:30:00Z", Locale.US)
        // Exact day-of-month depends on the host machine's local timezone offset from the fixed UTC
        // instant above (never the whole calendar date, which is deterministic to within one day) — a
        // loose "does it contain the right year and no Arabic-Indic digits" assertion is what matters
        // here, not a byte-exact string (avoids a timezone-flaky CI assertion).
        assertTrue("Expected the year to appear: $formatted", formatted.contains("2026"))
        assertOnlyWesternDigits(formatted)
    }

    @Test
    fun pinsWesternNumerals_evenUnderAnArabicLocale() {
        val formatted = formatCertificateDate("2026-01-12T10:30:00Z", Locale.forLanguageTag("ar"))
        assertOnlyWesternDigits(formatted)
    }

    /**
     * Round-1 review finding (LOW): the test above passes even on `java.time` builds where
     * `.withDecimalStyle(DecimalStyle.STANDARD)` (`CertificateFormatting.kt`'s own defensive pin) is a
     * complete no-op — `DateTimeFormatterBuilder.toFormatter(Locale)` always constructs with
     * `DecimalStyle.STANDARD` already, so `withLocale("ar")` alone never actually introduces
     * Arabic-Indic digits on this JDK. This control test makes that concrete rather than leaving the
     * "why does the pin matter" question just asserted in a kdoc: `DecimalStyle.of(Locale("ar"))` (the
     * locale's OWN native numbering system, obtained directly rather than through a `DateTimeFormatter`)
     * genuinely does carry Arabic-Indic digits — proving the pin guards against something real, even if
     * `java.time` doesn't currently need to be told.
     */
    @Test
    fun arabicLocalesOwnDecimalStyle_genuinelyUsesArabicIndicDigits_provingThePinGuardsSomethingReal() {
        val arabicZeroDigit = DecimalStyle.of(Locale.forLanguageTag("ar")).zeroDigit
        assertNotEquals("Expected the Arabic locale's own zero digit to differ from Western '0'", '0', arabicZeroDigit)
    }

    @Test
    fun malformedInput_fallsBackToTheRawString_ratherThanCrashing() {
        val formatted = formatCertificateDate("not-a-real-date", Locale.US)
        assertEquals("not-a-real-date", formatted)
    }

    private fun assertOnlyWesternDigits(text: String) {
        val hasNonWesternDigit = text.any { it.isDigit() && it !in '0'..'9' }
        assertFalse("Expected only Western 0-9 digits, got: $text", hasNonWesternDigit)
    }
}
