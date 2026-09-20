import SwiftUI
import UIKit
import shared

// Phase 5 Task T10 — Register's SwiftUI presentation. All state/network/validation logic lives in
// `RegisterModel.swift`; this file resolves strings, lays out `Components/*` atoms, and owns its model
// via `@State` (`PHASE_5_IOS_SYSTEM_DESIGN.md § 3.1` rules 2-3).

/// `ux/SCREEN_UX_SPECS.md § 7`'s minimal header + title + Name + Email + Password (helper text unless
/// an error is set — `PasswordField`'s own "error wins over helper" rule already implements this) +
/// Create Account button + a link back to Login, in that order. Presented inside `AuthFlowView`'s
/// sheet-local `NavigationStack` (`Navigation/AuthGate.swift`) — `onOpenLogin` pops back to `LoginView`.
///
/// DISCLOSED, DEFERRED GAP (T10 follow-up review): `ux/SCREEN_UX_SPECS.md § 6` (inherited by § 7 via
/// its own "identical pattern to Login" line) requires a submit failure to be "announced via a live
/// region AND focus moves to the first invalid field." The live-region half is implemented (the
/// `UIAccessibility.post` announcements below); the focus-move half (a `@FocusState` shifted to the
/// email or password field when `RegisterModel.route(_:)` sets that field's error) is NOT — deferred
/// rather than built here, since it needs its own `@FocusState` enum wired through both fields and
/// verified live (MC-3), not a quick addition to bundle silently into this task's own fix round.
struct RegisterView: View {
    let environment: AppEnvironment
    let onOpenLogin: () -> Void
    /// T10 follow-up review fix: passed in EXPLICITLY by `AuthFlowView`, never
    /// `@Environment(\.dismiss)` read directly here -- `RegisterView` is PUSHED onto the sheet-local
    /// `NavigationStack` (`Navigation/AuthGate.swift`'s `.navigationDestination(isPresented:)`), and
    /// `DismissAction` is context-sensitive: read from a pushed view it pops that view instead of
    /// dismissing the enclosing sheet. An earlier draft read `@Environment(\.dismiss)` directly here,
    /// which made this screen's own "Close" button silently behave as a second Back button (landing on
    /// Login, requiring a second activation) instead of actually closing -- a real, confirmed defect,
    /// fixed by threading the same closure `LoginView` uses down from `AuthFlowView`'s own level
    /// instead. See `LoginView.swift`'s identical property for the full rationale.
    let onClose: () -> Void

    @State private var model: RegisterModel

    init(environment: AppEnvironment, onOpenLogin: @escaping () -> Void, onClose: @escaping () -> Void) {
        self.environment = environment
        self.onOpenLogin = onOpenLogin
        self.onClose = onClose
        _model = State(wrappedValue: RegisterModel(client: environment.client))
    }

    private var locale: AppLocale { environment.localeController.currentLocale }

    /// T10 review fix: trims, matching `RegisterModel.submit()`'s own blank-field guard exactly.
    private var canSubmit: Bool {
        !model.isLoading
            && !model.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !model.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !model.password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MentoraSpacing.space6) {
                AuthWordmarkHeader(environment: environment)

                VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
                    Text(MentoraStrings.text("auth_register_title", locale: locale))
                        .mentoraFont(.h2)

                    if let generalErrorKey = model.generalErrorKey {
                        AuthGeneralErrorBanner(message: MentoraStrings.text(generalErrorKey, locale: locale))
                    }

                    MentoraTextField(
                        text: $model.name,
                        label: MentoraStrings.text("auth_name_label", locale: locale)
                    )
                    .textContentType(.name)
                    .disabled(model.isLoading)

                    MentoraTextField(
                        text: $model.email,
                        label: MentoraStrings.text("auth_email_label", locale: locale),
                        errorText: model.emailErrorKey.map { MentoraStrings.text($0, locale: locale) }
                    )
                    .keyboardType(.emailAddress)
                    .textContentType(.username)
                    .autocorrectionDisabled()
                    .disabled(model.isLoading)

                    PasswordField(
                        text: $model.password,
                        label: MentoraStrings.text("auth_password_label", locale: locale),
                        showPasswordLabel: MentoraStrings.text("auth_show_password", locale: locale),
                        hidePasswordLabel: MentoraStrings.text("auth_hide_password", locale: locale),
                        helperText: MentoraStrings.text("auth_password_hint", locale: locale),
                        errorText: model.passwordErrorKey.map { MentoraStrings.text($0, locale: locale) }
                    )
                    .disabled(model.isLoading)

                    MentoraButton(
                        MentoraStrings.text("auth_register_button", locale: locale),
                        isLoading: model.isLoading
                    ) {
                        Task { await model.submit() }
                    }
                    .disabled(!canSubmit)

                    TextButton(label: MentoraStrings.text("auth_register_secondary_link", locale: locale)) {
                        onOpenLogin()
                    }
                }
            }
            // T10 review fix: see `LoginView.swift`'s identical modifier for the full rationale
            // (`ux/SCREEN_UX_SPECS.md § 6-7` "Responsive" form-width cap, shared `AuthFormMaxWidth`).
            .frame(maxWidth: AuthFormMaxWidth)
            .frame(maxWidth: .infinity)
            .padding(MentoraSpacing.space6)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                MentoraIconButton(
                    icon: .close,
                    accessibilityLabel: MentoraStrings.text(
                        "course_player_curriculum_sheet_close_content_description",
                        locale: locale
                    ),
                    action: onClose
                )
            }
        }
        // T10 follow-up review fix: a field-level-only failure (e.g. server `fields["email"]=="INVALID"`)
        // sets NO `generalErrorKey` at all, so `AuthGeneralErrorBanner`'s own announcement never fires --
        // `MentoraTextField`'s `errorText` is exposed only via `.accessibilityHint`, read solely when
        // that specific field takes focus (`MentoraTextField.swift`), which leaves a VoiceOver user who
        // just tapped Submit with ZERO feedback that anything failed. Same
        // `UIAccessibility.post(notification: .announcement, argument:)` mechanism as
        // `AuthGeneralErrorBanner`/`MentoraSnackbar.swift`.
        //
        // Triggered off `model.isLoading`'s `true -> false` transition (a submit attempt just
        // completed) rather than off `emailErrorKey`/`passwordErrorKey` individually -- an earlier draft
        // used two separate `.onChange`s, one per field key. `RegisterUseCase.kt` (`shared`) can set
        // BOTH keys from a single failure (a bad email AND a weak password together), which fired both
        // handlers back to back; `UIAccessibility.post` calls issued in immediate succession do not
        // reliably queue, so only the second was ever actually spoken. Reading both keys together at
        // the ONE moment a submit attempt finishes, and composing a single combined announcement when
        // more than one fired, fixes that collision. This trigger also cannot fire on an ordinary field
        // edit, so it never spuriously re-announces a still-unrelated field's error just because a
        // sibling field changed (`RegisterModel`'s per-field `didSet` clear never touches `isLoading`).
        .onChange(of: model.isLoading) { wasLoading, isLoading in
            guard wasLoading, !isLoading else { return }
            let messages = [model.emailErrorKey, model.passwordErrorKey]
                .compactMap { $0 }
                .map { MentoraStrings.text($0, locale: locale) }
            guard !messages.isEmpty else { return }
            UIAccessibility.post(notification: .announcement, argument: messages.joined(separator: ". "))
        }
    }
}
