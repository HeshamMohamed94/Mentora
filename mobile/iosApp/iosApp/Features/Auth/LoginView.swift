import SwiftUI
import UIKit
import shared

// Phase 5 Task T10 — Login's SwiftUI presentation. All state/network logic lives in `LoginModel.swift`;
// this file resolves strings, lays out `Components/*` atoms, and owns its model via `@State`
// (`PHASE_5_IOS_SYSTEM_DESIGN.md § 3.1` rules 2-3 — the model is constructed here, where
// `AppEnvironment` is passed in explicitly, never a `@State` default that reaches a client itself).

/// `ux/SCREEN_UX_SPECS.md § 6`'s minimal header (wordmark only) + title + Email + Password + Login
/// button + a link to Register, in that order. Presented inside `AuthFlowView`'s sheet-local
/// `NavigationStack` (`Navigation/AuthGate.swift`) — `onOpenRegister` pushes `RegisterView` there.
struct LoginView: View {
    let environment: AppEnvironment
    let onOpenRegister: () -> Void
    /// T10 follow-up review fix: restores the explicit close affordance the former T9 placeholder had
    /// (the former `LoginSheetPlaceholderView`'s `X` button) -- `AuthFlowView`
    /// (`Navigation/AuthGate.swift`) dropped it when this real screen replaced that placeholder.
    /// Swipe-to-dismiss alone (still fully supported, `ux/NAVIGATION_SPEC.md § 3`'s "platform-native
    /// modal... dismiss") is the least discoverable path for a Switch Control/Voice Control user who
    /// cannot perform that gesture. Passed in EXPLICITLY by `AuthFlowView` -- never
    /// `@Environment(\.dismiss)` read directly here -- because `LoginView` sits at the sheet-local
    /// `NavigationStack`'s ROOT, where `dismiss()` does correctly dismiss the whole sheet, but
    /// `RegisterView` (pushed onto that same stack) would instead have `dismiss()` pop back to Login --
    /// a real, confirmed defect in an earlier draft. Reading it once at `AuthFlowView`'s own level
    /// (genuinely the sheet's content root) and threading it down as a plain closure sidesteps that
    /// context-sensitivity for both screens identically.
    let onClose: () -> Void

    @State private var model: LoginModel

    init(environment: AppEnvironment, onOpenRegister: @escaping () -> Void, onClose: @escaping () -> Void) {
        self.environment = environment
        self.onOpenRegister = onOpenRegister
        self.onClose = onClose
        _model = State(wrappedValue: LoginModel(client: environment.client))
    }

    private var locale: AppLocale { environment.localeController.currentLocale }

    /// T10 review fix: trims, matching `LoginModel.submit()`'s own blank-field guard exactly -- a
    /// whitespace-only field used to leave this `true` (button enabled) while `submit()` itself silently
    /// no-op'd, giving no feedback at all on tap.
    private var canSubmit: Bool {
        !model.isLoading
            && !model.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !model.password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: MentoraSpacing.space6) {
                AuthWordmarkHeader(environment: environment)

                VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
                    Text(MentoraStrings.text("auth_login_title", locale: locale))
                        .mentoraFont(.h2)

                    if let generalErrorKey = model.generalErrorKey {
                        AuthGeneralErrorBanner(message: MentoraStrings.text(generalErrorKey, locale: locale))
                    }

                    MentoraTextField(
                        text: $model.email,
                        label: MentoraStrings.text("auth_email_label", locale: locale)
                    )
                    .keyboardType(.emailAddress)
                    .textContentType(.username)
                    .autocorrectionDisabled()
                    .disabled(model.isLoading)

                    PasswordField(
                        text: $model.password,
                        label: MentoraStrings.text("auth_password_label", locale: locale),
                        showPasswordLabel: MentoraStrings.text("auth_show_password", locale: locale),
                        hidePasswordLabel: MentoraStrings.text("auth_hide_password", locale: locale)
                    )
                    .disabled(model.isLoading)

                    MentoraButton(
                        MentoraStrings.text("auth_login_button", locale: locale),
                        isLoading: model.isLoading
                    ) {
                        Task { await model.submit() }
                    }
                    .disabled(!canSubmit)

                    TextButton(label: MentoraStrings.text("auth_login_secondary_link", locale: locale)) {
                        onOpenRegister()
                    }
                }
            }
            // T10 review fix: `ux/SCREEN_UX_SPECS.md § 6` "Responsive" -- "centered form card at
            // form-width cap (`RESPONSIVE_BEHAVIOR.md § 4`) at every breakpoint" -- an uncapped `VStack`
            // stretched edge-to-edge on wider layouts (iPad, `TARGETED_DEVICE_FAMILY: "1,2"` in
            // `project.yml`). `AuthFormMaxWidth` mirrors Android's own identical
            // `LoginScreen.kt`/`RegisterScreen.kt` `widthIn(max = AuthFormMaxWidth)` cap (480pt); the
            // outer `.frame(maxWidth: .infinity)` centers that capped column within the full width.
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
    }
}

/// `ux/SCREEN_UX_SPECS.md § 6`'s "Responsive" form-width cap, shared by `LoginView`/`RegisterView`
/// (T10 review fix) -- matches Android's own `AuthFormMaxWidth` (`LoginScreen.kt`) exactly.
let AuthFormMaxWidth: CGFloat = 480

/// The minimal, logo/wordmark-only header both `LoginView`/`RegisterView` share (`ux/SCREEN_UX_SPECS.md
/// § 6-7`). Not `private` — reused by `RegisterView.swift` in this same `Features/Auth` group.
struct AuthWordmarkHeader: View {
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        Text(MentoraStrings.text("app_name", locale: locale))
            .mentoraFont(.h3)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A plain, clearly-visible inline banner for a view-blocking general auth failure (I4) — deliberately
/// not a dedicated `ErrorState`/banner component (that is `Components/*.swift`'s T11 job, not this
/// screen task's). Shared by `LoginView`/`RegisterView`. `message` is already-resolved text — never
/// resolves `MentoraStrings` itself, matching this kit's own atom convention.
///
/// T10 review fix — VOICEOVER ANNOUNCEMENT: `design-system/ACCESSIBILITY.md` line 71 requires
/// mobile form-level submit errors to "announce via accessibility notification," and
/// `ux/SCREEN_UX_SPECS.md § 6` requires the error be "announced via a live region" — a plain `Text`
/// appearing on screen does not reliably get spoken by VoiceOver on its own (it never takes focus).
/// Uses the identical, already-established `UIAccessibility.post(notification: .announcement,
/// argument:)` mechanism `Components/MentoraSnackbar.swift` already uses for this exact class of
/// problem (see that file's header for the full disclosed-confidence note — same MODERATE-HIGH
/// confidence applies here, unverified on this Windows host's lack of a Swift toolchain/simulator).
/// Both `.onAppear` (the banner's first appearance after a failed submit) AND `.onChange(of: message)`
/// (a SECOND failed submit with a DIFFERENT failure reason, e.g. invalid-credentials then
/// rate-limited, where the banner's own view identity persists and `.onAppear` alone would not
/// re-fire) are covered, unlike `MentoraSnackbar`'s own single-message-per-appearance case.
///
/// DISCLOSED, UNFIXED EDGE CASE (T10 follow-up review, not device-verifiable from this Windows host):
/// two IDENTICAL failures in immediate succession (e.g. `AuthInvalidCredentials` twice in a row) set
/// `generalErrorKey` to the same string both times; if SwiftUI ever coalesces the intervening `nil`
/// (`LoginModel.submit()`/`RegisterModel.submit()` both clear it before their `await`) into a single
/// render with the final, unchanged value, `.onChange(of: message)` would see no change and skip the
/// second announcement. Both models always suspend at a real `await` between clearing and re-setting,
/// which in practice gives SwiftUI a render pass at the intermediate `nil` state — but this has not
/// been confirmed on a device/simulator. A monotonic counter observed instead of `message` itself would
/// close this gap unconditionally; not built here, since it is a narrow repeat-identical-failure edge
/// case, not the common path.
struct AuthGeneralErrorBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: MentoraSpacing.space2) {
            MentoraIcon(name: .cancel, size: MentoraIconSize.medium)
                .foregroundStyle(Color.mentoraErrorOnErrorContainer)
                // Decorative — the banner's own text communicates the failure (I2 precedent, see
                // `MentoraTextField.swift`'s identical treatment of its error icon).
                .accessibilityHidden(true)
            Text(message)
                .mentoraFont(.bodyMedium)
                .foregroundStyle(Color.mentoraErrorOnErrorContainer)
                .multilineTextAlignment(.leading)
                // Decorative reinforcement that this content is a fresh, time-sensitive failure --
                // same secondary signal `MentoraSnackbar.swift` applies to its own message.
                .accessibilityAddTraits(.updatesFrequently)
            // Deliberately NOT `.accessibilityHidden` — a view-blocking failure must be announced.
        }
        .padding(MentoraSpacing.space4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.mentoraErrorContainer.clipShape(MentoraShape.medium))
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: message)
        }
        .onChange(of: message) { _, newMessage in
            UIAccessibility.post(notification: .announcement, argument: newMessage)
        }
    }
}
