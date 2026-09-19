import Foundation
import shared

// Phase 5 Task T7 slice 3 (D123/D124) — locale-explicit formatters, per `design-system/LOCALIZATION.md`
// §§ 7 (Locale-Aware Formatting) and 8 (Locked Decisions). § 7 requires dates, numbers/counts, and
// durations to be "presentation-formatted per active UI locale, not hardcoded to one English
// convention," via "the platform's locale-aware date formatter … never a hand-built date string"; § 8
// locks "Western Arabic numerals (0–9) everywhere, including Arabic UI."
//
// H6 names four kinds of formatting ("dates, counts, durations, prices") — this file deliberately
// ships two (date, count) and defers the other two, correction to an earlier draft of this comment:
//
// A DURATION formatter is NOT built here, even though Android DOES have playback-time formatting
// (`PlayerControls.kt`'s `formatPlaybackTime`, `String.format(Locale.US, "%02d:%02d", …)`, deliberately
// pinned to `Locale.US` per `LOCALIZATION.md § 8` rather than locale-varied) and the catalog already
// carries the two keys that would consume one (`course_player_time_label` "%1$@ / %2$@",
// `course_player_scrubber_state_description` "%1$@ of %2$@") — so "no Android precedent" would be a
// false justification. The real reason is scope: § 7's own Duration row states the exact string is "a
// copy/localization-file decision, not a design-system one," i.e. it is expressed via those already-
// ported catalog keys and `MentoraStrings`'s formatted lookup once a real Course Player screen needs
// one — the `mm:ss` computation itself belongs with T16 (Course Player), not this slice. Deferred, not
// forgotten.
//
// A PRICE formatter is also NOT built here, even though Android DOES have one (`formatDemoPrice`,
// `"$currency $amount"`, hand-built by documented decision, not locale-varied) — `product/DEMO_PAYMENT_FLOW.md § 5`'s
// demo prices are a T14 (Demo Checkout) concern, not this slice's.
//
// Every function below takes `AppLocale` explicitly, the same rule `MentoraStrings.swift` documents —
// never `Locale.current`/the device locale — and converts to `Locale` via the one shared, already-
// proven mapping, `MentoraThemeRules.foundationLocale(for:)` (`Theme/MentoraTheme.swift`), rather than
// re-deriving the ar/en mapping (or the `-u-nu-latn` Western-numeral override it carries) a second
// time.
enum MentoraFormatters {

    /// Locale-explicit date formatting, per § 7's date row ("Use the platform's locale-aware date
    /// formatter … never a hand-built date string"). `style` covers both the short and medium styles §
    /// 7's row illustrates (e.g. "Sep 3, 2026") — defaulted to `.medium` since that is the row's own
    /// example style, but callable with `.short` wherever a screen needs the more compact form.
    ///
    /// Arabic numerals stay Western per § 8, for free: `MentoraThemeRules.foundationLocale(for: .arabic)`
    /// already carries the `-u-nu-latn` numbering-system override (`arabicLocaleIdentifier`), and
    /// `DateFormatter` — like the `NumberFormatter` `MentoraThemeTests.swift`'s
    /// `test_arabicLocaleForcesWesternNumerals` already regression-guards — renders any numeral it emits
    /// (day/year digits) through that same locale's numbering system.
    static func date(_ date: Date, style: DateFormatter.Style = .medium, locale: AppLocale) -> String {
        let formatter = DateFormatter()
        formatter.locale = MentoraThemeRules.foundationLocale(for: locale)
        formatter.dateStyle = style
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }

    /// Locale-explicit integer/count formatting, per § 7's "Numbers / counts" row (e.g. "1,234
    /// students"/"1,234 طالب") — grouping/decimal separators follow locale convention via the platform
    /// formatter, exactly as the row specifies, while the numeral GLYPHS stay Western per § 8's locked
    /// decision. For use wherever a screen needs a plain formatted count to interpolate into a
    /// `MentoraStrings` catalog string (e.g. "3 courses", "12 lessons") via that string's `%1$@`
    /// specifier — this function returns the formatted count alone, not the surrounding sentence.
    ///
    /// H6: never produces an Eastern Arabic-Indic digit (٠-٩) under `.arabic` — reuses the exact same
    /// `MentoraThemeRules.foundationLocale(for:)` call (and therefore the exact same `-u-nu-latn`
    /// override) `MentoraThemeTests.swift`'s `test_arabicLocaleForcesWesternNumerals` already
    /// regression-guards for the typography/theme layer, rather than re-deriving or duplicating that
    /// numeral-forcing logic a second time here.
    ///
    /// DELIBERATE cross-platform divergence, recorded here rather than left implicit: Android's own
    /// student-count display (`CourseCard.kt`) uses `it.toString()` — no grouping separator, e.g.
    /// "1234" not "1,234". This function's `.decimal` style DOES group (`"1,234"`), matching
    /// `LOCALIZATION.md § 7`'s own worked example ("1,234 students") more closely than Android's
    /// current behavior does. iOS is treated as the correct target here, not Android — flag for a
    /// future cross-platform consistency pass rather than silently reproducing Android's ungrouped
    /// form.
    static func count(_ value: Int, locale: AppLocale) -> String {
        let formatter = NumberFormatter()
        formatter.locale = MentoraThemeRules.foundationLocale(for: locale)
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}
