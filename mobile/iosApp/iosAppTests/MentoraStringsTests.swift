import XCTest
import shared
@testable import iosApp

// Phase 5 Task T7 slice 3 -- `MentoraStrings`/`MentoraFormatters` (`Support/MentoraStrings.swift`,
// `Support/Formatters.swift`), the sole sanctioned string-resolution/formatting API for this app
// target going forward. Mirrors `CatalogParityTests.swift`'s house style (a small `Sample` struct,
// values copied verbatim from the actually-authored `Localizable.xcstrings` catalog, `MENTORA-L10N:`
// print prefix per D121/D123's evidence-over-guessing convention) and `MentoraThemeTests.swift`'s
// negative-control-locale pattern for the numeral-forcing regression guard (H6).
@MainActor
final class MentoraStringsTests: XCTestCase {

    // MARK: - H4: resolution follows the INJECTED locale, never Locale.current / the device locale

    /// `nav_home`'s values, copied verbatim from `Localizable.xcstrings` (identical to
    /// `CatalogParityTests.swift`'s own sample for this key).
    private let englishNavHome = "Home"
    private let arabicNavHome = "الرئيسية"

    /// The whole point of `MentoraStrings.text(_:locale:)` requiring an explicit `AppLocale` parameter
    /// is that resolution follows THAT argument, never `Locale.current`/the simulator's actual device
    /// locale (H4) -- this is the structural fix for the exact bug class D93 (Phase 4/Android) found
    /// once already. This test resolves `nav_home` as `.arabic` while running under whatever the CI
    /// simulator's own locale actually is (expected to be `en`, logged rather than hard-asserted --
    /// this test's real claim does not depend on that expectation being exactly right) and confirms the
    /// Arabic value comes back regardless of what `Locale.current` reports.
    func test_resolutionFollowsInjectedLocaleNotDeviceLocale() {
        print("MENTORA-L10N: MentoraStringsTests Locale.current=\(Locale.current.identifier) " +
              "(CI simulator default; expected \"en\" -- not hard-asserted, since this test's real " +
              "claim holds regardless of what the device locale actually is)")

        let resolvedArabic = MentoraStrings.text("nav_home", locale: .arabic)
        XCTAssertEqual(resolvedArabic, arabicNavHome,
            "MentoraStrings.text(_:locale: .arabic) must resolve \"nav_home\" to the Arabic value " +
            "regardless of Locale.current (\(Locale.current.identifier)) -- if this fails while " +
            "Locale.current is \"en\", resolution has silently fallen back to reading the device/" +
            "environment locale instead of the explicitly injected AppLocale argument, reintroducing " +
            "the exact D93 bug class H4 exists to make unrepresentable.")

        let resolvedEnglish = MentoraStrings.text("nav_home", locale: .english)
        XCTAssertEqual(resolvedEnglish, englishNavHome)

        print("MENTORA-L10N: MentoraStringsTests nav_home en=\"\(resolvedEnglish)\" ar=\"\(resolvedArabic)\"")
    }

    // MARK: - Formatted lookup: positional substitution AND "%%" -> "%" collapsing

    /// `String(format:locale:arguments:)` under an RTL (`ar`) locale wraps each substituted `%@`
    /// argument in Unicode bidirectional-isolate marks (U+2068 FIRST STRONG ISOLATE / U+2069 POP
    /// DIRECTIONAL ISOLATE) -- real, CI-observed Foundation behavior (run 35459405076's first attempt
    /// failed on the two tests below with e.g. `"اكتمل \u{2068}75\u{2069}٪"`, not the plain-digit
    /// literal originally assumed), not a bug: it is Apple's documented mechanism for keeping an
    /// embedded LTR run (Western-numeral digits) from visually disordering the surrounding RTL text,
    /// and iOS RTL screens want this. Rather than hardcode the exact isolate characters into every
    /// expected literal (brittle -- Apple has changed which isolate character it uses in exactly this
    /// scenario across OS versions), assertions below compare AFTER stripping bidi control characters,
    /// so the check stays focused on the actual substituted content and word order.
    private func strippingBidiControlCharacters(_ value: String) -> String {
        let bidiControls = CharacterSet(charactersIn: "\u{2066}\u{2067}\u{2068}\u{2069}\u{200E}\u{200F}")
        return String(String.UnicodeScalarView(value.unicodeScalars.filter { !bidiControls.contains($0) }))
    }

    /// `course_player_top_bar_meta` -- verified by reading `Localizable.xcstrings` directly, not
    /// assumed -- carries three positional arguments AND, in its EN value only, the literal "%%" escape
    /// mixed with a real substitution: EN `"Lesson %1$@ of %2$@ · %3$@%%"`, AR
    /// `"الدرس %1$@ من %2$@ · %3$@٪"` (AR uses the literal Arabic percent-sign glyph U+066A directly,
    /// no "%%" needed there -- exactly the same EN-escapes/AR-literal-glyph mix D124's
    /// `course_player_progress_content_description` sample already established for
    /// `CatalogParityTests.swift`). This is exactly the key `MentoraStrings.text(_:locale:_:)`'s doc
    /// comment names as a representative example.
    func test_formattedLookupSubstitutesArgumentsAndCollapsesLiteralPercent() {
        let resolvedEnglish = MentoraStrings.text("course_player_top_bar_meta", locale: .english, "1", "2", "50")
        XCTAssertEqual(resolvedEnglish, "Lesson 1 of 2 · 50%",
            "Expected \"%1$@\"/\"%2$@\"/\"%3$@\" substituted positionally and EN's literal \"%%\" " +
            "collapsed to a single \"%\" -- got \"\(resolvedEnglish)\"")
        XCTAssertFalse(resolvedEnglish.contains("%%"),
            "The formatted-lookup overload must collapse the catalog's literal \"%%\" escape to a " +
            "single \"%\" via String(format:locale:arguments:) -- \"%%\" survived unprocessed")

        let resolvedArabic = MentoraStrings.text("course_player_top_bar_meta", locale: .arabic, "1", "2", "50")
        XCTAssertEqual(strippingBidiControlCharacters(resolvedArabic), "الدرس 1 من 2 · 50٪",
            "Expected the Arabic value's positional arguments substituted (bidi-isolate marks around " +
            "each digit run stripped before comparing -- see this file's own note above; AR carries no " +
            "\"%%\" to collapse, it already uses the literal Arabic percent-sign glyph U+066A) -- got " +
            "\"\(resolvedArabic)\" (raw, un-stripped)")

        print("MENTORA-L10N: MentoraStringsTests course_player_top_bar_meta " +
              "en=\"\(resolvedEnglish)\" ar=\"\(resolvedArabic)\"")
    }

    /// A second, simpler formatted-lookup sample -- a single-argument key mixing one positional
    /// substitution with EN's literal "%%" (`my_learning_percent_complete`, values verified by reading
    /// `Localizable.xcstrings`: EN `"%1$@%% complete"`, AR `"اكتمل %1$@٪"`) -- covering the single-arg
    /// shape the three-arg test above does not.
    func test_formattedLookupSingleArgumentPercentKey() {
        let resolvedEnglish = MentoraStrings.text("my_learning_percent_complete", locale: .english, "75")
        XCTAssertEqual(resolvedEnglish, "75% complete")

        let resolvedArabic = MentoraStrings.text("my_learning_percent_complete", locale: .arabic, "75")
        XCTAssertEqual(strippingBidiControlCharacters(resolvedArabic), "اكتمل 75٪",
            "Bidi-isolate marks around the substituted digit run stripped before comparing -- see this " +
            "file's own note above this test group. Got \"\(resolvedArabic)\" (raw, un-stripped)")

        print("MENTORA-L10N: MentoraStringsTests my_learning_percent_complete " +
              "en=\"\(resolvedEnglish)\" ar=\"\(resolvedArabic)\"")
    }

    // MARK: - H6: the count formatter never produces an Eastern Arabic-Indic digit under .arabic

    /// Positive assertion: `MentoraFormatters.count(_:locale: .arabic)` must render Western/ASCII
    /// digits only -- reusing `MentoraThemeRules.foundationLocale(for:)`'s `-u-nu-latn` override, the
    /// exact same mechanism `MentoraThemeTests.swift`'s `test_arabicLocaleForcesWesternNumerals`
    /// (D121) already regression-guards for the typography/theme layer.
    ///
    /// Negative control: proves the underlying `Locale` actually WOULD produce Eastern Arabic-Indic
    /// digits under an unmodified Arabic numbering-system request, so the positive assertion above
    /// cannot be passing vacuously (e.g. because the detector itself is broken, or because Apple's ICU
    /// never shapes digits at all regardless of locale). Uses `"ar@numbers=arab"` -- D121's
    /// `MentoraThemeTests.swift` (`test_arabicLocaleForcesWesternNumerals`'s diagnostic block) already
    /// found `Locale(identifier: "ar-u-nu-arab")` malformed on this toolchain and `"ar@numbers=arab"`
    /// (the legacy ICU keyword form) CI-proven to actually produce Eastern digits -- reused verbatim
    /// here rather than re-derived, per this slice's "do not re-guess a mechanism another slice already
    /// proved" discipline.
    func test_countFormatterNeverProducesEasternArabicIndicDigitsUnderArabic() {
        let value = 1_234_567_890
        let easternArabicDigits = CharacterSet(charactersIn: "٠١٢٣٤٥٦٧٨٩")

        // Non-vacuity guard on the detector itself, mirroring MentoraThemeTests.swift's own guard.
        XCTAssertNotNil("١٢٣٤٥٦٧٨٩٠".unicodeScalars.first { easternArabicDigits.contains($0) },
            "Eastern Arabic-Indic detector is broken -- it must match U+0660-U+0669")
        XCTAssertNil(String(value).unicodeScalars.first { easternArabicDigits.contains($0) },
            "Eastern Arabic-Indic detector is broken -- it must not match ASCII digits")

        // Positive assertion: production's real Arabic-locale count formatting.
        let formatted = MentoraFormatters.count(value, locale: .arabic)
        XCTAssertNil(formatted.unicodeScalars.first { easternArabicDigits.contains($0) },
            "MentoraFormatters.count(_:locale: .arabic) must never render an Eastern Arabic-Indic " +
            "digit (H6) -- got \"\(formatted)\"")

        // Negative control: an unmodified "ar@numbers=arab" locale genuinely produces Eastern digits
        // for the identical value, proving the positive assertion above is not vacuous.
        let negativeControlFormatter = NumberFormatter()
        negativeControlFormatter.numberStyle = .decimal
        negativeControlFormatter.locale = Locale(identifier: "ar@numbers=arab")
        negativeControlFormatter.usesGroupingSeparator = false
        negativeControlFormatter.minimumFractionDigits = 0
        negativeControlFormatter.maximumFractionDigits = 0
        let negativeControlOutput = negativeControlFormatter.string(from: NSNumber(value: value)) ?? "<nil>"
        XCTAssertNotNil(negativeControlOutput.unicodeScalars.first { easternArabicDigits.contains($0) },
            "Negative control \"ar@numbers=arab\" was expected to produce Eastern Arabic-Indic digits " +
            "for \(value) (per D121's own CI evidence) -- got \"\(negativeControlOutput)\", which would " +
            "make the positive assertion above vacuous rather than a real regression guard")

        print("MENTORA-L10N: MentoraStringsTests count(\(value), .arabic)=\"\(formatted)\" " +
              "negativeControl(ar@numbers=arab)=\"\(negativeControlOutput)\"")
    }

    /// English-locale sanity check -- grouping separators follow locale convention
    /// (`design-system/LOCALIZATION.md § 7`'s "Numbers / counts" row, e.g. "1,234 students"), and no
    /// Eastern digit appears here either (there is nothing Arabic-specific about this leg; it exists so
    /// a regression that broke `.decimal` styling generally, not just the Arabic branch, is still
    /// caught by this file).
    func test_countFormatterEnglishLocaleUsesDecimalGrouping() {
        let formatted = MentoraFormatters.count(1234, locale: .english)
        XCTAssertEqual(formatted, "1,234")
    }

    // MARK: - Date formatter: locale-explicit, never Locale.current

    /// Confirms `MentoraFormatters.date(_:style:locale:)` actually routes through the requested
    /// `AppLocale` (via distinguishable EN/AR month-name output) rather than any ambient locale --
    /// the same H4-shaped guarantee `MentoraStrings` provides, applied to the date formatter.
    func test_dateFormatterIsLocaleExplicit() {
        var components = DateComponents()
        components.year = 2026
        components.month = 9
        components.day = 3
        components.hour = 12 // noon UTC, not midnight -- MentoraFormatters.date does not set a
        // timeZone (uses TimeZone.current), so this test's assertions (year present, EN != AR,
        // no Eastern digits) are what's actually guaranteed regardless of the CI runner's own
        // configured time zone; picking noon rather than midnight UTC is a best-effort nudge to
        // also keep the rendered CALENDAR DAY itself "Sep 3" everywhere short of an extreme
        // (>12h) offset runner, not a hard guarantee this test depends on.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let date = calendar.date(from: components)!

        let english = MentoraFormatters.date(date, style: .medium, locale: .english)
        let arabic = MentoraFormatters.date(date, style: .medium, locale: .arabic)

        XCTAssertTrue(english.contains("2026"))
        XCTAssertTrue(arabic.contains("2026"))
        XCTAssertNotEqual(english, arabic,
            "The EN and AR renderings of the same date must differ (different month-name script) -- " +
            "identical output would suggest the formatter is not actually locale-driven")

        let easternArabicDigits = CharacterSet(charactersIn: "٠١٢٣٤٥٦٧٨٩")
        XCTAssertNil(arabic.unicodeScalars.first { easternArabicDigits.contains($0) },
            "MentoraFormatters.date(_:style:locale: .arabic) must never render an Eastern Arabic-Indic " +
            "digit (H6) -- got \"\(arabic)\"")

        print("MENTORA-L10N: MentoraStringsTests date(2026-09-03) en=\"\(english)\" ar=\"\(arabic)\"")
    }

    // MARK: - Sanity: MentoraStrings / MentoraFormatters never read Locale.current internally
    //
    // Neither `Support/MentoraStrings.swift` nor `Support/Formatters.swift` references `Locale.current`
    // or `Locale.autoupdatingCurrent` anywhere -- confirmed by reading both files, which is the
    // authoritative check for a two-file, ~100-line surface at this slice (an automated grep gate over
    // the whole target is slice 4's `localization-checks.js` job, not this test's). The runtime tests
    // above are this file's closest automatable proxy: `test_resolutionFollowsInjectedLocaleNotDeviceLocale`
    // would fail the moment `MentoraStrings.text` read `Locale.current` instead of its `locale:`
    // argument on any CI simulator whose default locale is not Arabic (true for every simulator this
    // project's CI has ever used), and `test_countFormatterNeverProducesEasternArabicIndicDigitsUnderArabic`
    // /`test_dateFormatterIsLocaleExplicit` would similarly fail to show Arabic-specific behavior (the
    // `-u-nu-latn` Western-numeral override, distinguishable AR month names) if either formatter secretly
    // ignored its `locale:` argument in favor of the device locale.
}
