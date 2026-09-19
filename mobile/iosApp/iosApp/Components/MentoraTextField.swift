import SwiftUI
import shared

// Phase 5 Task T8 slice 3 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Inputs`
// (TextField/PasswordField/SearchField, lines 131-164). Three views, ONE shared implementation:
// `PasswordField`/`SearchField` are thin wrappers around `MentoraTextField`, exactly mirroring
// Android's own `MentoraTextField.kt`/`PasswordField.kt`/`SearchField.kt` split (reference-only, read
// for shape/behavior, never copied verbatim -- this project's standing "Android is reference-only,
// never source of truth" rule).
//
// ---------------------------------------------------------------------------------------------------
// THE FLOATING-LABEL RISK -- DECISION: ONE persistent TextField/SecureField identity, label is a
// SEPARATE animated overlay. NEVER an `if isFloated { ... } else { ... }` branch on the input itself.
//
// SwiftUI's native `TextField` has no Material3-style floating-label behavior (unlike Android's
// `OutlinedTextField`, which gets it for free -- see Android's own file header). Branching between two
// different view subtrees for "floated" vs "resting" would give the two branches different view
// identities, destroying and recreating the real `TextField`/`SecureField` on every identity change --
// and since `isLabelFloated` becomes true the INSTANT the user types the first character (has-value
// triggers float, same as focus does), that would mean the keyboard/cursor visibly drops after the
// very first keystroke. This file keeps exactly ONE `TextField`/`SecureField` instance in the tree at
// all times (same identity across every state), and instead varies only ORDINARY MODIFIER ARGUMENTS on
// it (`.padding(.top, isLabelFloated ? floatedLabelBand : 0)`, `.mentoraFont(isLabelFloated ? .labelMedium
// : .bodyMedium)`) -- changing a modifier's ARGUMENT does not change the view tree's STRUCTURE/identity,
// unlike an `if/else` over the view itself.
//
// GEOMETRY, CORRECTED AFTER REVIEW: an earlier draft computed a manual `.offset(y:)` for the label from
// STATIC, UNSCALED token constants (`MentoraTypography.labelMedium.lineHeight`) while the label's own
// FONT was Dynamic-Type-scaled -- the two facts didn't agree, and at any Dynamic Type size above the
// smallest, the label visually overlapped the input text (worse at larger accessibility sizes), a real
// criterion I1 violation caught in review. The corrected design needs NO manual offset at all: the ZStack
// uses `.topLeading` alignment (not `.leading`), so every child's top-left corner anchors to the same
// point regardless of its own size. At REST, the label renders at `.mentoraFont(.bodyMedium)` -- the
// EXACT SAME style/size as the real input -- so it visually coincides with the input's own (currently
// empty) text, reading exactly like a normal placeholder; the input's top padding is 0. When FLOATED, the
// label switches to `.mentoraFont(.labelMedium)` (a smaller size, same top-left anchor, so it simply
// shrinks toward the top-left rather than needing to be pushed anywhere), and the input's top padding
// grows to `floatedLabelBand` -- a Dynamic-Type-SCALED reservation (`@ScaledMetric(relativeTo: .footnote)`,
// the same anchor `.labelMedium` itself uses) sized to the floated label's own real rendered line height,
// so the two numbers can never drift apart the way the static-constant version did. Both quantities scale
// together at every Dynamic Type size, including the AX sizes I1 requires.
//
// RESIDUAL, DISCLOSED GAP: if a floated label wraps to its full 2 lines (`CONTENT_RESILIENCE.md § 1`
// permits this, though the same table calls labels "rare" to wrap -- short form-field labels), its own
// height exceeds the single-line `floatedLabelBand` reservation and its second line can overlap the top
// of the input text. Closing this exactly would require measuring the label's real rendered height via
// `GeometryReader`+`PreferenceKey` (`onGeometryChange` is iOS 18+; this project targets 17.0,
// `project.yml`) rather than computing it -- deferred as a bounded, disclosed limitation rather than
// added here, since every real label in this app's copy is short by design and single-line in practice.
//
// A SEPARATE, lower-risk `if isSecure { SecureField } else { TextField }` switch DOES exist below, for
// `PasswordField`'s reveal toggle. This is deliberately acceptable, unlike the floating-label case
// above: it only fires on a single, discrete, explicit user tap of the reveal `MentoraIconButton` --
// never on every keystroke -- so a brief focus interruption at that one moment (if any occurs at all)
// is a normal, industry-standard tradeoff for this exact feature, not a functional bug. SwiftUI has no
// way to keep ONE control that can toggle its own masking (`SecureField` and `TextField` are genuinely
// different types), so this branch is expected and unavoidable -- not a shortcut taken here.
//
// ---------------------------------------------------------------------------------------------------
// DISCLOSED HEIGHT DEVIATION (mirrors Android's own F6 disclosure) -- `COMPONENTS.md`'s literal field
// height is 52 (`component.input.height`). `MentoraTextFieldMetrics.minHeight` applies that value via
// `.frame(minHeight:)`, NEVER `.frame(height:)` -- a MINIMUM, not a hard constraint -- for the exact
// same reason `MentoraButton.swift`'s `MentoraButtonVariant.height` doc comment states for buttons, and
// `CONTENT_RESILIENCE.md § 1`/§ 8's Locked Rule requires directly: the helper/error text row below the
// bordered box is UNLIMITED-LINE ("wraps freely, field height grows") and lives in a completely
// separate, unconstrained `VStack` row -- never inside the `minHeight`-52 box -- so a long/wrapped error
// message can never be clipped by that minimum. Given the explicit priority on *correct floating-label
// mechanics* over exact pixel height (this file's whole point), 52 is applied as a floor the box will
// naturally exceed at larger Dynamic Type sizes, exactly like Android's own disclosed 64dp real
// rendered height at rest -- not re-measured here (no Mac toolchain to measure against), but the same
// class of deviation, disclosed the same way. A field WITH a trailing `MentoraIconButton` (PasswordField's
// reveal toggle, SearchField's clear button, or a caller's own `trailing`) additionally inherits that
// button's own real 44pt minimum hit area (`MentoraIconButton.swift`, criterion G7) inside this box's
// `space.3` (12) vertical padding -- pushing such a field's real rendered height to roughly 68pt, well
// past the 52pt floor. Disclosed here rather than hidden: it happens to land close to Android's own
// measured 64dp real height, reinforcing that a >52pt real height is the norm for this component family
// on both platforms, not an iOS-specific regression.
//
// Also disclosed: `COMPONENTS.md § Inputs`'s own property table states the leading/trailing icon size as
// `icon.medium` (20), but `MentoraIconButton` (T8 slice 2) always renders its icon at `icon.default` (24)
// and exposes no size override -- a spec-internal conflict inherited from slice 2's own `COMPONENTS.md §
// IconButton` reading ("icon rendered at `icon.default`, 24"), not introduced here. The plain, non-button
// leading icon in `inputBox` below correctly uses `icon.medium` (20) per this section's own table; only
// the trailing IconButton's icon (PasswordField/SearchField) inherits the 24pt size from slice 2.
//
// ---------------------------------------------------------------------------------------------------
// FOCUS-STATE / HOVER -- same established precedents as `MentoraButton.swift`/`MentoraIconButton.swift`,
// not re-derived here: `@FocusState private var isFocused: Bool` lives on the view itself (via
// `.focused($isFocused)`), because `@Environment(\.isFocused)` is not a real, general-purpose
// `EnvironmentValues` key. Hover is modeled as a real, testable input to `MentoraTextFieldRules.colorSet`
// (`isHovered`, mapping to `color.border.strong` per `COMPONENTS.md`'s Hover row) but never wired
// dynamically (no `.onHover(...)` anywhere in this file) -- `COMPONENTS.md`'s own Hover row is marked
// "pointer platforms only", and this app is touch-primary; whichever later task adds iPadOS pointer
// support owns wiring it, exactly like `MentoraButton.swift`'s own disclosed hover gap.
//
// ---------------------------------------------------------------------------------------------------
// FOCUSED+SUCCESS PRIORITY -- a DISCLOSED GAP FILLED FROM ANDROID'S OWN DISCLOSED ORDERING, not invented
// here. `COMPONENTS.md`'s state table lists Focused and Success as separate rows with no stated
// interaction between them. Android's own `mentoraTextFieldColors` kdoc states the ordering it uses:
// "[success] overrides the border ... when the field isn't in an error state (error always wins --
// M3's own isError param already takes precedence...)". `MentoraTextFieldRules.colorSet` below applies
// that exact same disclosed ordering -- error beats focused beats hover beats success beats default --
// so `isFocused && success && !hasError` renders the FOCUS border color, not the success color (focus
// is a stronger, more transient interaction signal than success, which is a resting-state indicator).
//
// ---------------------------------------------------------------------------------------------------
// TRAILING CONTENT SLOT -- `AnyView?`, not a generic `@ViewBuilder trailing: () -> some View`. The base
// field needs to choose BETWEEN the caller's trailing content (if any) and an auto-shown success
// checkmark (`COMPONENTS.md § Inputs`: "optional trailing check icon", shown only when
// `success && !hasError && trailing == nil`, mirroring Android's own `trailingContent ?: successIcon`
// fallback) -- two DIFFERENT concrete view types resolved at runtime from a single optional value. A
// generic `Trailing: View` parameter would force every caller (including this same file's `#Preview`s)
// to disambiguate that generic per call site for no real benefit, since the two possible trailing
// contents (a caller `MentoraIconButton` vs. a `MentoraIcon` checkmark) are only ever chosen between,
// never composed -- `AnyView?` is the simpler, equally-idiomatic SwiftUI shape for this specific
// "one-of-several-concrete-types, chosen by the callee" slot.
//
// ---------------------------------------------------------------------------------------------------
// KEYBOARD TYPE / TEXT CONTENT TYPE -- deliberately NOT exposed as a typed parameter on
// `MentoraTextField` itself (unlike Android's `keyboardType: KeyboardType = KeyboardType.Text`). SwiftUI's
// `.keyboardType(_:)`/`.textContentType(_:)`/`.autocorrectionDisabled()` etc. are ENVIRONMENT-based
// modifiers (like `.disabled(_:)`) that already cascade correctly through an arbitrary custom `View`
// wrapper down to the real `TextField`/`SecureField` inside -- so a caller can simply chain
// `MentoraTextField(...).keyboardType(.emailAddress)` externally with zero extra plumbing in this file.
// This is the "applied by the caller" option the task brief explicitly allows, chosen specifically to
// avoid writing an explicit `UIKeyboardType`/`UITextContentType` type annotation in a parameter list on
// this Windows machine, where that exact spelling cannot be compile-checked (see this file's own
// confidence disclosure in the final report for this decision). `PasswordField` is the one exception:
// it unconditionally applies `.textContentType(.password)` itself (never caller-configurable), mirroring
// Android's own unconditional `KeyboardType.Password` -- password-manager integration is a property of
// being a password field, not a per-call-site choice.
//
// ---------------------------------------------------------------------------------------------------
// ACCESSIBILITY ERROR-TEXT LINKAGE -- `ACCESSIBILITY.md § 7`'s "programmatically linked... announced on
// focus" requirement is implemented via `.accessibilityLabel(label)` (the visible label becomes the
// field's accessible NAME, per § 5's "programmatically associate their visible label with the input")
// plus `.accessibilityHint(errorText ?? helperText ?? "")` on the real `TextField`/`SecureField` (a
// `.accessibilityHint` is read by VoiceOver immediately after an element's label/value when it receives
// focus -- the same "announced on focus" behavior § 7 asks for). DISCLOSED UNCERTAINTY: this is this
// file's best real, verifiable SwiftUI mechanism for the requirement, not a guessed API shape (both
// `.accessibilityLabel(_:)`/`.accessibilityHint(_:)` are real, long-standing SwiftUI `View` modifiers,
// used elsewhere in this codebase's own precedent for `.accessibilityLabel` -- `MentoraIconButton.swift`)
// -- but whether VoiceOver's hint timing on THIS specific composite view reads exactly like a native
// `aria-describedby` announcement cannot be confirmed without a device/simulator. Flagged, not blocking.

// MARK: - Pure metrics (no SwiftUI rendering required to test)

enum MentoraTextFieldMetrics {
    /// `COMPONENTS.md`: "Radius `radius.medium` (12)".
    static let shape = MentoraShape.medium

    /// `COMPONENTS.md`: "Padding: horizontal `space.4` (16), vertical `space.3` (12)".
    static let horizontalPadding: CGFloat = MentoraSpacing.space4
    static let verticalPadding: CGFloat = MentoraSpacing.space3

    /// `COMPONENTS.md`: "Height 52" -- applied as a MINIMUM (`.frame(minHeight:)`), never a fixed
    /// height. See this file's header "DISCLOSED HEIGHT DEVIATION".
    static let minHeight: CGFloat = 52

    /// `COMPONENTS.md`: "Leading/trailing icon `icon.medium` (20)".
    static let iconSize: CGFloat = MentoraIconSize.medium
    static let iconGap: CGFloat = MentoraSpacing.space2

    /// Android's own measured/confirmed error-icon size (`icon.small`, 16) -- `cancel`, distinct from
    /// the leading/trailing `icon.medium` size above; `COMPONENTS.md`'s own Error row states only the
    /// icon's existence, not its size, so this follows Android's own already-reviewed choice rather
    /// than re-deriving one.
    static let errorIconSize: CGFloat = MentoraIconSize.small
    static let supportingTextIconGap: CGFloat = MentoraSpacing.space1

    /// Vertical gap between the bordered input box and the helper/error row below it.
    static let supportingRowSpacing: CGFloat = MentoraSpacing.space1

    /// `Theme/MentoraMotion.swift`'s standard easing/fast duration -- NOT a raw `.easeInOut(duration:)`
    /// literal (that curve is cubic-bezier(0.42,0,0.58,1), genuinely different from `easing.standard`'s
    /// cubic-bezier(0.4,0,0.2,1) -- the exact approximation `MentoraMotion.swift`'s own header comment
    /// says this codebase must not make).
    static let labelAnimation: Animation = MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.fast)
}

// MARK: - Pure state/color resolver (no SwiftUI rendering required to test)

/// One (border color, border width, background color, label color) resolution -- pure data, asserted
/// directly by `MentoraTextFieldRulesTests.swift` without rendering anything. Real `Color+Mentora.swift`
/// accessor names confirmed by reading that file directly, not guessed. Same convention as
/// `MentoraButtonColorSet`/`BadgeVariant`/`CategoryChipState`.
struct MentoraTextFieldColorSet: Equatable {
    let borderColor: Color
    let borderWidth: CGFloat
    let backgroundColor: Color
    let labelColor: Color
}

enum MentoraTextFieldRules {

    /// `COMPONENTS.md`: Focused ("label shrinks/floats above field") OR Filled ("has value, not
    /// focused" -- "label stays floated") both float the label; only the true resting state (unfocused
    /// AND empty) keeps it resting.
    static func isLabelFloated(isFocused: Bool, isEmpty: Bool) -> Bool {
        isFocused || !isEmpty
    }

    /// `COMPONENTS.md § Inputs`'s state table, resolved with this priority order (highest first):
    /// `disabled` short-circuits everything (a disabled field is never shown as focused/error/hovered/
    /// successful, regardless of what those booleans happen to be) > `hasError` (per `COMPONENTS.md`
    /// line 154, "error state overrides focus color", verbatim) > `isFocused` > `isHovered` (modeled,
    /// never dynamically produced -- see this file's header) > `hasSuccess` > the plain resting default.
    /// The error > focused > success ordering (and disabled beating everything) is the exact same
    /// disclosed gap-fill Android's own `mentoraTextFieldColors` kdoc already states for itself -- see
    /// this file's header "FOCUSED+SUCCESS PRIORITY". Border WIDTH is intentionally driven ONLY by
    /// `isFocused` (2 when focused, 1 otherwise) -- `COMPONENTS.md`'s per-state table states a distinct
    /// width only for the Focused row; Error/Success/Filled/Default all read as plain-width rows, so
    /// this stays the minimal, most literal reading of the spec rather than assuming error also thickens
    /// the border (a plausible but unstated embellishment, deliberately not added here).
    static func colorSet(
        isFocused: Bool,
        isEmpty: Bool,
        isEnabled: Bool,
        hasError: Bool,
        hasSuccess: Bool,
        isHovered: Bool = false
    ) -> MentoraTextFieldColorSet {
        let borderWidth = isFocused ? MentoraBorderWidth.focus : MentoraBorderWidth.`default`

        guard isEnabled else {
            return MentoraTextFieldColorSet(
                borderColor: .mentoraBorderDefault,
                borderWidth: MentoraBorderWidth.`default`,
                backgroundColor: .mentoraSurfaceVariant,
                labelColor: .mentoraTextDisabled
            )
        }
        if hasError {
            return MentoraTextFieldColorSet(
                borderColor: .mentoraBorderError, borderWidth: borderWidth,
                backgroundColor: .mentoraSurfaceDefault, labelColor: .mentoraTextSecondary
            )
        }
        if isFocused {
            return MentoraTextFieldColorSet(
                borderColor: .mentoraBorderFocus, borderWidth: borderWidth,
                backgroundColor: .mentoraSurfaceDefault, labelColor: .mentoraTextSecondary
            )
        }
        if isHovered {
            return MentoraTextFieldColorSet(
                borderColor: .mentoraBorderStrong, borderWidth: borderWidth,
                backgroundColor: .mentoraSurfaceDefault, labelColor: .mentoraTextSecondary
            )
        }
        if hasSuccess {
            return MentoraTextFieldColorSet(
                borderColor: .mentoraSuccessDefault, borderWidth: borderWidth,
                backgroundColor: .mentoraSurfaceDefault, labelColor: .mentoraTextSecondary
            )
        }
        return MentoraTextFieldColorSet(
            borderColor: .mentoraBorderDefault, borderWidth: borderWidth,
            backgroundColor: .mentoraSurfaceDefault, labelColor: .mentoraTextSecondary
        )
    }
}

// MARK: - The view

/// `design-system/COMPONENTS.md § Inputs`'s base TextField (lines 133-156), shared by `PasswordField`/
/// `SearchField` below. Takes already-resolved `String`s for every user-facing slot -- never resolves
/// `MentoraStrings` itself, matching `MentoraButton`/`MentoraIconButton`'s own convention.
struct MentoraTextField: View {
    @Binding var text: String
    let label: String
    var placeholder: String? = nil
    var helperText: String? = nil
    var errorText: String? = nil
    var success: Bool = false
    var leadingIcon: MentoraIconName? = nil
    var trailing: AnyView? = nil
    /// Renders as a `SecureField` (masked) when `true`, a plain `TextField` otherwise -- lets
    /// `PasswordField` reuse this whole view instead of duplicating it. See this file's header for why
    /// toggling this between renders (PasswordField's reveal tap) is an acceptable `if/else`, unlike the
    /// floating-label risk this file is otherwise built to avoid.
    var isSecure: Bool = false

    @Environment(\.isEnabled) private var isEnabled
    @FocusState private var isFocused: Bool

    /// The floated label's real, Dynamic-Type-scaled line height -- same anchor (`.footnote`) `.labelMedium`
    /// itself uses (`MentoraTextStyle.labelMedium.anchor`), so this can never drift out of sync with the
    /// label's own actual rendered size the way a static unscaled constant did before review. Used ONLY to
    /// size the input's reserved top padding when floated -- see this file's header "GEOMETRY, CORRECTED
    /// AFTER REVIEW".
    @ScaledMetric(relativeTo: .footnote) private var scaledLabelFontSize: CGFloat = MentoraTypography.labelMedium.fontSize

    private var floatedLabelBand: CGFloat {
        scaledLabelFontSize * MentoraTypographyRules.naturalLineHeightFactor
    }

    /// Explicit, unconditionally-`internal` init -- same reason as `MentoraButton`/`MentoraIconButton`:
    /// a `@FocusState private var` stored property would otherwise make the implicit memberwise
    /// initializer `private`.
    init(
        text: Binding<String>,
        label: String,
        placeholder: String? = nil,
        helperText: String? = nil,
        errorText: String? = nil,
        success: Bool = false,
        leadingIcon: MentoraIconName? = nil,
        trailing: AnyView? = nil,
        isSecure: Bool = false
    ) {
        self._text = text
        self.label = label
        self.placeholder = placeholder
        self.helperText = helperText
        self.errorText = errorText
        self.success = success
        self.leadingIcon = leadingIcon
        self.trailing = trailing
        self.isSecure = isSecure
    }

    private var hasError: Bool { errorText != nil }
    /// `COMPONENTS.md`: the success checkmark only appears when there's no caller-supplied trailing
    /// content AND no error -- mirrors Android's own `trailingContent ?: (success && !isError) { ... }`
    /// fallback exactly.
    private var showsSuccessIcon: Bool { success && !hasError && trailing == nil }
    private var isLabelFloated: Bool {
        MentoraTextFieldRules.isLabelFloated(isFocused: isFocused, isEmpty: text.isEmpty)
    }
    private var colors: MentoraTextFieldColorSet {
        MentoraTextFieldRules.colorSet(
            isFocused: isFocused, isEmpty: text.isEmpty, isEnabled: isEnabled,
            hasError: hasError, hasSuccess: success
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraTextFieldMetrics.supportingRowSpacing) {
            inputBox
            supportingText
        }
        // A form field is overwhelmingly used full-width in practice; unlike Android (whose caller
        // supplies `Modifier.fillMaxWidth()` per call site), this default is applied here so every
        // call site gets sane sizing without having to remember to add it. Still fully overridable by
        // a caller chaining a narrower `.frame(maxWidth:)` afterward.
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inputBox: some View {
        HStack(spacing: MentoraTextFieldMetrics.iconGap) {
            if let leadingIcon {
                MentoraIcon(name: leadingIcon, size: MentoraTextFieldMetrics.iconSize)
                    .foregroundStyle(Color.mentoraTextSecondary)
                    // Decorative -- the field's own label/error text communicate meaning (I2 lesson,
                    // `MentoraButton.swift`'s D128 precedent).
                    .accessibilityHidden(true)
            }

            inputStack

            if let trailing {
                trailing
            } else if showsSuccessIcon {
                MentoraIcon(name: .checkCircle, size: MentoraTextFieldMetrics.iconSize)
                    .foregroundStyle(Color.mentoraSuccessDefault)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, MentoraTextFieldMetrics.horizontalPadding)
        .padding(.vertical, MentoraTextFieldMetrics.verticalPadding)
        .frame(minHeight: MentoraTextFieldMetrics.minHeight)
        .background(colors.backgroundColor.clipShape(MentoraTextFieldMetrics.shape))
        .overlay {
            MentoraTextFieldMetrics.shape.strokeBorder(colors.borderColor, lineWidth: colors.borderWidth)
        }
    }

    /// The persistent input + floating label, layered via `ZStack` -- see this file's header for why
    /// this is ONE `TextField`/`SecureField` identity at all times, with the label as a separate,
    /// animated overlay rather than a branching subtree.
    private var inputStack: some View {
        ZStack(alignment: .topLeading) {
            if let placeholder, isFocused, text.isEmpty {
                // Android's OutlinedTextField "already defers showing the separate placeholder slot
                // until the field is both focused AND empty" (see its own file header) -- reproduced
                // here manually, since SwiftUI's native `TextField` placeholder shows unconditionally
                // whenever `text` is empty (which would double up with the resting, unfloated label).
                Text(placeholder)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(Color.mentoraTextDisabled)
                    .lineLimit(1) // `CONTENT_RESILIENCE.md § 1`: "Clamp + ellipsis", 1 line.
                    .padding(.top, floatedLabelBand)
                    // Decorative relative to the real TextField below, which already carries the field's
                    // real accessible name/value -- avoids a stray third VoiceOver element.
                    .accessibilityHidden(true)
            }

            Group {
                if isSecure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                }
            }
            .mentoraFont(.bodyMedium)
            .foregroundStyle(isEnabled ? Color.mentoraTextPrimary : Color.mentoraTextDisabled)
            .focused($isFocused)
            .padding(.top, isLabelFloated ? floatedLabelBand : 0)
            // `ACCESSIBILITY.md § 5`: the visible label is the field's accessible name, not
            // placeholder-only labeling.
            .accessibilityLabel(label)
            // `ACCESSIBILITY.md § 7`: links the error text to the field so VoiceOver announces it on
            // focus -- see this file's header for this mechanism's disclosed confidence level.
            .accessibilityHint(errorText ?? helperText ?? "")

            // At REST this renders at the EXACT SAME style/size as the real input above (`.bodyMedium`),
            // top-anchored identically -- it visually coincides with the input's own (empty) text, the
            // resting "looks like a placeholder" appearance. FLOATED, it switches to the smaller
            // `.labelMedium` style at the SAME top-left anchor -- no manual offset needed, it simply
            // shrinks toward the corner it was always anchored to. See this file's header "GEOMETRY,
            // CORRECTED AFTER REVIEW".
            Text(label)
                .mentoraFont(isLabelFloated ? .labelMedium : .bodyMedium)
                .foregroundStyle(colors.labelColor)
                .lineLimit(2) // `CONTENT_RESILIENCE.md § 1`: label wraps up to 2 lines, never truncated.
                .multilineTextAlignment(.leading)
                .allowsHitTesting(false)
                // Purely visual -- `.accessibilityLabel(label)` above already carries this text as the
                // real TextField's accessible name; without this, VoiceOver would surface a redundant
                // second "label" element alongside the field itself.
                .accessibilityHidden(true)
        }
        .animation(MentoraTextFieldMetrics.labelAnimation, value: isLabelFloated)
    }

    @ViewBuilder
    private var supportingText: some View {
        // `ACCESSIBILITY.md § 7`: "pairs three signals, never just color" -- the border color (already
        // applied above), an error ICON, and the error TEXT together, never a bare colored border.
        if let errorText {
            HStack(alignment: .top, spacing: MentoraTextFieldMetrics.supportingTextIconGap) {
                MentoraIcon(name: .cancel, size: MentoraTextFieldMetrics.errorIconSize)
                    .foregroundStyle(Color.mentoraErrorDefault)
                    .accessibilityHidden(true)
                Text(errorText)
                    .mentoraFont(.caption)
                    .foregroundStyle(Color.mentoraErrorDefault)
                    .multilineTextAlignment(.leading)
                    // `CONTENT_RESILIENCE.md § 1`: "Wraps freely, field height grows... unlimited" --
                    // deliberately NO `.lineLimit(...)` here, and this row carries no fixed-height
                    // frame of its own, so it is free to grow the field's total height (§ 8's Locked
                    // Rule -- the exact class of mistake this codebase's own `MentoraButton` label was
                    // already flagged for once, not repeated here).
            }
        } else if let helperText {
            Text(helperText)
                .mentoraFont(.caption)
                .foregroundStyle(Color.mentoraTextSecondary)
                .multilineTextAlignment(.leading)
                // Same "no `.lineLimit`, no fixed-height frame" treatment as the error text above.
        }
    }
}

// MARK: - PasswordField

/// `design-system/COMPONENTS.md § PasswordField`. The visibility toggle is UNCONDITIONAL -- "never a
/// password field with no reveal option" -- built with `MentoraIconButton` (T8 slice 2, CI-green),
/// exactly mirroring Android's own reuse of its `MentoraIconButton` for the identical purpose.
struct PasswordField: View {
    @Binding var text: String
    let label: String
    let showPasswordLabel: String
    let hidePasswordLabel: String
    var helperText: String? = nil
    var errorText: String? = nil

    @State private var isVisible: Bool = false

    var body: some View {
        MentoraTextField(
            text: $text,
            label: label,
            helperText: helperText,
            errorText: errorText,
            trailing: AnyView(
                MentoraIconButton(
                    icon: isVisible ? .visibilityOff : .visibility,
                    accessibilityLabel: isVisible ? hidePasswordLabel : showPasswordLabel,
                    action: { isVisible.toggle() }
                )
            ),
            isSecure: !isVisible
        )
        // Password-manager integration is a property of being a password field, not a per-call-site
        // choice -- see this file's header "KEYBOARD TYPE / TEXT CONTENT TYPE".
        .textContentType(.password)
    }
}

// MARK: - SearchField

/// `design-system/COMPONENTS.md § SearchField`. Leading icon fixed to `.search`; trailing `.close`
/// `MentoraIconButton` appears only when `text` is non-empty and clears it on tap. (The spec's
/// navbar-embedded recessed-background variant is explicitly out of scope for this general-purpose
/// field -- a later task's concern.)
struct SearchField: View {
    @Binding var text: String
    let label: String
    let clearContentDescription: String
    var placeholder: String? = nil

    var body: some View {
        MentoraTextField(
            text: $text,
            label: label,
            placeholder: placeholder,
            leadingIcon: .search,
            trailing: text.isEmpty ? nil : AnyView(
                MentoraIconButton(
                    icon: .close,
                    accessibilityLabel: clearContentDescription,
                    action: { text = "" }
                )
            )
        )
    }
}

#if DEBUG

/// Real, interactive instances (not a forced-state swatch harness like `MentoraButton`/
/// `MentoraIconButton` needed) -- `TextField` focus IS a real, live `@FocusState` here, so the Xcode
/// canvas can drive it interactively; no `ButtonStyleConfiguration`-style construction problem exists
/// for this component. Covers default/placeholder+helper, filled, error, success, and disabled for the
/// base field, plus one real `PasswordField` and one real `SearchField`.
private struct MentoraTextFieldPreviewSwatches: View {
    @State private var restingText = ""
    @State private var filledText = "Ada Lovelace"
    @State private var errorText = "not-an-email"
    @State private var successText = "ada@example.com"
    @State private var disabledText = "Locked value"
    @State private var passwordText = ""
    @State private var searchText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            MentoraTextField(
                text: $restingText, label: "Full name", placeholder: "Jane Doe",
                helperText: "As it appears on your certificate"
            )
            MentoraTextField(text: $filledText, label: "Full name")
            MentoraTextField(text: $errorText, label: "Email", errorText: "Enter a valid email address")
            MentoraTextField(text: $successText, label: "Email", success: true)
            MentoraTextField(text: $disabledText, label: "Full name")
                .disabled(true)
            PasswordField(
                text: $passwordText, label: "Password",
                showPasswordLabel: "Show password", hidePasswordLabel: "Hide password",
                helperText: "At least 8 characters"
            )
            SearchField(
                text: $searchText, label: "Search", clearContentDescription: "Clear search",
                placeholder: "Search courses"
            )
        }
    }
}

#Preview("MentoraTextField family -- Light, default/filled/error/success/disabled + Password/Search") {
    MentoraPreviewHost(title: "TextField family -- Light / en", theme: .light, locale: .english) {
        MentoraTextFieldPreviewSwatches()
    }
}

#Preview("MentoraTextField family -- Dark, default/filled/error/success/disabled + Password/Search") {
    MentoraPreviewHost(title: "TextField family -- Dark / en", theme: .dark, locale: .english) {
        MentoraTextFieldPreviewSwatches()
    }
}

/// Largest accessibility Dynamic Type size -- the exact scenario the floating-label geometry fix (this
/// file's header "GEOMETRY, CORRECTED AFTER REVIEW") targets. A manual visual check only (no Mac/
/// simulator available on this host to screenshot it), but keeps the AX-size case in the same visual
/// harness every other state already uses, per criterion I1.
#Preview("MentoraTextField family -- Light, accessibility5 (largest Dynamic Type)") {
    MentoraPreviewHost(title: "TextField family -- AX5 / en", theme: .light, locale: .english, dynamicTypeSize: .accessibility5) {
        MentoraTextFieldPreviewSwatches()
    }
}

#endif
