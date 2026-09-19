import SwiftUI
import shared

// Phase 5 Task T9 slice 3 -- the guest auth-gate's sheet/dismiss/replay wiring (B8), applied once at
// `RootView`'s root, above `TabShell`/`SplashView`.
//
// D132 review fix -- REPLAY TIMING CORRECTED: the original design deferred the pending-route replay to
// `.sheet`'s `onDismiss:` closure, reasoning that login success only ever flips `isPresentingLogin` first
// (starting the dismiss animation) and replays once that animation completes. That reasoning missed that
// `isAuthenticated` becoming `true` is ITSELF an async event (`SessionController`'s own `authStates()`
// subscription, crossing a Kotlin-coroutine -> Swift-`AsyncSequence` boundary) with no guaranteed ordering
// against a real login screen's own `dismiss()` call -- which is the idiomatic thing for T10's `LoginView`
// to do on success. If `dismiss()` fires before `isAuthenticated` is observed true, the old design would
// silently drop the pending intent (B8 broken). `TabRouter.authenticationObserved()` now performs the
// replay SYNCHRONOUSLY the instant auth is observed, decoupled from whenever/however the sheet's own
// dismissal happens -- see that method's own doc comment (`Navigation/TabRouter.swift`) for the full
// reasoning. `loginSheetDismissed()` below is now the USER-CANCELLED path only.
struct MentoraAuthGate: ViewModifier {
    let environment: AppEnvironment

    /// `set:` only ever fires for a USER-DRIVEN dismissal (swipe-to-dismiss, or the sheet's own
    /// Cancel/close affordance calling `@Environment(\.dismiss)`) -- SwiftUI never calls a `.sheet`
    /// binding's setter for an app-driven state change (i.e. `authenticationObserved()` flipping
    /// `isPresentingLogin` directly). Routing this setter through the SAME `loginSheetDismissed()` the
    /// `onDismiss:` closure below also calls is deliberately safe/idempotent, not a double-fire bug: by
    /// the time either one runs after a SUCCESSFUL login, `authenticationObserved()` has already cleared
    /// `pendingIntent` and pushed the real destination, so `loginSheetDismissed()` finds nothing left to
    /// clear and pushes nothing itself -- this is exactly `AuthGateLogicTests.swift`'s "double-dismiss
    /// defense" case.
    ///
    /// D132 review fix: wraps both closures in `MainActor.assumeIsolated`, matching
    /// `TabRouter.path(for:)`/`TabShell.selectionBinding`'s identical treatment -- see
    /// `TabRouter.swift`'s doc comment on `path(for:)` for why this is applied consistently everywhere a
    /// hand-rolled `Binding(get:set:)` in this codebase touches `@MainActor`-isolated state.
    private var isPresentingLoginBinding: Binding<Bool> {
        Binding(
            get: { MainActor.assumeIsolated { environment.router.isPresentingLogin } },
            set: { newValue in
                MainActor.assumeIsolated {
                    if !newValue {
                        environment.router.loginSheetDismissed()
                    }
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .sheet(
                isPresented: isPresentingLoginBinding,
                onDismiss: {
                    environment.router.loginSheetDismissed()
                }
            ) {
                LoginSheetPlaceholderView(environment: environment)
            }
            .onChange(of: environment.sessionController.isAuthenticated) { _, isAuthenticated in
                if isAuthenticated {
                    environment.router.authenticationObserved()
                } else {
                    environment.router.resetAllForLogout()
                }
            }
    }
}

extension View {
    func mentoraAuthGate(environment: AppEnvironment) -> some View {
        modifier(MentoraAuthGate(environment: environment))
    }
}

// TEMPORARY (T9) -- T10 swaps in the real LoginView here. Does NOT authenticate anything; exists only to
// prove the sheet/dismiss/replay mechanics above actually work. `auth_login_title` and the sheet-close
// content-description key are both real, already-ported catalog keys (confirmed by grepping
// `Resources/Localizable.xcstrings` directly) -- no new key is introduced.
struct LoginSheetPlaceholderView: View {
    let environment: AppEnvironment

    @Environment(\.dismiss) private var dismiss

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        VStack(spacing: MentoraSpacing.space4) {
            HStack {
                Spacer()
                MentoraIconButton(
                    icon: .close,
                    accessibilityLabel: MentoraStrings.text(
                        "course_player_curriculum_sheet_close_content_description",
                        locale: locale
                    )
                ) {
                    dismiss()
                }
            }
            Text(MentoraStrings.text("auth_login_title", locale: locale))
                .mentoraFont(.h3)
            Spacer()
        }
        .padding(MentoraSpacing.space4)
    }
}
