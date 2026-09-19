import XCTest
import Foundation
@testable import iosApp

// Phase 5 Task T7 slice 2 -- the in-target half of the catalog parity/specifier lint. The other half,
// `tools/ios-checks/catalog-parity.js`, is a Windows-runnable Node script that checks the SOURCE
// `Localizable.xcstrings` JSON against Android's `values(-ar)/strings.xml` in full detail (three-way
// key parity, per-key specifier-index parity, iOS specifier-conversion lint, `.xcstrings` structure,
// and a cross-check against `ErrorCopy.swift`'s own key set). This file deliberately does NOT
// re-implement any of that -- its only job is to catch divergence between the source JSON and what
// Xcode's build step actually PRODUCES in the compiled app bundle, which the Node script can never see
// (it never runs a build). See `execution/DECISIONS_LOG.md` D123 for slice 1's full account of why
// `LocalizedStringResource(_:locale:bundle:)` (mechanism 2 below) is the proven-working resolution path
// on this toolchain, and why the naive `String(localized:table:bundle:locale:)` form is NOT used here
// -- that mechanism is CI-confirmed broken for locale selection on this exact Xcode/iOS toolchain.
@MainActor
final class CatalogParityTests: XCTestCase {

    /// A representative sample spanning: a plain string with no format specifier (`nav_home`), a
    /// string with a single `%1$@` positional specifier (`error_state_retry_label` has none, so
    /// `quiz_progress_label` is used -- two specifiers, `%1$@`/`%2$@`), and one of the 5 keys that mix
    /// a real positional specifier with a literal-percent form (EN's escaped `%%`, AR's own `٪` glyph,
    /// U+066A) -- `course_player_progress_content_description`. Values copied verbatim from
    /// `mobile/androidApp/src/main/res/values(-ar)/strings.xml`, with the Android `%N$s` ->
    /// `%N$@` conversion already applied (this test never calls `String(format:)`, so the raw
    /// placeholder text is expected to survive unresolved, exactly as D123's own diagnostic test
    /// already established for `String(localized:)` generally).
    private struct Sample {
        let key: String
        let english: String
        let arabic: String
    }

    private let samples: [Sample] = [
        Sample(key: "nav_home", english: "Home", arabic: "الرئيسية"),
        Sample(
            key: "quiz_progress_label",
            english: "Question %1$@ of %2$@",
            arabic: "السؤال %1$@ من %2$@"
        ),
        Sample(
            key: "course_player_progress_content_description",
            english: "%1$@%% complete",
            arabic: "اكتمل %1$@٪"
        ),
    ]

    // MARK: - Compiled-catalog resolution, both locales, across the representative sample

    /// Asserts the COMPILED catalog (as it actually exists in the running test host's app bundle, via
    /// `Bundle.main`) resolves every sample key correctly under both `en` and `ar`, using the one
    /// mechanism D123's CI evidence proved actually honors an explicitly-injected locale on this
    /// toolchain: `LocalizedStringResource(_:locale:bundle:)` + `String(localized:)`. A pass here means
    /// the source `.xcstrings` JSON (already checked in exhaustive detail by
    /// `tools/ios-checks/catalog-parity.js`) and what Xcode's build step actually compiled into the
    /// test bundle agree, for at least this sample -- catching a whole class of divergence (stale
    /// derived data, a build-step string-catalog compiler regression, a project.yml resource-glob
    /// miss) the Node script can never see since it never runs a real build.
    func test_compiledCatalogResolvesSampleKeysInBothLocales() {
        let englishLocale = Locale(identifier: "en")
        let arabicLocale = Locale(identifier: MentoraThemeRules.arabicLocaleIdentifier)

        for sample in samples {
            let resolvedEnglish = resolvedValue(forKey: sample.key, locale: englishLocale)
            XCTAssertEqual(
                resolvedEnglish, sample.english,
                "Key \"\(sample.key)\" resolved to \"\(resolvedEnglish)\" under en, expected " +
                "\"\(sample.english)\" -- the compiled catalog may have diverged from the source " +
                "Localizable.xcstrings JSON."
            )

            let resolvedArabic = resolvedValue(forKey: sample.key, locale: arabicLocale)
            XCTAssertEqual(
                resolvedArabic, sample.arabic,
                "Key \"\(sample.key)\" resolved to \"\(resolvedArabic)\" under " +
                "\(MentoraThemeRules.arabicLocaleIdentifier), expected \"\(sample.arabic)\" -- the " +
                "compiled catalog may have diverged from the source Localizable.xcstrings JSON."
            )

            print("MENTORA-L10N: CatalogParityTests key=\(sample.key) en=\"\(resolvedEnglish)\" ar=\"\(resolvedArabic)\"")
        }
    }

    /// `app_name` deliberately carries no `ar` localization unit (matching Android, which has no
    /// `values-ar` entry for it either). Confirms the compiled catalog still resolves it under `en`
    /// without throwing/crashing, and separately exercises the AR-locale fallback path D123's own
    /// diagnostic test already characterized for this exact key (falls back to the English value on
    /// this toolchain) -- logged, not hard-asserted either way, since a fallback mechanism change here
    /// is Apple's to make, not this app's, and slice 1 already established this is non-actionable.
    func test_appNameCompiledResolutionUnderBothLocales() {
        let englishLocale = Locale(identifier: "en")
        let arabicLocale = Locale(identifier: MentoraThemeRules.arabicLocaleIdentifier)

        let resolvedEnglish = resolvedValue(forKey: "app_name", locale: englishLocale)
        XCTAssertEqual(resolvedEnglish, "Mentora")

        let resolvedArabicAttempt = resolvedValue(forKey: "app_name", locale: arabicLocale)
        print("MENTORA-L10N: CatalogParityTests app_name en=\"\(resolvedEnglish)\" arAttempt=\"\(resolvedArabicAttempt)\"")
    }

    // MARK: - Full compiled-catalog key enumeration -- deliberately NOT attempted

    /// A String Catalog (`.xcstrings`) compiles to an opaque, Apple-internal on-disk form -- unlike a
    /// legacy `.strings` file, there is no documented, stable public API to enumerate every key a
    /// compiled catalog resolves inside a given `.lproj` bundle at runtime (no `NSDictionary(contentsOf:)`
    /// equivalent exists for the modern String Catalog compiled format, and reverse-engineering the
    /// private on-disk representation would be exactly the kind of unstable, undocumented-API guess this
    /// project's own evidence-over-guessing discipline (`DECISIONS_LOG.md` D121/D123) argues against).
    /// Rather than guess at such an API, this test only logs that the attempt was deliberately not made,
    /// leaving full-catalog count verification (279 EN / 278 AR keys) to
    /// `tools/ios-checks/catalog-parity.js`'s Group A, which checks the SOURCE JSON directly and is
    /// authoritative for count/parity today.
    func test_fullCompiledCatalogEnumeration_notAttempted() {
        print(
            "MENTORA-L10N: CatalogParityTests full compiled-.lproj key enumeration was NOT attempted -- " +
            "no documented, stable public API exists to enumerate every key a compiled Xcode 15+ String " +
            "Catalog (.xcstrings) resolves at runtime (unlike a legacy .strings file's " +
            "NSDictionary(contentsOf:) idiom). Full 279/278 key-count parity is instead verified against " +
            "the SOURCE Localizable.xcstrings JSON by tools/ios-checks/catalog-parity.js's Group A; this " +
            "file only spot-checks that the actually-compiled bundle agrees with that source for a " +
            "representative sample (see the two tests above)."
        )
    }

    // MARK: - Private harness

    /// Resolves `key` under `locale` against the built app bundle via `LocalizedStringResource`, using
    /// `.atURL(Bundle.main.bundleURL)` -- the exact construction D123's CI evidence confirmed succeeds
    /// on this toolchain (`LocalizationPlumbingTests.swift`'s mechanism 2), deliberately NOT
    /// `String(localized:table:bundle:locale:)` (mechanism 1), which that same CI evidence confirmed
    /// does NOT honor its `locale:` argument here.
    private func resolvedValue(forKey key: String, locale: Locale) -> String {
        let resource = LocalizedStringResource(
            String.LocalizationValue(key),
            locale: locale,
            bundle: .atURL(Bundle.main.bundleURL)
        )
        return String(localized: resource)
    }
}
