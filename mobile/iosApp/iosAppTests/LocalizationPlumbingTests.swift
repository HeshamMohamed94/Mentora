import XCTest
import SwiftUI
import UIKit
@testable import iosApp

// Phase 5 Task T7 slice 1 -- Localization foundation PROBE. This slice exists to resolve exactly two
// unknowns before committing to the full 279-key catalog port and a public `MentoraStrings` API:
// (1) does `ar.lproj` actually land in the built app bundle from a 4-key `Localizable.xcstrings`
//     (i.e. does XcodeGen's directory-glob `sources:` pick the new catalog up, and does
//     `xcstringstool`/the build system compile it the way the design assumes), and
// (2) which of the plausible Foundation/SwiftUI string-resolution mechanisms actually honors an
//     explicitly injected Arabic locale on THIS Xcode/iOS toolchain (`PHASE_5_IOS_SYSTEM_DESIGN.md § 12`
//     calls this "the single riskiest untested assumption in the localization design") -- tested against
//     both bare `"ar"` and production's real `MentoraThemeRules.arabicLocaleIdentifier`
//     (`"ar-u-nu-latn"`), since D121 already proved this CI's Darwin ICU does not treat every
//     `-u-nu-*`-suffixed identifier the way its spelling suggests.
//
// Nothing in the app target consumes this catalog yet -- no production call site renders any of these
// 4 keys. This file's only job is to produce real CI evidence for both unknowns above, the same
// evidence-over-guessing discipline `DECISIONS_LOG.md` D121 already had to relearn the hard way for
// `NumberFormatter`/`Locale` behavior on this exact CI. Diagnostic prints below use the `MENTORA-L10N:`
// prefix, mirroring D121's `MENTORA-NUMFMT` convention (see `MentoraThemeTests.swift`) -- grep the
// `xcodebuild test` log for that prefix to read the raw findings this slice exists to produce.
@MainActor
final class LocalizationPlumbingTests: XCTestCase {

    // Values copied verbatim from `mobile/androidApp/src/main/res/values(-ar)/strings.xml`'s
    // `nav_home` key -- see this file's own resolution tests below for why these two literals matter.
    private let englishNavHome = "Home"
    private let arabicNavHome = "الرئيسية"

    // MARK: - 1. `ar.lproj` actually exists in the built bundle

    /// Hard assertion, deliberately not softened. If either half of this fails, that IS the real
    /// answer slice 1 exists to get -- it means a 4-key manually-authored `.xcstrings` does not compile
    /// into a discoverable `ar.lproj` the way the architecture doc assumes, and T7's later slices need
    /// to revisit the mechanism (§ 12's own pre-decided fallback is an explicit bundle-path lookup,
    /// which is exactly mechanism 3 in `test_navHomeResolvesToArabicByAtLeastOneMechanism` below).
    func test_arabicLocalizationIsDiscoverableInMainBundle() {
        // NOTE: `Bundle.main.localizations` is the UNION of Info.plist's `CFBundleLocalizations` (which
        // this slice just added "ar" to) and whatever `.lproj` directories actually exist on disk -- so
        // this first assertion is true by construction the moment Info.plist is edited, regardless of
        // whether Localizable.xcstrings compiled into anything. It does NOT, on its own, answer unknown
        // (1). Only the second assertion (the on-disk `ar.lproj` path) does that.
        XCTAssertTrue(Bundle.main.localizations.contains("ar"),
            "Bundle.main.localizations = \(Bundle.main.localizations) does not contain \"ar\" -- " +
            "even CFBundleLocalizations (Info.plist alone) is not reporting it, which would be a more " +
            "basic failure than the .xcstrings compile step this test is really targeting.")

        let arLprojPath = Bundle.main.path(forResource: "ar", ofType: "lproj")
        XCTAssertNotNil(arLprojPath,
            "Bundle.main.path(forResource: \"ar\", ofType: \"lproj\") returned nil -- no compiled " +
            "ar.lproj directory was found in the built app bundle. This is the real answer to unknown " +
            "(1): either Localizable.xcstrings did not compile per-locale .lproj output the way Xcode " +
            "15+ String Catalogs are assumed to, or it was not picked up by project.yml's directory-glob " +
            "sources at all. (The first assertion above passing is NOT evidence against this -- it is " +
            "true by construction from the Info.plist edit alone.)")

        print("MENTORA-L10N: Bundle.main.localizations=\(Bundle.main.localizations)")
        print("MENTORA-L10N: ar.lproj path=\(arLprojPath ?? "<nil>")")
    }

    // MARK: - 2. At least one resolution mechanism honors an explicitly injected `ar` locale

    /// Tries every mechanism `PHASE_5_IOS_SYSTEM_DESIGN.md § 12` and this slice's own task description
    /// consider plausible (1: `String(localized:table:bundle:locale:)`, 2: `LocalizedStringResource`,
    /// 3/3b: explicit bundle-path lookup in its `String(localized:bundle:)` and canonical
    /// `Bundle.localizedString(forKey:...)` forms, 4: SwiftUI `Text` rendering) -- mechanisms 1 and 2 are
    /// each additionally tried against both a bare `"ar"` locale and production's real
    /// `MentoraThemeRules.arabicLocaleIdentifier` -- logs each one's actual returned/measured value via
    /// `MENTORA-L10N:` whether it succeeded or not, then asserts that AT LEAST ONE resolved `nav_home` to
    /// the Arabic value rather than the English one. This is deliberately an "any" assertion, not "all" --
    /// the whole point of probing multiple mechanisms is that we do not yet know which one(s) this
    /// toolchain actually honors, and § 12's own fallback plan (mechanism 3/3b, an explicit bundle-path
    /// lookup funnelled through `MentoraStrings`) exists precisely because mechanism 1 (the "just works"
    /// `String(localized:)` path) is not guaranteed.
    func test_navHomeResolvesToArabicByAtLeastOneMechanism() {
        print("MENTORA-L10N: expected english=\"\(englishNavHome)\" expected arabic=\"\(arabicNavHome)\"")
        var anySucceeded = false

        // Tried against both bare "ar" and production's real MentoraThemeRules.arabicLocaleIdentifier
        // ("ar-u-nu-latn") -- D121 already proved this CI's Darwin ICU does not treat every `-u-nu-*`
        // identifier the way its spelling suggests, so bare "ar" succeeding is NOT evidence the exact
        // locale value production actually injects at the theme root also succeeds.
        let arLocales: [(String, Locale)] = [
            ("bare-ar", Locale(identifier: "ar")),
            ("production-ar-u-nu-latn", Locale(identifier: MentoraThemeRules.arabicLocaleIdentifier)),
        ]

        for (label, arLocale) in arLocales {
            // Mechanism 1: String(localized:table:bundle:locale:) with an explicit locale argument.
            let m1 = String(localized: "nav_home", table: nil, bundle: .main, locale: arLocale)
            let m1Succeeded = (m1 == arabicNavHome)
            anySucceeded = anySucceeded || m1Succeeded
            print("MENTORA-L10N: mechanism1[\(label)] String(localized:table:bundle:locale:) -> \"\(m1)\" " +
                  "succeeded=\(m1Succeeded)")

            // Mechanism 2: LocalizedStringResource with an explicit locale + bundle(atURL:). Confirmed by
            // review against the real Foundation initializer `init(_:table:locale:bundle:comment:)` (the
            // string literal "nav_home" is inferred as a String.LocalizationValue via
            // ExpressibleByStringLiteral).
            let m2Resource = LocalizedStringResource(
                "nav_home",
                locale: arLocale,
                bundle: .atURL(Bundle.main.bundleURL)
            )
            let m2 = String(localized: m2Resource)
            let m2Succeeded = (m2 == arabicNavHome)
            anySucceeded = anySucceeded || m2Succeeded
            print("MENTORA-L10N: mechanism2[\(label)] LocalizedStringResource(locale:bundle:.atURL) -> " +
                  "\"\(m2)\" succeeded=\(m2Succeeded)")
        }

        // Mechanism 3: explicit bundle-path lookup via String(localized:bundle:) -- the § 12 pre-decided
        // fallback if mechanism 1 (SwiftUI/String(localized:)'s "just works" path) is not honored on this
        // toolchain. Deliberately omits `locale:` (defaults to `.current`, English on the CI simulator) --
        // that is the whole point: this variant depends on `String(localized:)`'s own bundle-driven
        // lookup semantics, which is a real code path production could plausibly use.
        let arBundle = Bundle.main.path(forResource: "ar", ofType: "lproj").flatMap { Bundle(path: $0) }
        let m3 = String(localized: "nav_home", bundle: arBundle ?? .main)
        let m3Succeeded = (arBundle != nil) && (m3 == arabicNavHome)
        anySucceeded = anySucceeded || m3Succeeded
        print("MENTORA-L10N: mechanism3 String(localized:bundle:) on ar.lproj -> " +
              "bundleFound=\(arBundle != nil) value=\"\(m3)\" succeeded=\(m3Succeeded)")

        // Mechanism 3b: the CANONICAL Bundle.localizedString(forKey:value:table:) idiom § 12 actually
        // names as its fallback -- does not depend on String(localized:)'s lookup semantics at all, so it
        // stays meaningful even if mechanism 3 above is contaminated by whatever mechanism 1 turns out to
        // do.
        let m3b = (arBundle ?? .main).localizedString(forKey: "nav_home", value: nil, table: nil)
        let m3bSucceeded = (arBundle != nil) && (m3b == arabicNavHome)
        anySucceeded = anySucceeded || m3bSucceeded
        print("MENTORA-L10N: mechanism3b Bundle.localizedString(forKey:value:table:) on ar.lproj -> " +
              "bundleFound=\(arBundle != nil) value=\"\(m3b)\" succeeded=\(m3bSucceeded)")

        // Mechanism 4: SwiftUI rendering differential. Hosts Text(LocalizedStringKey("nav_home")) under
        // .environment(\.locale, ar) vs en (same UIHostingController/sizeThatFits harness as
        // MentoraTypographyGeometryTests.swift/MentoraThemeTests.swift) and compares the AR rendering's
        // measured width against a KNOWN-ARABIC reference width (Text(verbatim: arabicNavHome)) rather
        // than merely against the EN rendering -- a bare ar-vs-en differential would also fire if the ar
        // lookup fell through to the raw key "nav_home" (8 Latin glyphs, still very different in width
        // from "Home"'s 4), which would report success while nothing had actually resolved Arabic.
        let widthArKey = measureTextWidth(localizedKey: "nav_home", locale: MentoraThemeRules.arabicLocaleIdentifier)
        let widthArReference = measureTextWidth(verbatim: arabicNavHome)
        let widthRawKeyReference = measureTextWidth(verbatim: "nav_home")
        let matchesArabicReference = abs(widthArKey - widthArReference) <= max(widthArReference * 0.05, 1)
        let differsFromRawKey = abs(widthArKey - widthRawKeyReference) > max(widthRawKeyReference * 0.05, 1)
        let m4Succeeded = matchesArabicReference && differsFromRawKey
        anySucceeded = anySucceeded || m4Succeeded
        print("MENTORA-L10N: mechanism4 SwiftUI Text(LocalizedStringKey(\"nav_home\")) widths -- " +
              "arKeyRendered=\(widthArKey) arabicReference=\(widthArReference) " +
              "rawKeyReference=\(widthRawKeyReference) matchesArabicReference=\(matchesArabicReference) " +
              "differsFromRawKey=\(differsFromRawKey) succeeded=\(m4Succeeded)")

        XCTAssertTrue(anySucceeded,
            "None of the resolution mechanisms resolved \"nav_home\" to the Arabic value " +
            "(\"\(arabicNavHome)\") under any explicitly-injected Arabic locale (bare \"ar\" or " +
            "production's \"\(MentoraThemeRules.arabicLocaleIdentifier)\"). See the MENTORA-L10N: lines " +
            "in the test log for each mechanism's actual returned/measured value -- this is exactly the " +
            "unresolved risk PHASE_5_IOS_SYSTEM_DESIGN.md § 12 flags as needing the bundle-lookup " +
            "fallback.")
    }

    // MARK: - 3. Diagnostic only: does the raw "%%" survive without String(format:)?

    /// `my_learning_percent_complete`'s EN value is authored as `"%1$@%% complete"` (ported from
    /// Android's `"%1$s%% complete"`, per the plan's `%N$s` -> `%N$@` conversion rule -- the `%%` escape
    /// itself is untouched). This logs what `String(localized:)` alone (NOT `String(format:)`) actually
    /// returns for it, so a later T7 slice knows -- from real CI evidence, not a guess -- whether `%%`
    /// survives as two literal percent signs (meaning `String(format:)` is still required downstream to
    /// collapse it to one) or whether something in this resolution path already collapses it. NEVER
    /// asserted either way -- this is purely evidence for the next slice to consume.
    func test_diagnosticOnly_percentLiteralSurvivesWithoutStringFormat() {
        let raw = String(localized: "my_learning_percent_complete", locale: Locale(identifier: "en"))
        print("MENTORA-L10N: my_learning_percent_complete (en, no String(format:)) -> \"\(raw)\" " +
              "(contains literal \"%%\" = \(raw.contains("%%")))")
    }

    // MARK: - 4. Diagnostic only: app_name's AR-locale fallback behavior

    /// `app_name` deliberately carries no `ar` localization unit at all (see this slice's task
    /// description and the catalog itself) -- specifically to observe what Foundation does when asked
    /// to resolve a key under a locale it has no unit for. Logs, but never asserts, which of the three
    /// plausible outcomes actually occurred: falls back to the English value, returns the raw key
    /// string unchanged, or something else entirely.
    func test_diagnosticOnly_appNameFallbackUnderArabicLocale() {
        let englishValue = String(localized: "app_name", locale: Locale(identifier: "en"))
        let arabicAttempt = String(localized: "app_name", locale: Locale(identifier: "ar"))

        let outcome: String
        if arabicAttempt == englishValue {
            outcome = "fell back to the English value"
        } else if arabicAttempt == "app_name" {
            outcome = "returned the raw key unchanged"
        } else {
            outcome = "returned something else entirely"
        }

        print("MENTORA-L10N: app_name english=\"\(englishValue)\" arabicAttempt=\"\(arabicAttempt)\" " +
              "outcome=\(outcome)")
    }

    // MARK: - Private harness

    /// Mirrors `MentoraTypographyGeometryTests.swift`'s `Geometry.measure(_:)` harness pattern: a bare,
    /// unattached `UIHostingController`, forced to lay out, measured via `sizeThatFits`.
    ///
    /// Renders `Text(LocalizedStringKey(key))` under an injected `\.locale` -- deliberately NOT
    /// `Text(key)`, since `Text.init(_ key: LocalizedStringKey, ...)` is only reachable from a string
    /// LITERAL via `ExpressibleByStringLiteral`; a `String` variable resolves to the non-localizing
    /// `Text.init<S: StringProtocol>(_ content: S)` overload instead and would always render the raw key
    /// verbatim regardless of catalog lookup, silently defeating this whole mechanism.
    @MainActor
    private func measureTextWidth(localizedKey key: String, locale identifier: String) -> CGFloat {
        measure(Text(LocalizedStringKey(key)).environment(\.locale, Locale(identifier: identifier)))
    }

    /// Renders a fixed literal string with no locale-driven lookup at all -- used as a known-value
    /// reference width (e.g. "does this measured width match rendering the real Arabic string verbatim,
    /// or does it match rendering the raw un-resolved key verbatim") rather than a bare ar-vs-en
    /// differential, which cannot distinguish "resolved to Arabic" from "fell through to the raw key".
    @MainActor
    private func measureTextWidth(verbatim text: String) -> CGFloat {
        measure(Text(verbatim: text))
    }

    @MainActor
    private func measure(_ content: Text) -> CGFloat {
        let host = UIHostingController(rootView: content.fixedSize(horizontal: true, vertical: true))
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        let size = host.sizeThatFits(in: CGSize(width: 200_000, height: 200_000))
        return size.width
    }
}
