package com.mentora.android.ui.certificates

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

/**
 * `design-system/LOCALIZATION.md § 7`: "Use the platform's locale-aware date formatter ... never a
 * hand-built date string." combined with `§ 8`'s locked "Western Arabic numerals ... everywhere,
 * including Arabic UI" rule. [java.time.format.DateTimeFormatter.ofLocalizedDate] is the real
 * platform formatter (localized word order/text per [locale]) — `.withDecimalStyle(STANDARD)` states
 * the intent to pin DIGIT rendering to Western numerals regardless of [locale]'s own numbering system,
 * the same "real formatter, digits pinned" reconciliation `PlayerControls.kt`'s `formatPlaybackTime`
 * already uses via a `Locale.US` pin (that one pins the whole format to English since a scrubber time
 * label has no translatable text at all; pinning the whole `Locale` here instead would lose real
 * translatable text at wider [FormatStyle]s than [FormatStyle.MEDIUM] — `withDecimalStyle` pins only
 * the numeral system, leaving whatever word-level localization the style/locale combination has intact).
 *
 * **Round-1 review finding (LOW), correcting this kdoc's own prior claim**: CLDR's `ar` MEDIUM pattern
 * (`dd‏/MM‏/yyyy`) is actually all-numeric — no month name exists to lose at THIS specific style, so
 * `withDecimalStyle` is not preserving a translated month name today. It still matters in principle
 * (a future switch to [FormatStyle.LONG]/[FormatStyle.FULL] would introduce real month-name text this
 * pin must not touch) and, separately, `.withDecimalStyle(DecimalStyle.STANDARD)` is ALSO verified to
 * be a genuine no-op on the current JDK: `DateTimeFormatterBuilder.toFormatter(Locale)` always
 * constructs with `DecimalStyle.STANDARD` already — `java.time` never derives `DecimalStyle` from the
 * locale at all (confirmed empirically: `ofLocalizedDate(MEDIUM).withLocale(Locale("ar"))` already
 * renders Western digits with no `withDecimalStyle` call). Kept anyway, explicitly, as intent-
 * documenting defensive code — the one thing standing between this function and Arabic-Indic digits if
 * a future JDK/AGP upgrade ever changes that default, and cheap enough to keep for that guarantee.
 *
 * java.time is part of the Android SDK itself starting at API 26 (this app's `minSdk`, confirmed in
 * `androidApp/build.gradle.kts`) — no desugaring, no new Gradle dependency.
 *
 * [iso] is [com.mentora.shared.domain.model.CertificateSummary.issuedAt]/
 * [com.mentora.shared.domain.model.CertificateDetail.issuedAt]/`.completionDateSnapshot` — a raw
 * ISO-8601 instant string (`shared` has no `kotlinx-datetime`-based domain-model convention for these
 * fields, per that model's own kdoc). No existing Android screen displays a raw-ISO-string date yet
 * (Profile/Settings, the only other candidate, is Task 18 — not built yet as of this task) — this is
 * this app's first such call site, built directly from the locked doc rules above rather than an
 * existing precedent to copy.
 */
internal fun formatCertificateDate(iso: String, locale: Locale): String = try {
    val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.STANDARD)
    date.format(formatter)
} catch (e: DateTimeParseException) {
    // Defensive only — the backend always emits a real ISO-8601 instant (never expected in practice).
    iso
}
