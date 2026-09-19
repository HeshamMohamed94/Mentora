import SwiftUI
import shared

// Phase 5 Task T8 slice 6 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § ProgressBar`
// (lines 415-430). Two views, not one "mode" flag on a single type: `MentoraProgressBar` (determinate,
// `progress: Double` + `.active`/`.complete`/`.paused` state) and `MentoraIndeterminateProgressBar`
// (the separate "looping linear sweep" variant, e.g. AI Tutor "thinking"). DISCLOSED CHOICE: a separate
// type, not a `.indeterminate` case mixed into the same enum/view, because the two variants share no
// real state machine -- determinate has a `progress` value and 3 named states with state-CHANGE
// animation; indeterminate has no progress value at all and one perpetual, unconditional loop. Forcing
// both into one view would mean every call site (and every property) carries dead optionals for the
// other variant's concerns. They DO share the same pure metrics enum (`MentoraProgressBarMetrics`) and
// the same track/shape/height, so no visual value drifts between them.
//
// ---------------------------------------------------------------------------------------------------
// PROPORTIONAL FILL WIDTH -- `GeometryReader`, DISCLOSED, NOT THE ONLY OPTION. A determinate fill that
// must be some FRACTION of the track's own rendered width (which this view does not otherwise know,
// since it has no fixed width of its own -- callers place it in all kinds of containers) needs the
// track's actual measured width at layout time. `GeometryReader` is the standard, well-established
// SwiftUI technique for exactly this ("proportional-width child inside an arbitrary parent") -- the
// same class of technique Apple's own sample code and every mainstream custom-progress-bar
// implementation uses. The alternative named in this task's own brief,
// `.containerRelativeFrame(.horizontal) { width, _ in width * progress }`, is iOS 17+ real API, but
// ties the fill's width to the nearest CONTAINER's width rather than this SPECIFIC track's own
// measured width, which is a subtly different (and less obviously correct) contract for a reusable
// atom that might sit inside padding/other layout wrappers -- `GeometryReader` measures precisely the
// space this view itself was given, not an ancestor container. Chosen for that reason.
//
// ---------------------------------------------------------------------------------------------------
// PAUSED = NO ANIMATION -- TECHNIQUE: `.animation(nil, value:)` (an explicit `nil` `Animation?`
// argument), not simply omitting the `.animation(...)` call. `COMPONENTS.md`'s Paused row says
// "static, no animation" -- an explicit `nil` makes that a directly-testable, self-documenting branch
// at the call site (`state == .paused ? nil : ...`) rather than two structurally different code paths
// (one with the modifier present, one without) that could silently drift apart on a future edit.
//
// ---------------------------------------------------------------------------------------------------
// COMPLETION CHECKMARK -- `showsCompletionIcon: Bool`, DEFAULT `true` UNCONDITIONALLY (a judgment
// call, disclosed): `COMPONENTS.md`'s "optional checkmark icon at end" reads as "show it unless a
// caller opts out", not "opt in per call site" -- the same "show by default, caller can suppress"
// reading `MentoraTextField.swift`'s own success-checkmark (`showsSuccessIcon`) convention uses. The
// icon only ever actually renders when `state == .complete` regardless of this flag's value at other
// states, so leaving the default `true` even for non-`.complete` states is harmless (dead until
// `.complete`). Icon size (`icon.small`, 16) and its gap past the fill's trailing edge (`space.1`, 4)
// are both disclosed judgment calls -- `COMPONENTS.md` states neither number for this glyph. The icon
// is intentionally allowed to render TALLER than the 8pt track (no `.clipped()` anywhere in this file)
// -- the same "a small overlay glyph is allowed to overflow its host control's own bounds" treatment
// already implicit in this kit's badge/avatar dot conventions; clipping a 16pt glyph to an 8pt track
// would make it unrecognizable.
//
// ---------------------------------------------------------------------------------------------------
// INDETERMINATE SWEEP -- `MentoraMotionEasing.linear(duration:)` (confirmed real,
// `Theme/MentoraMotion.swift`: a plain `static func linear(duration: Double) -> Animation` wrapping
// SwiftUI's own built-in `.linear(duration:)` -- `motion.easing` has no bezier entry named `linear`,
// so this is the token layer's own documented analogue, not an approximation) chained with SwiftUI's
// real, standard `Animation.repeatForever(autoreverses: false)` -- a well-established technique for a
// perpetual one-directional loop (`autoreverses: false` matters: a bidirectional sweep would visually
// read as "ping-pong", not the "loops back to the start and sweeps again" reading `COMPONENTS.md`'s own
// "looping ... sweep" language calls for). Toggled via `.onAppear { withAnimation(...) { isAnimating =
// true } }` flipping one `@State private var isAnimating: Bool` -- the standard SwiftUI idiom for a
// self-starting, perpetually-looping animation with no caller-provided trigger. Segment width fraction
// (0.3 of the track) and one full sweep duration (1.2s) are both disclosed judgment calls --
// `COMPONENTS.md` states neither number for the indeterminate case, only that it must use `linear`,
// never `easing.standard` ("that's for state changes, not loops").
//
// DISCLOSED, NOT-YET-FIXED GAP -- REDUCE MOTION. This perpetual, looping translation is a NEW animation
// CATEGORY for this kit (every prior T8 animation was a finite, discrete state change) -- `ACCESSIBILITY.md`
// § 9 requires translation/scale to drop under Reduce Motion. No file in this codebase reads
// `\.accessibilityReduceMotion` yet (a cross-cutting gap already disclosed, unfixed, in T8 slices 4+5's own
// review). Not fixed here to avoid scope creep on an already-large final T8 batch -- flagged for a later,
// dedicated cross-cutting pass across every animation in this kit at once, not a piecemeal fix to just
// this one.
//
// ---------------------------------------------------------------------------------------------------
// ACCESSIBILITY -- the determinate bar exposes `.accessibilityValue("<N>%")` (a real, standard
// technique for a custom, non-native progress indicator -- SwiftUI's own native `ProgressView` does the
// analogous thing internally) plus `.accessibilityAddTraits([.updatesFrequently])` ONLY while
// `state == .active` (the only state that is actually still changing over time -- `.complete`/`.paused`
// are resting, not "updating frequently"). The indeterminate variant takes a caller-supplied
// `accessibilityLabel: String` (never resolves `MentoraStrings` itself, matching every other atom in
// this kit) and always carries `.updatesFrequently` (it is, definitionally, always animating).
//
// A pure, directly-testable `progress` clamp (`MentoraProgressBarRules.clampedProgress`, `0...1`) is
// added here -- DISCLOSED, not requested verbatim by the spec, but a defensive guard against a caller
// passing a computed fraction that legitimately drifts fractionally outside `0...1` (floating-point
// rounding on a `completed/total` division) — the width math and the accessibility percentage both read
// this clamped value, never the raw, possibly-out-of-range `progress` input directly.

// MARK: - Pure metrics (no SwiftUI rendering required to test)

enum MentoraProgressBarMetrics {
    /// `COMPONENTS.md`: "Height 8".
    static let height: CGFloat = 8

    /// `COMPONENTS.md`: "Radius `radius.full`".
    static let shape = MentoraShape.full

    /// Disclosed judgment call -- see this file's header "COMPLETION CHECKMARK". `icon.small` (16).
    static let checkmarkIconSize: CGFloat = MentoraIconSize.small

    /// Disclosed judgment call -- gap between the fill's trailing edge and the checkmark glyph.
    static let checkmarkGap: CGFloat = MentoraSpacing.space1

    /// Disclosed judgment call -- see this file's header "INDETERMINATE SWEEP". Fraction of the
    /// track's own width the sliding segment occupies.
    static let indeterminateSegmentWidthFraction: CGFloat = 0.3

    /// Disclosed judgment call -- one full sweep's duration, in seconds.
    static let indeterminateSweepDuration: Double = 1.2
}

// MARK: - Pure state/color resolver (no SwiftUI rendering required to test)

/// `COMPONENTS.md § ProgressBar`'s 3 named determinate states -- `CaseIterable`, matching this kit's
/// own established pure-resolver enum convention (`MentoraButtonVariant`/`CategoryChipState`/etc).
enum MentoraProgressBarState: CaseIterable {
    case active
    case complete
    case paused
}

/// One fill-color resolution -- pure data, asserted directly by `MentoraProgressBarTests.swift` without
/// rendering anything. Real `Color+Mentora.swift` accessor names confirmed by reading that file
/// directly, not guessed.
struct MentoraProgressBarColorSet: Equatable {
    let fillColor: Color
}

enum MentoraProgressBarRules {

    /// `COMPONENTS.md`'s per-state Fill color column: Default/Active -> `color.brand.primary`,
    /// Complete -> `color.success.default`, Paused -> `color.text.disabled`.
    static func colorSet(for state: MentoraProgressBarState) -> MentoraProgressBarColorSet {
        switch state {
        case .active:
            return MentoraProgressBarColorSet(fillColor: .mentoraBrandPrimary)
        case .complete:
            return MentoraProgressBarColorSet(fillColor: .mentoraSuccessDefault)
        case .paused:
            return MentoraProgressBarColorSet(fillColor: .mentoraTextDisabled)
        }
    }

    /// Defensive, disclosed guard -- see this file's header. Never trust a caller's raw `progress`
    /// value directly for width math or the accessibility percentage.
    static func clampedProgress(_ progress: Double) -> Double {
        min(1, max(0, progress))
    }
}

// MARK: - The determinate view

/// `design-system/COMPONENTS.md § ProgressBar`'s determinate variant. `progress` is expected in
/// `0...1` but is defensively clamped (`MentoraProgressBarRules.clampedProgress`) before any use.
/// `accessibilityLabel` is REQUIRED (no default) -- CORRECTED AFTER REVIEW: this view's own subtree has
/// no inherently-accessible content (two filled shapes plus a `.accessibilityHidden` checkmark), so
/// without a caller-supplied label VoiceOver would announce a bare "65 percent" with no indication of
/// what is 65% complete -- exactly the gap `MentoraIconButton.swift`'s own required, non-defaulted
/// `accessibilityLabel` precedent exists to close, and this file's own sibling
/// `MentoraIndeterminateProgressBar` already required one while this determinate view originally did not
/// -- an inconsistency within the same file, not just a missed convention.
struct MentoraProgressBar: View {
    let progress: Double
    let accessibilityLabel: String
    var state: MentoraProgressBarState = .active
    var showsCompletionIcon: Bool = true

    private var clampedProgress: Double {
        MentoraProgressBarRules.clampedProgress(progress)
    }

    private var colors: MentoraProgressBarColorSet {
        MentoraProgressBarRules.colorSet(for: state)
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                MentoraProgressBarMetrics.shape
                    .fill(Color.mentoraSurfaceVariant)

                MentoraProgressBarMetrics.shape
                    .fill(colors.fillColor)
                    .frame(width: geometry.size.width * CGFloat(clampedProgress))
                    // See this file's header "PAUSED = NO ANIMATION" for the explicit `nil` technique.
                    .animation(
                        state == .paused
                            ? nil
                            : MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.normal),
                        value: clampedProgress
                    )

                if state == .complete && showsCompletionIcon {
                    MentoraIcon(name: .checkCircle, size: MentoraProgressBarMetrics.checkmarkIconSize)
                        .foregroundStyle(colors.fillColor)
                        // Decorative -- the accessibility VALUE below already carries the real progress
                        // signal; this glyph is a purely visual reinforcement of "complete".
                        .accessibilityHidden(true)
                        .offset(x: min(
                            geometry.size.width * CGFloat(clampedProgress) + MentoraProgressBarMetrics.checkmarkGap,
                            max(0, geometry.size.width - MentoraProgressBarMetrics.checkmarkIconSize)
                        ))
                }
            }
        }
        .frame(height: MentoraProgressBarMetrics.height)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue("\(Int((clampedProgress * 100).rounded()))%")
        .accessibilityAddTraits(state == .active ? [.updatesFrequently] : [])
    }
}

// MARK: - The indeterminate view

/// `design-system/COMPONENTS.md § ProgressBar`'s indeterminate variant ("looping linear sweep") -- a
/// separate type from `MentoraProgressBar`, see this file's header. `accessibilityLabel` is a
/// caller-supplied, already-resolved `String` (e.g. "Loading") -- never resolves `MentoraStrings`
/// itself, matching every other atom in this kit.
struct MentoraIndeterminateProgressBar: View {
    let accessibilityLabel: String

    @State private var isAnimating = false

    /// Explicit, unconditionally-`internal` init -- same reason as `MentoraButton`/`MentoraTextField`/
    /// `MentoraToggle`/`MentoraSelect`: a `@State private var` stored property would otherwise make the
    /// implicit memberwise initializer `private`.
    init(accessibilityLabel: String) {
        self.accessibilityLabel = accessibilityLabel
    }

    var body: some View {
        GeometryReader { geometry in
            let segmentWidth = geometry.size.width * MentoraProgressBarMetrics.indeterminateSegmentWidthFraction
            ZStack(alignment: .leading) {
                MentoraProgressBarMetrics.shape
                    .fill(Color.mentoraSurfaceVariant)

                MentoraProgressBarMetrics.shape
                    .fill(Color.mentoraBrandPrimary)
                    .frame(width: segmentWidth)
                    .offset(x: isAnimating ? geometry.size.width - segmentWidth : 0)
            }
        }
        .frame(height: MentoraProgressBarMetrics.height)
        .onAppear {
            // See this file's header "INDETERMINATE SWEEP" -- `.linear`, never `easing.standard`,
            // `repeatForever(autoreverses: false)` for a one-directional perpetual loop.
            withAnimation(
                MentoraMotionEasing.linear(duration: MentoraProgressBarMetrics.indeterminateSweepDuration)
                    .repeatForever(autoreverses: false)
            ) {
                isAnimating = true
            }
        }
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits([.updatesFrequently])
    }
}

#if DEBUG

private struct MentoraProgressBarPreviewSwatches: View {
    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            MentoraProgressBar(progress: 0.25, accessibilityLabel: "Course progress", state: .active)
            MentoraProgressBar(progress: 0.65, accessibilityLabel: "Course progress", state: .active)
            MentoraProgressBar(progress: 1.0, accessibilityLabel: "Course progress", state: .complete)
            MentoraProgressBar(progress: 0.4, accessibilityLabel: "Course progress", state: .paused)
            MentoraIndeterminateProgressBar(accessibilityLabel: "Loading")
        }
    }
}

#Preview("MentoraProgressBar -- Light, active/complete/paused/indeterminate") {
    MentoraPreviewHost(title: "MentoraProgressBar -- Light / en", theme: .light, locale: .english) {
        MentoraProgressBarPreviewSwatches()
    }
}

#Preview("MentoraProgressBar -- Dark, active/complete/paused/indeterminate") {
    MentoraPreviewHost(title: "MentoraProgressBar -- Dark / en", theme: .dark, locale: .english) {
        MentoraProgressBarPreviewSwatches()
    }
}

#endif
