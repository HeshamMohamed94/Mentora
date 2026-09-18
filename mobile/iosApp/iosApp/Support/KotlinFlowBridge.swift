import Foundation
import os
import shared

// Phase 5 Task T4b (D108) — minimal, generic Flow-bridging helper.
//
// System Design § 5/§ 7 assumed `observeAuthState`/`observeLocale`/`KeychainStatus.failures` would
// surface to Swift as SKIE `AsyncSequence` wrappers. The real CI-run-#6-captured interface shows
// otherwise: none of the three has a generated `Shared.Observe*UseCase.swift`/`Shared.KeychainStatus
// .swift` wrapper, and the raw `shared.h` header shows all three returning the plain, type-erased
// `Kotlinx_coroutines_core{State,Shared}Flow` Obj-C protocols instead. This is a disclosed,
// artifact-verified deviation — see `execution/DECISIONS_LOG.md` D108. T5's `Support/SharedBridge/`
// layer will build this out further; this file stays intentionally small and generic — just enough
// for the three call sites T4b itself needs (`SessionController`/`LocaleController`).
//
// CI run #7 (real compile, e11b4b4) proved a Swift-name detail no static header could show: the
// Swift-visible protocol requirement is `__emit`, not `emit` — SKIE's own generated name, not the
// raw Obj-C selector `emitValue:completionHandler:` transliterated the obvious way. Real compiler
// diagnostic quoted here rather than re-guessed:
//   shared.Kotlinx_coroutines_coreFlowCollector.__emit:2:6: note: protocol requires function
//   '__emit(value:completionHandler:)' with type '(Any?, @escaping ((any Error)?) -> Void) -> Void'
// A second requirement, `__emit(value:) async throws`, was also listed as unsatisfied in that same
// diagnostic — implementing only the completion-handler form below is this round's best-evidenced
// next step (it's the one this file's threading design already targets); if SKIE's protocol has no
// default extension bridging one form from the other, the next CI run will report the async form as
// still missing, and that's the next slice, not a guess made now.

private let kotlinFlowWatcherLog = Logger(subsystem: "com.mentora.ios", category: "KotlinFlowBridge")

/// Adapts a Kotlin `FlowCollector` to a Swift closure, downcasting each type-erased emission to a
/// caller-specified concrete type before forwarding it onto `onValue` on the main thread.
///
/// Post-review fix (D108 fix round): Kotlin/Native calls `-emitValue:completionHandler:` via
/// `objc_msgSend` from whatever thread the coroutine dispatcher happens to be running the emission on —
/// **not** necessarily main (verified: `SessionManager.kt`'s token-refresh path sets `_authState.value`
/// off any Ktor/Darwin engine thread; `IosTokenStorage`'s suspend functions publish to
/// `KeychainStatus` similarly off-main). A class-level `@MainActor` provides zero enforcement against
/// that call — Obj-C message sends are not actor-isolation-checked — so this type is a plain
/// `nonisolated` `NSObject` conformer, and `emit` hops to main *explicitly* via `DispatchQueue.main
/// .async` rather than relying on actor isolation. `DispatchQueue` is used instead of
/// `Task { @MainActor in }` specifically because it is FIFO: StateFlow emission order is preserved
/// across separate emissions this way, whereas separately-spawned `Task`s have no ordering guarantee
/// relative to each other (which could let e.g. an auth-state mirror observe `.unauthenticated` then
/// `.authenticated` out of order). `completionHandler` is invoked synchronously on the Kotlin-calling
/// thread — not after the main-thread hop completes — so the Kotlin collector is never stalled waiting
/// on the Swift side.
///
/// Emissions that don't downcast to `Value` trip an `assertionFailure` in debug builds — a silently
/// dropped emission on this channel is exactly the failure mode B10 exists to prevent — plus a real
/// `Logger` call, since release builds compile `assertionFailure` out entirely.
private final class KotlinFlowWatcher<Value>: NSObject, Kotlinx_coroutines_coreFlowCollector {
    private let onValue: @MainActor (Value) -> Void

    init(onValue: @escaping @MainActor (Value) -> Void) {
        self.onValue = onValue
    }

    func __emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        let typed = value as? Value
        DispatchQueue.main.async { [onValue] in
            guard let typed else {
                kotlinFlowWatcherLog.error(
                    "watchKotlinFlow: emission did not downcast to \(String(describing: Value.self)): \(String(describing: value))"
                )
                assertionFailure(
                    "watchKotlinFlow: emission did not downcast to \(Value.self): \(String(describing: value))"
                )
                return
            }
            onValue(typed)
        }
        completionHandler(nil)
    }
}

/// Starts collecting `flow`, forwarding every emission that downcasts to `Value` onto `onValue` on the
/// main thread. Uses the `async throws` suspend form of `collect` (not the completion-handler form),
/// which suspends for the actual lifetime of the collection — the completion-handler form returns
/// immediately without blocking, which would make the `Task` below complete on its very first turn.
/// Because collection now genuinely runs inside this `Task`'s body, callers must store the returned
/// `Task` (or cancel it) rather than starting a new one per view render — System Design § 7's "never
/// subscribe from a view body" rule still holds even though the transport here is manual, not a SKIE
/// `AsyncSequence`.
///
/// **Open question, disclosed and unconfirmed (D108 fix round #2, Fix B):** `try await flow.collect(
/// collector:)` above is Swift's automatic completion-handler-to-async sugar over the raw, imported
/// Obj-C `collect(collector:completionHandler:)` — not a SKIE-generated, cancellation-aware suspend
/// wrapper (no such wrapper exists for this generic `Flow` protocol method; see this file's header
/// comment). What IS confirmed: cancelling the returned `Task` marks it cancelled locally, and if
/// Kotlin/Native's own suspend-cancellation bridge honors that, `try await flow.collect(...)` throws
/// `CancellationError` and this function's `catch` swallows it as a no-op. What is **not** confirmed —
/// and not verifiable by reading a static header — is whether cancellation actually propagates into
/// the underlying Kotlin coroutine and tears down its collection (freeing Kotlin-side resources), or
/// whether it instead leaks that collection while only the Swift-side `Task` stops observing it.
/// Swift's automatic async-import sugar over an Obj-C completion-handler method does not, by itself,
/// guarantee that forwarding. This is not a live defect in T4b: all three of this file's call sites
/// (`observeAuthState`, `observeLocale`, `KeychainStatus.failures`) are started once in
/// `AppEnvironment`/`SessionController`/`LocaleController`'s initializers and live for the entire app
/// process — none of T4b's own code ever calls `.cancel()` on the returned `Task`. It's a latent risk
/// for *future* reuse (e.g. T5+ per-screen subscriptions with real cancel-on-navigate lifecycles), not
/// a live one today. See `execution/DECISIONS_LOG.md` D108 for the recommended path before this
/// bridge's cancellation semantics are ever made load-bearing.
@discardableResult
@MainActor
func watchKotlinFlow<Value>(
    _ flow: any Kotlinx_coroutines_coreFlow,
    as valueType: Value.Type,
    onValue: @escaping @MainActor (Value) -> Void
) -> Task<Void, Never> {
    Task {
        let watcher = KotlinFlowWatcher<Value>(onValue: onValue)
        do {
            try await flow.collect(collector: watcher)
        } catch is CancellationError {
            // Expected when the caller cancels the returned `Task` — no-op.
        } catch {
            kotlinFlowWatcherLog.error("watchKotlinFlow: collect(collector:) threw: \(error)")
        }
    }
}
