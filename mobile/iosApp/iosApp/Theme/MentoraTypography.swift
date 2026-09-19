import SwiftUI

// Phase 5 Task T6 — the hand-authored typography BEHAVIOR layer on top of T2's generated METRICS
// (`MentoraTokens.swift`'s `MentoraTypography` / `MentoraTypographyMetrics`). Nothing here is a token
// VALUE; every number comes from the generated layer or is one of the two named constants below,
// which are ratios/rules, not tokens.
//
// WHY A ViewModifier AND NOT A `Font` EXTENSION — do not "simplify" this back:
// `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1` (a correction to
// `design-to-code/shared/platform-contract.json#/ios/typographyMapping`'s prose, with
// `design-system/platform-mapping.md § 2` — LOCKED — as the authority). Three reasons, in short:
//   1. `relativeTo:` exists only on `Font.custom(_:size:relativeTo:)`, which needs a bundled font,
//      which H8 forbids (bundling one is what would break Apple's automatic SF Arabic substitution).
//      A static `Font.system(size:weight:)` constant does not scale with Dynamic Type at all.
//   2. A `Font` cannot carry tracking or line height -- `.tracking(_:)`/`.lineSpacing(_:)` are
//      View/Text modifiers, so `letterSpacing`/`lineHeight` and the Arabic overrides have nowhere
//      to live in a `Font`-shaped API.
//   3. `UIFontMetrics` is NOT the workaround -- it reads the trait collection directly and ignores an
//      injected `.dynamicTypeSize(...)`, which is exactly how this file's own tests drive assertions.
//      `UIFontMetrics` is banned repo-wide (criterion G3, enforced by `tools/ios-checks/theme-checks.js`
//      Check A2 -- see `PHASE_5_IOS_IMPLEMENTATION_PLAN.md`'s T6 completion gate section).

// MARK: - The 12 scale steps

/// The 12 steps of `design-tokens.json#/typography/scale`, named per
/// `design-system/platform-mapping.md § 2` (LOCKED) -- `.h1`, NOT the JSON prose's `HeadingH1`.
/// The generated constants they map to are spelled `headingH1`...`headingH4`; that asymmetry is real
/// and deliberate (the generator follows the token dot-path, this enum follows the LOCKED mapping).
enum MentoraTextStyle: String, CaseIterable {
    case displayLarge
    case displayMedium
    case h1
    case h2
    case h3
    case h4
    case bodyLarge
    case bodyMedium
    case bodySmall
    case labelLarge
    case labelMedium
    case caption
}

extension MentoraTextStyle {

    /// The generated, UNSCALED metrics for this step (`Theme/MentoraTokens.swift`).
    var metrics: MentoraTypographyMetrics {
        switch self {
        case .displayLarge:  return MentoraTypography.displayLarge
        case .displayMedium: return MentoraTypography.displayMedium
        case .h1:            return MentoraTypography.headingH1
        case .h2:            return MentoraTypography.headingH2
        case .h3:            return MentoraTypography.headingH3
        case .h4:            return MentoraTypography.headingH4
        case .bodyLarge:     return MentoraTypography.bodyLarge
        case .bodyMedium:    return MentoraTypography.bodyMedium
        case .bodySmall:     return MentoraTypography.bodySmall
        case .labelLarge:    return MentoraTypography.labelLarge
        case .labelMedium:   return MentoraTypography.labelMedium
        case .caption:       return MentoraTypography.caption
        }
    }

    /// The Dynamic Type anchor passed to `@ScaledMetric(relativeTo:)`. Copied VERBATIM from
    /// `platform-contract.json#/ios/typographyMapping/relativeToAnchors` -- the anchors were never
    /// the part of that mapping that was wrong (§ 15.1's closing paragraph).
    var anchor: Font.TextStyle {
        switch self {
        case .displayLarge:  return .largeTitle
        case .displayMedium: return .largeTitle
        case .h1:            return .title
        case .h2:            return .title2
        case .h3:            return .title3
        case .h4:            return .headline
        case .bodyLarge:     return .body
        case .bodyMedium:    return .body
        case .bodySmall:     return .subheadline
        case .labelLarge:    return .subheadline
        case .labelMedium:   return .footnote
        case .caption:       return .caption
        }
    }

    /// `typography.body.*` -- the ONLY steps that receive `design-system/LOCALIZATION.md § 4`'s
    /// Arabic +10% line-height bump. Matches Android's `isBody = true` call sites exactly
    /// (`androidApp/.../theme/MentoraTheme.kt:268-270`), cross-checked, not copied.
    var isBodyStep: Bool {
        switch self {
        case .bodyLarge, .bodyMedium, .bodySmall: return true
        default:                                  return false
        }
    }
}

// MARK: - The rules (pure, testable, no SwiftUI rendering required)

/// Every typography COMPUTATION lives here as a pure function of (style, scaledSize, isArabic), so
/// the whole of criterion G3(a)(b)(c) can be asserted without rendering anything. The ViewModifier
/// below contains no arithmetic of its own -- it only supplies `scaledSize` and the locale.
///
/// NOT named `MentoraTypography`: that name is already taken by the GENERATED metrics enum in
/// `MentoraTokens.swift`.
enum MentoraTypographyRules {

    /// SF Pro renders at ~1.2 x point size. ONE named constant, per § 15.1 -- never inlined.
    /// This value measures the LATIN (SF Pro) face; the substituted SF Arabic face's natural leading
    /// factor is materially larger (diacritic clearance), so the effective Arabic body +10% bump
    /// (see `arabicBodyLineHeightMultiplier` below) likely renders looser than +10% in practice --
    /// safe direction (more space, never overlap/clipping), but worth an explicit MC-3 measurement
    /// item rather than a silent assumption. If the MC-2 token gallery shows drift this may become a
    /// measured `UIFont.systemFont(ofSize: scaledSize, weight:).lineHeight / scaledSize` -- same
    /// formula, same non-negativity guarantee, one line changed (though that specific replacement
    /// still measures the Latin face, so it would not by itself fix the Arabic-specific gap above).
    /// That is NOT `UIFontMetrics`, which stays banned.
    static let naturalLineHeightFactor: CGFloat = 1.2

    /// `design-tokens.json#/typography/arabicAdjustments/bodyLineHeightMultiplier` (= 1.1).
    static let arabicBodyLineHeightMultiplier: CGFloat = 1.10

    /// `design-tokens.json#/typography/arabicAdjustments/letterSpacing` (= 0).
    static let arabicLetterSpacing: CGFloat = 0

    /// Locale-level, not text-run-level, script detection.
    /// `design-system/LOCALIZATION.md § 4` says "detect the script of the text run"; NEITHER platform
    /// does that -- Android's real call site (`MainActivity`) passes `arabicScript` explicitly from
    /// `observeLocale()` (the app's chosen locale, per `MentoraTheme.kt:316`'s kdoc; the
    /// `LocalConfiguration`-derived value there is only a default-parameter fallback, not what ships),
    /// and iOS keys off `@Environment(\.locale)` here -- both are locale-level, not run-level. This is
    /// an inherited, DISCLOSED simplification, recorded in `DECISIONS_LOG.md`, not a silent claim of
    /// § 4 compliance.
    ///
    /// T6 slice 3b's `MentoraThemeRules.arabicLocaleIdentifier` (`Theme/MentoraTheme.swift`) sets the
    /// environment locale for Arabic to `ar-u-nu-latn` (`design-system/LOCALIZATION.md § 8`, Western
    /// numerals); `language.languageCode` on that identifier is still `"ar"`, so this keeps working.
    /// (T7 is a separate, later task — localization strings/`.xcstrings` catalog — and is not what sets
    /// this environment locale.)
    static func isArabic(_ locale: Locale) -> Bool {
        locale.language.languageCode?.identifier == "ar"
    }

    /// Dimensionless target-leading ratio. Scale-invariant by construction, which is the entire
    /// point of § 15.1's formula.
    static func lineHeightRatio(for style: MentoraTextStyle, isArabic: Bool) -> CGFloat {
        let m = style.metrics
        let base = m.lineHeight / m.fontSize
        return (isArabic && style.isBodyStep) ? base * arabicBodyLineHeightMultiplier : base
    }

    /// `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`'s formula, COPIED VERBATIM, NOT RE-DERIVED:
    ///
    ///     lineSpacing = max(0, scaledTargetLineHeight - scaledSize * naturalLineHeightFactor)
    ///                == scaledSize * max(0, ratio - naturalLineHeightFactor)
    ///
    /// Written in the first (subtraction) form deliberately, so it reads identically to the design
    /// document. Both quantities are multiples of the SAME single `@ScaledMetric` value, so the
    /// intended leading ratio holds identically at `.large` and at `.accessibility5`; and the
    /// explicit `max(0, ...)` over a product of a positive scaled size means it can never go negative.
    ///
    /// The earlier `.lineSpacing(lineHeight - scaledSize)` form is WITHDRAWN -- it subtracted an
    /// UNSCALED token value from a SCALED one, so it hit zero and then went negative at accessibility
    /// sizes, taking the claimed leading ratio and the Arabic +10% rule with it. Do not reintroduce it.
    ///
    /// `.displayLarge` (56/48 = 1.167) and `.displayMedium` (48/40 = 1.20) ask for leading TIGHTER
    /// than the system font's own; `.lineSpacing` is additive and cannot tighten, so those two clamp
    /// to 0 and render at the font's natural leading. That is a bounded, deliberate deviation on two
    /// display steps (§ 15.1), compared against Android at MC-3 -- NOT a bug to "fix".
    static func lineSpacing(for style: MentoraTextStyle,
                            scaledSize: CGFloat,
                            isArabic: Bool) -> CGFloat {
        let ratio = lineHeightRatio(for: style, isArabic: isArabic)
        let scaledTargetLineHeight = scaledSize * ratio
        let naturalLineHeight = scaledSize * naturalLineHeightFactor
        return max(0, scaledTargetLineHeight - naturalLineHeight)
    }

    /// Tracking is DELIBERATELY NOT SCALED (§ 15.1). The token value is intentionally negative on the
    /// two display steps (-0.25); scaling it would amplify that negative value at AX sizes and is the
    /// single most plausible route to glyph overlap. Forced to 0 for Arabic, which is a cursive,
    /// connected script where tracking breaks letter joining (`design-system/LOCALIZATION.md § 4`).
    static func tracking(for style: MentoraTextStyle, isArabic: Bool) -> CGFloat {
        isArabic ? arabicLetterSpacing : style.metrics.tracking
    }

    /// `design-tokens.json#/typography/fontWeight` numeric values -> SwiftUI `Font.Weight`.
    /// The generated metrics carry the NUMBER (400/500/600/700); this is the only place it becomes
    /// a `Font.Weight`.
    static func weight(for style: MentoraTextStyle) -> Font.Weight {
        switch style.metrics.fontWeight {
        case 700: return .bold
        case 600: return .semibold
        case 500: return .medium
        case 400: return .regular
        default:
            // Unreachable for the current token file; fail loudly in Debug, degrade safely in Release.
            assertionFailure("Unmapped fontWeight \(style.metrics.fontWeight) for \(style.rawValue)")
            return .regular
        }
    }
}

// MARK: - The one modifier

/// The SINGLE place `Font.system(size:weight:)` appears in the entire app target (criterion G3;
/// enforced by `tools/ios-checks/theme-checks.js` Check A1, per `PHASE_5_IOS_IMPLEMENTATION_PLAN.md`'s
/// T6 completion gate section). No bundled font, so SF Arabic substitution for Arabic runs stays
/// automatic (H8).
private struct MentoraFontModifier: ViewModifier {

    /// The ONLY scaled value. `@ScaledMetric` honors both the system Dynamic Type setting AND an
    /// injected `.dynamicTypeSize(...)` in a `#Preview` or an XCTest -- which is what makes the AX
    /// behavior testable at all (`Font.system(size:)` alone and `UIFontMetrics` both fail that).
    @ScaledMetric private var scaledSize: CGFloat

    @Environment(\.locale) private var locale

    private let style: MentoraTextStyle

    init(style: MentoraTextStyle) {
        self.style = style
        _scaledSize = ScaledMetric(wrappedValue: style.metrics.fontSize, relativeTo: style.anchor)
    }

    func body(content: Content) -> some View {
        let isArabic = MentoraTypographyRules.isArabic(locale)
        return content
            .font(.system(size: scaledSize,
                          weight: MentoraTypographyRules.weight(for: style)))
            .tracking(MentoraTypographyRules.tracking(for: style, isArabic: isArabic))
            .lineSpacing(MentoraTypographyRules.lineSpacing(for: style,
                                                            scaledSize: scaledSize,
                                                            isArabic: isArabic))
    }
}

extension View {
    /// `design-system/platform-mapping.md § 2`'s LOCKED handle: `.mentoraFont(.h1)`.
    ///
    /// Apply this LAST among font-affecting modifiers -- a later `.font(...)`/`.tracking(...)`/
    /// `.lineSpacing(...)` applied outside it wins and silently discards the token metrics.
    /// Never pair with `.minimumScaleFactor` or `.dynamicTypeSize(...max)` to "fix" a layout:
    /// the layout gives way, per `PHASE_5_IOS_SYSTEM_DESIGN.md §§ 11, 21` and `CONTENT_RESILIENCE.md § 8`.
    func mentoraFont(_ style: MentoraTextStyle) -> some View {
        modifier(MentoraFontModifier(style: style))
    }
}
