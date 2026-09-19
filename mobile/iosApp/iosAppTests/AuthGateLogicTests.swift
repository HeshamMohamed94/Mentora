import XCTest
import shared
@testable import iosApp

// Phase 5 Task T9 slice 1 -- the guest auth-gate's pure logic on `TabRouter`
// (`requestGatedRoute`/`loginSheetDismissed`/`authenticationObserved`), independent of any real
// `.sheet`/SwiftUI presentation (`MentoraAuthGate`, `Navigation/AuthGate.swift`, is exercised only
// manually/by MC-3 -- there is no XCTest harness for a live `.sheet` presentation in this target).
//
// D132 review fix: `authenticationObserved()` now performs the pending-route replay itself (synchronously,
// the instant auth is observed -- see `TabRouter.swift`'s doc comment), and `loginSheetDismissed()` lost
// its `isAuthenticated:` parameter (it is now the user-cancelled path only, never a replay path). Tests
// below were updated to match: what were `loginSheetDismissed(isAuthenticated: true)` replay assertions
// are now `authenticationObserved()` assertions.
@MainActor
final class AuthGateLogicTests: XCTestCase {

    // MARK: - 10. Gate, authenticated: pushes immediately, no pending intent, sheet never raised

    func test_gateAuthenticatedPushesImmediately() {
        let router = TabRouter()

        router.requestGatedRoute(.coursePlayer(courseId: "c1", lessonId: nil), from: .explore, isAuthenticated: true)

        XCTAssertEqual(router.explore, [.coursePlayer(courseId: "c1", lessonId: nil)])
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
    }

    // MARK: - 11. Gate, guest: records the pending intent, raises the sheet, pushes nothing

    func test_gateGuestRecordsPendingIntentAndRaisesSheet() {
        let router = TabRouter()
        let route = Route.coursePlayer(courseId: "c1", lessonId: nil)

        router.requestGatedRoute(route, from: .explore, isAuthenticated: false)

        XCTAssertEqual(router.pendingIntent, PendingIntent(route: route, tab: .explore))
        XCTAssertTrue(router.isPresentingLogin)
        XCTAssertEqual(router.explore, [], "A guest's gated request must push nothing.")
        XCTAssertEqual(router.home, [])
        XCTAssertEqual(router.myLearning, [])
        XCTAssertEqual(router.aiTutor, [])
        XCTAssertEqual(router.profile, [])
    }

    // MARK: - 12. authenticationObserved() replays onto the RECORDED tab (B8), synchronously

    func test_authenticationObservedReplaysOntoRecordedTabNotCurrentSelection() {
        let router = TabRouter()
        let route = Route.coursePlayer(courseId: "c1", lessonId: nil)
        router.requestGatedRoute(route, from: .explore, isAuthenticated: false)

        // Prove the replay uses the RECORDED tab, not whatever happens to be selected right now.
        router.selectedTab = .profile

        router.authenticationObserved()

        XCTAssertEqual(router.selectedTab, .explore, "Must return to the ORIGINAL intent's tab (B8), not the tab selected when auth was observed.")
        XCTAssertEqual(router.explore, [route])
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
    }

    // MARK: - 12b. A subsequent loginSheetDismissed() after a successful replay is a safe no-op
    // (double-dismiss defense: onDismiss: still fires once the sheet's own animation completes, AFTER
    // authenticationObserved() already replayed and cleared everything).

    func test_loginSheetDismissedAfterSuccessfulReplayDoesNotDoubleReplayOrClearAnythingNew() {
        let router = TabRouter()
        let route = Route.coursePlayer(courseId: "c1", lessonId: nil)
        router.requestGatedRoute(route, from: .explore, isAuthenticated: false)
        router.authenticationObserved()

        router.loginSheetDismissed()

        XCTAssertEqual(router.explore, [route], "A trailing onDismiss: firing after a successful replay must not push a second time or clear the already-pushed route.")
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
    }

    // MARK: - 13. loginSheetDismissed() -- the swipe-to-dismiss/Cancel case (guest never authenticated)

    func test_loginSheetDismissedClearsPendingMutatesNoPath() {
        let router = TabRouter()
        let route = Route.coursePlayer(courseId: "c1", lessonId: nil)
        router.requestGatedRoute(route, from: .explore, isAuthenticated: false)

        router.loginSheetDismissed()

        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
        XCTAssertEqual(router.explore, [], "A dismissed-without-authenticating gate must not have pushed anything.")
    }

    // MARK: - 14. Both auth-gate dismissal methods are safe no-ops with no pending intent

    func test_authGateDismissalMethodsWithNoPendingIntentAreSafeNoOps() {
        let router = TabRouter()

        router.authenticationObserved()
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
        XCTAssertEqual(router.routes(for: .home), [])
        XCTAssertEqual(router.routes(for: .explore), [])
        XCTAssertEqual(router.routes(for: .myLearning), [])
        XCTAssertEqual(router.routes(for: .aiTutor), [])
        XCTAssertEqual(router.routes(for: .profile), [])

        router.loginSheetDismissed()
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
    }

    // MARK: - 15. Logout while the gate/sheet is up clears both the pending intent and isPresentingLogin

    func test_logoutWhileGateIsUpClearsPendingIntentAndDismissesSheet() {
        let router = TabRouter()
        router.requestGatedRoute(.quiz(courseId: "c1"), from: .home, isAuthenticated: false)
        XCTAssertNotNil(router.pendingIntent)
        XCTAssertTrue(router.isPresentingLogin)

        router.resetAllForLogout()

        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
    }
}
