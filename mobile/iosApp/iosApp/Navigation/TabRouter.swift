import SwiftUI
import Observation

// Phase 5 Task T9 slice 1 -- the single source of truth for every tab's `NavigationStack` path, the
// active tab, and the guest auth-gate's pending intent (`PHASE_5_IOS_SYSTEM_DESIGN.md` § 10,
// `PHASE_5_ACCEPTANCE_CRITERIA.md` D1-D8/B8). Constructed exactly once by `AppEnvironment` (`let router:
// TabRouter`, constructed in `AppEnvironment.init`) -- never a `.shared`/singleton static (A3), never
// constructed from a view.
//
// FIVE STORED PROPERTIES, NEVER A `[Tab: [Route]]` DICTIONARY -- `PHASE_5_IOS_SYSTEM_DESIGN.md § 3.1`'s
// central rule for this type, made a real gate (`tools/ios-checks/navigation-checks.js` Check B2), not
// only a comment. The reason is mechanical, not stylistic: `NavigationStack(path:)` needs a real
// `Binding<[Route]>`, and a dictionary subscript (`dict[tab]`) cannot produce one that writes back into
// the dictionary the way a genuine stored-property `Binding(get:set:)` can.
//
// PENDING-INTENT LOCATION -- A DISCLOSED DEVIATION FROM § 10'S LITERAL TEXT. § 10 states the pending
// `Route` is "stored on AppEnvironment." It is stored here, on `TabRouter`, instead. `AppEnvironment`
// (`Support/AppEnvironment.swift`) is a plain `@MainActor final class`, NOT `@Observable` (by its own
// documented, deliberate contract) -- a stored `var` there would be invisible to SwiftUI's Observation
// tracking and could never re-present/re-drive a sheet when it changed. `TabRouter` is already
// `@Observable` and is already the thing `AppEnvironment` owns as a `let`, and § 10 itself couples
// pending-route clearing to `TabRouter`'s own logout reset (`resetAllForLogout()` below) -- so putting
// the pending intent here satisfies § 10's actual intent (a single, atomic place that both sets and
// clears it) while actually compiling into working, observable UI. This is the ONE deliberate deviation
// from § 10's literal wording in this task.
@MainActor
@Observable
final class TabRouter {

    // MARK: - Per-tab navigation paths (D3/D4)

    private(set) var home: [Route] = []
    private(set) var explore: [Route] = []
    private(set) var myLearning: [Route] = []
    private(set) var aiTutor: [Route] = []
    private(set) var profile: [Route] = []

    // MARK: - Selection + auth-gate state

    var selectedTab: Tab = .home
    private(set) var pendingIntent: PendingIntent?
    private(set) var isPresentingLogin: Bool = false

    // MARK: - Read access (logic/tests)

    func routes(for tab: Tab) -> [Route] {
        switch tab {
        case .home: return home
        case .explore: return explore
        case .myLearning: return myLearning
        case .aiTutor: return aiTutor
        case .profile: return profile
        }
    }

    // MARK: - The one permitted indirection

    /// The ONE permitted indirection over the five stored properties above -- everything else
    /// (`push`/`popToRoot`/`setPath`/`resetAllForLogout`) switches directly over them instead of routing
    /// through this. `func path(for` is asserted to appear ONLY in this file by
    /// `navigation-checks.js` Check B3.
    ///
    /// Each branch hand-rolls a `Binding(get:set:)` rather than returning a computed dictionary-backed
    /// binding (see this file's header on why a dictionary can't do this at all). Every closure is wrapped
    /// in `MainActor.assumeIsolated { ... }` -- a runtime assertion that execution really is on the main
    /// actor, which it always is here: SwiftUI reads/writes a `NavigationStack`'s `path` binding on the
    /// main thread. D132 review note: whether Swift's type system would actually have required this
    /// wrapper to compile (`Binding`'s closures inheriting the enclosing `@MainActor` isolation vs. being
    /// treated as plain non-isolated closures) was not conclusively determined by static reading alone --
    /// this repo's `SWIFT_VERSION: "5.0"` with no strict-concurrency flag makes inference the likely
    /// outcome, but nothing else in this target used a hand-rolled `Binding(get:set:)` before this task to
    /// prove it either way. `assumeIsolated` is correct and harmless under EITHER outcome, so it is now
    /// applied consistently at all 3 `Binding(get:set:)` sites this task introduces (this one,
    /// `TabShell.swift`'s `selectionBinding`, `AuthGate.swift`'s `isPresentingLoginBinding`) rather than
    /// only here -- the first real CI compile is what actually adjudicates which theory was correct, and
    /// if it turns out unnecessary everywhere, removing it later is a no-risk cleanup, never a correctness
    /// fix. The alternative of dropping `@MainActor` from the class itself would be wrong regardless, since
    /// every other method here (and `SessionController`/`ThemeController`/`LocaleController`'s identical
    /// `@MainActor @Observable final class` shape elsewhere in `Support/`) depends on that isolation.
    func path(for tab: Tab) -> Binding<[Route]> {
        switch tab {
        case .home:
            return Binding(
                get: { MainActor.assumeIsolated { self.home } },
                set: { newValue in MainActor.assumeIsolated { self.home = newValue } }
            )
        case .explore:
            return Binding(
                get: { MainActor.assumeIsolated { self.explore } },
                set: { newValue in MainActor.assumeIsolated { self.explore = newValue } }
            )
        case .myLearning:
            return Binding(
                get: { MainActor.assumeIsolated { self.myLearning } },
                set: { newValue in MainActor.assumeIsolated { self.myLearning = newValue } }
            )
        case .aiTutor:
            return Binding(
                get: { MainActor.assumeIsolated { self.aiTutor } },
                set: { newValue in MainActor.assumeIsolated { self.aiTutor = newValue } }
            )
        case .profile:
            return Binding(
                get: { MainActor.assumeIsolated { self.profile } },
                set: { newValue in MainActor.assumeIsolated { self.profile = newValue } }
            )
        }
    }

    // MARK: - Mutation (D3/D4/D8)

    func push(_ route: Route, onto tab: Tab) {
        switch tab {
        case .home: home.append(route)
        case .explore: explore.append(route)
        case .myLearning: myLearning.append(route)
        case .aiTutor: aiTutor.append(route)
        case .profile: profile.append(route)
        }
    }

    func popToRoot(_ tab: Tab) {
        switch tab {
        case .home: home = []
        case .explore: explore = []
        case .myLearning: myLearning = []
        case .aiTutor: aiTutor = []
        case .profile: profile = []
        }
    }

    /// A primitive a future task (T14, Purchase Success, D8) will use to replace a whole stack in one
    /// step -- not otherwise consumed by anything in T9 itself.
    func setPath(_ routes: [Route], for tab: Tab) {
        switch tab {
        case .home: home = routes
        case .explore: explore = routes
        case .myLearning: myLearning = routes
        case .aiTutor: aiTutor = routes
        case .profile: profile = routes
        }
    }

    // MARK: - Tab selection (D2)

    /// D2: tapping the ALREADY-active tab pops it to root, exactly like the system convention; tapping a
    /// different tab only changes selection, touching no path.
    func selectTab(_ tab: Tab) {
        if tab == selectedTab {
            popToRoot(tab)
        } else {
            selectedTab = tab
        }
    }

    // MARK: - Logout reset (D7, § 10)

    /// ALL FIVE paths cleared, pending intent cleared, login sheet dismissed, selection reset to
    /// `.home` -- § 10's literal text. Safe as a single shared reset here since this task's own
    /// guest-shell override (see `Route.swift`'s header) means there is only one shell shape for both
    /// guest and authenticated users; a logout never needs to swap shell TYPES, only reset state within
    /// the one shell that already exists.
    func resetAllForLogout() {
        home = []
        explore = []
        myLearning = []
        aiTutor = []
        profile = []
        pendingIntent = nil
        isPresentingLogin = false
        selectedTab = .home
    }

    // MARK: - Auth gate (B8)

    /// Authenticated: pushes immediately, no gate. Guest: records the pending intent and raises the
    /// login sheet instead of pushing.
    func requestGatedRoute(_ route: Route, from tab: Tab, isAuthenticated: Bool) {
        if isAuthenticated {
            push(route, onto: tab)
        } else {
            pendingIntent = PendingIntent(route: route, tab: tab)
            isPresentingLogin = true
        }
    }

    /// Called from an `onChange` watcher the INSTANT `SessionController.isAuthenticated` flips `true`
    /// while the gate is up. Performs the pending-route replay SYNCHRONOUSLY here, not deferred to the
    /// sheet's `onDismiss:` -- see D132 (`execution/DECISIONS_LOG.md`) for why the original design (which
    /// deferred the replay to `onDismiss:`) was a real, latent bug: `isAuthenticated` becoming `true` is
    /// itself an async event (it arrives via `SessionController`'s own `authStates()` subscription,
    /// crossing a Kotlin coroutine -> Swift `AsyncSequence` boundary), so it can arrive either BEFORE or
    /// AFTER a real login screen's own `dismiss()` call completes its animation -- there is no ordering
    /// guarantee between those two independent async events. Replaying here, at the moment auth is
    /// OBSERVED true, removes that race entirely: the destination is already pushed by the time anything
    /// dismisses. Setting `isPresentingLogin = false` afterward only STARTS the sheet's dismissal (or is a
    /// no-op if the login screen already called `dismiss()` itself) -- either way, the push already
    /// happened underneath it.
    func authenticationObserved() {
        if let intent = pendingIntent {
            selectedTab = intent.tab
            push(intent.route, onto: intent.tab)
        }
        pendingIntent = nil
        isPresentingLogin = false
    }

    /// The USER-CANCELLED-the-gate path ONLY (swipe-to-dismiss, or an in-sheet Cancel/close affordance) --
    /// never the success path, which `authenticationObserved()` above already handles synchronously.
    /// `MentoraAuthGate` (`Navigation/AuthGate.swift`) routes every dismissal -- the sheet's own
    /// `isPresented` setter (fired for a genuine user-driven dismissal) AND `onDismiss:` (fired once ANY
    /// dismissal's animation completes, success included) -- through this one method, so unconditionally
    /// clearing `pendingIntent` here is always safe: on a real cancel it is the ONLY clear that happens; on
    /// a success it is redundant with the clear `authenticationObserved()` already performed (double-fire
    /// defense, `AuthGateLogicTests.swift`'s "double-dismiss" case), never a second replay, since this
    /// method never pushes anything itself.
    func loginSheetDismissed() {
        defer { isPresentingLogin = false }
        pendingIntent = nil
    }
}
