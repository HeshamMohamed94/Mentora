import SwiftUI
import shared

// Phase 5 Task T8 slice 5 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Select / Dropdown`
// (lines 189-241). Field styling is IDENTICAL to `MentoraTextField`'s (same height/radius/padding/
// typography/border/background/text tokens) -- Select is an input VARIANT, not a visually distinct
// control. Android's own `MentoraSelect.kt` (reference-only, read for behavior comparison, never
// source of truth) confirms the same "identical field chrome, only the trailing indicator + popover
// differ" framing, built on Material3's `ExposedDropdownMenuBox` there vs. this file's native SwiftUI
// `Menu` here -- the two platforms' own native mapping, per `COMPONENTS.md` line 235.
//
// ---------------------------------------------------------------------------------------------------
// SYSTEM-OWNED POPOVER CHROME -- A KNOWN, ALREADY-ACCEPTED ARCHITECTURAL DEVIATION.
// `COMPONENTS.md`'s "Option list (menu)" tables (lines 217-229) state the popover surface as
// `color.surface.elevated`/`elevation.3`/`radius.medium`, and each option ROW's background as
// transparent/hover-tinted/`color.brand.primaryContainer`(selected)/transparent(disabled). SwiftUI's
// native `Menu` popover is SYSTEM-OWNED: its background material, elevation/shadow, corner radius, and
// per-row highlight/selection background are all rendered by UIKit's/SwiftUI's own menu presentation
// machinery and are NOT exposed as styleable API surface (`Menu`/`MenuStyle`'s real, documented API has
// no background/elevation/corner-radius/row-background parameter). This file deliberately does NOT
// attempt to fight that chrome with hacks, and does NOT build a from-scratch custom popover to chase
// pixel-exactness -- exactly the kind of excessive-polish work this project's current phase explicitly
// avoids, and the task's own explicit instruction. What this file CAN and DOES still get right within
// `Menu`'s real capabilities: each option's real user-visible text, a selected-option indicator (see
// "MENU VS. PICKER" below), and per-option disabling (`.disabled(!option.isEnabled)`, which the SYSTEM
// enforces regardless of styling -- a disabled row is genuinely non-selectable either way).
//
// CORRECTED AFTER REVIEW -- WHAT THIS FILE CANNOT CONFIDENTLY CLAIM: an earlier draft of this comment
// additionally claimed the `.mentoraFont(.bodyMedium)`/`.lineLimit(1)`/`.foregroundStyle(...
// mentoraTextDisabled)` applied to each option row's own `Text`/`HStack` (`menuContent` below) reliably
// SURVIVE into the rendered popover. `Menu`'s content is bridged to UIKit's `UIMenu`/`UIAction` machinery,
// which extracts text/image and very likely re-renders each row with the SYSTEM's own font/color/layout,
// not this view's own modifiers -- so those calls may be inert in the real popover (a disabled row will
// still visually dim, but from the system's own disabled-action treatment, not from this file's explicit
// color). They are kept anyway (textually correct against `COMPONENTS.md` line 222's typography
// requirement, free if the rendering behavior ever changes, harmless if not) rather than removed, but this
// file no longer claims they are confirmed to visibly apply. The single highest-uncertainty item in this
// file is whether the `.checkCircle` selected-indicator glyph (an asset-catalog image, not SF Symbol)
// survives that same extraction -- if it does not, this component has NO visible selection signal left at
// all (the row-background signal is already the accepted deviation above), and `Picker(selection:)
// .pickerStyle(.menu)` (which gets a native checkmark for free) would become the more correct choice after
// all. Flagged as the FIRST thing to check on a real simulator/device (MC-3), not verifiable on this
// Windows host.
//
// ---------------------------------------------------------------------------------------------------
// MENU VS. PICKER -- A REAL, DISCLOSED CHOICE: `Menu` (manual `Button` rows), not
// `Picker(selection:).pickerStyle(.menu)`. Reasons: (1) `Picker`'s native checkmark/selection handling
// does not, to this file's author's confidence, cleanly support a genuine "no selection yet, showing a
// placeholder" state -- `Picker(selection:)` wants its binding to always match one of the presented
// tags, and forcing a `nil`-tagged placeholder row into that shape is a less certain, less-idiomatic fit
// than a plain optional-binding `Menu` that simply renders whatever `selection` currently is. (2) Per-row
// DISABLING with a specific dimmed color (`color.text.disabled`) for individually-disabled options is
// direct and explicit with `Button(...).disabled(true) + .foregroundStyle(...)` per row -- exactly the
// mapping this task's own brief names as the `Menu` path -- whereas `Picker`'s per-tag disabling/styling
// surface is less direct to control precisely. (3) The system-owned-chrome deviation above already
// forces this file to substitute a manual checkmark for the spec's row-background highlight regardless
// of which path is chosen, so `Picker`'s main advantage (native checkmark for free) is a smaller
// differentiator than it would otherwise be. Net: `Menu` gives strictly more direct control over exactly
// the two things this component most needs to get right (placeholder/no-selection + per-option
// disabling), at the cost of writing the checkmark by hand -- a small, explicit, testable cost. The
// selected-option indicator uses `MentoraIconName.checkCircle` (already a real case, confirmed by
// reading `Theme/MentoraIcon.swift` directly) rather than `Image(systemName: "checkmark")` -- this
// codebase has ZERO existing SF Symbol usage anywhere (confirmed by a direct grep before writing this
// file), and `.checkCircle` is the SAME glyph `MentoraTextField.swift`'s own success-checkmark treatment
// already uses for an identical "this is the correct/chosen one" semantic -- the more consistent,
// idiomatic choice for THIS codebase specifically, even though it's a filled-circle glyph rather than a
// bare checkmark.
//
// ---------------------------------------------------------------------------------------------------
// COLOR RESOLVER REUSE -- `MentoraSelect` calls `MentoraTextFieldRules.colorSet(...)` DIRECTLY, with ZERO
// changes to that function's signature. This works because `COMPONENTS.md`'s Select state table's Open
// row is COLOR-IDENTICAL to its Focused row (`color.border.focus`, 2px, `color.surface.default` --
// verified directly from the table, not assumed) -- the two states differ only in the TRAILING ICON
// (`expand_more` vs `expand_less`, a Select-only concern this file handles separately, see
// `MentoraSelectRules.trailingIconName`) and in whether the popover is actually presented, neither of
// which `MentoraTextFieldColorSet` represents at all. So "is this field showing focus-style chrome" is
// simply `isFocused || isOpen` (`MentoraSelectRules.effectiveIsFocused`, a small named, directly-testable
// function rather than an inline `||` buried in the view) -- passed as `MentoraTextFieldRules.colorSet`'s
// existing `isFocused` parameter, with no new parameter added and no second, drifting copy of that
// resolver's logic. `isEmpty` is passed through too (for API-shape completeness) but is a documented
// NO-OP inside that function today (confirmed by reading `colorSet`'s real body -- it never references
// `isEmpty`, only `isLabelFloated` does), so its value here is inert either way. `hasSuccess` is always
// `false` -- `COMPONENTS.md § Select / Dropdown` names no Success state at all, unlike TextField's.
//
// TWO DISCLOSED-DEAD STATE ROWS -- same "modeled but not (fully) wired" convention `MentoraTextField.swift`
// already establishes for its own Hover row. (1) Hover (`COMPONENTS.md` line 208, "pointer platforms
// only") is never produced here either -- `isHovered` is not even threaded through to `colorSet`'s call
// (defaults `false`), consistent with this app being touch-primary. (2) `COMPONENTS.md` line 209's
// "Focused ... not yet open -- reached via Tab" row is very likely unreachable on iOS in practice: a `Menu`
// trigger is not meaningfully keyboard-focusable the way a `TextField` is, so `.focused($isFocused)`
// (below) probably never becomes `true` on a touch device. This is harmless rather than broken --
// `effectiveIsFocused` ORs it with `isOpen`, which real user interaction DOES reliably set -- but the
// pure "focused, not yet open" chrome specifically is likely dead code, same class of gap as Hover.
//
// ---------------------------------------------------------------------------------------------------
// FIELD LABEL -- STATIC, NOT FLOATING, A DELIBERATE JUDGMENT CALL. `COMPONENTS.md`'s Select property
// table states only "Label typography `typography.label.medium`, `color.text.secondary`" -- unlike
// TextField's own states table, Select's per-state table (lines 205-213) NEVER mentions the label
// shrinking/floating at all, in any row. Rebuilding `MentoraTextField.swift`'s whole animated
// floating-label mechanism here would be unrequested complexity for a control that, per the spec's own
// silence on the subject, does not ask for it -- the simpler, spec-literal reading is a plain, always-
// visible label rendered ABOVE the field (Select always has a "value concept" at rest, via its own
// placeholder, unlike TextField's genuinely-empty resting state that floating exists to distinguish).
// This file therefore renders `Text(label)` once, statically, above `fieldBox`, HIDDEN from VoiceOver
// (`.accessibilityHidden(true)`) since the real accessible name lives on the `Menu` control itself via
// `.accessibilityLabel(label)` -- the exact same "avoid a redundant second VoiceOver element" technique
// `MentoraTextField.swift`'s own floated-label `Text` already established.
//
// ---------------------------------------------------------------------------------------------------
// SUPPORTING TEXT (HELPER/ERROR ROW) -- DUPLICATED, NOT SHARED, AND WHY. Android's own `MentoraSelect.kt`
// reuses a shared `MentoraFieldSupportingText` composable also used by its `MentoraTextField.kt`. The
// iOS equivalent view logic (`MentoraTextField.swift`'s `supportingText` computed property) is a
// `private`, INSTANCE-scoped property of that file's `MentoraTextField` struct -- not a free, reusable
// type -- so true sharing would require refactoring `MentoraTextField.swift` to extract a standalone
// view type. This task's own scope is constrained to exactly the new files it adds (this file + its
// test, plus `MentoraToggle.swift` + its test) -- touching `MentoraTextField.swift` is out of scope for
// this pass. The PRAGMATIC choice taken: reuse `MentoraTextFieldMetrics`'s real constants directly
// (`errorIconSize`/`supportingTextIconGap`/`supportingRowSpacing`) so no numeric value drifts from
// TextField's own, but duplicate the small (~15-line) view composition itself locally as this file's own
// `supportingText`. Disclosed here rather than silently copy-pasted.
//
// ---------------------------------------------------------------------------------------------------
// LOADING STATE -- THE MENU IS FULLY DISABLED WHILE LOADING, NOT LEFT OPENABLE. `COMPONENTS.md`: "the
// field shows a disabled-looking state with a small inline spinner ... until options resolve; if opened
// before options are ready, the menu shows a single 'Loading options...' row". This file disables the
// `Menu` entirely while `isLoading` (`fieldEnabled = isEnabled && !isLoading`, mirroring Android's own
// identical `fieldEnabled` split and this kit's own `MentoraButtonMetrics.isInteractive` precedent) --
// the simpler reading, and arguably the more correct one given the spec's own "the field shows a
// disabled-looking state" framing (a disabled-LOOKING field that still opens on tap would be a confusing,
// half-disabled affordance). The `isLoading` branch inside `menuContent` (the "Loading options..." row)
// is consequently NOT reachable through this view's own wiring today -- kept anyway as a real, testable,
// disclosed branch (exercised directly by `MentoraSelectTests.swift`, not just visually) documenting the
// intended content for that state, matching this codebase's own "modeled but not (fully) wired" disclosed-
// gap convention (`MentoraButton.swift`/`MentoraTextField.swift`'s own Hover precedent) -- just for a
// different, real reason here (a deliberate simplicity choice, not a missing platform API).
//
// ---------------------------------------------------------------------------------------------------
// `isOpen` TRACKING -- A GENUINE, DISCLOSED LIMITATION OF SWIFTUI'S `Menu`. Unlike `.sheet(isPresented:)`/
// `.popover(isPresented:)`/`.confirmationDialog(isPresented:)`, plain `Menu` exposes NO `isPresented`/
// `isExpanded` binding or open/close callback anywhere in its real, documented initializers (confirmed
// against this file author's knowledge of the real SwiftUI API surface -- not independently re-verified
// against a live SDK header on this Windows host, since no Swift toolchain exists here; flagged in this
// task's final report). This file tracks `@State private var isOpen` via `.simultaneousGesture(TapGesture
// ...)` on the field's own label content -- `simultaneousGesture` (unlike a plain `.onTapGesture`, which
// would REPLACE and break `Menu`'s own built-in tap-to-open handling) fires ALONGSIDE `Menu`'s native
// gesture without interfering with it, a well-established real SwiftUI technique for observing a tap on
// a control without hijacking its own gesture. `isOpen` resets to `false` the moment any option is
// selected. KNOWN, DISCLOSED GAP: there is no way to observe a backdrop/outside-tap dismissal (closing
// the menu WITHOUT picking an option) via this technique, so `isOpen` (and therefore the trailing
// `expand_less` icon) can go stale after such a dismissal until the field is tapped again. This is the
// same class of disclosed, real-platform-limitation gap this codebase already accepts elsewhere (e.g.
// `MentoraTextField.swift`'s own "RESIDUAL, DISCLOSED GAP" for a wrapped floated label) -- not fixable
// without either a custom popover (explicitly out of scope per this file's own "SYSTEM-OWNED POPOVER
// CHROME" section) or a private/undocumented API.

// MARK: - Pure option model

/// One selectable option -- `value` is the caller's real domain value (equality-compared against the
/// current `selection`), `label` an already-resolved, caller-supplied display string (never resolved
/// from `MentoraStrings` internally, matching every other atom in this kit). `isEnabled: false` renders
/// the row in `color.text.disabled`, excluded from selection (`COMPONENTS.md` line 241).
struct MentoraSelectOption<Value: Hashable>: Identifiable {
    let value: Value
    let label: String
    var isEnabled: Bool = true

    var id: Value { value }
}

// MARK: - Pure metrics/state resolver (no SwiftUI rendering required to test)

/// Select-specific pure logic that does NOT already live in `MentoraTextFieldRules` -- see this file's
/// header "COLOR RESOLVER REUSE" for why border/background/label colors are resolved by calling
/// `MentoraTextFieldRules.colorSet` directly instead of a second copy living here.
enum MentoraSelectRules {

    /// `COMPONENTS.md`'s Open row is color-identical to Focused (`color.border.focus`, 2px) -- named,
    /// testable OR rather than an inline `||` at the call site. See this file's header "COLOR RESOLVER
    /// REUSE".
    static func effectiveIsFocused(isFocused: Bool, isOpen: Bool) -> Bool {
        isFocused || isOpen
    }

    /// Mirrors `MentoraButtonMetrics.isInteractive`/Android's own `fieldEnabled = enabled && !loading`
    /// split exactly -- loading always blocks interaction regardless of `isEnabled`.
    static func fieldEnabled(isEnabled: Bool, isLoading: Bool) -> Bool {
        isEnabled && !isLoading
    }

    /// `COMPONENTS.md`: "Selected-value / placeholder typography ... `color.text.primary` (has a value) /
    /// `color.text.disabled` (placeholder, no selection yet)". Disabled/loading always renders
    /// `color.text.disabled` regardless of whether a value is selected -- mirrors Android's own disclosed
    /// F2-class fix (`MentoraSelect.kt`'s `displayColor` comment) rather than leaving a disabled Select's
    /// value text at full strength while its border/background correctly dim.
    static func valueTextColor(hasSelection: Bool, isFieldEnabled: Bool) -> Color {
        guard isFieldEnabled else { return .mentoraTextDisabled }
        return hasSelection ? .mentoraTextPrimary : .mentoraTextDisabled
    }

    /// `COMPONENTS.md`: "Trailing indicator ... `expand_more` closed / `expand_less` open (both
    /// non-directional, never mirror)". Named/tested separately from the loading-spinner substitution,
    /// which is a pure view-composition concern (`MentoraSelect.fieldBox`), not a color-table state.
    static func trailingIconName(isOpen: Bool) -> MentoraIconName {
        isOpen ? .expandLess : .expandMore
    }
}

// MARK: - The view

/// `design-system/COMPONENTS.md § Select / Dropdown`. Every user-facing string is an already-resolved
/// `String` -- never resolves `MentoraStrings` itself. `loadingOptionsLabel` is REQUIRED (no default),
/// matching `MentoraIconButton`'s own `accessibilityLabel` precedent -- Android's own `MentoraSelect.kt`
/// disclosed a real, later-fixed bug (a raw-English default for this exact string) that a required
/// parameter with no default structurally cannot repeat here.
struct MentoraSelect<Value: Hashable>: View {
    let label: String
    let options: [MentoraSelectOption<Value>]
    @Binding var selection: Value?
    let loadingOptionsLabel: String
    var placeholder: String? = nil
    var helperText: String? = nil
    var errorText: String? = nil
    var leadingIcon: MentoraIconName? = nil
    var isLoading: Bool = false

    @Environment(\.isEnabled) private var isEnabled
    @FocusState private var isFocused: Bool
    @State private var isOpen: Bool = false

    /// Explicit, unconditionally-`internal` init -- same reason as `MentoraButton`/`MentoraTextField`: a
    /// `@FocusState`/`@State private var` stored property would otherwise make the implicit memberwise
    /// initializer `private`.
    init(
        label: String,
        options: [MentoraSelectOption<Value>],
        selection: Binding<Value?>,
        loadingOptionsLabel: String,
        placeholder: String? = nil,
        helperText: String? = nil,
        errorText: String? = nil,
        leadingIcon: MentoraIconName? = nil,
        isLoading: Bool = false
    ) {
        self.label = label
        self.options = options
        self._selection = selection
        self.loadingOptionsLabel = loadingOptionsLabel
        self.placeholder = placeholder
        self.helperText = helperText
        self.errorText = errorText
        self.leadingIcon = leadingIcon
        self.isLoading = isLoading
    }

    private var hasError: Bool { errorText != nil }
    private var fieldEnabled: Bool { MentoraSelectRules.fieldEnabled(isEnabled: isEnabled, isLoading: isLoading) }
    private var selectedOption: MentoraSelectOption<Value>? {
        options.first { $0.value == selection }
    }
    private var colors: MentoraTextFieldColorSet {
        MentoraTextFieldRules.colorSet(
            isFocused: MentoraSelectRules.effectiveIsFocused(isFocused: isFocused, isOpen: isOpen),
            isEmpty: selectedOption == nil,
            isEnabled: fieldEnabled,
            hasError: hasError,
            hasSuccess: false
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraTextFieldMetrics.supportingRowSpacing) {
            Text(label)
                .mentoraFont(.labelMedium)
                // `colors.labelColor`, not a hardcoded `.mentoraTextSecondary` -- `MentoraTextFieldRules
                // .colorSet`'s disabled branch resolves this to `.mentoraTextDisabled` (`COMPONENTS.md`
                // line 213: Disabled text is `color.text.disabled`), matching `MentoraTextField`'s own
                // label dimming when disabled. Using the hardcoded color here would leave this label at
                // full strength while the border/background/value text all correctly dim.
                .foregroundStyle(colors.labelColor)
                // See this file's header "FIELD LABEL" -- the real accessible name lives on `fieldBox`'s
                // `Menu` via `.accessibilityLabel(label)` below.
                .accessibilityHidden(true)
            fieldBox
            supportingText
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var fieldBox: some View {
        Menu {
            menuContent
        } label: {
            HStack(spacing: MentoraTextFieldMetrics.iconGap) {
                if let leadingIcon {
                    MentoraIcon(name: leadingIcon, size: MentoraTextFieldMetrics.iconSize)
                        .foregroundStyle(Color.mentoraTextSecondary)
                        .accessibilityHidden(true)
                }

                Text(selectedOption?.label ?? placeholder ?? "")
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(MentoraSelectRules.valueTextColor(hasSelection: selectedOption != nil, isFieldEnabled: fieldEnabled))
                    .lineLimit(1) // `CONTENT_RESILIENCE.md § 1`: Select value/placeholder -- clamp + ellipsis, 1 line.

                Spacer(minLength: 0)

                if isLoading {
                    // Sized to match the `icon.medium` (20) trailing indicator it replaces, per
                    // `COMPONENTS.md`'s own "small inline spinner replacing the trailing indicator"
                    // framing -- an unsized `ProgressView()` has no relation to that token otherwise.
                    ProgressView()
                        .progressViewStyle(.circular)
                        .frame(width: MentoraTextFieldMetrics.iconSize, height: MentoraTextFieldMetrics.iconSize)
                        .tint(Color.mentoraTextSecondary)
                        .accessibilityHidden(true)
                } else {
                    MentoraIcon(name: MentoraSelectRules.trailingIconName(isOpen: isOpen), size: MentoraTextFieldMetrics.iconSize)
                        .foregroundStyle(Color.mentoraTextSecondary)
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
            // See this file's header "`isOpen` TRACKING" -- fires ALONGSIDE Menu's own native
            // tap-to-open gesture, never replacing it.
            .simultaneousGesture(TapGesture().onEnded { isOpen = true })
        }
        .buttonStyle(.plain) // Strips Menu's default tinted/inset label chrome -- standard technique.
        .disabled(!fieldEnabled)
        .focused($isFocused)
        .accessibilityLabel(label)
        // `.accessibilityLabel` REPLACES the accessible name composed from this Menu's own children --
        // the selected value's `Text` (`fieldBox`) is inside that replaced subtree, so without an
        // explicit `.accessibilityValue`, VoiceOver announces only "<label>, Button" with no indication
        // of whether anything is selected or what (`COMPONENTS.md` line 237's "the accessible name" for
        // both the selected value and any option; `ACCESSIBILITY.md` § 14's Select row; criterion I2).
        // Unlike `MentoraTextField`, where the real `TextField` supplies its own accessibility value
        // under an identical-looking `.accessibilityLabel` override, `Menu` supplies none on its own.
        .accessibilityValue(selectedOption?.label ?? placeholder ?? "")
        .accessibilityHint(errorText ?? helperText ?? "")
    }

    @ViewBuilder
    private var menuContent: some View {
        if isLoading {
            // See this file's header "LOADING STATE" -- not reachable through this view's own
            // `.disabled(!fieldEnabled)` wiring today, kept as a real, disclosed, directly-tested branch.
            Text(loadingOptionsLabel)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextSecondary)
        } else {
            ForEach(options) { option in
                Button {
                    selection = option.value
                    isOpen = false
                } label: {
                    HStack {
                        Text(option.label)
                            .mentoraFont(.bodyMedium) // `COMPONENTS.md`: "Option typography `typography.body.medium`".
                            .lineLimit(1) // `CONTENT_RESILIENCE.md § 1`: Select option row text -- clamp + ellipsis, 1 line.
                        if option.value == selection {
                            Spacer()
                            MentoraIcon(name: .checkCircle, size: MentoraTextFieldMetrics.iconSize)
                                .accessibilityHidden(true)
                        }
                    }
                    .foregroundStyle(option.isEnabled ? Color.mentoraTextPrimary : Color.mentoraTextDisabled)
                }
                .disabled(!option.isEnabled)
                // The `.checkCircle` glyph above is hidden from accessibility as purely decorative, but
                // it is this row's ONLY visual selection signal (the spec's `color.brand.primaryContainer`
                // row background is the disclosed system-owned-popover deviation this file's header
                // names) -- without this trait, a VoiceOver user has no way to tell which option is
                // currently selected at all.
                .accessibilityAddTraits(option.value == selection ? [.isSelected] : [])
            }
        }
    }

    @ViewBuilder
    private var supportingText: some View {
        // See this file's header "SUPPORTING TEXT" -- duplicated from `MentoraTextField.swift`'s own
        // `supportingText`, reusing that file's real metrics constants rather than re-deriving them.
        if let errorText {
            HStack(alignment: .top, spacing: MentoraTextFieldMetrics.supportingTextIconGap) {
                MentoraIcon(name: .cancel, size: MentoraTextFieldMetrics.errorIconSize)
                    .foregroundStyle(Color.mentoraErrorDefault)
                    .accessibilityHidden(true)
                Text(errorText)
                    .mentoraFont(.caption)
                    .foregroundStyle(Color.mentoraErrorDefault)
                    .multilineTextAlignment(.leading)
            }
        } else if let helperText {
            Text(helperText)
                .mentoraFont(.caption)
                .foregroundStyle(Color.mentoraTextSecondary)
                .multilineTextAlignment(.leading)
        }
    }
}

#if DEBUG

private enum MentoraSelectPreviewFruit: String, CaseIterable {
    case apple, banana, cherry, durian
}

private struct MentoraSelectPreviewSwatches: View {
    @State private var emptySelection: MentoraSelectPreviewFruit?
    @State private var filledSelection: MentoraSelectPreviewFruit? = .banana
    @State private var errorSelection: MentoraSelectPreviewFruit?
    @State private var disabledSelection: MentoraSelectPreviewFruit? = .apple

    private var options: [MentoraSelectOption<MentoraSelectPreviewFruit>] {
        MentoraSelectPreviewFruit.allCases.map { fruit in
            MentoraSelectOption(value: fruit, label: fruit.rawValue.capitalized, isEnabled: fruit != .durian)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            MentoraSelect(
                label: "Favorite fruit", options: options, selection: $emptySelection,
                loadingOptionsLabel: "Loading options...", placeholder: "Choose a fruit"
            )
            MentoraSelect(
                label: "Favorite fruit", options: options, selection: $filledSelection,
                loadingOptionsLabel: "Loading options..."
            )
            MentoraSelect(
                label: "Favorite fruit", options: options, selection: $errorSelection,
                loadingOptionsLabel: "Loading options...", placeholder: "Choose a fruit",
                errorText: "Selection required"
            )
            MentoraSelect(
                label: "Favorite fruit", options: options, selection: $disabledSelection,
                loadingOptionsLabel: "Loading options..."
            )
            .disabled(true)
            MentoraSelect(
                label: "Favorite fruit", options: options, selection: .constant(nil),
                loadingOptionsLabel: "Loading options...", placeholder: "Choose a fruit", isLoading: true
            )
        }
    }
}

#Preview("MentoraSelect -- Light, default/selected/error/disabled/loading") {
    MentoraPreviewHost(title: "MentoraSelect -- Light / en", theme: .light, locale: .english) {
        MentoraSelectPreviewSwatches()
    }
}

#Preview("MentoraSelect -- Dark, default/selected/error/disabled/loading") {
    MentoraPreviewHost(title: "MentoraSelect -- Dark / en", theme: .dark, locale: .english) {
        MentoraSelectPreviewSwatches()
    }
}

#endif
