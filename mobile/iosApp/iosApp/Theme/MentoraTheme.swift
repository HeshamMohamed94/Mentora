import SwiftUI
import shared

// Phase 5 Task T6 slice 3b (D121) — theme-root wiring. This is the ONE place in the entire app target
// that writes `.preferredColorScheme`, `\.locale`, and `\.layoutDirection` (`PHASE_5_IOS_SYSTEM_DESIGN.md`
// §§ 12/13/14, criterion G6) — every other file in `Theme/` (`MentoraTypography.swift`,
// `MentoraShape.swift`, `MentoraElevation.swift`) stays free of any `AppEnvironment`/SDK dependency, and
// this file stays free of any SwiftUI *rendering* of its own; it only supplies values and environment
// transforms for a caller to apply.
//
// Android's `isAppearanceLightStatusBars`/`decorFitsSystemWindows` status-bar mechanisms
// (`androidApp/.../theme/MentoraTheme.kt`) have NO iOS analogue and are deliberately NOT ported here:
// iOS derives status-bar appearance (light/dark content) from the window's `userInterfaceStyle`, which
// `.preferredColorScheme` applied at the true root already sets for the whole window -- there is no
// separate "decor fits system windows" concept on iOS to mirror.
//
// `import shared` is required directly, not merely transitively via a prior slice's import of it
// elsewhere -- module imports are not transitive in Swift, the exact mistake an earlier slice's review
// caught (a missing `import SwiftUI`).

// MARK: - Pure rules (no SwiftUI rendering, no SDK calls)

/// Every theme/locale COMPUTATION lives here as a pure function of a literal Kotlin enum value, so it
/// is fully unit-testable without constructing an `AppEnvironment` or rendering anything. Mirrors
/// `MentoraTypographyRules`'s "pure rules, testable in isolation" shape (slice 1).
enum MentoraThemeRules {

    /// `ThemePreference` -> SwiftUI `ColorScheme?`, per `PHASE_5_IOS_SYSTEM_DESIGN.md § 12`.
    ///
    /// `.system -> nil`: SwiftUI's `.preferredColorScheme(nil)` means "follow the OS", which is exactly
    /// what System mode means -- so `nil` is not a missing case, it IS the correct mapping.
    ///
    /// Deliberately diverges from Android's `resolveDarkTheme()` (`ThemeController.kt`), which resolves
    /// `System` to a concrete `Boolean` via `isSystemInDarkTheme()` at read time. Do NOT port that
    /// resolution logic here: collapsing `.system` to a concrete `ColorScheme` would freeze the choice
    /// at the moment this function runs, whereas `nil` keeps `.preferredColorScheme` tracking *live* OS
    /// appearance changes (e.g. an automatic light/dark switch at sunset) for as long as `.system`
    /// remains selected -- a real behavioral improvement over Android's snapshot-based read, not a gap.
    ///
    /// Exhaustive switch, deliberately NO `default:` clause: a future Kotlin case added to
    /// `ThemePreference` must become a compile error here, not a silent fallthrough to some arbitrary
    /// branch.
    static func colorScheme(for theme: ThemePreference) -> ColorScheme? {
        switch theme {
        case .light:
            return .light
        case .dark:
            return .dark
        case .system:
            return nil
        }
    }

    /// `design-system/LOCALIZATION.md § 8` / `PHASE_5_IOS_SYSTEM_DESIGN.md § 12` -- Arabic locale
    /// identifier forcing Western (ASCII) numerals. NOT plain `"ar"`: region-specific Arabic locales
    /// (`ar_EG`, `ar_SA`) default to Eastern Arabic-Indic digits (٠-٩) per Apple's own published ICU
    /// data, and `architecture/LOCALIZATION_ARCHITECTURE.md` requires the same `-u-nu-latn` override on
    /// Web and Android, where it is unconditionally load-bearing -- so this override is applied
    /// unconditionally here too, for cross-platform consistency, rather than being conditioned on any
    /// one Arabic locale tag's own default. (An earlier version of this comment claimed CI had confirmed
    /// bare `"ar"` defaults to `latn` on Apple's ICU -- CI runs #24/#25 (`MentoraThemeTests.swift`/
    /// `DECISIONS_LOG.md` D121) proved that claim rested only on reading Apple's open-source ICU data,
    /// not on anything CI actually observed; whether Darwin resolves ANY `-u-nu-*` numbering-system
    /// extension is still an open question, tracked via that test's non-assertive diagnostics.) This is
    /// the ONE named constant for this string -- it must never be inlined a second time anywhere in this
    /// target's production code (enforced by `tools/ios-checks/theme-checks.js` Check C5, the automated
    /// successor to the manual grep `DECISIONS_LOG.md` D121 recorded).
    static let arabicLocaleIdentifier = "ar-u-nu-latn"

    /// `AppLocale` -> Foundation `Locale`, per `PHASE_5_IOS_SYSTEM_DESIGN.md § 12`/D4.
    ///
    /// The Arabic case resolves through `arabicLocaleIdentifier`, NOT plain `"ar"` -- see that
    /// constant's doc comment. Note that `Locale(identifier: "ar-u-nu-latn").language.languageCode` is
    /// still `"ar"` (the `-u-nu-latn` suffix is a Unicode locale extension for numbering system, not a
    /// language-subtag change), which is exactly what keeps `MentoraTypographyRules.isArabic(_:)`
    /// (`Theme/MentoraTypography.swift`) working correctly once this locale reaches `\.locale` -- see
    /// `test_arabicLocaleKeepsArabicLanguageSubtag` in `MentoraThemeTests.swift` for the regression
    /// guard on that exact invariant. Whether SwiftUI's `Text`/String Catalog (`.xcstrings`) lookup
    /// itself honors `\.locale` for this value is a SEPARATE, still-open question --
    /// `PHASE_5_IOS_SYSTEM_DESIGN.md § 12` names it explicitly as "the single riskiest untested
    /// assumption in the localization design," with an MC-2 verification step and a documented
    /// bundle-lookup fallback if it doesn't hold. T7 owns that check, not this slice.
    ///
    /// Exhaustive switch, deliberately no `default:` clause -- see `colorScheme(for:)` above for why.
    static func foundationLocale(for locale: AppLocale) -> Locale {
        switch locale {
        case .english:
            return Locale(identifier: "en")
        case .arabic:
            return Locale(identifier: arabicLocaleIdentifier)
        }
    }

    /// `AppLocale` -> `LayoutDirection`, per `PHASE_5_IOS_SYSTEM_DESIGN.md § 12`/D4.
    ///
    /// An EXPLICIT 2-case switch, deliberately NOT derived from `Locale.Language.characterDirection` --
    /// this app supports exactly two locales today, and hard-coding the mapping keeps it independent of
    /// Foundation's own (correct, but indirect) script-direction inference, matching Android's own
    /// explicit `LayoutDirection` selection rather than introducing a second inference path that could
    /// diverge from it for a locale this app doesn't otherwise support.
    ///
    /// Exhaustive switch, deliberately no `default:` clause -- see `colorScheme(for:)` above for why.
    static func layoutDirection(for locale: AppLocale) -> LayoutDirection {
        switch locale {
        case .english:
            return .leftToRight
        case .arabic:
            return .rightToLeft
        }
    }
}

// MARK: - The one View extension

extension View {
    /// Applies theme (`ColorScheme`) and locale (`Locale` + `LayoutDirection`) to `self`, from plain
    /// literal values -- NOT from `@Environment` reads, and with NO knowledge of `AppEnvironment` or
    /// any controller. This is what keeps the whole theme layer unit-testable with literal enum values
    /// (see `MentoraThemeTests.swift`) and cheaply re-appliable (see the sheet/cover note below).
    ///
    /// MUST be applied at the true `WindowGroup`-content root (see `MentoraRootView` in
    /// `MentoraApp.swift`) -- applying it lower in the tree would leave everything above that point
    /// (including any chrome SwiftUI itself renders around the window) un-themed/un-localed.
    ///
    /// `locale: AppLocale?` -- the only remaining nil source after `LocaleController.currentLocale`
    /// became non-optional (T6 slice 3b, D121/D3) is the nil-`AppEnvironment` degradation path
    /// (`AppEnvironment.swift`'s `AppEnvironmentKey` doc comment) -- an atypical/unconfirmed launch
    /// context, not the normal app-launch path. A `nil` locale here means "no known app locale ->
    /// inherit whatever the environment already has" (i.e. the OS locale SwiftUI defaults to), which is
    /// why `.transformEnvironment` (leaving the environment value untouched when `locale` is `nil`) is
    /// used instead of an `if/else` branch: an `if/else` here would create a `_ConditionalContent`
    /// identity split in the view tree for a case that is not a real content difference, only a
    /// missing-value default. `.preferredColorScheme` needs no equivalent nil-handling since
    /// `theme: ThemePreference` is never optional and `.system -> nil` (`MentoraThemeRules.colorScheme`)
    /// is already the correct SwiftUI no-op for "follow the OS".
    ///
    /// SHEET/COVER INHERITANCE (`PHASE_5_IOS_SYSTEM_DESIGN.md §§ 12/14`, D6 in `DECISIONS_LOG.md` D121):
    /// per § 12, SwiftUI's `\.locale`/`\.layoutDirection` environment already propagates into
    /// sheets/alerts presented from the SAME hierarchy -- this is a deliberate divergence from Android,
    /// which needed a custom `ContextWrapper`/`CompositionLocal` to cross those boundaries (D93) and
    /// whose iOS analogue is genuinely unnecessary here. Only a sheet/cover presented from a
    /// **detached** context (rare, and avoidable per § 12) is a real open risk, to re-check at MC-3.
    /// `.preferredColorScheme` is a different mechanism (a SwiftUI *preference*, not a plain environment
    /// value) and § 14 states the requirement -- "sheets/covers must inherit it" -- as an explicit MC-3
    /// check, not yet a proven fact; this is the iOS form of a real defect class Phase 4 already shipped
    /// once (illegible status-bar icons from an untold background luminance). NOT solved by this slice
    /// either way -- this function taking plain values (not environment-derived) is what would make a
    /// future re-application cheap IF the MC-3 check finds root application insufficient for some
    /// presentation shape; it is not evidence that re-application is already known to be required.
    ///
    /// Holds no state and reads no environment of its own, so it is fully unit-testable
    /// (`MentoraThemeTests.swift`) and safely re-appliable anywhere in a view tree.
    func mentoraTheme(theme: ThemePreference, locale: AppLocale?) -> some View {
        self
            .preferredColorScheme(MentoraThemeRules.colorScheme(for: theme))
            .transformEnvironment(\.locale) { value in
                if let locale {
                    value = MentoraThemeRules.foundationLocale(for: locale)
                }
            }
            .transformEnvironment(\.layoutDirection) { value in
                if let locale {
                    value = MentoraThemeRules.layoutDirection(for: locale)
                }
            }
    }
}
