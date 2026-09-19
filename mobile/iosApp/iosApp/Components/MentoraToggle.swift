import SwiftUI
import shared

// Phase 5 Task T8 slice 4 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Toggle / Switch`
// (lines 166-187). No Android reference file exists for this component under
// `mobile/androidApp/.../ui/components/` (confirmed by a direct glob search before writing this file) --
// this is built straight from the spec, not ported from a Kotlin precedent, unlike most of this kit's
// other atoms.
//
// ---------------------------------------------------------------------------------------------------
// NATIVE-CONTROL MAPPING -- `COMPONENTS.md` line 168 is explicit: "Implement using the platform's
// native switch control ... iOS SwiftUI `Toggle` ... styled to these tokens ... rather than a
// custom-built control -- this inherits correct keyboard/screen-reader semantics for free." This file
// therefore wraps SwiftUI's real `Toggle`, never a from-scratch `Capsule`+`DragGesture`/`Button` fake --
// the entire visual chrome below is composed inside a custom `ToggleStyle` (`MentoraToggleStyle`, the
// FIRST custom `ToggleStyle` in this codebase, same idea as `MentoraButton.swift`'s FIRST custom
// `ButtonStyle`), not a replacement control.
//
// ---------------------------------------------------------------------------------------------------
// `ToggleStyleConfiguration`'S REAL SHAPE -- DISCLOSED, NOT GUESSED. Apple's actually-declared API is
// `struct ToggleStyleConfiguration { struct Label: View; var label: Label { get }; var isOn: Bool { get
// nonmutating set } }` -- `isOn` is a plain `Bool` with a `nonmutating set` (which is what makes
// `configuration.isOn.toggle()` a valid mutation of the real underlying source of truth), NOT a
// `Binding<Bool>` literally, and there is no separate `$isOn` property required for this file's usage.
// This matches Apple's own documented custom-`ToggleStyle` sample code exactly (`Button {
// configuration.isOn.toggle() } label: { configuration.label }`) -- the same "matches Apple's own
// documented sample" confidence class `MentoraButtonStyle`'s header already claims for reading
// `@Environment(\.isEnabled)` inside a custom style. Confident, not guessed -- but not independently
// re-verified against a live SDK header on this Windows host (no Swift toolchain here), so flagged in
// this file's final report per this task's own disclosure requirement.
//
// This file's custom style deliberately never renders `configuration.label` at all: `MentoraToggle`
// always constructs the underlying `Toggle(isOn:) { EmptyView() }` itself (see the view below), so
// `configuration.label` is always an `EmptyView` by construction -- the REAL adjacent text label (when
// supplied) is composed OUTSIDE the styled `Toggle`, in `MentoraToggle.body`'s own `HStack`, exactly the
// same "compose the interesting layout in the VIEW, keep the STYLE to pure chrome" split
// `MentoraButton.swift`'s `body` (icon/label/spinner `ZStack`) vs. `MentoraButtonStyle` (chrome only)
// already establishes.
//
// ---------------------------------------------------------------------------------------------------
// FOCUS-STATE -- identical, unmodified precedent from `MentoraButton.swift`/`MentoraTextField.swift`:
// `@FocusState private var isFocused: Bool` lives on `MentoraToggle` itself (via `.focused($isFocused)`
// on the real `Toggle`), the resulting plain `Bool` passed into `MentoraToggleStyle` as an ordinary
// stored property -- `@Environment(\.isFocused)` is not a real, general-purpose `EnvironmentValues` key,
// per that file's own header, not re-derived here.
//
// ---------------------------------------------------------------------------------------------------
// TOUCH TARGET -- unlike `MentoraButton`/`MentoraIconButton` (custom `Button`s that had to manually
// expand their own hit area to `MentoraTouchTarget.iosPt`), this file's interactive surface is the
// visual track/thumb chrome rendered INSIDE a plain `Button` inside `MentoraToggleStyle.makeBody`
// (Apple's own documented custom-`ToggleStyle` shape, see above) -- so the exact same "visual size
// innermost, 44pt hit-area minimum outer, `.contentShape(Rectangle())` last" technique from
// `MentoraButtonStyle.makeBody` is reused identically here, since `ACCESSIBILITY.md § 4`'s "applies to
// all tappable controls... checkboxes/radios" 44pt minimum is not spelled out per-component in
// `COMPONENTS.md § Toggle / Switch` but is a system-wide requirement, same reasoning `IconButton`'s own
// 40x40-visual/44pt-hit-area split already applies.
//
// ---------------------------------------------------------------------------------------------------
// ELEVATION REUSE -- the thumb's `elevation.1` (On/Off states) reuses the real, shared
// `.mentoraElevation(_:in:fill:border:)` handle (`Theme/MentoraElevation.swift`) rather than
// hand-rolling a second shadow-composition path. That modifier ALWAYS also paints a 1pt border (default
// `.mentoraBorderDefault`) as part of its own contract -- but `COMPONENTS.md`'s Toggle thumb states name
// only a fill + shadow ("`color.surface.default`, `elevation.1`"), no border at all, in any state. Rather
// than fork a second, thumb-specific elevation helper, this file passes `border: .clear` explicitly
// (`Color.clear` is deliberately exempt from `tools/ios-checks/theme-checks.js`'s forbidden-raw-color
// palette -- SANCTIONED_EXCEPTIONS.colorClearExcluded there, and the same technique
// `MentoraButtonColorSet`'s own `base: .clear` cases already use) -- a disclosed, minimal way to reuse
// the shared helper without inventing a spec-unstated border. Disabled uses `.level0` (a genuine,
// documented no-op per `MentoraElevationLevel`'s own header -- "a real no-op at that level") rather than
// skipping the modifier call entirely, since `COMPONENTS.md`'s Disabled row states no elevation at all
// for the thumb (only On/Off explicitly name `elevation.1`) -- the minimal, literal reading, matching
// this kit's own established "deliberate, minimal reading of the spec" convention
// (`MentoraTextFieldRules.colorSet`'s own border-width doc comment states the identical reasoning style).
//
// ---------------------------------------------------------------------------------------------------
// FOCUS RING OFFSET -- unlike `MentoraButton.swift`'s disclosed "2px offset" assumption (borrowed from
// Primary's own explicitly-stated Focused row and reused elsewhere for consistency), `COMPONENTS.md`'s
// Toggle Focused row states only "+ `color.border.focus` outline", with no offset language anywhere in
// this section. This file therefore draws the focus ring INLINE, flush with the track's own edge
// (offset 0) -- the minimal, literal reading, not an assumed outward ring.
//
// ---------------------------------------------------------------------------------------------------
// THUMB MOTION -- animates via a plain `.offset(x:)` (leading-anchored track, thumb slides right by
// `MentoraToggleMetrics.thumbTravel` when on) rather than swapping `ZStack` `alignment:` between
// `.leading`/`.trailing` -- `.offset` changes are a simple, well-established SwiftUI animation target;
// animating an `alignment:` swap is a plausible but less certain-to-interpolate-smoothly approach this
// file deliberately avoids. Uses `Theme/MentoraMotion.swift`'s real `easing.standard` / `duration.fast`
// tokens (never a raw `.easeInOut` approximation -- see that file's own header for why), matching the
// exact fix already applied to `MentoraTextField.swift`'s `labelAnimation` this same task.
//
// ---------------------------------------------------------------------------------------------------
// LOCKED RULE (v1.3) -- CALLER RESPONSIBILITY, DISCLOSED, NOT ENFORCEABLE AT COMPILE TIME.
// `COMPONENTS.md` line 187: whenever a Toggle conveys a meaningful NAMED state (e.g. a course's
// Published/Draft status), it MUST be paired with a visible text label naming that state, per
// `ACCESSIBILITY.md § 8`'s "color is never the only signal" rule. `label: String?` below is OPTIONAL
// because a bare, unlabeled Toggle is legitimate for a self-evident/unnamed preference (an external form
// label already states what's being toggled) -- but this type has no way to know, at compile time,
// WHICH case a given call site is in. Every caller wiring a NAMED-state Toggle (Published/Draft,
// enabled/disabled feature flags with a stated meaning, etc.) MUST supply `label`; this is a disclosed
// caller responsibility, the same class of unenforceable-at-compile-time spec requirement this codebase
// already discloses elsewhere (e.g. `MentoraTextField.swift`'s own accessibility-linkage disclosures).

// MARK: - Pure metrics (no SwiftUI rendering required to test)

enum MentoraToggleMetrics {
    /// `COMPONENTS.md`: "Track width x height `space.10` (40) x `space.6` (24)".
    static let trackWidth: CGFloat = MentoraSpacing.space10
    static let trackHeight: CGFloat = MentoraSpacing.space6

    /// `COMPONENTS.md`: "Thumb size `space.5` (20)".
    static let thumbSize: CGFloat = MentoraSpacing.space5

    /// `COMPONENTS.md`: "Thumb inset `border.width.focus` (2)" -- the gap kept between the thumb's own
    /// edge and the track's edge at rest, on either side. Consistent with the stated 40/24/20/16 numbers:
    /// `(trackHeight - thumbSize) / 2 == 2 == thumbInset`.
    static let thumbInset: CGFloat = MentoraBorderWidth.focus

    /// `COMPONENTS.md`: "Thumb travel distance `space.4` (16)" -- `trackWidth - thumbSize - 2 *
    /// thumbInset == 40 - 20 - 4 == 16`, asserted as an internal consistency guard in
    /// `MentoraToggleTests.swift` rather than trusted as a coincidence.
    static let thumbTravel: CGFloat = MentoraSpacing.space4

    /// `COMPONENTS.md`: "Track radius `radius.full`".
    static let trackShape = MentoraShape.full

    /// `COMPONENTS.md` line 187: "`space.2` gap" between the adjacent state label and the control.
    static let labelGap: CGFloat = MentoraSpacing.space2

    /// See this file's header "TOUCH TARGET" -- the same system-wide 44pt minimum
    /// `MentoraButtonMetrics.hitAreaMinimum` / `MentoraIconButtonMetrics.hitAreaMinimum` already apply.
    static let hitAreaMinimum: CGFloat = MentoraTouchTarget.iosPt
}

// MARK: - Pure state/color resolver (no SwiftUI rendering required to test)

/// One (track fill + opacity, thumb fill + elevation flag, focus ring) resolution -- pure data, asserted
/// directly by `MentoraToggleTests.swift` without rendering anything. Real `Color+Mentora.swift` accessor
/// names confirmed by reading that file directly, not guessed. Same convention as
/// `MentoraButtonColorSet`/`MentoraTextFieldColorSet`.
struct MentoraToggleColorSet: Equatable {
    let trackColor: Color
    let trackOpacity: Double
    let thumbColor: Color
    let thumbHasElevation: Bool
    let focusRingColor: Color?
}

enum MentoraToggleRules {

    /// `COMPONENTS.md § Toggle / Switch`'s full state table. `disabled` beats every other input (a
    /// disabled toggle is never also shown focused, matching every other atom in this kit's own
    /// disabled-short-circuits-everything convention -- `MentoraTextFieldRules.colorSet`'s identical
    /// priority). On/off IS this component's whole "Selected" state (`COMPONENTS.md` line 185) -- there
    /// is no separate selected-layer input here.
    static func colorSet(isOn: Bool, isEnabled: Bool, isFocused: Bool, colorScheme: ColorScheme) -> MentoraToggleColorSet {
        guard isEnabled else {
            return MentoraToggleColorSet(
                trackColor: .mentoraBorderDefault,
                trackOpacity: MentoraButtonMetrics.stateOpacities(for: colorScheme).disabledContainerOpacity,
                thumbColor: .mentoraSurfaceDefault,
                thumbHasElevation: false,
                focusRingColor: nil
            )
        }
        return MentoraToggleColorSet(
            trackColor: isOn ? .mentoraBrandPrimary : .mentoraBorderStrong,
            trackOpacity: 1.0,
            thumbColor: .mentoraSurfaceDefault,
            thumbHasElevation: true,
            focusRingColor: isFocused ? .mentoraBorderFocus : nil
        )
    }
}

// MARK: - The ToggleStyle

/// The FIRST custom `ToggleStyle` in this codebase -- see this file's header for the
/// `ToggleStyleConfiguration` shape disclosure, the focus-state precedent, and the touch-target/
/// elevation/focus-ring/motion decisions. Reads `@Environment(\.isEnabled)`/`@Environment(\.colorScheme)`
/// directly, the same well-established pattern `MentoraButtonStyle`/`MentoraIconButtonStyle` already use.
private struct MentoraToggleStyle: ToggleStyle {
    let isFocused: Bool

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        let colors = MentoraToggleRules.colorSet(
            isOn: configuration.isOn, isEnabled: isEnabled, isFocused: isFocused, colorScheme: colorScheme
        )

        return Button {
            configuration.isOn.toggle()
        } label: {
            ZStack(alignment: .leading) {
                MentoraToggleMetrics.trackShape
                    .fill(colors.trackColor.opacity(colors.trackOpacity))
                    .frame(width: MentoraToggleMetrics.trackWidth, height: MentoraToggleMetrics.trackHeight)
                    .overlay {
                        if let ring = colors.focusRingColor {
                            MentoraToggleMetrics.trackShape.strokeBorder(ring, lineWidth: MentoraBorderWidth.focus)
                        }
                    }

                // See this file's header "ELEVATION REUSE" for why `border: .clear` / `.level0` are
                // passed to the shared `.mentoraElevation(_:in:fill:border:)` handle instead of a
                // second, hand-rolled shadow path.
                Color.clear
                    .frame(width: MentoraToggleMetrics.thumbSize, height: MentoraToggleMetrics.thumbSize)
                    .mentoraElevation(
                        colors.thumbHasElevation ? .level1 : .level0,
                        in: .full,
                        fill: colors.thumbColor,
                        border: .clear
                    )
                    .padding(.leading, MentoraToggleMetrics.thumbInset)
                    .offset(x: configuration.isOn ? MentoraToggleMetrics.thumbTravel : 0)
            }
            // Outer hit-area expansion -- see this file's header "TOUCH TARGET" (identical technique to
            // `MentoraButtonChrome`/`MentoraIconButtonStyle`: real visual size innermost, 44pt minimum
            // outer, `.contentShape` last).
            .frame(minWidth: MentoraToggleMetrics.hitAreaMinimum, minHeight: MentoraToggleMetrics.hitAreaMinimum)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .animation(
            MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.fast),
            value: configuration.isOn
        )
    }
}

// MARK: - The view

/// `design-system/COMPONENTS.md § Toggle / Switch`. Takes an already-resolved, OPTIONAL `String` label --
/// never resolves `MentoraStrings` itself, matching `MentoraButton`/`MentoraTextField`'s own convention.
/// See this file's header "LOCKED RULE (v1.3)" for the disclosed, unenforceable-at-compile-time caller
/// responsibility around when `label` must be supplied.
struct MentoraToggle: View {
    @Binding var isOn: Bool
    var label: String? = nil

    @FocusState private var isFocused: Bool

    /// Explicit, unconditionally-`internal` init -- same reason as `MentoraButton`/`MentoraTextField`: a
    /// `@FocusState private var` stored property would otherwise make the implicit memberwise
    /// initializer `private`.
    init(isOn: Binding<Bool>, label: String? = nil) {
        self._isOn = isOn
        self.label = label
    }

    var body: some View {
        HStack(spacing: label == nil ? 0 : MentoraToggleMetrics.labelGap) {
            if let label {
                Text(label)
                    .mentoraFont(.labelLarge)
                    .foregroundStyle(Color.mentoraTextPrimary)
                    // The REAL accessible name/value/trait live on `toggleControl`'s own
                    // `.accessibilityRepresentation` below (a genuine native `Toggle`) -- without hiding
                    // this visible duplicate, VoiceOver would surface a redundant second "label" element
                    // alongside the switch, the exact same lesson `MentoraTextField.swift`'s own
                    // floated-label `Text` already discloses and avoids.
                    .accessibilityHidden(true)
            }
            toggleControl
        }
    }

    /// `ACCESSIBILITY.md` line 174 is explicit about why the native-control mandate (this file's header
    /// "NATIVE-CONTROL MAPPING") exists at all: the native control "inherits correct `aria-checked`/state
    /// announcements ... a custom-built View reproducing the visual without the native role is
    /// non-compliant", and v1.3 requires an announcement shaped like "Published, on". Real, replaced by
    /// `MentoraToggleStyle`'s custom `Button`-based chrome (a plain `Button` carries a button trait/no
    /// on-off VALUE, not a switch trait) -- fixed with `.accessibilityRepresentation`, which substitutes an
    /// entirely separate (never visually rendered) view's accessibility properties for this one: a REAL
    /// native `Toggle` styled `.switch` (explicit, so it does NOT pick `MentoraToggleStyle` back up from
    /// the environment and recurse). This also sidesteps a real localization trap the alternative
    /// (`.accessibilityValue("On"/"Off")`) would have created: this component may never resolve
    /// `MentoraStrings` itself, so hand-writing "On"/"Off" text here would either hardcode English or
    /// require a new caller-supplied parameter for text the OS already announces correctly, for free, on
    /// a genuine native `Toggle` -- delegating to a real one keeps that system-localized announcement
    /// intact. DISCLOSED CONFIDENCE: `.accessibilityRepresentation(representation:)` is believed real on
    /// this project's iOS 17 target (`project.yml`) but not independently re-verified against a live SDK
    /// header on this Windows host -- flagged per this task's own disclosure requirement; if CI reports it
    /// unavailable, the fallback is `.accessibilityAddTraits(.isToggle)` + a caller-supplied on/off string
    /// pair (mirroring `PasswordField`'s `showPasswordLabel`/`hidePasswordLabel` shape).
    @ViewBuilder
    private var toggleControl: some View {
        Toggle(isOn: $isOn) { EmptyView() }
            .toggleStyle(MentoraToggleStyle(isFocused: isFocused))
            .focused($isFocused)
            .accessibilityRepresentation {
                Toggle(isOn: $isOn) {
                    if let label {
                        Text(label)
                    }
                }
                .toggleStyle(.switch)
            }
    }
}

#if DEBUG

private struct MentoraTogglePreviewSwatches: View {
    @State private var onValue = true
    @State private var offValue = false
    @State private var namedStateValue = true
    @State private var disabledOnValue = true
    @State private var disabledOffValue = false

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            MentoraToggle(isOn: $onValue)
            MentoraToggle(isOn: $offValue)
            MentoraToggle(isOn: $namedStateValue, label: namedStateValue ? "Published" : "Draft")
            MentoraToggle(isOn: $disabledOnValue)
                .disabled(true)
            MentoraToggle(isOn: $disabledOffValue, label: "Draft")
                .disabled(true)
        }
    }
}

#Preview("MentoraToggle -- Light, on/off/named-label/disabled") {
    MentoraPreviewHost(title: "MentoraToggle -- Light / en", theme: .light, locale: .english) {
        MentoraTogglePreviewSwatches()
    }
}

#Preview("MentoraToggle -- Dark, on/off/named-label/disabled") {
    MentoraPreviewHost(title: "MentoraToggle -- Dark / en", theme: .dark, locale: .english) {
        MentoraTogglePreviewSwatches()
    }
}

#endif
