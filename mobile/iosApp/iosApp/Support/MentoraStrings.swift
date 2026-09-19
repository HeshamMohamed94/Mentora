import Foundation
import shared

// Phase 5 Task T7 slice 3 (D123/D124) — the SOLE sanctioned string-resolution entry point for this
// app target, going forward for T8-T23's 18 screens as well. Per the user's explicit, already-made
// decision (not revisited here): plain SwiftUI `Text("key")` catalog lookup is forbidden at call
// sites app-wide, even though slice 1's `LocalizationPlumbingTests.swift` mechanism 4 proved it
// technically resolves correctly under an injected `\.locale`. Funnelling every lookup through this
// one file is what keeps a single resolution path and makes slice 4's future "no hardcoded string"
// lint simple to write.
//
// Internal mechanism — CI-PROVEN, not guessed (`DECISIONS_LOG.md` D123/D124, `CatalogParityTests.swift`):
// `LocalizedStringResource(String.LocalizationValue(key), locale:, bundle: .atURL(Bundle.main.bundleURL))`
// + `String(localized:)`. NEVER `String(localized:table:bundle:locale:)` — that form is CI-confirmed
// broken for locale selection on this exact toolchain: it silently ignores its own `locale:` argument
// and always resolves against `Locale.current` when `bundle: .main` is used (D123 finding, unknown 2).
//
// Every entry point below takes an explicit `AppLocale`, never reading `Locale.current`/the device
// locale itself — the structural fix for the exact bug class D93 (Phase 4/Android) found once already
// (a quick-action's visible label was Arabic while the prompt actually sent to the model was English,
// because it was resolved off a context outside the composition). Making `locale` a required, non-
// optional parameter here makes that bug unrepresentable rather than merely unlikely (H4).
enum MentoraStrings {

    /// Plain lookup for keys with no format arguments. Resolves `key` from the compiled
    /// `Localizable.xcstrings` catalog against `Bundle.main`, under `locale` — never the environment's
    /// or device's current locale.
    static func text(_ key: String, locale: AppLocale) -> String {
        let resource = LocalizedStringResource(
            String.LocalizationValue(key),
            locale: MentoraThemeRules.foundationLocale(for: locale),
            bundle: .atURL(Bundle.main.bundleURL)
        )
        let resolved = String(localized: resource)
#if DEBUG
        // A missing/typo'd key resolves to the raw key string itself (Foundation's documented
        // fallback, and exactly what D123's own probe relied on as a "did NOT resolve" reference
        // width) -- with every screen now funneling through this one runtime-string-keyed function
        // instead of a compile-checked call, that failure mode would otherwise ship silently as a
        // raw key visible on screen. Asserted in Debug only; Release still degrades to the raw key
        // rather than crashing, which is the correct behavior for a real end-user build.
        assert(resolved != key,
            "MentoraStrings.text(\"\(key)\") did not resolve -- \"\(key)\" is missing from " +
            "Localizable.xcstrings (or is a genuine typo). Resolved to the raw key itself.")
#endif
        return resolved
    }

    /// Formatted lookup for keys carrying positional `%1$@`/`%2$@`/`%3$@` arguments. Resolves `key` via
    /// the exact same mechanism as `text(_:locale:)` above, then runs the raw catalog value through
    /// `String(format:locale:arguments:)` to substitute `args` positionally.
    ///
    /// This is also where the catalog's literal `%%` escape (used for keys that mix a real positional
    /// argument with a literal percent sign, e.g. `my_learning_percent_complete`'s EN value
    /// `"%1$@%% complete"`) gets collapsed to a single `%` — D123's diagnostic test
    /// (`LocalizationPlumbingTests.test_diagnosticOnly_percentLiteralSurvivesWithoutStringFormat`) proved
    /// `%%` survives as two literal characters through plain `String(localized:)` alone; `String(format:)`
    /// is a standard printf-style formatter and is what actually collapses it, as a side effect of the
    /// same call that substitutes the positional arguments — no separate collapsing step is needed or
    /// implemented here.
    ///
    /// `args` are `CVarArg`, matching the catalog convention (`PHASE_5_IOS_SYSTEM_DESIGN.md`/plan T7:
    /// every substitution is authored as `%N$@`, never `%N$d`, ported verbatim from Android's own
    /// `%N$s`-only convention) — callers pass already-formatted `String`s (e.g. via `MentoraFormatters`
    /// for numeric values), not raw `Int`s, since `%@` expects an object-bridgeable argument.
    ///
    /// `locale` is also passed to `String(format:locale:arguments:)` itself (not just to the initial
    /// catalog lookup) so any locale-sensitive formatting Foundation performs while substituting the
    /// arguments (e.g. numeral shaping) is also locale-correct, per H6/`MentoraThemeRules.foundationLocale(for:)`'s
    /// Western-numeral guarantee.
    static func text(_ key: String, locale: AppLocale, _ args: CVarArg...) -> String {
        let format = text(key, locale: locale)
#if DEBUG
        // Debug-only guard against a real, previously-shipped crash class (`PHASE_5_ACCEPTANCE_CRITERIA.md`
        // H2 / D93 finding 8: "a dropped specifier passes key-parity and crashes at runtime"). A format
        // string with more positional %N$@ specifiers than supplied arguments reads past the end of the
        // constructed va_list under String(format:) — undefined behavior, not a graceful failure. This
        // can only ever fire from a call-site authoring mistake (wrong arg count for a given key), never
        // from catalog data itself (catalog-parity.js's Group B already guarantees every key's own
        // EN/AR specifier sets agree) — so it is asserted, not handled, and compiled out of Release.
        let specifierCount = Set(matchesOfPositionalSpecifiers(in: format)).count
        assert(specifierCount == args.count,
            "MentoraStrings.text(\"\(key)\") expects \(specifierCount) argument(s) (found in " +
            "\"\(format)\"), but \(args.count) were passed.")
#endif
        let foundationLocale = MentoraThemeRules.foundationLocale(for: locale)
        return String(format: format, locale: foundationLocale, arguments: args)
    }

#if DEBUG
    /// Returns the positional specifier indices (`%1$@` -> `1`, etc.) found in `value`. Debug-only
    /// helper for the argument-count assertion above; deliberately not a general-purpose parser (no
    /// need to distinguish `%%` from a real specifier here, since a literal `%%` never matches
    /// `%(\d)\$@` in the first place).
    private static func matchesOfPositionalSpecifiers(in value: String) -> [Int] {
        var indices: [Int] = []
        let chars = Array(value)
        var i = 0
        while i < chars.count - 3 {
            if chars[i] == "%", chars[i + 1].isNumber, chars[i + 2] == "$", chars[i + 3] == "@" {
                indices.append(Int(String(chars[i + 1]))!)
                i += 4
            } else {
                i += 1
            }
        }
        return indices
    }
#endif
}
