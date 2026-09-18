import Foundation
import shared

// Phase 5 Task T5 (slice 1 of 2: auth + user) — the single boundary layer between SwiftUI and KMP
// (System Design § 2). `SWIFT_VERSION: "5.0"` in `project.yml` means strict concurrency is OFF for
// this whole target — do not "fix" this file (or any other file under `Support/SharedBridge/`) by
// adding `@MainActor`/`Sendable` annotations; none of this code was designed against strict
// concurrency and none of it needs to be.

/// The single error type crossing out of `SharedBridge`. Carries `ApiResult.Failure` verbatim --
/// the bridge performs NO error translation beyond code passthrough (System Design § 2,
/// "What the bridge must NOT become").
struct MentoraError: Error {
    /// The contract. `shared.ApiErrorCode` is a Kotlin SEALED CLASS, not an enum class -- it bridges
    /// as an Obj-C class hierarchy and is switched via SKIE's `onEnum(of:)`, exactly like `ApiResult`
    /// and `AuthState`. `ApiErrorCode` does NOT match A5's `Kotlin[A-Z]`/`ApiResult` grep, so it is
    /// permitted in `Features/` -- but screens read copy through `ErrorCopy`, never by switching
    /// this themselves.
    let code: ApiErrorCode

    /// Diagnostic-only. NEVER rendered. `ApiResult`'s own kdoc makes this a contract (never assumed
    /// localized, stable across backend versions, or safe to show verbatim).
    let message: String

    /// Real Kotlin type is `Map<String, String>?`, exported as `NSDictionary<NSString*, NSString*>?`
    /// -- bridges to `[String: String]?` cleanly. Field-reason -> inline-slot routing is per-screen
    /// (later tasks); T5 carries the dictionary and stops there.
    let fields: [String: String]?

    /// Kotlin `Int`, exported as `int32_t`. `0` for locally-synthesized failures.
    let httpStatus: Int32

    /// Stable string form of `code` -- used for logging, equality and test assertions without
    /// depending on Obj-C class identity.
    var wire: String { code.wire }

    init(failure: ApiResultFailure) {
        self.init(code: failure.code, message: failure.message, fields: failure.fields, httpStatus: failure.httpStatus)
    }

    init(code: ApiErrorCode, message: String, fields: [String: String]? = nil, httpStatus: Int32 = 0) {
        self.code = code
        self.message = message
        self.fields = fields
        self.httpStatus = httpStatus
    }

    /// The one failure the bridge itself can originate: `ApiResultSuccess.data` arrived nil, or an
    /// erased-list element failed its cast. Modelled as an `Unknown` code so `ErrorCopy` needs no
    /// special case and the user sees the ordinary "something went wrong" copy.
    static func unexpectedNilData(_ context: String) -> MentoraError {
        MentoraError(code: ApiErrorCode.Unknown(raw: "IOS_BRIDGE_UNEXPECTED_NIL"),
                     message: "Bridge received a nil payload for \(context).")
    }
}

extension MentoraError: Equatable {
    static func == (l: MentoraError, r: MentoraError) -> Bool {
        l.wire == r.wire && l.fields == r.fields && l.httpStatus == r.httpStatus
    }
}

// Deliberately NOT `LocalizedError` -- copy comes from `ErrorCopy`, not a second unlocalized path.
//
// Deliberately does NOT catch/wrap Kotlin `CancellationException` anywhere in this file or anywhere
// else under `Support/SharedBridge/` -- see `MentoraClient.swift`'s header comment: cancellation
// propagates untouched through every `async throws` bridge method, and callers write
// `catch let e as MentoraError { ... } catch { /* cancelled */ }`.
