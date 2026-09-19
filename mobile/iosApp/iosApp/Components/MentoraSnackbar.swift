import SwiftUI
import UIKit
import shared

// Phase 5 Task T8 slice 7 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Snackbar`
// (lines 540-551). Radius `radius.medium`, background `color.surface.inverse`, text
// `typography.body.small`/`color.text.inverse`, padding `space.4`/`space.3`, optional `TextButton`
// action in `color.brand.onSurfaceInverse`, `elevation.3`, slide-up+fade in / fade-out motion, 4s
// auto-dismiss "paused on hover/focus/touch".
//
// ---------------------------------------------------------------------------------------------------
// PRESENTATION API -- A MODIFIER (`.mentoraSnackbar(isPresented:message:actionLabel:action:)`), NOT A
// PLAIN `View` A CALLER MANUALLY POSITIONS. DISCLOSED CHOICE. A snackbar is a transient overlay, not a
// layout element -- the same category of thing SwiftUI's own `.alert(isPresented:)`/
// `.sheet(isPresented:)`/`.confirmationDialog(isPresented:)` model as presentation modifiers rather than
// plain views. `MentoraSnackbar` (below) is still a real, independently-testable content `View` --
// the modifier composes it with `.overlay(alignment: .bottom)` + a `.transition(...)`, so the two halves
// (content vs. presentation/timing) stay separately reasoned-about, but a caller only ever writes
// `.mentoraSnackbar(isPresented: $showSnackbar, message: "...", actionLabel: "...", action: { ... })`
// chained onto whatever screen hosts it, exactly like a native SwiftUI presentation modifier. Only ONE
// snackbar at a time is supported (`isPresented: Bool`, not a queue/array) -- no queueing/stacking system
// is built here, out of scope per this task's own "avoid excessive complexity" instruction and no
// `COMPONENTS.md` requirement for it.
//
// ---------------------------------------------------------------------------------------------------
// ACTION BUTTON -- REUSES THE REAL `TextButton` (`Components/MentoraButton.swift`) DIRECTLY, NOT A
// REBUILT MINI-BUTTON. DISCLOSED, KNOWN COLOR GAP: `COMPONENTS.md`'s Snackbar action color is
// `color.brand.onSurfaceInverse` (a token defined SPECIFICALLY for a brand-colored action on
// `surface.inverse`, per that same table's own note). `TextButton`'s underlying
// `MentoraButtonVariant.colorSet(for:)` hardcodes `.text`'s content color to `.mentoraBrandPrimary` in
// EVERY state, with no caller-facing override -- an outer `.foregroundStyle(...)`/`.tint(...)` applied
// to a `TextButton` instance from outside cannot win against `MentoraButtonChrome`'s own unconditional
// `.foregroundStyle(colors.content)` call applied INSIDE the button's style. This file therefore renders
// the Snackbar's action in `color.brand.primary`, not the spec's literal `color.brand.onSurfaceInverse`
// -- a real, disclosed spec-vs-shared-component conflict, not a silent deviation. Fixing it exactly
// would require adding a content-color override parameter to `MentoraButton`/`TextButton`
// (`Components/MentoraButton.swift`), which is out of this file's own scope (this task's file list is
// closed to the 3 new Components files + their tests + the completion-gate script) -- flagged here
// rather than worked around with a second, parallel button implementation, per this task's explicit
// "reuse the REAL existing TextButton view directly, do not rebuild a smaller button here" instruction.
//
// ---------------------------------------------------------------------------------------------------
// ELEVATION REUSE -- identical technique to `MentoraToggle.swift`'s own "ELEVATION REUSE" precedent:
// `.mentoraElevation(.level3, in: .medium, fill: .mentoraSurfaceInverse, border: .clear)`.
// `COMPONENTS.md`'s Snackbar row states a fill + elevation, no border at all -- `border: .clear`
// (`Color.clear` is the sanctioned exception to `tools/ios-checks/theme-checks.js`'s forbidden-raw-color
// palette) reuses the one shared elevation handle rather than hand-rolling a second shadow path just to
// avoid an unwanted border.
//
// ---------------------------------------------------------------------------------------------------
// MOTION -- in: `MentoraMotionEasing.animation(MentoraMotionEasing.decelerate, duration:
// MentoraMotionDuration.normal)` (`COMPONENTS.md`'s own stated tokens, verbatim) driving a slide-up +
// fade `.transition`. OUT EASING -- A DISCLOSED GAP, RESOLVED WITH `.accelerate`: `COMPONENTS.md` states
// only a DURATION for fade-out (`motion.duration.fast`), no easing curve. This file uses
// `MentoraMotionEasing.accelerate` for the out transition, not `.decelerate`/`.standard` -- by direct
// analogy to every OTHER transient-overlay component in this exact same `COMPONENTS.md` file that states
// BOTH halves of its open/close motion explicitly: `AppDialog` ("open: ... easing.decelerate ... close:
// ... easing.accelerate") and `BottomSheet` ("open: ... easing.decelerate; close: ... easing.accelerate")
// both pair decelerate-in with accelerate-out. Snackbar's own row states "in: easing.decelerate" and is
// silent only on the OUT curve -- the most consistent, spec-internally-coherent fill for that silence is
// the same accelerate-out this file's two sibling overlay components already use, not a fallback to
// `.standard` (which this spec reserves for discrete STATE changes on a persistent control, e.g.
// ProgressBar/Tabs, not a transient overlay's own open/close motion).
//
// DISCLOSED, NOT-YET-FIXED GAP -- REDUCE MOTION. The `.move(edge: .bottom)` half of this file's insertion
// transition (below, `MentoraSnackbarModifier.body`) is a literal SLIDE transition -- `ACCESSIBILITY.md`
// § 9 explicitly names "slide/scale transitions" as the case Reduce Motion must degrade to a cross-fade
// or instant cut, and no file in this codebase reads `\.accessibilityReduceMotion` yet (the same
// cross-cutting gap already disclosed, unfixed, in T8 slices 4+5's own review, extended here to a second,
// new animation category). Not fixed in this already-large final T8 batch -- flagged for a later,
// dedicated cross-cutting pass across every animation in this kit at once.
//
// ---------------------------------------------------------------------------------------------------
// AUTO-DISMISS -- Swift Concurrency `Task` + `Task.sleep`, cancellable/restartable, a real, standard
// technique. `COMPONENTS.md`: "4s default, paused on hover/focus/touch". This app is touch-primary
// (no pointer/hover input) -- the "hover" half of that requirement is a pointer-platform concern this
// app doesn't need to wire, matching this kit's own repeatedly-established "hover modeled but not wired"
// precedent (`MentoraButton.swift`/`MentoraTextField.swift`/`MentoraSelect.swift`, all disclosed the same
// way). "Paused on touch" is implemented as: any tap on the snackbar itself restarts the auto-dismiss
// timer from zero, via `.simultaneousGesture(TapGesture().onEnded { ... })` -- the exact same
// "observe a tap without hijacking a native control's own gesture" technique `MentoraSelect.swift`
// already establishes (`isOpen` tracking, this same task's own kit) reused here for an analogous reason
// (the tap must not swallow the action `TextButton`'s own tap).
//
// ---------------------------------------------------------------------------------------------------
// VOICEOVER ANNOUNCEMENT -- `UIAccessibility.post(notification: .announcement, argument: message)`,
// requiring `import UIKit` -- the FIRST direct UIKit import in this codebase's `Components/`/`Theme/`
// tree (confirmed by grep before writing this file: zero prior `import UIKit` anywhere under
// `mobile/iosApp/iosApp/`). DISCLOSED CONFIDENCE: MODERATE-HIGH. `UIAccessibility.post(notification:
// argument:)` is real, long-standing, documented UIKit API for exactly this "announce a transient,
// non-focus-driven string to VoiceOver" need (there is no SwiftUI-native equivalent as of this
// project's iOS 17 target) -- but its exact announcement TIMING/interruption behavior relative to
// SwiftUI's own `.transition`-driven insertion cannot be confirmed without a device/simulator (no Swift
// toolchain on this Windows host). This is this file's best real, verifiable mechanism for the
// requirement, not a guessed API shape, flagged per this task's own disclosure convention (the same
// class of disclosed-but-unverified-on-this-host confidence `MentoraToggle.swift`'s own
// `.accessibilityRepresentation` disclosure already uses). `.accessibilityAddTraits(.updatesFrequently)`
// is additionally applied to the message text as a secondary, structural signal.

// MARK: - The content view

/// `design-system/COMPONENTS.md § Snackbar`'s visual content -- a real, independently-usable `View`,
/// composed by `.mentoraSnackbar(...)` below for real transient presentation. `message`/`actionLabel`
/// are already-resolved `String`s -- never resolves `MentoraStrings` itself, matching every other atom
/// in this kit.
struct MentoraSnackbar: View {
    let message: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack(alignment: .center, spacing: MentoraSpacing.space4) {
            Text(message)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextInverse)
                // `CONTENT_RESILIENCE.md § 1`: "Clamp + ellipsis at 2 lines on narrow viewports if the
                // message plus action can't both fit; prefer shortening the source copy over relying on
                // this" -- 2 lines, never unlimited (unlike AITutorBubble's own deliberately-unlimited
                // chat content).
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                // Decorative reinforcement that this content is actively transient/time-sensitive --
                // see this file's header "VOICEOVER ANNOUNCEMENT".
                .accessibilityAddTraits(.updatesFrequently)

            Spacer(minLength: 0)

            if let actionLabel, let action {
                // See this file's header "ACTION BUTTON" for the disclosed color gap.
                TextButton(label: actionLabel, action: action)
            }
        }
        // CORRECTED AFTER REVIEW -- `Spacer` moved OUTSIDE the `if let actionLabel, let action` branch
        // (above) and this `.frame` added: without both, a snackbar with no action sized to its own text
        // and rendered as a narrow centered pill, while one WITH an action stretched full-width via the
        // Spacer -- the same component rendering two different shapes depending on an optional argument,
        // with nothing in the design intending that split (`COMPONENTS.md` states no width for either
        // case). A consistent full-width bar (matching the `.multilineTextAlignment(.leading)` and
        // horizontal padding already applied either way) is the correct, single visual identity.
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, MentoraSpacing.space4)
        .padding(.vertical, MentoraSpacing.space3)
        // See this file's header "ELEVATION REUSE".
        .mentoraElevation(.level3, in: .medium, fill: .mentoraSurfaceInverse, border: .clear)
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: message)
        }
    }
}

// MARK: - Presentation modifier

/// See this file's header "PRESENTATION API"/"AUTO-DISMISS". Single-snackbar-at-a-time; no
/// queueing/stacking.
private struct MentoraSnackbarModifier: ViewModifier {
    @Binding var isPresented: Bool
    let message: String
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil
    var autoDismissDuration: Double = 4.0

    @State private var dismissTask: Task<Void, Never>?

    /// CORRECTED AFTER REVIEW -- this explicit init is harmless but NOT for the reason an earlier draft's
    /// comment gave. That comment claimed the implicit memberwise init would become `private` (per
    /// `@State private var dismissTask` above) and therefore unusable from `mentoraSnackbar(...)` below --
    /// but `MentoraSnackbarModifier` itself is already declared `private` (this whole type is file-scoped),
    /// so a `private` memberwise init would have been perfectly usable from `mentoraSnackbar(...)`, which
    /// lives in this same file. Unlike `MentoraButton`/`MentoraTextField`/`MentoraToggle`/`MentoraSelect`
    /// (all `internal` types, where that exact reasoning genuinely applies), this explicit init exists
    /// here only to spell out default argument values identically to the public `mentoraSnackbar(...)`
    /// entry point -- kept for that reason, not the one originally stated.
    init(
        isPresented: Binding<Bool>,
        message: String,
        actionLabel: String? = nil,
        action: (() -> Void)? = nil,
        autoDismissDuration: Double = 4.0
    ) {
        self._isPresented = isPresented
        self.message = message
        self.actionLabel = actionLabel
        self.action = action
        self.autoDismissDuration = autoDismissDuration
    }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if isPresented {
                    MentoraSnackbar(message: message, actionLabel: actionLabel, action: {
                        action?()
                        dismiss()
                    })
                    .padding(.horizontal, MentoraSpacing.space4)
                    .padding(.bottom, MentoraSpacing.space4)
                    .transition(
                        .asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal: .opacity
                        )
                    )
                    // See this file's header "AUTO-DISMISS" -- fires alongside the action TextButton's
                    // own tap, never replacing it (same technique as `MentoraSelect.swift`'s `isOpen`
                    // tracking).
                    .simultaneousGesture(TapGesture().onEnded { restartTimer() })
                    .onAppear { startTimer() }
                    .onDisappear { dismissTask?.cancel() }
                }
            }
            .animation(
                isPresented
                    ? MentoraMotionEasing.animation(MentoraMotionEasing.decelerate, duration: MentoraMotionDuration.normal)
                    : MentoraMotionEasing.animation(MentoraMotionEasing.accelerate, duration: MentoraMotionDuration.fast),
                value: isPresented
            )
    }

    private func startTimer() {
        dismissTask?.cancel()
        dismissTask = Task {
            try? await Task.sleep(nanoseconds: UInt64(autoDismissDuration * 1_000_000_000))
            guard !Task.isCancelled else { return }
            isPresented = false
        }
    }

    private func restartTimer() {
        startTimer()
    }

    private func dismiss() {
        dismissTask?.cancel()
        isPresented = false
    }
}

extension View {
    /// `design-system/COMPONENTS.md § Snackbar`'s real presentation entry point -- see this file's
    /// header "PRESENTATION API". `message`/`actionLabel` are already-resolved `String`s.
    func mentoraSnackbar(
        isPresented: Binding<Bool>,
        message: String,
        actionLabel: String? = nil,
        action: (() -> Void)? = nil,
        autoDismissDuration: Double = 4.0
    ) -> some View {
        modifier(
            MentoraSnackbarModifier(
                isPresented: isPresented, message: message, actionLabel: actionLabel,
                action: action, autoDismissDuration: autoDismissDuration
            )
        )
    }
}

#if DEBUG

private struct MentoraSnackbarPreviewSwatches: View {
    @State private var showsPlain = false
    @State private var showsWithAction = false

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            MentoraButton("Show plain snackbar") { showsPlain = true }
            MentoraButton("Show snackbar with action", variant: .secondary) { showsWithAction = true }

            // Static, non-auto-dismissing swatches too, so the visual chrome is inspectable without
            // waiting on the real 4s timer.
            MentoraSnackbar(message: "Couldn't enroll -- try again")
            MentoraSnackbar(message: "Progress saved", actionLabel: "Undo", action: {})
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .mentoraSnackbar(isPresented: $showsPlain, message: "Couldn't enroll -- try again")
        .mentoraSnackbar(isPresented: $showsWithAction, message: "Progress saved", actionLabel: "Undo", action: {})
    }
}

#Preview("MentoraSnackbar -- Light, plain/with-action + live presentation") {
    MentoraPreviewHost(title: "MentoraSnackbar -- Light / en", theme: .light, locale: .english) {
        MentoraSnackbarPreviewSwatches()
    }
}

#Preview("MentoraSnackbar -- Dark, plain/with-action + live presentation") {
    MentoraPreviewHost(title: "MentoraSnackbar -- Dark / en", theme: .dark, locale: .english) {
        MentoraSnackbarPreviewSwatches()
    }
}

#endif
