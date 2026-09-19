import SwiftUI
import shared

// Phase 5 Task T8 slice 2 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Buttons`
// (PrimaryButton/SecondaryButton/TonalButton/TextButton, lines ~51-111). Four variants of ONE
// component, all sharing the same dims-by-variant table and the same custom `ButtonStyle`
// (`MentoraButtonStyle`, private below) -- the FIRST custom `ButtonStyle` in this codebase. Every
// color/dp/type value composed from the semantic/primitive token layer at the point of use -- there is
// deliberately no generated `component.button.*` Swift constant (`PHASE_5_IOS_SYSTEM_DESIGN.md § 15.2`;
// Android's `MentoraButton.kt` -- reference-only, read for the states/values it encodes, never copied
// verbatim -- emitted none either).
//
// Takes an already-resolved `String` label -- NEVER resolves `MentoraStrings` itself.
//
// ---------------------------------------------------------------------------------------------------
// THE FOCUS-STATE RISK (flagged by the architect's slicing plan) -- DECISION: hoisted `@FocusState`,
// NOT `@Environment(\.isFocused)`.
//
// `ButtonStyleConfiguration` exposes only `isPressed` -- no focus. `@Environment(\.isFocused)` is NOT a
// real, general-purpose SwiftUI `EnvironmentValues` key (unlike `\.isEnabled`/`\.colorScheme`, which
// this file DOES read directly inside `MentoraButtonStyle.makeBody` -- a well-established, extremely
// common pattern for custom `ButtonStyle`/`ToggleStyle`/`LabelStyle` implementations, e.g. Apple's own
// sample code reading `@Environment(\.isEnabled)` inside a custom `ButtonStyle` to dim a disabled
// button). The real, documented way to observe whether a SPECIFIC view is focused is `@FocusState` +
// `.focused($binding)` on that view -- not a general environment read. Per this project's own repeated
// lesson about guessing an API shape that turns out not to exist (D108/D109/D115), the safer,
// structurally-simpler choice is taken: `@FocusState private var isFocused: Bool` lives on `MentoraButton`
// itself (the actual `Button` call site, via `.focused($isFocused)`), and the resulting plain `Bool` is
// passed into `MentoraButtonStyle` as an ordinary stored property -- a custom `ButtonStyle` struct can
// always carry additional state passed in at `init`, which is certain to compile regardless of whether
// `\.isFocused` turns out to be real on this toolchain.
//
// ---------------------------------------------------------------------------------------------------
// STATE-LAYER RENDERING -- NOT AN APPROXIMATION OF ALPHA COMPOSITING, THE SAME THING.
// `COMPONENTS.md`'s Tonal/Secondary/TextButton hover/pressed rows and Primary's Disabled row all read as
// "color A @ opacity OVER color B" (Android's `MentoraButton.kt` implements this with
// `Color.compositeOver(...)`, which needs a resolved RGBA `Color`/`UIColor` -- unavailable here without
// dropping into `UIColor`, which `tools/ios-checks/theme-checks.js` Check B3 bans repo-wide). Rendering
// a solid base fill plus a SEPARATE, semi-transparent overlay of the same shape on top (this file's
// `MentoraStateLayer`) is not a visual approximation of that composite -- it is the literal same
// operation (alpha-blending two colors), just performed by the compositor at draw time instead of
// precomputed into one resolved RGBA value. Both produce the identical on-screen pixel.
//
// ---------------------------------------------------------------------------------------------------
// HOVER IS MODELED BUT NOT WIRED -- disclosed gap, same reasoning Android's own `MentoraButton.kt`
// header states explicitly for itself: `COMPONENTS.md`'s own Interaction State Matrix marks every
// button's Hover row "(pointer platforms)"; iOS phones/most iPads are touch-primary. `hover` is still a
// real case of `MentoraButtonInteractionState` (so its color mapping is testable, per this slice's own
// requirement), but `MentoraButtonStyle` never produces it dynamically -- no `.onHover(...)` is attached.
// Whichever later task adds iPadOS pointer support owns wiring `.onHover` into an `@State` and feeding it
// through, exactly as CategoryChip's own SCOPE NOTE (slice 1) discloses a different, analogous gap.
//
// ---------------------------------------------------------------------------------------------------
// VISUAL SIZE vs. HIT AREA ORDERING -- mirrors Android's own F4 fix precedent (`MentoraButton.kt`'s own
// kdoc: "clickable outermost, minimumInteractiveComponentSize() immediately inside it, and the real
// visual size innermost"). Here: the exact visual pill (`variant.height`/`variant.minWidth`,
// background+border scoped to THAT frame) is built first/innermost; the 44pt minimum hit area
// (`MentoraTouchTarget.iosPt`, criterion G7) is an OUTER `.frame(minWidth:minHeight:)` added after, so
// TextButton's 40pt visual pill sits centered inside an invisible 44pt tappable region rather than
// visually growing to 44pt. `.contentShape(Rectangle())` is applied LAST/outermost so the added
// invisible margin is actually tappable (SwiftUI's default: transparent space from `.frame(...)` alone is
// not hit-testable without an explicit `.contentShape`).
//
// The "2px offset" the spec states explicitly only for Primary's focus ring is reused, as a disclosed
// assumption, for every variant that has NO resting border to swap in place (Primary/Tonal/TextButton) --
// giving each an outward-offset ring rather than eating into the filled/text area. Secondary already has
// a resting inline border, so its Focused row REPLACES that border in place (offset 0, thicker/refocused
// color), never adds a second ring.

// MARK: - Pure metrics (no SwiftUI rendering required to test)

/// One of `COMPONENTS.md § Buttons`'s 4 named variants -- PrimaryButton/SecondaryButton/TonalButton/
/// TextButton are 4 configurations of ONE `MentoraButton`, not 4 separate implementations.
enum MentoraButtonVariant: CaseIterable {
    case primary
    case secondary
    case tonal
    case text

    /// `COMPONENTS.md`: 48 for Primary/Secondary/Tonal, 40 for TextButton. This is a MINIMUM, not a
    /// fixed height -- `CONTENT_RESILIENCE.md § 1`'s explicit Buttons row ("Wraps to 2 lines before
    /// clipping. Button height grows to fit; never ellipsis a call-to-action.") and § 8's Locked Rule
    /// forbid a fixed-height text container here; `MentoraButtonChrome` applies this via
    /// `.frame(minHeight:)`, not `.frame(height:)`.
    var height: CGFloat {
        self == .text ? 40 : 48
    }

    /// `COMPONENTS.md`: `space.6` (24) for Primary/Secondary/Tonal, `space.3` (12) for TextButton.
    var horizontalPadding: CGFloat {
        self == .text ? MentoraSpacing.space3 : MentoraSpacing.space6
    }

    /// `COMPONENTS.md`: 88 for Primary/Secondary/Tonal ("Min width", line 58 -- no matching
    /// `spacing.scale` step, same disclosed gap Android's own kdoc records for the identical value).
    /// TextButton has no stated minimum -- `0` here is "no minimum applied", not a token value.
    var minWidth: CGFloat {
        self == .text ? 0 : 88
    }
}

/// Shared, non-variant-specific numeric/token constants -- named once, referenced by both
/// `MentoraButtonStyle` and `MentoraButtonMetricsTests.swift`, never re-typed as a raw literal in either.
enum MentoraButtonMetrics {
    /// `COMPONENTS.md`: "Icon size (optional leading/trailing) `icon.medium` (20)".
    static let iconSize: CGFloat = MentoraIconSize.medium

    /// `COMPONENTS.md`: "gap `space.2` (8)".
    static let iconGap: CGFloat = MentoraSpacing.space2

    /// `COMPONENTS.md`: "Radius `radius.medium` (12)" -- every variant, including TextButton's
    /// "hit/hover area only, no visible fill by default" radius.
    static let shape = MentoraShape.medium

    /// criterion G7 / `ACCESSIBILITY.md § 4` -- the SAME 44pt minimum every variant's hit area must
    /// meet, even TextButton's 40pt VISUAL height (see this file's header comment).
    static let hitAreaMinimum: CGFloat = MentoraTouchTarget.iosPt

    /// Disclosed assumption (see file header): the "2px offset" `COMPONENTS.md` states explicitly only
    /// for Primary's Focused row, reused for every variant whose Focused state adds a NEW ring rather
    /// than swapping an existing inline border in place. Deliberately equal to `MentoraBorderWidth.focus`
    /// (2) -- a convenient, disclosed coincidence, not re-derived from a separate token.
    static let focusRingOffset: CGFloat = MentoraBorderWidth.focus

    /// One resolved (hover, pressed, disabledContainer) opacity triple for a given `ColorScheme` --
    /// `MentoraStateOpacityLight`/`MentoraStateOpacityDark` (`Theme/MentoraTokens.swift`) differ only in
    /// `pressedOpacity`/`focusOpacity`/`disabledContainerOpacity` (hover is 0.08 in both), but this reads
    /// all three from whichever theme is live rather than assuming which fields do/don't differ.
    struct StateOpacities: Equatable {
        let hoverOpacity: Double
        let pressedOpacity: Double
        let disabledContainerOpacity: Double
    }

    static func stateOpacities(for colorScheme: ColorScheme) -> StateOpacities {
        if colorScheme == .dark {
            return StateOpacities(
                hoverOpacity: MentoraStateOpacityDark.hoverOpacity,
                pressedOpacity: MentoraStateOpacityDark.pressedOpacity,
                disabledContainerOpacity: MentoraStateOpacityDark.disabledContainerOpacity
            )
        }
        return StateOpacities(
            hoverOpacity: MentoraStateOpacityLight.hoverOpacity,
            pressedOpacity: MentoraStateOpacityLight.pressedOpacity,
            disabledContainerOpacity: MentoraStateOpacityLight.disabledContainerOpacity
        )
    }

    /// Mirrors Android's own `interactive = enabled && !loading` split (`MentoraButton.kt`) exactly:
    /// loading renders with the SAME colors as `.default` (`COMPONENTS.md` line 70 -- background stays
    /// `color.brand.primary`, never the disabled palette) but must still block interaction. Kept as a
    /// pure, directly-testable function rather than only inline in `MentoraButton.body`.
    static func isInteractive(isEnabled: Bool, isLoading: Bool) -> Bool {
        isEnabled && !isLoading
    }
}

/// The 5 states `COMPONENTS.md`'s per-variant tables enumerate. `loading` is deliberately NOT a case
/// here -- its colors equal `.default`'s exactly; only the label/spinner composition differs, a pure
/// view concern handled in `MentoraButton.body`, not a color-table state.
enum MentoraButtonInteractionState: CaseIterable {
    case `default`
    case hover
    case pressed
    case focused
    case disabled

    /// `disabled` beats every other input; `pressed` beats `focused` (immediate touch feedback outranks
    /// a resting focus ring); `focused` beats the plain resting `default`. `hover` is never returned here
    /// -- see this file's header "HOVER IS MODELED BUT NOT WIRED".
    static func resolve(isEnabled: Bool, isPressed: Bool, isFocused: Bool) -> MentoraButtonInteractionState {
        if !isEnabled { return .disabled }
        if isPressed { return .pressed }
        if isFocused { return .focused }
        return .`default`
    }
}

/// A translucent overlay of `color` at `opacity`, drawn on top of a `MentoraButtonColorSet.base` fill,
/// same shape -- see this file's header "STATE-LAYER RENDERING" for why this is a real alpha composite,
/// not an approximation of one.
struct MentoraStateLayer: Equatable {
    let color: Color
    let opacity: Double
}

/// `offset == 0` -- an INLINE border, drawn on the shape's own edge (Secondary's resting/hover/pressed/
/// disabled outline, and Secondary's Focused replacement border). `offset > 0` -- an outward-offset RING,
/// drawn around a shape inset by `-offset` (every other variant's Focused indicator, which has no resting
/// border to swap in place). See this file's header for the "2px offset" assumption.
struct MentoraButtonBorderStyle: Equatable {
    let color: Color
    let width: CGFloat
    let offset: CGFloat
}

/// One (background, content, border) resolution for a `MentoraButtonVariant` at a given
/// `MentoraButtonInteractionState`. Pure data -- `MentoraButtonVariantTests.swift` asserts every mapping
/// without rendering anything. Real `Color+Mentora.swift` accessor names confirmed by reading that file
/// directly, not guessed.
struct MentoraButtonColorSet: Equatable {
    let base: Color
    let stateLayer: MentoraStateLayer?
    let content: Color
    let border: MentoraButtonBorderStyle?
}

extension MentoraButtonVariant {

    /// `COMPONENTS.md § Buttons`'s full per-variant state table. `colorScheme` only matters for the
    /// hover/pressed/disabledContainer OPACITY numbers (`MentoraButtonMetrics.stateOpacities`) --
    /// Primary's hover/pressed use distinct named tokens instead (`.mentoraBrandPrimaryHover`/
    /// `.mentoraBrandPrimaryPressed`), no opacity math at all.
    func colorSet(for state: MentoraButtonInteractionState, colorScheme: ColorScheme) -> MentoraButtonColorSet {
        let opacities = MentoraButtonMetrics.stateOpacities(for: colorScheme)
        let ringOffset = MentoraButtonMetrics.focusRingOffset

        switch self {
        case .primary:
            switch state {
            case .`default`, .hover, .pressed:
                let base: Color = state == .hover ? .mentoraBrandPrimaryHover
                    : state == .pressed ? .mentoraBrandPrimaryPressed
                    : .mentoraBrandPrimary
                return MentoraButtonColorSet(base: base, stateLayer: nil, content: .mentoraBrandOnPrimary, border: nil)
            case .focused:
                return MentoraButtonColorSet(
                    base: .mentoraBrandPrimary, stateLayer: nil, content: .mentoraBrandOnPrimary,
                    border: MentoraButtonBorderStyle(color: .mentoraBorderFocus, width: MentoraBorderWidth.focus, offset: ringOffset)
                )
            case .disabled:
                // "`color.brand.primary` @ `state.disabledContainerOpacity` over `surface.default`".
                return MentoraButtonColorSet(
                    base: .mentoraSurfaceDefault,
                    stateLayer: MentoraStateLayer(color: .mentoraBrandPrimary, opacity: opacities.disabledContainerOpacity),
                    content: .mentoraTextDisabled, border: nil
                )
            }

        case .secondary:
            let restingBorder = MentoraButtonBorderStyle(color: .mentoraBrandPrimary, width: MentoraBorderWidth.`default`, offset: 0)
            switch state {
            case .`default`:
                return MentoraButtonColorSet(base: .clear, stateLayer: nil, content: .mentoraBrandPrimary, border: restingBorder)
            case .hover:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: MentoraStateLayer(color: .mentoraBrandPrimary, opacity: opacities.hoverOpacity),
                    content: .mentoraBrandPrimary, border: restingBorder
                )
            case .pressed:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: MentoraStateLayer(color: .mentoraBrandPrimary, opacity: opacities.pressedOpacity),
                    content: .mentoraBrandPrimary, border: restingBorder
                )
            case .focused:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: nil, content: .mentoraBrandPrimary,
                    border: MentoraButtonBorderStyle(color: .mentoraBorderFocus, width: MentoraBorderWidth.focus, offset: 0)
                )
            case .disabled:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: nil, content: .mentoraTextDisabled,
                    border: MentoraButtonBorderStyle(color: .mentoraBorderDefault, width: MentoraBorderWidth.`default`, offset: 0)
                )
            }

        case .tonal:
            switch state {
            case .`default`:
                return MentoraButtonColorSet(base: .mentoraBrandPrimaryContainer, stateLayer: nil, content: .mentoraBrandOnPrimaryContainer, border: nil)
            case .hover:
                return MentoraButtonColorSet(
                    base: .mentoraBrandPrimaryContainer,
                    stateLayer: MentoraStateLayer(color: .mentoraBrandOnPrimaryContainer, opacity: opacities.hoverOpacity),
                    content: .mentoraBrandOnPrimaryContainer, border: nil
                )
            case .pressed:
                return MentoraButtonColorSet(
                    base: .mentoraBrandPrimaryContainer,
                    stateLayer: MentoraStateLayer(color: .mentoraBrandOnPrimaryContainer, opacity: opacities.pressedOpacity),
                    content: .mentoraBrandOnPrimaryContainer, border: nil
                )
            case .focused:
                return MentoraButtonColorSet(
                    base: .mentoraBrandPrimaryContainer, stateLayer: nil, content: .mentoraBrandOnPrimaryContainer,
                    border: MentoraButtonBorderStyle(color: .mentoraBorderFocus, width: MentoraBorderWidth.focus, offset: ringOffset)
                )
            case .disabled:
                return MentoraButtonColorSet(base: .mentoraSurfaceVariant, stateLayer: nil, content: .mentoraTextDisabled, border: nil)
            }

        case .text:
            switch state {
            case .`default`:
                return MentoraButtonColorSet(base: .clear, stateLayer: nil, content: .mentoraBrandPrimary, border: nil)
            case .hover:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: MentoraStateLayer(color: .mentoraBrandPrimary, opacity: opacities.hoverOpacity),
                    content: .mentoraBrandPrimary, border: nil
                )
            case .pressed:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: MentoraStateLayer(color: .mentoraBrandPrimary, opacity: opacities.pressedOpacity),
                    content: .mentoraBrandPrimary, border: nil
                )
            case .focused:
                return MentoraButtonColorSet(
                    base: .clear, stateLayer: nil, content: .mentoraBrandPrimary,
                    border: MentoraButtonBorderStyle(color: .mentoraBorderFocus, width: MentoraBorderWidth.focus, offset: ringOffset)
                )
            case .disabled:
                return MentoraButtonColorSet(base: .clear, stateLayer: nil, content: .mentoraTextDisabled, border: nil)
            }
        }
    }
}

// MARK: - Shared visual chrome

/// The background/border/sizing chrome BOTH `MentoraButtonStyle`'s real interactive rendering AND the
/// `#if DEBUG` state-swatch preview harness below compose from a resolved `MentoraButtonColorSet` --
/// factored out into one `ViewModifier` so the two can never drift apart (the preview harness needs to
/// render `.hover`/`.pressed`/`.focused` states that `ButtonStyleConfiguration` cannot be driven into
/// from outside SwiftUI for a static preview -- see this file's header).
private struct MentoraButtonChrome: ViewModifier {
    let variant: MentoraButtonVariant
    let colors: MentoraButtonColorSet

    func body(content: Content) -> some View {
        let shape = MentoraButtonMetrics.shape
        content
            .foregroundStyle(colors.content)
            .tint(colors.content)
            .padding(.horizontal, variant.horizontalPadding)
            .padding(.vertical, MentoraSpacing.space1)
            .frame(minHeight: variant.height)
            .frame(minWidth: variant.minWidth)
            .background(
                ZStack {
                    colors.base
                    if let layer = colors.stateLayer {
                        layer.color.opacity(layer.opacity)
                    }
                }
                .clipShape(shape)
            )
            .overlay {
                if let border = colors.border, border.offset == 0 {
                    shape.strokeBorder(border.color, lineWidth: border.width)
                }
            }
            .overlay {
                if let border = colors.border, border.offset > 0 {
                    // `strokeBorder` insets `self` by an ADDITIONAL `lineWidth / 2` before stroking
                    // (Apple's documented behavior), so the painted stroke spans
                    // `[insetAmount, insetAmount + lineWidth]`. To make the ring's INNER edge sit the
                    // full `border.offset` outside the shape's own edge (a real gap, not a ring flush
                    // against the button -- `shape.inset(by: -border.offset)` alone gives a 0pt gap),
                    // the pre-stroke inset must additionally back out the full `lineWidth`.
                    shape.inset(by: -(border.offset + border.width)).strokeBorder(border.color, lineWidth: border.width)
                }
            }
    }
}

// MARK: - The ButtonStyle

/// The FIRST custom `ButtonStyle` in this codebase -- see this file's header for the focus-state and
/// state-layer design decisions. Reads `@Environment(\.isEnabled)`/`@Environment(\.colorScheme)` directly
/// (a well-established pattern for a custom style, distinct from the genuinely-uncertain
/// `@Environment(\.isFocused)` this file deliberately avoids).
private struct MentoraButtonStyle: ButtonStyle {
    let variant: MentoraButtonVariant
    let isFocused: Bool

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        let state = MentoraButtonInteractionState.resolve(isEnabled: isEnabled, isPressed: configuration.isPressed, isFocused: isFocused)
        let colors = variant.colorSet(for: state, colorScheme: colorScheme)

        return configuration.label
            .modifier(MentoraButtonChrome(variant: variant, colors: colors))
            // Outer hit-area expansion -- see this file's header "VISUAL SIZE vs. HIT AREA ORDERING".
            .frame(minWidth: MentoraButtonMetrics.hitAreaMinimum, minHeight: MentoraButtonMetrics.hitAreaMinimum)
            .contentShape(Rectangle())
    }
}

// MARK: - The view

/// `COMPONENTS.md § Buttons`. Takes an already-resolved `String` label and an optional leading icon
/// (`MentoraIconName`) -- never resolves `MentoraStrings` itself. `isLoading` hides the label (kept in
/// the layout at `opacity(0)`, per "width preserved") behind a centered `ProgressView`, and blocks
/// interaction WITHOUT touching `\.isEnabled` (so the rendered colors stay `.default`'s, per
/// `COMPONENTS.md` line 70 -- see `MentoraButtonMetrics.isInteractive`'s doc comment).
struct MentoraButton: View {
    let label: String
    var variant: MentoraButtonVariant = .primary
    var icon: MentoraIconName?
    var isLoading: Bool = false
    let action: () -> Void

    @FocusState private var isFocused: Bool
    @Environment(\.isEnabled) private var isEnabled

    init(
        _ label: String,
        variant: MentoraButtonVariant = .primary,
        icon: MentoraIconName? = nil,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) {
        self.label = label
        self.variant = variant
        self.icon = icon
        self.isLoading = isLoading
        self.action = action
    }

    var body: some View {
        Button {
            if MentoraButtonMetrics.isInteractive(isEnabled: isEnabled, isLoading: isLoading) {
                action()
            }
        } label: {
            ZStack {
                HStack(spacing: MentoraButtonMetrics.iconGap) {
                    if let icon {
                        MentoraIcon(name: icon, size: MentoraButtonMetrics.iconSize)
                            .accessibilityHidden(true)
                    }
                    Text(label)
                        .mentoraFont(.labelLarge)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                }
                .opacity(isLoading ? 0 : 1)

                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                }
            }
        }
        .buttonStyle(MentoraButtonStyle(variant: variant, isFocused: isFocused))
        .focused($isFocused)
        .allowsHitTesting(MentoraButtonMetrics.isInteractive(isEnabled: isEnabled, isLoading: isLoading))
    }
}

// MARK: - Named variant wrappers (call-site sugar, per this section's own naming)

/// `COMPONENTS.md § PrimaryButton`.
struct PrimaryButton: View {
    let label: String
    var icon: MentoraIconName?
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        MentoraButton(label, variant: .primary, icon: icon, isLoading: isLoading, action: action)
    }
}

/// `COMPONENTS.md § SecondaryButton`.
struct SecondaryButton: View {
    let label: String
    var icon: MentoraIconName?
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        MentoraButton(label, variant: .secondary, icon: icon, isLoading: isLoading, action: action)
    }
}

/// `COMPONENTS.md § TonalButton`.
struct TonalButton: View {
    let label: String
    var icon: MentoraIconName?
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        MentoraButton(label, variant: .tonal, icon: icon, isLoading: isLoading, action: action)
    }
}

/// `COMPONENTS.md § TextButton`. Not named `TextButton` alone to dodge a clash -- unlike Android's real
/// `androidx.compose.material3.TextButton` collision (which forced `MentoraTextButton` there), SwiftUI
/// has no `TextButton` type, so no rename is needed here.
struct TextButton: View {
    let label: String
    var icon: MentoraIconName?
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        MentoraButton(label, variant: .text, icon: icon, isLoading: isLoading, action: action)
    }
}

#if DEBUG

/// A NON-interactive, DEBUG-only visual for one (variant, state) pair -- renders the exact same
/// `MentoraButtonChrome` the real interactive `MentoraButtonStyle` uses, but with `state` forced
/// directly rather than derived from a live `ButtonStyleConfiguration`. This exists because
/// `ButtonStyleConfiguration.isPressed` cannot be constructed from outside SwiftUI, so a real `Button`
/// has no way to render a static "pressed" (or "hover"/"focused") screenshot for this preview harness --
/// see this file's header. Never used outside `#if DEBUG`.
private struct MentoraButtonStateSwatch: View {
    let variant: MentoraButtonVariant
    let state: MentoraButtonInteractionState
    let label: String
    var icon: MentoraIconName?

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: MentoraButtonMetrics.iconGap) {
            if let icon {
                MentoraIcon(name: icon, size: MentoraButtonMetrics.iconSize)
            }
            Text(label)
                .mentoraFont(.labelLarge)
        }
        .modifier(MentoraButtonChrome(variant: variant, colors: variant.colorSet(for: state, colorScheme: colorScheme)))
    }
}

/// A named case for `MentoraButtonSwatches`'s `ForEach` -- `Identifiable` rather than a tuple + a
/// `\.1` key path, so identity is unambiguous (this repo has no prior CI-green precedent for a tuple
/// element key path in a `ForEach`, unlike the well-established `\.self`/`Identifiable` patterns
/// already proven throughout `MentoraTokenGallery.swift`).
private struct ButtonSwatchCase: Identifiable {
    let state: MentoraButtonInteractionState
    let name: String
    var id: String { name }
}

private struct MentoraButtonSwatches: View {
    private let states: [ButtonSwatchCase] = [
        ButtonSwatchCase(state: .`default`, name: "Default"),
        ButtonSwatchCase(state: .hover, name: "Hover"),
        ButtonSwatchCase(state: .pressed, name: "Pressed"),
        ButtonSwatchCase(state: .focused, name: "Focused"),
        ButtonSwatchCase(state: .disabled, name: "Disabled"),
    ]

    private func row(_ variant: MentoraButtonVariant) -> some View {
        HStack(spacing: MentoraSpacing.space2) {
            ForEach(states) { swatchCase in
                MentoraButtonStateSwatch(variant: variant, state: swatchCase.state, label: swatchCase.name)
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            row(.primary)
            row(.secondary)
            row(.tonal)
            row(.text)
            HStack(spacing: MentoraSpacing.space2) {
                PrimaryButton(label: "Loading", isLoading: true) {}
                PrimaryButton(label: "With icon", icon: .add) {}
            }
        }
    }
}

#Preview("MentoraButton -- Light, all 4 variants x 5 states + loading") {
    MentoraPreviewHost(title: "MentoraButton -- Light / en", theme: .light, locale: .english) {
        MentoraButtonSwatches()
    }
}

#Preview("MentoraButton -- Dark, all 4 variants x 5 states + loading") {
    MentoraPreviewHost(title: "MentoraButton -- Dark / en", theme: .dark, locale: .english) {
        MentoraButtonSwatches()
    }
}

#endif
