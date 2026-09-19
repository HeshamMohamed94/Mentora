import XCTest
import SwiftUI
import UIKit
import shared
@testable import iosApp

// Phase 5 Task T6 slice 3b -- theme-root wiring (`Theme/MentoraTheme.swift`). Covers the pure rules in
// `MentoraThemeRules` (color scheme / locale / layout-direction mapping), the cross-slice integration
// with slice 1's `MentoraTypographyRules.isArabic(_:)`, and a real environment-propagation round-trip
// through `.mentoraTheme(...)` via the same `UIHostingController` harness pattern established by
// `MentoraTypographyGeometryTests.swift`.
//
// WHAT THIS FILE DELIBERATELY DOES NOT TEST: a round-trip of `.preferredColorScheme` itself reaching
// `\.colorScheme`. `.preferredColorScheme` is a SwiftUI PREFERENCE, not a plain environment write -- it
// propagates UP to the enclosing presentation/window (the mechanism `\.locale`/`\.layoutDirection`'s
// `.transformEnvironment` do not use). A bare, unattached `UIHostingController` in a unit test has no
// real window/scene to receive that preference, so any such round-trip here would not be reliably
// meaningful and would risk becoming a future flake rather than a real regression guard. This is
// intentionally left to code review (W) plus the MC-3 live device/simulator check
// (`PHASE_5_IOS_SYSTEM_DESIGN.md § 14`), not to this test file.
@MainActor
final class MentoraThemeTests: XCTestCase {

    // MARK: - 1. colorScheme(for:) mapping

    func test_colorSchemeMapping() {
        XCTAssertEqual(MentoraThemeRules.colorScheme(for: .light), .light)
        XCTAssertEqual(MentoraThemeRules.colorScheme(for: .dark), .dark)
        XCTAssertNil(MentoraThemeRules.colorScheme(for: .system),
            ".system must map to nil so .preferredColorScheme(nil) keeps following live OS appearance changes")
    }

    // MARK: - 2. Enum case-count guard

    /// Guards the exhaustive, `default:`-free switches in `MentoraThemeRules` against a future Kotlin
    /// case silently going unmapped. If either count ever changes, `MentoraThemeRules` must be updated
    /// to handle the new case FIRST (which will itself fail to compile until done, since none of its
    /// switches has a `default:` clause) -- this test is a second, independent guard for anyone who
    /// only reads test failures rather than compiler errors.
    func test_enumCaseCountsGuardAgainstUnmappedFutureCases() {
        XCTAssertEqual(ThemePreference.allCases.count, 3,
            "ThemePreference gained/lost a case -- MentoraThemeRules.colorScheme(for:) must be updated to handle it")
        XCTAssertEqual(AppLocale.allCases.count, 2,
            "AppLocale gained/lost a case -- MentoraThemeRules.foundationLocale(for:)/layoutDirection(for:) must be updated to handle it")
    }

    // MARK: - 3. layoutDirection(for:) mapping

    func test_layoutDirectionMapping() {
        XCTAssertEqual(MentoraThemeRules.layoutDirection(for: .arabic), .rightToLeft)
        XCTAssertEqual(MentoraThemeRules.layoutDirection(for: .english), .leftToRight)
    }

    // MARK: - 4. Arabic locale keeps the "ar" language subtag (cross-slice integration)

    /// `ar-u-nu-latn`'s `-u-nu-latn` suffix is a Unicode locale EXTENSION (numbering system), not a
    /// language-subtag change -- `language.languageCode` must still read `"ar"`. This is asserted both
    /// directly and via slice 1's real `MentoraTypographyRules.isArabic(_:)` (`Theme/MentoraTypography.swift`)
    /// so a typo in `arabicLocaleIdentifier` that broke Arabic detection would be caught here, not only
    /// discovered later via a typography-side symptom.
    func test_arabicLocaleKeepsArabicLanguageSubtag() {
        let arabicLocale = MentoraThemeRules.foundationLocale(for: .arabic)
        XCTAssertEqual(arabicLocale.language.languageCode?.identifier, "ar")

        XCTAssertTrue(MentoraTypographyRules.isArabic(arabicLocale),
            "MentoraThemeRules.foundationLocale(for: .arabic) must keep MentoraTypographyRules.isArabic(_:) true")
        XCTAssertFalse(MentoraTypographyRules.isArabic(MentoraThemeRules.foundationLocale(for: .english)),
            "MentoraThemeRules.foundationLocale(for: .english) must keep MentoraTypographyRules.isArabic(_:) false")
    }

    // MARK: - 5. Arabic locale forces Western (ASCII) numerals

    /// `NumberFormatter` is the real Foundation API that respects a `Locale`'s numbering-system
    /// extension (`-u-nu-latn`) when formatting -- unlike, e.g., `String(format:)`, which does not
    /// consult `Locale` for digit shaping at all. A bare `"ar"` locale would render this same integer
    /// with Eastern Arabic-Indic digits (٠-٩); `arabicLocaleIdentifier`'s `-u-nu-latn` suffix exists
    /// specifically to prevent that (`design-system/LOCALIZATION.md § 8`).
    func test_arabicLocaleForcesWesternNumerals() {
        let value = 1_234_567_890

        let formatter = NumberFormatter()
        formatter.locale = MentoraThemeRules.foundationLocale(for: .arabic)
        formatter.numberStyle = .none
        formatter.usesGroupingSeparator = false

        let formatted = formatter.string(from: NSNumber(value: value))
        XCTAssertEqual(formatted, String(value),
            "Arabic locale (ar-u-nu-latn) must format digits identically to plain ASCII -- got \(formatted ?? "nil")")

        let easternArabicDigits = CharacterSet(charactersIn: "٠١٢٣٤٥٦٧٨٩")
        XCTAssertNil(formatted?.unicodeScalars.first { easternArabicDigits.contains($0) },
            "Formatted output must contain no Eastern Arabic-Indic digit characters")

        // Negative control: proves the `-u-nu-latn` extension is actually doing something, rather
        // than this test passing merely because the current platform's bare "ar" already renders
        // ASCII digits. If this ever stops finding an Eastern Arabic-Indic digit, the positive
        // assertions above are no longer a real regression guard for the identifier itself.
        let bareArabicFormatter = NumberFormatter()
        bareArabicFormatter.locale = Locale(identifier: "ar")
        bareArabicFormatter.numberStyle = .none
        bareArabicFormatter.usesGroupingSeparator = false
        let bareFormatted = bareArabicFormatter.string(from: NSNumber(value: value))
        XCTAssertNotNil(bareFormatted?.unicodeScalars.first { easternArabicDigits.contains($0) },
            "Plain \"ar\" (no -u-nu-latn) is expected to render Eastern Arabic-Indic digits on this " +
            "platform -- if it no longer does, this test's positive assertions no longer prove the " +
            "numbering-system extension is doing anything")
    }

    // MARK: - 6. \.locale / \.layoutDirection propagate through .mentoraTheme(...)

    /// Reuses the exact `UIHostingController` + `setNeedsLayout()`/`layoutIfNeeded()` harness pattern
    /// established by `MentoraTypographyGeometryTests.swift`'s `Geometry.measure(_:)`, adapted here to
    /// record `@Environment` values (via a reference-type sink written from the probe's `body`) rather
    /// than measure geometry.
    func test_localeEnvironmentPropagatesThroughMentoraTheme() {
        let arabicSink = probeEnvironment(theme: .light, locale: .arabic)
        XCTAssertEqual(arabicSink.locale?.identifier, MentoraThemeRules.foundationLocale(for: .arabic).identifier)
        XCTAssertEqual(arabicSink.layoutDirection, .rightToLeft)

        let englishSink = probeEnvironment(theme: .light, locale: .english)
        XCTAssertEqual(englishSink.locale?.identifier, MentoraThemeRules.foundationLocale(for: .english).identifier)
        XCTAssertEqual(englishSink.layoutDirection, .leftToRight)

        // nil locale must leave both environment values at their un-transformed default -- i.e.
        // identical to a probe hosted with no .mentoraTheme(...) applied at all.
        let nilSink = probeEnvironment(theme: .light, locale: nil)
        let bareSink = probeEnvironmentBare()
        // Proves the harness itself is live for this leg first -- without this, a probe whose body
        // never ran (e.g. an unattached-hierarchy layout regression) would make both sinks equally
        // nil and the two assertions below would pass vacuously instead of catching anything.
        XCTAssertNotNil(bareSink.locale, "EnvironmentProbe.body did not run -- harness is not live")
        XCTAssertEqual(nilSink.locale?.identifier, bareSink.locale?.identifier,
            "nil locale must leave \\.locale untransformed")
        XCTAssertEqual(nilSink.layoutDirection, bareSink.layoutDirection,
            "nil locale must leave \\.layoutDirection untransformed")
    }

    // MARK: - Private harness

    @MainActor
    private func probeEnvironment(theme: ThemePreference, locale: AppLocale?) -> EnvironmentSink {
        let sink = EnvironmentSink()
        let host = UIHostingController(
            rootView: EnvironmentProbe(sink: sink).mentoraTheme(theme: theme, locale: locale)
        )
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        _ = host.sizeThatFits(in: CGSize(width: 200, height: 200))
        return sink
    }

    @MainActor
    private func probeEnvironmentBare() -> EnvironmentSink {
        let sink = EnvironmentSink()
        let host = UIHostingController(rootView: EnvironmentProbe(sink: sink))
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        _ = host.sizeThatFits(in: CGSize(width: 200, height: 200))
        return sink
    }
}

/// Reference-type sink so `EnvironmentProbe.body` (a `struct` `View`) can record the `@Environment`
/// values it reads during a real SwiftUI render pass, for the test to inspect afterward.
@MainActor
private final class EnvironmentSink {
    var locale: Locale?
    var layoutDirection: LayoutDirection?
}

private struct EnvironmentProbe: View {
    let sink: EnvironmentSink

    @Environment(\.locale) private var locale
    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        sink.locale = locale
        sink.layoutDirection = layoutDirection
        return Color.clear.frame(width: 10, height: 10)
    }
}
