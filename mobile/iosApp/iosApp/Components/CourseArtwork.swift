import SwiftUI
import shared

// Phase 5 Task T11 slice 1 (Component Kit B, composites) -- `design-to-code/shared/artwork.json`,
// the governed 5-motif course-artwork system. Governs every place a course thumbnail renders
// (CourseCard, CourseProgressCard, Course Details hero, Checkout line item, Learning Path Details
// course rows -- later T11 slices wire real course data into `CourseThumbnail`/`CourseArtwork` below).
//
// Ported from Android's own already-built, CI-green reference implementation,
// `androidApp/.../ui/components/CourseArtwork.kt`, which itself transcribes `artwork.json` verbatim --
// see that file's own kdoc for the full disclosed-simplification rationale, summarized here:
//
// **Composition recipe implemented here (artwork.json `compositionRecipe`):**
// 1. Base -- a dark purple/indigo `linear-gradient(135deg, C1 0%, C2 X%, C3 100%)`, one of 5.
// 2. Motif -- one centered white geometric icon naming the subject.
// 3. Light source -- a soft directional radial highlight for depth.
// 4. Category chip -- overlaid on the artwork's own scrim, pinned to the logical-start corner
//    (`CourseArtworkWithChip`, not `CourseArtwork` itself -- the chip composition lives one level up
//    so `CourseArtwork` stays a pure "just the artwork" primitive).
//
// **Disclosed simplification -- fine texture layers omitted.** Every motif's `gradient` string in
// `artwork.json` also layers a `repeating-linear-gradient`/`repeating-radial-gradient` fine line/dot
// texture on top of the base+highlight. SwiftUI has no direct primitive for an infinitely-repeating
// fine CSS pattern brush any more than Compose does -- reproducing it exactly would mean a custom
// per-motif `Canvas` tiling loop for a purely decorative, very subtle (<=0.16 alpha) detail. Per the
// task brief's own explicit allowance (mirroring Android's identical, already-reviewed decision), this
// is an acceptable, disclosed simplification: the two most important layers (the exact dominant
// `linear-gradient` base, transcribed stop-for-stop from `artwork.json`, and the radial highlight at
// its exact cited center/alpha/radius) are implemented faithfully, and -- per `artwork.json`'s own
// `constraintsPreventingGenericSameness` -- the 5 distinct motif icons + deterministic per-category
// gradient assignment already satisfy the "not a generic purple rectangle" constraint without the fine
// texture. The texture is not silently dropped; it is this comment.
//
// **Gradient-angle approximation.** SwiftUI's `LinearGradient` has no CSS-angle parameter -- its
// `startPoint`/`endPoint` are `UnitPoint`s. `.topLeading` -> `.bottomTrailing` (the built-in diagonal
// corner points) is the closest SwiftUI primitive to CSS's `135deg` (which, for a roughly-16:9 box,
// points from upper-left toward lower-right, the same general direction as that diagonal) -- an
// approximation, not an exact angle match, disclosed here rather than silently assumed equivalent.
// Exactly Android's own disclosed `Offset.Zero` -> `Offset(size.width, size.height)` approximation,
// translated to SwiftUI's idiomatic equivalent.

// MARK: - CourseMotif

/// The 5 category-motif pairs (`artwork.json#/motifSystem/motifs`), transcribed verbatim from
/// Android's `CourseMotif` enum (itself transcribed verbatim from `artwork.json`). Declaration order
/// matches Android's `CourseMotif.entries` order exactly (`analytics`=0, `design`=1, `code`=2,
/// `grid`=3, `layers`=4) -- `courseArtworkHash`'s `% 5` result indexes into `CaseIterable.allCases`,
/// so this order is load-bearing, not cosmetic.
enum CourseMotif: CaseIterable, Hashable {
    case analytics, design, code, grid, layers

    /// One `linear-gradient(135deg, ...)` stop -- a governed literal value per `artwork.json`'s own
    /// `governanceNote` ("these bases and motifs are an artwork system, not application UI colours --
    /// they must never enter the semantic colour tokens"), hence the hex colors below are literal, not
    /// `Color.mentora*` semantic tokens.
    struct BaseStop {
        let fraction: CGFloat
        let color: Color
    }

    /// Centered white motif icon (`Theme/MentoraIcon.swift`'s `MentoraIconName`).
    var icon: MentoraIconName {
        switch self {
        case .analytics: return .courseAnalytics
        case .design: return .courseDesign
        case .code: return .courseCode
        case .grid: return .courseGrid
        case .layers: return .courseLayers
        }
    }

    /// `linear-gradient(135deg, ...)` base layer stops, transcribed verbatim from `artwork.json`
    /// (via Android's `baseStops`).
    var baseStops: [BaseStop] {
        switch self {
        case .analytics:
            return [
                BaseStop(fraction: 0, color: Color(mentoraArtworkHex: 0x241C5C)),
                BaseStop(fraction: 0.58, color: Color(mentoraArtworkHex: 0x4A3EB0)),
                BaseStop(fraction: 1, color: Color(mentoraArtworkHex: 0x4A62F0)),
            ]
        case .design:
            return [
                BaseStop(fraction: 0, color: Color(mentoraArtworkHex: 0x35257F)),
                BaseStop(fraction: 0.60, color: Color(mentoraArtworkHex: 0x6558D3)),
                BaseStop(fraction: 1, color: Color(mentoraArtworkHex: 0x7C4DFF)),
            ]
        case .code:
            return [
                BaseStop(fraction: 0, color: Color(mentoraArtworkHex: 0x1C2470)),
                BaseStop(fraction: 0.50, color: Color(mentoraArtworkHex: 0x3B4278)),
                BaseStop(fraction: 1, color: Color(mentoraArtworkHex: 0x4A62F0)),
            ]
        case .grid:
            return [
                BaseStop(fraction: 0, color: Color(mentoraArtworkHex: 0x3B2A7A)),
                BaseStop(fraction: 0.55, color: Color(mentoraArtworkHex: 0x6558D3)),
                BaseStop(fraction: 1, color: Color(mentoraArtworkHex: 0x7C4DFF)),
            ]
        case .layers:
            return [
                BaseStop(fraction: 0, color: Color(mentoraArtworkHex: 0x191A20)),
                BaseStop(fraction: 0.55, color: Color(mentoraArtworkHex: 0x2B2170)),
                BaseStop(fraction: 1, color: Color(mentoraArtworkHex: 0x3B4278)),
            ]
        }
    }

    /// `radial-gradient(circle at X% Y%, ...)` light-source center, as a fraction of the artwork's
    /// own size.
    var highlightCenterFraction: UnitPoint {
        switch self {
        case .analytics: return UnitPoint(x: 0.84, y: 0.16)
        case .design: return UnitPoint(x: 0.76, y: 0.78)
        case .code: return UnitPoint(x: 0.72, y: 0.50)
        case .grid: return UnitPoint(x: 0.20, y: 0.20)
        case .layers: return UnitPoint(x: 0.18, y: 0.82)
        }
    }

    /// The highlight's `rgba(255,255,255,A)` alpha.
    var highlightAlpha: Double {
        switch self {
        case .analytics: return 0.22
        case .design: return 0.20
        case .code: return 0.22
        case .grid: return 0.20
        case .layers: return 0.18
        }
    }

    /// The highlight's `transparent R%` radius, as a fraction of the artwork's longer side.
    var highlightRadiusFraction: CGFloat {
        switch self {
        case .analytics: return 0.48
        case .design: return 0.42
        case .code: return 0.30
        case .grid: return 0.46
        case .layers: return 0.44
        }
    }
}

/// A `0xRRGGBB` literal color at full opacity -- SwiftUI has no native hex-literal `Color` initializer
/// (unlike Compose's `Color(0xFFRRGGBB)`); this local helper is scoped to this file's own governed
/// literal-value exception (see `CourseMotif.BaseStop`'s doc comment above) and is not a general-
/// purpose color utility for the rest of the app.
private extension Color {
    init(mentoraArtworkHex hex: UInt32) {
        let r = Double((hex >> 16) & 0xFF) / 255
        let g = Double((hex >> 8) & 0xFF) / 255
        let b = Double(hex & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}

/// The highlight layer's `rgba(255,255,255,A)` white and the centered motif icon's own "white
/// geometric icon" (`artwork.json` `compositionRecipe` items 2-3) -- expressed via the same governed-
/// literal-hex helper above (`0xFFFFFF`), deliberately NOT `Color.white`/`.white`: this codebase's
/// `tools/ios-checks/theme-checks.js` Check Group B bans the raw system color name `white` (and every
/// other `Color.<name>`/`.<name>` system-color literal) over ALL production Swift, with `Color.clear`
/// as the ONLY sanctioned exception (`SANCTIONED_EXCEPTIONS.colorClearExcluded`) -- there is no
/// carve-out for this file's own governed-artwork-literal exception, so this stays inside that same
/// hex-literal mechanism rather than tripping Check B1/B2.
private let mentoraArtworkWhite = Color(mentoraArtworkHex: 0xFFFFFF)

// MARK: - Deterministic hash / motif assignment

/// `artwork.json`'s `assignmentRule`, transcribed exactly: `hash = (hash*31 + charCode) >>> 0` over
/// the seed string, `motif index = hash % 5`.
///
/// Swift's native `Int` is 64-bit, so the 32-bit two's-complement wraparound JS's per-character
/// `>>> 0` truncation (and Kotlin's `Int` overflow) produce must be replicated explicitly: accumulate
/// in `Int32` using the wrapping operators `&*`/`&+` (giving the identical 32-bit bit pattern Kotlin's
/// `Int` overflow produces), then reinterpret that final bit pattern as `UInt32` (`UInt32(bitPattern:)`
/// -- which reinterprets the same bits rather than trapping on a negative value) before `% 5`, exactly
/// matching Android's own `courseArtworkHash`/`motifFor` split. The `UInt32` reinterpretation is NOT
/// optional: a negative signed 32-bit value and its unsigned 32-bit bit-twin are NOT congruent mod 5
/// for the same bits (2^32 ≡ 1 mod 5, not 0) -- see Android's `CourseArtwork.kt` doc comment for the
/// same reasoning.
///
/// Kotlin's `Char` (this function's Kotlin twin's iteration unit) is a UTF-16 code unit -- this
/// function iterates `seed.utf16` (not `seed` as `Character`s) to match exactly for any seed
/// containing non-ASCII text, e.g. this app's real Arabic category names, where a Swift `Character`
/// can be a multi-UTF-16-code-unit extended grapheme cluster.
func courseArtworkHash(seed: String) -> Int32 {
    var hash: Int32 = 0
    for unit in seed.utf16 {
        hash = hash &* 31 &+ Int32(unit)
    }
    return hash
}

/// `artwork.json`'s `assignmentRule` in full: deterministic per-seed, never random per render, so the
/// same course/category always renders the same motif everywhere it appears. Callers pass
/// `categoryId` (preferred) falling back to `courseId` -- see `CourseThumbnail`'s own seed selection.
func motifFor(seed: String) -> CourseMotif {
    let unsignedHash = UInt32(bitPattern: courseArtworkHash(seed: seed))
    let index = Int(unsignedHash % 5)
    return CourseMotif.allCases[index]
}

// MARK: - CourseArtwork

/// Renders just the base+highlight gradient and centered motif icon (layers 1-3 of the composition
/// recipe) -- no category chip (layer 4, `CourseArtworkWithChip`) and no image-fallback logic
/// (`CourseThumbnail`). 16:9 aspect ratio per `artwork.json`'s own convention ("scales from an 88px
/// list thumbnail to a full-width hero without re-composition").
struct CourseArtwork: View {
    let motif: CourseMotif

    /// Decorative by default (`nil`) -- the caller supplies the real accessible name one level up
    /// (e.g. `CourseThumbnail`), matching Android's own `contentDescription: String?` handling. When
    /// non-nil, applied to the whole view via `.accessibilityLabel(_:)`.
    var accessibilityLabel: String? = nil

    var body: some View {
        Group {
            if let accessibilityLabel {
                artwork.accessibilityLabel(accessibilityLabel)
            } else {
                artwork
            }
        }
    }

    private var artwork: some View {
        GeometryReader { geometry in
            ZStack {
                LinearGradient(
                    gradient: Gradient(stops: motif.baseStops.map { Gradient.Stop(color: $0.color, location: $0.fraction) }),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                RadialGradient(
                    gradient: Gradient(colors: [mentoraArtworkWhite.opacity(motif.highlightAlpha), .clear]),
                    center: motif.highlightCenterFraction,
                    startRadius: 0,
                    endRadius: max(geometry.size.width, geometry.size.height) * motif.highlightRadiusFraction
                )
                MentoraIcon(name: motif.icon, size: MentoraIconSize.large)
                    .foregroundStyle(mentoraArtworkWhite)
                    // Decorative -- the Group above already carries the accessible name (when the
                    // caller supplies one); this icon must never be announced as its own element.
                    .accessibilityHidden(true)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
        }
        .aspectRatio(16 / 9, contentMode: .fit)
    }
}

// MARK: - CourseThumbnail

/// `artwork.json`'s `fallbackBehavior`: "the artwork system itself IS the fallback, not a secondary
/// fallback behind it." If `thumbnailUrl` is present and non-blank, loads it via `AsyncImage`; on a
/// `.failure` phase, swaps to `CourseArtwork` in its place -- never a generic broken-image icon.
///
/// **SwiftUI vs. the web spec's `detection` field.** `artwork.json`'s `detection` field describes a
/// web-specific gotcha: an `<img>` can return a 200 response with `naturalWidth === 0` (a broken image
/// that never fires a plain `onError`), needing an extra `onLoad`/rAF check on top of `onError`.
/// `AsyncImage`'s pipeline doesn't have that failure mode: its `.success` phase only ever wraps an
/// `Image` built from an already-successfully-decoded `UIImage`/`NSImage` -- a corrupt/undecodable
/// response fails that decode step and surfaces as `.failure` instead, the same reasoning Android's own
/// doc comment gives for why Coil's `AsyncImagePainter.State.Error` alone is sufficient there (a
/// successful Coil `Success` state likewise always wraps a decoded, non-zero-dimension `Bitmap`). This
/// is disclosed as the expected, documented `AsyncImage` behavior (not independently verified against a
/// real corrupt-JPEG device test in this slice) -- the platform-appropriate equivalent Android's own
/// doc comment already argues for, not a lesser check.
///
/// `mediaId` is accepted for a later task (real SDK media wiring) to pass alongside a resolved
/// `thumbnailUrl` -- unlike Coil, `AsyncImage` has no request-level memory-cache-key parameter to pass
/// it to, so it is currently unused beyond being accepted, matching Android's own disclosed "accepted
/// for a later task" scope for this same parameter.
struct CourseThumbnail: View {
    let mediaId: String?
    let thumbnailUrl: String?
    let seed: String
    let categoryId: String?
    let accessibilityLabel: String

    /// `artwork.json` `assignmentRule`: `categoryId` preferred, `courseId` (`seed`) fallback --
    /// matches Android's `categoryId?.takeIf { it.isNotBlank() } ?: seed` exactly (blank, not merely
    /// non-nil: whitespace-only category ids fall back to `seed` too).
    private var effectiveSeed: String {
        if let categoryId, !categoryId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return categoryId
        }
        return seed
    }

    private var motif: CourseMotif { motifFor(seed: effectiveSeed) }

    private var resolvedURL: URL? {
        guard let thumbnailUrl,
              !thumbnailUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return URL(string: thumbnailUrl)
    }

    var body: some View {
        Group {
            if let resolvedURL {
                AsyncImage(url: resolvedURL) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .clipped()
                    case .failure:
                        CourseArtwork(motif: motif, accessibilityLabel: accessibilityLabel)
                    case .empty:
                        // Loading placeholder -- deliberately plain (no shimmer here, that's
                        // `LoadingState`'s job in a later T11 slice, out of scope for this file).
                        Color.clear
                    @unknown default:
                        Color.clear
                    }
                }
                .accessibilityLabel(accessibilityLabel)
            } else {
                CourseArtwork(motif: motif, accessibilityLabel: accessibilityLabel)
            }
        }
        .aspectRatio(16 / 9, contentMode: .fit)
    }
}

// MARK: - CourseThumbnailTopCornersShape

/// `CourseThumbnail`/`CourseArtwork` top-corners-only clip for embedding as a card's top slice in a
/// later T11 slice (e.g. a `CourseCard`'s thumbnail: radius.large top corners, square bottom corners
/// where the card body begins). Mirrors Android's identical `CourseThumbnailTopCornersShape`
/// (`RoundedCornerShape(topStart = large, topEnd = large)`), which is declared locally in its own
/// `CourseArtwork.kt`, not the shared Compose shape-token file -- for the same reason this is declared
/// here rather than as a new `Theme/MentoraShape.swift` `Step` case: that file's own `.sheetTop` case
/// hardcodes `.xlarge`'s radius (24) for a *different*, already-named use
/// (`componentUsage.bottomSheet`) -- there is no existing `MentoraShape` step for "top corners only at
/// radius.large," and growing that shared token enum for this one call site would be an unrequested
/// scope expansion of a different task's file. `UnevenRoundedRectangle` (iOS 17+, this project's own
/// deployment target -- already used inside `MentoraShape.swift`'s own `.sheetTop` case) is the correct
/// native primitive, used directly here the same way.
let CourseThumbnailTopCornersShape = UnevenRoundedRectangle(
    topLeadingRadius: MentoraRadius.large,
    bottomLeadingRadius: 0,
    bottomTrailingRadius: 0,
    topTrailingRadius: MentoraRadius.large,
    style: .circular
)

// MARK: - CourseArtworkWithChip

/// `artwork.json`'s `categoryChipOverlay` (layer 4 of the composition recipe): `CourseThumbnail` with
/// `CategoryChip(_:state:.onImageOverlay)` (already built in Task T8 -- reused, not rebuilt) pinned to
/// the *logical*-start corner over the artwork's own scrim.
///
/// `.onImageOverlay`'s own background (`Color.mentoraOverlayChipScrim`, confirmed by reading
/// `Components/CategoryChip.swift`'s `CategoryChipState.backgroundColor` directly) already paints the
/// chip's own scrim-aware chrome -- this view adds no extra scrim layer of its own, it only positions
/// the chip.
///
/// `.topLeading` is SwiftUI's direction-aware alignment (flips for RTL automatically), exactly
/// "logical start," not a hardcoded physical corner -- this codebase already relies on this same
/// direction-awareness pervasively for its own text/stack alignment (e.g. `Features/Auth/LoginView.swift`
/// and `RegisterView.swift`'s `VStack(alignment: .leading, ...)`/`.frame(..., alignment: .leading)`,
/// which must already flip correctly for this app's real Arabic/RTL locale support), so no manual RTL
/// branching is needed here either.
struct CourseArtworkWithChip: View {
    let seed: String
    let categoryId: String?
    /// Already-resolved display text -- never resolves `MentoraStrings` itself, matching every other
    /// atom/composite in this kit.
    let categoryLabel: String
    let accessibilityLabel: String
    var mediaId: String? = nil
    var thumbnailUrl: String? = nil
    var thumbnailShape: (any Shape)? = nil

    var body: some View {
        content
            .aspectRatio(16 / 9, contentMode: .fit)
    }

    @ViewBuilder
    private var content: some View {
        if let thumbnailShape {
            thumbnailWithChip.clipShape(thumbnailShape)
        } else {
            thumbnailWithChip
        }
    }

    private var thumbnailWithChip: some View {
        CourseThumbnail(
            mediaId: mediaId,
            thumbnailUrl: thumbnailUrl,
            seed: seed,
            categoryId: categoryId,
            accessibilityLabel: accessibilityLabel
        )
        .overlay(alignment: .topLeading) {
            CategoryChip(categoryLabel, state: .onImageOverlay)
                .padding(MentoraSpacing.space2)
        }
    }
}

#if DEBUG

private struct CourseArtworkSwatches: View {
    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            ForEach(CourseMotif.allCases, id: \.self) { motif in
                CourseArtwork(motif: motif)
                    .frame(width: 240)
            }
        }
    }
}

#Preview("CourseArtwork -- all 5 motifs") {
    MentoraPreviewHost(title: "CourseArtwork -- Light / en", theme: .light, locale: .english) {
        CourseArtworkSwatches()
    }
}

#Preview("CourseArtworkWithChip -- category chip overlay") {
    MentoraPreviewHost(title: "CourseArtworkWithChip -- Light / en", theme: .light, locale: .english) {
        CourseArtworkWithChip(
            seed: "course-123",
            categoryId: "Software Development",
            categoryLabel: "Software Development",
            accessibilityLabel: "Software Development course artwork"
        )
        .frame(width: 240)
    }
}

#endif
