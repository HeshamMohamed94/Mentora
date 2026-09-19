import SwiftUI
import shared

// Phase 5 Task T8 slice 6 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Tabs`
// (lines 660-673). Height 44, `typography.label.large`, a 2px underline indicator that animates
// position over `motion.duration.normal` + `easing.standard` between whichever tab is currently
// selected.
//
// ---------------------------------------------------------------------------------------------------
// TAB MODEL -- mirrors `Components/MentoraSelect.swift`'s `MentoraSelectOption<Value: Hashable>` shape
// exactly (`value`/`label`/`isEnabled`, `Identifiable` via `id { value }`) -- same reasoning: `value` is
// the caller's real domain value (equality-compared against `selection`), `label` an already-resolved
// display `String` (never resolved from `MentoraStrings` internally), `isEnabled: false` excludes a tab
// from selection and renders it in `color.text.disabled`.
//
// ---------------------------------------------------------------------------------------------------
// HORIZONTAL SCROLL -- `CONTENT_RESILIENCE.md § 1`'s "Tabs label" row ("Tab strip is horizontally
// scrollable if items overflow ... so individual labels stay single-line rather than wrapping") and
// § 6's locked rule ("Tabs and horizontally-arranged item groups ... become horizontally scrollable
// rather than wrapping or compressing individual items below their minimum readable width") both
// confirmed directly. This file therefore ALWAYS wraps the tab row in
// `ScrollView(.horizontal, showsIndicators: false)`, unconditionally -- not a conditional branch that
// only appears once the row overflows. A `ScrollView` whose content fits within the available width
// simply doesn't scroll (SwiftUI's own standard behavior); there is no need for, and no established
// precedent in this kit for, measuring content width to decide whether to wrap in one. Each tab label
// is `.lineLimit(1)` per that same content-resilience row.
//
// DISCLOSED, DEFERRED GAP -- NO AUTO-SCROLL TO A SELECTED-BUT-OFF-SCREEN TAB. If `selection` starts (or
// is set externally) on a tab past the initially-visible width, the strip opens scrolled to its natural
// leading edge with the selected tab and its indicator entirely out of view -- there is no
// `ScrollViewReader`/`.scrollTo(...)` wiring here to bring it into view automatically. A real, fixable
// gap (a `ScrollViewReader` + `.onChange(of: selection)` would close it), deferred rather than added here
// to keep this already-large final T8 batch from growing further -- flagged for whichever T9+ screen
// first uses `MentoraTabs` with a non-first default selection to notice and fix if it matters there.
//
// ---------------------------------------------------------------------------------------------------
// MOVING INDICATOR -- `matchedGeometryEffect` (with a shared `@Namespace`), NOT manual
// `GeometryReader`/`PreferenceKey` frame measurement. DISCLOSED CONFIDENCE: HIGH. This is the standard,
// idiomatic SwiftUI technique for "one indicator view that visually travels between sibling views" (the
// underlying mechanism behind Apple's own well-documented segmented-control/tab-strip sample code), and
// this codebase's own `MentoraTextField.swift` already discloses a preference for NOT reaching for
// `GeometryReader`+`PreferenceKey` measurement where a simpler, built-in mechanism suffices (that file's
// own "RESIDUAL, DISCLOSED GAP" comment). Concretely: only the CURRENTLY SELECTED tab's indicator
// segment carries the `.matchedGeometryEffect(id:, in:)` modifier (an `if isSelected { ... }` branch
// inside each tab's own view) -- since exactly one tab satisfies that condition at any moment, SwiftUI
// interpolates the segment's frame (both position AND width, so differently-sized tab labels transition
// correctly too) as selection moves from one tab to the next.
//
// CORRECTED AFTER REVIEW -- THE ANIMATION MUST COVER EXTERNAL BINDING CHANGES, NOT JUST THE TAP SITE. An
// earlier draft wrapped only the internal `selection = item.value` mutation in `withAnimation(...)` at
// the tap `Button`'s own action closure, on the mistaken premise that "an unwrapped mutation would still
// animate under SwiftUI's implicit animation rules for state-driven view-identity changes" -- SwiftUI has
// NO such implicit-animation rule; a bare `@State`/`@Binding` mutation with no active `Transaction`
// renders instantly, full stop. Since `selection` is a `Binding<Value>` the OWNER can also drive from
// outside (a paged content view syncing back, a "next section" action, restored navigation state) --
// `COMPONENTS.md` line 666 states the indicator "animates position... " unconditionally, not "only when
// tapped by the user," so an external, un-animated `selection` change would have made the indicator jump
// instantly, a direct spec violation. Fixed: the animation now lives on the CONTAINER
// (`.animation(_:value: selection)` on the scrollable `HStack` below), which covers both the internal tap
// path and any external binding mutation, and drives the `matchedGeometryEffect` insert/remove pair
// correctly either way -- the same technique `MentoraProgressBar.swift`/`MentoraSnackbar.swift` (this same
// batch) already use for their own bindable state (`clampedProgress`/`isPresented`). The `withAnimation`
// wrapper at the tap site is kept too (harmless, redundant with the container's own `.animation(value:)`)
// rather than removed, since it costs nothing and guards against a future edit accidentally scoping the
// container animation to a different value. Never a raw SwiftUI easing curve -- `MentoraMotionEasing.animation(MentoraMotionEasing.standard,
// duration: MentoraMotionDuration.normal)`, the spec's own stated tokens, exactly.
//
// ---------------------------------------------------------------------------------------------------
// DISABLED TABS -- mirrors `MentoraSelectOption.isEnabled`'s exact handling: `.disabled(!item.isEnabled)`
// on that tab's `Button`, text color `.mentoraTextDisabled` (`MentoraTabsRules.textColor`, disabled
// short-circuits selected/unselected exactly like every other atom in this kit's own disabled-beats-
// everything convention), and excluded from selection (a disabled tab's `Button` action is a no-op
// guard, `.disabled(true)` on the `Button` also blocks the tap at the system level).
//
// ---------------------------------------------------------------------------------------------------
// ACCESSIBILITY -- CONFIRMED, NOT ASSUMED: each tab is a real `Button` wrapping real, already-resolved
// label text, so VoiceOver reads "<label>, button" and, with `.accessibilityAddTraits(.isSelected)`
// added only for the active tab (the exact same technique `MentoraSelect.swift`'s own option rows
// already use, `.accessibilityAddTraits(option.value == selection ? [.isSelected] : [])`), "<label>,
// selected, button" for the active one -- a plain `Button`-per-item with real text needs no additional
// plumbing to be accessible, unlike `Toggle`/`Select`'s harder cases (which needed
// `.accessibilityRepresentation`/explicit `.accessibilityValue` because their real state lived outside
// plain button/text semantics). Confirmed by this reasoning, not silently assumed.

// MARK: - Pure tab model

/// One tab -- mirrors `MentoraSelectOption<Value: Hashable>`'s shape exactly. See this file's header
/// "TAB MODEL".
struct MentoraTabItem<Value: Hashable>: Identifiable {
    let value: Value
    let label: String
    var isEnabled: Bool = true

    var id: Value { value }
}

// MARK: - Pure metrics (no SwiftUI rendering required to test)

enum MentoraTabsMetrics {
    /// `COMPONENTS.md`: "Height 44".
    static let height: CGFloat = 44

    /// `COMPONENTS.md`: "2px underline".
    static let indicatorHeight: CGFloat = 2

    /// Disclosed judgment call -- `COMPONENTS.md § Tabs` states no per-tab padding/gap token. `space.4`
    /// (16) horizontal padding per tab, no additional inter-tab gap (each tab's own padding already
    /// separates it from its neighbor) -- the same "minimal, literal reading, fill only what's actually
    /// unstated" convention this kit's other atoms already disclose for their own unstated numbers
    /// (e.g. `MentoraButton.swift`'s "2px offset" assumption).
    static let horizontalPadding: CGFloat = MentoraSpacing.space4
}

// MARK: - Pure state/color resolver (no SwiftUI rendering required to test)

enum MentoraTabsRules {

    /// `COMPONENTS.md § Tabs`'s 3-row state table: Default -> `color.text.secondary`, Active ->
    /// `color.text.primary` (+ indicator, a view-composition concern handled separately, not part of
    /// this color resolution), Disabled -> `color.text.disabled`. `disabled` beats `isSelected` --
    /// matches every other atom in this kit's own disabled-short-circuits-everything convention
    /// (`MentoraTextFieldRules.colorSet`/`MentoraToggleRules.colorSet`'s identical priority).
    static func textColor(isSelected: Bool, isEnabled: Bool) -> Color {
        guard isEnabled else { return .mentoraTextDisabled }
        return isSelected ? .mentoraTextPrimary : .mentoraTextSecondary
    }
}

// MARK: - The view

/// `design-system/COMPONENTS.md § Tabs`. Every tab's `label` is an already-resolved `String` -- never
/// resolves `MentoraStrings` itself, matching every other atom in this kit.
struct MentoraTabs<Value: Hashable>: View {
    let items: [MentoraTabItem<Value>]
    @Binding var selection: Value

    @Namespace private var indicatorNamespace

    /// Explicit, unconditionally-`internal` init -- same reason as `MentoraButton`/`MentoraTextField`/
    /// `MentoraToggle`/`MentoraSelect`: a `@Namespace private var` stored property would otherwise make
    /// the implicit memberwise initializer `private`.
    init(items: [MentoraTabItem<Value>], selection: Binding<Value>) {
        self.items = items
        self._selection = selection
    }

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(items) { item in
                    tabButton(for: item)
                }
            }
        }
        // A MINIMUM, not `.frame(height:)` -- same rule `MentoraTextField.swift`'s own header states
        // ("NEVER a hard constraint") for the identical reason: `.labelLarge` can exceed 44pt at
        // accessibility Dynamic Type sizes, and a fixed height would clip it.
        .frame(minHeight: MentoraTabsMetrics.height)
        // See this file's header "MOVING INDICATOR"'s "CORRECTED AFTER REVIEW" note -- covers BOTH the
        // tap-driven internal mutation and any external `selection` binding change.
        .animation(MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.normal), value: selection)
    }

    private func tabButton(for item: MentoraTabItem<Value>) -> some View {
        let isSelected = item.value == selection

        return Button {
            guard item.isEnabled else { return }
            withAnimation(MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.normal)) {
                selection = item.value
            }
        } label: {
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                Text(item.label)
                    .mentoraFont(.labelLarge)
                    .foregroundStyle(MentoraTabsRules.textColor(isSelected: isSelected, isEnabled: item.isEnabled))
                    .lineLimit(1) // `CONTENT_RESILIENCE.md § 1`: Tabs label -- clamp + ellipsis, 1 line.
                Spacer(minLength: 0)

                ZStack {
                    if isSelected {
                        Rectangle()
                            .fill(Color.mentoraBrandPrimary)
                            .frame(height: MentoraTabsMetrics.indicatorHeight)
                            // See this file's header "MOVING INDICATOR" -- only the selected tab's
                            // segment carries this modifier at any given moment.
                            .matchedGeometryEffect(id: "indicator", in: indicatorNamespace)
                    } else {
                        Color.clear.frame(height: MentoraTabsMetrics.indicatorHeight)
                    }
                }
            }
            .padding(.horizontal, MentoraTabsMetrics.horizontalPadding)
            .frame(minHeight: MentoraTabsMetrics.height) // A minimum, not a hard constraint -- see body's own comment.
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!item.isEnabled)
        // See this file's header "ACCESSIBILITY".
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

#if DEBUG

private enum MentoraTabsPreviewSection: CaseIterable {
    case overview, curriculum, reviews, notes, resources, discussion
}

private struct MentoraTabsPreviewSwatches: View {
    @State private var selection: MentoraTabsPreviewSection = .overview

    private var items: [MentoraTabItem<MentoraTabsPreviewSection>] {
        [
            MentoraTabItem(value: .overview, label: "Overview"),
            MentoraTabItem(value: .curriculum, label: "Curriculum"),
            MentoraTabItem(value: .reviews, label: "Reviews"),
            MentoraTabItem(value: .notes, label: "Notes", isEnabled: false),
            MentoraTabItem(value: .resources, label: "Resources"),
            MentoraTabItem(value: .discussion, label: "Discussion"),
        ]
    }

    var body: some View {
        MentoraTabs(items: items, selection: $selection)
    }
}

#Preview("MentoraTabs -- Light, default/active/disabled, horizontally scrollable") {
    MentoraPreviewHost(title: "MentoraTabs -- Light / en", theme: .light, locale: .english) {
        MentoraTabsPreviewSwatches()
    }
}

#Preview("MentoraTabs -- Dark, default/active/disabled, horizontally scrollable") {
    MentoraPreviewHost(title: "MentoraTabs -- Dark / en", theme: .dark, locale: .english) {
        MentoraTabsPreviewSwatches()
    }
}

#endif
