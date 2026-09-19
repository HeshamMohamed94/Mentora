import SwiftUI
import shared

// Phase 5 Task T8 slice 2 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § IconButton`
// (lines ~113-127). `COMPONENTS.md` states a 40x40 touch area, but this file implements the REAL hit
// area as `MentoraTouchTarget.iosPt` (44) instead -- the same G7/`ACCESSIBILITY.md § 4` override
// `MentoraButton.swift`'s TextButton already applies (40pt VISUAL height, 44pt hit area) -- 40 is kept
// as the VISUAL circle diameter only. Same custom-`ButtonStyle` pattern, same `@FocusState` decision
// (see `MentoraButton.swift`'s header for why, not re-derived here), same state-layer rendering
// technique, and the same pure-metrics-resolver split (`MentoraIconButtonMetrics`/
// `MentoraIconButtonRules`) as `MentoraButton.swift`/`BadgeVariant`/`CategoryChipState`.
//
// This is an icon-ONLY control (H1/I2's requirement) -- `accessibilityLabel` is a REQUIRED,
// non-defaulted `String` parameter, not merely relying on `tools/ios-checks/localization-checks.js`'s
// Check B4 (a string-literal-default heuristic) to catch a mistake after the fact.

// MARK: - Pure metrics (no SwiftUI rendering required to test)

enum MentoraIconButtonMetrics {
    /// `COMPONENTS.md`: "Size 40x40 touch area" -- kept here as the VISUAL circle diameter (see file
    /// header for why the REAL hit area is `hitAreaMinimum`, not this value).
    static let visualDiameter: CGFloat = 40

    /// `COMPONENTS.md`: "icon rendered at `icon.default` (24)".
    static let iconSize: CGFloat = MentoraIconSize.`default`

    /// criterion G7 / `ACCESSIBILITY.md § 4` -- see file header.
    static let hitAreaMinimum: CGFloat = MentoraTouchTarget.iosPt

    /// `COMPONENTS.md`: "Radius `radius.full`".
    static let shape = MentoraShape.full

    /// Same disclosed "2px offset" assumption as `MentoraButtonMetrics.focusRingOffset` -- IconButton
    /// has no resting border to swap in place either, so its Focused state also adds an outward ring.
    static let focusRingOffset: CGFloat = MentoraBorderWidth.focus
}

/// The 5 states `COMPONENTS.md § IconButton`'s table enumerates. Unlike `MentoraButtonInteractionState`,
/// `.focused`'s icon color genuinely DIFFERS from `.default`'s (`color.text.primary` vs
/// `color.text.secondary`/`color.brand.primary`) -- confirmed directly from the spec table, not assumed
/// to always mirror `.default` the way `MentoraButtonVariant`'s 4 button variants do.
enum MentoraIconButtonInteractionState: CaseIterable {
    case `default`
    case hover
    case pressed
    case focused
    case disabled

    /// Same priority ordering as `MentoraButtonInteractionState.resolve` -- disabled beats pressed beats
    /// focused beats default; hover is modeled but never returned (see `MentoraButton.swift`'s header
    /// "HOVER IS MODELED BUT NOT WIRED" -- identical reasoning, not re-derived here).
    static func resolve(isEnabled: Bool, isPressed: Bool, isFocused: Bool) -> MentoraIconButtonInteractionState {
        if !isEnabled { return .disabled }
        if isPressed { return .pressed }
        if isFocused { return .focused }
        return .`default`
    }
}

/// Pure, testable IconButton color/state-layer/focus-ring resolution -- kept OUT of the view body, same
/// convention as `BadgeVariant`/`CategoryChipState`/`MentoraButtonVariant.colorSet(for:colorScheme:)`.
enum MentoraIconButtonRules {

    /// `COMPONENTS.md`: Default icon `color.text.secondary`, or `color.brand.primary` when
    /// `isActive` (representing an active/selected toggle). Hover/Pressed/Focused all resolve to
    /// `color.text.primary` regardless of `isActive` -- confirmed directly from the spec table (the
    /// active tint only shows at rest). Disabled is `color.text.disabled` regardless of `isActive`.
    static func iconColor(isActive: Bool, state: MentoraIconButtonInteractionState) -> Color {
        switch state {
        case .`default`: return isActive ? .mentoraBrandPrimary : .mentoraTextSecondary
        case .hover, .pressed, .focused: return .mentoraTextPrimary
        case .disabled: return .mentoraTextDisabled
        }
    }

    /// Hover/Pressed only -- `color.text.primary` @ hover/pressedOpacity, a translucent overlay on the
    /// otherwise-transparent background (same real-alpha-composite reasoning as
    /// `MentoraButton.swift`'s `MentoraStateLayer` -- reused here, not redefined).
    static func stateLayer(for state: MentoraIconButtonInteractionState, colorScheme: ColorScheme) -> MentoraStateLayer? {
        let opacities = MentoraButtonMetrics.stateOpacities(for: colorScheme)
        switch state {
        case .hover: return MentoraStateLayer(color: .mentoraTextPrimary, opacity: opacities.hoverOpacity)
        case .pressed: return MentoraStateLayer(color: .mentoraTextPrimary, opacity: opacities.pressedOpacity)
        case .`default`, .focused, .disabled: return nil
        }
    }

    /// Focused only -- an outward-offset ring (reusing `MentoraButtonBorderStyle` from
    /// `MentoraButton.swift`), since IconButton has no resting border to swap in place.
    static func focusRing(for state: MentoraIconButtonInteractionState) -> MentoraButtonBorderStyle? {
        guard state == .focused else { return nil }
        return MentoraButtonBorderStyle(color: .mentoraBorderFocus, width: MentoraBorderWidth.focus, offset: MentoraIconButtonMetrics.focusRingOffset)
    }
}

// MARK: - The ButtonStyle

private struct MentoraIconButtonStyle: ButtonStyle {
    let isActive: Bool
    let isFocused: Bool

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        let state = MentoraIconButtonInteractionState.resolve(isEnabled: isEnabled, isPressed: configuration.isPressed, isFocused: isFocused)
        let iconColor = MentoraIconButtonRules.iconColor(isActive: isActive, state: state)
        let layer = MentoraIconButtonRules.stateLayer(for: state, colorScheme: colorScheme)
        let ring = MentoraIconButtonRules.focusRing(for: state)
        let shape = MentoraIconButtonMetrics.shape

        return configuration.label
            .foregroundStyle(iconColor)
            .frame(width: MentoraIconButtonMetrics.visualDiameter, height: MentoraIconButtonMetrics.visualDiameter)
            .background(
                Group {
                    if let layer {
                        layer.color.opacity(layer.opacity)
                    }
                }
                .clipShape(shape)
            )
            .overlay {
                if let ring {
                    // See `MentoraButton.swift`'s `MentoraButtonChrome.body` for why the pre-stroke
                    // inset must back out the full `lineWidth`, not just `ring.offset` -- otherwise
                    // `strokeBorder`'s own internal `lineWidth / 2` inset leaves a 0pt gap (the ring
                    // flush against the button edge) instead of the intended offset gap.
                    shape.inset(by: -(ring.offset + ring.width)).strokeBorder(ring.color, lineWidth: ring.width)
                }
            }
            // Outer hit-area expansion -- see MentoraButton.swift's header "VISUAL SIZE vs. HIT AREA
            // ORDERING" (identical technique: real visual size innermost, 44pt minimum outer).
            .frame(minWidth: MentoraIconButtonMetrics.hitAreaMinimum, minHeight: MentoraIconButtonMetrics.hitAreaMinimum)
            .contentShape(Rectangle())
    }
}

// MARK: - The view

/// `COMPONENTS.md § IconButton`. Icon-only -- `accessibilityLabel` is REQUIRED (no default), per H1/I2.
struct MentoraIconButton: View {
    let icon: MentoraIconName
    let accessibilityLabel: String
    var isActive: Bool = false
    let action: () -> Void

    @FocusState private var isFocused: Bool

    /// Explicit, unconditionally-`internal` init -- a `@FocusState private var` stored property can
    /// make the implicit memberwise initializer `private` (Swift: "the default memberwise initializer
    /// ... is private if any of the ... stored properties are private"), which would make this type
    /// uninitializable from another file. Mirrors `MentoraButton`'s own explicit `init` for the same
    /// reason. `isFocused` is intentionally NOT a parameter -- `@FocusState` initializes itself.
    init(icon: MentoraIconName, accessibilityLabel: String, isActive: Bool = false, action: @escaping () -> Void) {
        self.icon = icon
        self.accessibilityLabel = accessibilityLabel
        self.isActive = isActive
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            MentoraIcon(name: icon, size: MentoraIconButtonMetrics.iconSize)
        }
        .buttonStyle(MentoraIconButtonStyle(isActive: isActive, isFocused: isFocused))
        .focused($isFocused)
        .accessibilityLabel(accessibilityLabel)
    }
}

#if DEBUG

/// Same reasoning as `MentoraButton.swift`'s `MentoraButtonStateSwatch` -- a non-interactive, DEBUG-only
/// visual for one forced state, since `ButtonStyleConfiguration.isPressed` cannot be constructed from
/// outside SwiftUI for a static preview screenshot.
private struct MentoraIconButtonStateSwatch: View {
    let state: MentoraIconButtonInteractionState
    var isActive: Bool = false

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let iconColor = MentoraIconButtonRules.iconColor(isActive: isActive, state: state)
        let layer = MentoraIconButtonRules.stateLayer(for: state, colorScheme: colorScheme)
        let ring = MentoraIconButtonRules.focusRing(for: state)
        let shape = MentoraIconButtonMetrics.shape

        MentoraIcon(name: .settings, size: MentoraIconButtonMetrics.iconSize)
            .foregroundStyle(iconColor)
            .frame(width: MentoraIconButtonMetrics.visualDiameter, height: MentoraIconButtonMetrics.visualDiameter)
            .background(
                Group {
                    if let layer {
                        layer.color.opacity(layer.opacity)
                    }
                }
                .clipShape(shape)
            )
            .overlay {
                if let ring {
                    // See `MentoraButton.swift`'s `MentoraButtonChrome.body` for why the pre-stroke
                    // inset must back out the full `lineWidth`, not just `ring.offset` -- otherwise
                    // `strokeBorder`'s own internal `lineWidth / 2` inset leaves a 0pt gap (the ring
                    // flush against the button edge) instead of the intended offset gap.
                    shape.inset(by: -(ring.offset + ring.width)).strokeBorder(ring.color, lineWidth: ring.width)
                }
            }
    }
}

private struct MentoraIconButtonSwatches: View {
    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            HStack(spacing: MentoraSpacing.space3) {
                MentoraIconButtonStateSwatch(state: .`default`)
                MentoraIconButtonStateSwatch(state: .hover)
                MentoraIconButtonStateSwatch(state: .pressed)
                MentoraIconButtonStateSwatch(state: .focused)
                MentoraIconButtonStateSwatch(state: .disabled)
            }
            HStack(spacing: MentoraSpacing.space3) {
                MentoraIconButtonStateSwatch(state: .`default`, isActive: true)
                MentoraIconButton(icon: .settings, accessibilityLabel: "Settings") {}
            }
        }
    }
}

#Preview("MentoraIconButton -- Light, default/hover/pressed/focused/disabled + active") {
    MentoraPreviewHost(title: "MentoraIconButton -- Light / en", theme: .light, locale: .english) {
        MentoraIconButtonSwatches()
    }
}

#Preview("MentoraIconButton -- Dark, default/hover/pressed/focused/disabled + active") {
    MentoraPreviewHost(title: "MentoraIconButton -- Dark / en", theme: .dark, locale: .english) {
        MentoraIconButtonSwatches()
    }
}

#endif
