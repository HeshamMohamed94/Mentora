import SwiftUI
import shared

// Phase 5 Task T6, sub-slice 3c — the token gallery named as T6's Manual verification / MC-2 item
// ("A token gallery preview in light+dark, en+ar, default and AX text sizes — the iOS analogue of
// Android's TokenSwatchPreview", PHASE_5_IOS_IMPLEMENTATION_PLAN.md T6). `import SwiftUI` and
// `import shared` are BOTH required directly, not merely transitively via another file's import of
// `shared` elsewhere in this target — module imports are not transitive in Swift, the exact mistake an
// earlier T6 slice's review caught (a missing `import SwiftUI`, D118).
//
// THIS FILE IS PREVIEW-ONLY DEBUG TOOLING, NOT APP UI. It is NEVER referenced from `MentoraApp.swift`
// or any Feature/Component, has NO navigation/route wiring, and its content-bearing body below is
// wrapped in exactly one Debug-only compilation directive (opens immediately after these imports,
// closes at the very end of this file) — it does not exist at all in a Release build. Every visual
// value rendered here comes from a generated token (`Theme/MentoraTokens.swift`, `Color+Mentora.swift`)
// or a `CaseIterable` design-system enum (`MentoraTextStyle`, `MentoraShape.Step`,
// `MentoraElevationLevel`, `MentoraIconName`) — zero hand-duplicated values, with the sole necessary
// exception of `galleryColors` below (a hand list of the 46 color accessor NAMES, gated by
// `tools/ios-checks/theme-checks.js`'s E3 check so it can never silently drift from
// `Color+Mentora.swift`).
//
// NON-USER-FACING TEXT, T7 CARVE-OUT (record this now so it doesn't look like an oversight later):
// every English/Arabic sample sentence and every enum `.rawValue` label rendered below is debug-only
// text, never shown to a real user. This file is deliberately EXCLUDED from Task T7's future "no
// user-facing literal in the app target" completion gate — see `execution/DECISIONS_LOG.md` D122 for
// the recorded exclusion.
//
// TWO OPEN RISKS FOR THE REAL macOS CI RUN TO ANSWER (deliberately NOT pre-solved here — this
// project's own hard-learned lesson from T6 slice 3b's 3 CI rounds is to never guess unconfirmed
// platform/Preview-API behavior on this Windows host):
//   1. Whether the Xcode preview canvas actually honors `.preferredColorScheme` (applied transitively
//      via `.mentoraTheme(...)` below) for asset-catalog color resolution. If it does not, the
//      documented fallback is `.environment(\.colorScheme, ...)` applied directly on the preview (NOT
//      gated by Check C1, which only covers a literal `preferredColorScheme(` call) or `#Preview
//      (traits:)`.
//   2. Whether Preview-macro compilation needs an explicit "enable previews" build setting in
//      `project.yml`'s Debug configuration for this XcodeGen-generated project on Xcode 16.4. Do NOT
//      add that setting preemptively — only if the real CI build actually fails on these preview
//      macros, since it is known to inject codegen flags that could have other effects.
//
// Real API shapes below were read directly from the referenced files, never guessed:
// `MentoraTextStyle`/`.mentoraFont(_:)` (`Theme/MentoraTypography.swift`), `MentoraShape.Step`
// (`Theme/MentoraShape.swift`), `MentoraElevationLevel`/`.mentoraElevation(_:in:fill:border:)`
// (`Theme/MentoraElevation.swift`), `MentoraIconName`/`MentoraIcon` (`Theme/MentoraIcon.swift`),
// `MentoraSpacing`/`MentoraBorderWidth` (`Theme/MentoraTokens.swift`), `.mentoraTheme(theme:locale:)`
// (`Theme/MentoraTheme.swift`), `ThemePreference`/`AppLocale` (`mobile/shared`, real SKIE shape
// confirmed by T6 slice 3b/D121: plain frozen Swift enums `.light`/`.dark`/`.system` and
// `.english`/`.arabic`).

#if DEBUG

/// One color swatch row: the accessor NAME (also used as its on-screen label) paired with the `Color`
/// value itself. The two are supplied independently at each call site below so
/// `tools/ios-checks/theme-checks.js`'s E3 check can assert they always name the same accessor.
private struct GalleryColor: Identifiable {
    let name: String
    let color: Color
    var id: String { name }
}

/// Every non-shadow semantic color accessor in `Theme/Color+Mentora.swift` (46 entries) — the one
/// necessary hand-authored list in this file, verified against that file directly rather than
/// reconstructed from memory, and gated by `theme-checks.js`'s E3 check so it can never silently drift.
/// `mentoraShadowElevation0...4` are deliberately excluded (near-transparent, not useful as flat
/// swatches — the Elevation section below shows them in actual use instead).
private let galleryColors: [GalleryColor] = [
    GalleryColor(name: "mentoraBackgroundPrimary", color: .mentoraBackgroundPrimary),
    GalleryColor(name: "mentoraBackgroundSecondary", color: .mentoraBackgroundSecondary),
    GalleryColor(name: "mentoraSurfaceDefault", color: .mentoraSurfaceDefault),
    GalleryColor(name: "mentoraSurfaceElevated", color: .mentoraSurfaceElevated),
    GalleryColor(name: "mentoraSurfaceVariant", color: .mentoraSurfaceVariant),
    GalleryColor(name: "mentoraSurfaceInverse", color: .mentoraSurfaceInverse),
    GalleryColor(name: "mentoraTextPrimary", color: .mentoraTextPrimary),
    GalleryColor(name: "mentoraTextSecondary", color: .mentoraTextSecondary),
    GalleryColor(name: "mentoraTextDisabled", color: .mentoraTextDisabled),
    GalleryColor(name: "mentoraTextInverse", color: .mentoraTextInverse),
    GalleryColor(name: "mentoraTextLink", color: .mentoraTextLink),
    GalleryColor(name: "mentoraBorderDefault", color: .mentoraBorderDefault),
    GalleryColor(name: "mentoraBorderStrong", color: .mentoraBorderStrong),
    GalleryColor(name: "mentoraBorderFocus", color: .mentoraBorderFocus),
    GalleryColor(name: "mentoraBorderError", color: .mentoraBorderError),
    GalleryColor(name: "mentoraBrandPrimary", color: .mentoraBrandPrimary),
    GalleryColor(name: "mentoraBrandPrimaryHover", color: .mentoraBrandPrimaryHover),
    GalleryColor(name: "mentoraBrandPrimaryPressed", color: .mentoraBrandPrimaryPressed),
    GalleryColor(name: "mentoraBrandPrimaryContainer", color: .mentoraBrandPrimaryContainer),
    GalleryColor(name: "mentoraBrandOnPrimary", color: .mentoraBrandOnPrimary),
    GalleryColor(name: "mentoraBrandOnPrimaryContainer", color: .mentoraBrandOnPrimaryContainer),
    GalleryColor(name: "mentoraBrandOnSurfaceInverse", color: .mentoraBrandOnSurfaceInverse),
    GalleryColor(name: "mentoraSecondaryDefault", color: .mentoraSecondaryDefault),
    GalleryColor(name: "mentoraSecondaryHover", color: .mentoraSecondaryHover),
    GalleryColor(name: "mentoraSecondaryContainer", color: .mentoraSecondaryContainer),
    GalleryColor(name: "mentoraSecondaryOnSecondary", color: .mentoraSecondaryOnSecondary),
    GalleryColor(name: "mentoraSecondaryOnSecondaryContainer", color: .mentoraSecondaryOnSecondaryContainer),
    GalleryColor(name: "mentoraAccentDefault", color: .mentoraAccentDefault),
    GalleryColor(name: "mentoraSuccessDefault", color: .mentoraSuccessDefault),
    GalleryColor(name: "mentoraSuccessContainer", color: .mentoraSuccessContainer),
    GalleryColor(name: "mentoraSuccessOnSuccess", color: .mentoraSuccessOnSuccess),
    GalleryColor(name: "mentoraSuccessOnSuccessContainer", color: .mentoraSuccessOnSuccessContainer),
    GalleryColor(name: "mentoraWarningDefault", color: .mentoraWarningDefault),
    GalleryColor(name: "mentoraWarningContainer", color: .mentoraWarningContainer),
    GalleryColor(name: "mentoraWarningOnWarning", color: .mentoraWarningOnWarning),
    GalleryColor(name: "mentoraWarningOnWarningContainer", color: .mentoraWarningOnWarningContainer),
    GalleryColor(name: "mentoraErrorDefault", color: .mentoraErrorDefault),
    GalleryColor(name: "mentoraErrorContainer", color: .mentoraErrorContainer),
    GalleryColor(name: "mentoraErrorOnError", color: .mentoraErrorOnError),
    GalleryColor(name: "mentoraErrorOnErrorContainer", color: .mentoraErrorOnErrorContainer),
    GalleryColor(name: "mentoraInfoDefault", color: .mentoraInfoDefault),
    GalleryColor(name: "mentoraInfoContainer", color: .mentoraInfoContainer),
    GalleryColor(name: "mentoraInfoOnInfo", color: .mentoraInfoOnInfo),
    GalleryColor(name: "mentoraInfoOnInfoContainer", color: .mentoraInfoOnInfoContainer),
    GalleryColor(name: "mentoraOverlayScrim", color: .mentoraOverlayScrim),
    GalleryColor(name: "mentoraOverlayChipScrim", color: .mentoraOverlayChipScrim),
]

/// Debug-only, non-user-facing sample text (see the T7 carve-out note above). Reused across the
/// Typography section's 12 style rows so every row renders the identical multiline content — leading/
/// line-height differences are otherwise invisible on a single line.
private let gallerySampleParagraphEnglish =
    "The quick brown fox jumps over the lazy dog.\nDynamic Type scales this paragraph automatically."

/// Debug-only, non-user-facing sample text for the Arabic-vs-Latin leading comparison section.
private let gallerySampleParagraphArabic =
    "الثعلب البني السريع يقفز فوق الكلب الكسول.\nهذا نص تجريبي لمقارنة الارتفاع بين الأسطر."

/// The gallery's root content view. Never instantiated outside a `#Preview` in this file.
private struct MentoraTokenGallery: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MentoraSpacing.space8) {
                colorsSection
                typographySection
                arabicSampleSection
                shapesSection
                elevationSection
                iconsSection
            }
            .padding(MentoraSpacing.space4)
        }
        .frame(maxWidth: .infinity)
        .background(Color.mentoraBackgroundPrimary.ignoresSafeArea())
    }

    // MARK: - Colors

    private var colorsSection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            Text("Colors").mentoraFont(.h3)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 96), spacing: MentoraSpacing.space3)],
                spacing: MentoraSpacing.space3
            ) {
                ForEach(galleryColors) { entry in
                    VStack(spacing: MentoraSpacing.space1) {
                        MentoraShape(.small)
                            .fill(entry.color)
                            .frame(height: 48)
                            .overlay(
                                MentoraShape(.small)
                                    .strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.`default`)
                            )
                        Text(entry.name).mentoraFont(.caption)
                    }
                }
            }
        }
    }

    // MARK: - Typography

    private var typographySection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space5) {
            Text("Typography").mentoraFont(.h3)
            ForEach(MentoraTextStyle.allCases, id: \.self) { style in
                VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
                    Text(style.rawValue).mentoraFont(.caption)
                    Text(gallerySampleParagraphEnglish).mentoraFont(style)
                }
            }
        }
    }

    // MARK: - Arabic sample (leading comparison)

    private var arabicSampleSection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            Text("Arabic / Latin leading comparison (bodyMedium)").mentoraFont(.h3)
            HStack(alignment: .top, spacing: MentoraSpacing.space5) {
                VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
                    Text("ar").mentoraFont(.caption)
                    Text(gallerySampleParagraphArabic).mentoraFont(.bodyMedium)
                }
                VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
                    Text("en").mentoraFont(.caption)
                    Text(gallerySampleParagraphEnglish).mentoraFont(.bodyMedium)
                }
            }
        }
    }

    // MARK: - Shapes

    private var shapesSection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            Text("Shapes").mentoraFont(.h3)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 96), spacing: MentoraSpacing.space3)],
                spacing: MentoraSpacing.space3
            ) {
                ForEach(MentoraShape.Step.allCases, id: \.self) { step in
                    VStack(spacing: MentoraSpacing.space1) {
                        MentoraShape(step)
                            .fill(Color.mentoraBrandPrimaryContainer)
                            .frame(width: 64, height: 48)
                            .overlay(
                                MentoraShape(step)
                                    .strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.`default`)
                            )
                        Text(step.rawValue).mentoraFont(.caption)
                    }
                }
            }
        }
    }

    // MARK: - Elevation

    private var elevationSection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            Text("Elevation").mentoraFont(.h3)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 96), spacing: MentoraSpacing.space5)],
                spacing: MentoraSpacing.space5
            ) {
                ForEach(MentoraElevationLevel.allCases, id: \.self) { level in
                    VStack(spacing: MentoraSpacing.space2) {
                        Color.clear
                            .frame(width: 64, height: 48)
                            .mentoraElevation(level)
                        Text(level.rawValue).mentoraFont(.caption)
                    }
                }
            }
        }
    }

    // MARK: - Icons

    private var iconsSection: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            Text("Icons").mentoraFont(.h3)
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 72), spacing: MentoraSpacing.space3)],
                spacing: MentoraSpacing.space3
            ) {
                ForEach(MentoraIconName.allCases, id: \.self) { name in
                    VStack(spacing: MentoraSpacing.space1) {
                        MentoraIcon(name: name)
                            .foregroundStyle(Color.mentoraTextPrimary)
                        Text(name.rawValue).mentoraFont(.caption)
                    }
                }
            }
        }
    }
}

/// Routes every preview variation through the REAL production `.mentoraTheme(theme:locale:)` entry
/// point (`Theme/MentoraTheme.swift`, T6 slice 3b) rather than injecting `.environment(\.locale, ...)`
/// or `.preferredColorScheme(...)` directly — this both keeps the gallery from violating Checks
/// C1/C2/C3/C5 itself (a second, uncontrolled theme-application site) and means the gallery exercises
/// the real shipping theme-application path end to end, not a preview-only mock. `theme`/`locale` are
/// plain (non-optional) values; `.mentoraTheme(theme:locale:)`'s `locale` parameter is `AppLocale?`, so
/// passing a non-optional `AppLocale` here promotes implicitly, per ordinary Swift optional-parameter
/// calling convention.
private func galleryPreview(
    theme: ThemePreference,
    locale: AppLocale,
    dynamicType: DynamicTypeSize
) -> some View {
    MentoraTokenGallery()
        .mentoraTheme(theme: theme, locale: locale)
        .dynamicTypeSize(dynamicType)
}

// MARK: - Previews — full 2x2x2 matrix: light/dark x en/ar x default/AX5.
// "default" = .large (the standard Dynamic Type size). "AX5" = .accessibility5, matching slices 1/2's
// own accessibility-size convention (MentoraTypographyTests.swift / MentoraTypographyGeometryTests.swift).

#Preview("Light / en / default") {
    galleryPreview(theme: .light, locale: .english, dynamicType: .large)
}

#Preview("Dark / en / default") {
    galleryPreview(theme: .dark, locale: .english, dynamicType: .large)
}

#Preview("Light / ar / default") {
    galleryPreview(theme: .light, locale: .arabic, dynamicType: .large)
}

#Preview("Dark / ar / default") {
    galleryPreview(theme: .dark, locale: .arabic, dynamicType: .large)
}

#Preview("Light / en / AX5") {
    galleryPreview(theme: .light, locale: .english, dynamicType: .accessibility5)
}

#Preview("Dark / en / AX5") {
    galleryPreview(theme: .dark, locale: .english, dynamicType: .accessibility5)
}

#Preview("Light / ar / AX5") {
    galleryPreview(theme: .light, locale: .arabic, dynamicType: .accessibility5)
}

#Preview("Dark / ar / AX5") {
    galleryPreview(theme: .dark, locale: .arabic, dynamicType: .accessibility5)
}

#endif
