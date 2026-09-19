import XCTest
import shared
@testable import iosApp

// Phase 5 Task T9 slice 1 -- `TabRouter` (`Navigation/TabRouter.swift`) core mechanics: the five stored
// paths, `path(for:)`'s write-through binding, tab selection (D2), push/pop (D3/D4), logout reset (D7),
// and `Route`'s value semantics (D7). Auth-gate-specific behavior (`requestGatedRoute`/
// `loginSheetDismissed`/`authenticationObserved`) lives in `AuthGateLogicTests.swift` instead.
@MainActor
final class TabRouterTests: XCTestCase {

    // MARK: - 3. path(for:) reads the right tab AND writes through

    func test_pathForTabReadsCorrectArrayAndWritesThrough() {
        let router = TabRouter()
        let route = Route.courseDetails(courseId: "c1")

        XCTAssertEqual(router.path(for: .explore).wrappedValue, [])

        router.path(for: .explore).wrappedValue.append(route)

        XCTAssertEqual(router.explore, [route])
        XCTAssertEqual(router.path(for: .explore).wrappedValue, [route])
        // The other four must be untouched by a write through .explore's binding.
        XCTAssertEqual(router.home, [])
        XCTAssertEqual(router.myLearning, [])
        XCTAssertEqual(router.aiTutor, [])
        XCTAssertEqual(router.profile, [])
    }

    // MARK: - 4. selectTab(otherTab) changes selection, mutates no path

    func test_selectDifferentTabChangesSelectionMutatesNoPath() {
        let router = TabRouter()
        router.push(.quiz(courseId: "c1"), onto: .home)

        router.selectTab(.explore)

        XCTAssertEqual(router.selectedTab, .explore)
        XCTAssertEqual(router.home, [.quiz(courseId: "c1")], "selectTab to a DIFFERENT tab must not touch any path.")
    }

    // MARK: - 5. selectTab(sameTab) (D2) pops ONLY that tab, leaves the other four untouched

    func test_selectSameTabPopsOnlyThatTabPathLeavesOthersUntouched() {
        let router = TabRouter()
        router.push(.quiz(courseId: "home"), onto: .home)
        router.push(.quiz(courseId: "explore"), onto: .explore)
        router.push(.quiz(courseId: "myLearning"), onto: .myLearning)
        router.push(.quiz(courseId: "aiTutor"), onto: .aiTutor)
        router.push(.quiz(courseId: "profile"), onto: .profile)
        router.selectedTab = .explore

        router.selectTab(.explore)

        XCTAssertEqual(router.explore, [], "Re-tapping the ALREADY-active tab must pop it to root (D2).")
        XCTAssertEqual(router.home, [.quiz(courseId: "home")])
        XCTAssertEqual(router.myLearning, [.quiz(courseId: "myLearning")])
        XCTAssertEqual(router.aiTutor, [.quiz(courseId: "aiTutor")])
        XCTAssertEqual(router.profile, [.quiz(courseId: "profile")])
    }

    // MARK: - 6. push(_:onto:) appends to exactly one tab (D3/D4)

    func test_pushAppendsToExactlyOneTab() {
        let router = TabRouter()

        router.push(.courseDetails(courseId: "c1"), onto: .myLearning)

        XCTAssertEqual(router.myLearning, [.courseDetails(courseId: "c1")])
        XCTAssertEqual(router.home, [])
        XCTAssertEqual(router.explore, [])
        XCTAssertEqual(router.aiTutor, [])
        XCTAssertEqual(router.profile, [])
    }

    // MARK: - 6b. setPath(_:for:) replaces exactly one tab's whole stack (D8, future T14 Purchase Success)

    func test_setPathReplacesExactlyOneTabsWholeStack() {
        let router = TabRouter()
        router.push(.courseDetails(courseId: "old"), onto: .myLearning)
        router.push(.quiz(courseId: "other"), onto: .home)

        router.setPath([.courseDetails(courseId: "new1"), .coursePlayer(courseId: "new1", lessonId: nil)], for: .myLearning)

        XCTAssertEqual(router.myLearning, [.courseDetails(courseId: "new1"), .coursePlayer(courseId: "new1", lessonId: nil)])
        XCTAssertEqual(router.home, [.quiz(courseId: "other")], "setPath for one tab must not touch any other tab's path.")
    }

    // MARK: - 7. resetAllForLogout (D7, § 10)

    func test_resetAllForLogoutClearsEverything() {
        let router = TabRouter()
        router.push(.quiz(courseId: "home"), onto: .home)
        router.push(.quiz(courseId: "explore"), onto: .explore)
        router.push(.quiz(courseId: "myLearning"), onto: .myLearning)
        router.push(.quiz(courseId: "aiTutor"), onto: .aiTutor)
        router.push(.quiz(courseId: "profile"), onto: .profile)
        router.selectedTab = .profile
        router.requestGatedRoute(.courseDetails(courseId: "gated"), from: .explore, isAuthenticated: false)
        XCTAssertNotNil(router.pendingIntent, "Precondition: a pending intent must exist before reset.")
        XCTAssertTrue(router.isPresentingLogin, "Precondition: the gate must be up before reset.")

        router.resetAllForLogout()

        XCTAssertEqual(router.home, [])
        XCTAssertEqual(router.explore, [])
        XCTAssertEqual(router.myLearning, [])
        XCTAssertEqual(router.aiTutor, [])
        XCTAssertEqual(router.profile, [])
        XCTAssertNil(router.pendingIntent)
        XCTAssertFalse(router.isPresentingLogin)
        XCTAssertEqual(router.selectedTab, .home)
    }

    // MARK: - 8. Route value semantics (D7 -- ids only, no reference-type payload)

    func test_routeValueSemantics() {
        XCTAssertEqual(Route.courseDetails(courseId: "c1"), Route.courseDetails(courseId: "c1"))
        XCTAssertNotEqual(Route.courseDetails(courseId: "c1"), Route.courseDetails(courseId: "c2"))
        XCTAssertNotEqual(Route.coursePlayer(courseId: "c1", lessonId: "l1"),
                           Route.coursePlayer(courseId: "c1", lessonId: "l2"))
        XCTAssertNotEqual(Route.courseDetails(courseId: "c1"), Route.quiz(courseId: "c1"),
            "Different cases with the same id must not compare equal.")

        let route = Route.courseDetails(courseId: "c1")
        XCTAssertEqual(Set([route, route]).count, 1)
    }

    // MARK: - 9. hidesTabBar (D5 policy half)

    func test_hidesTabBarPolicy() {
        XCTAssertTrue(Route.coursePlayer(courseId: "c1", lessonId: nil).hidesTabBar)
        XCTAssertTrue(Route.quiz(courseId: "c1").hidesTabBar)
        XCTAssertFalse(Route.courseDetails(courseId: "c1").hidesTabBar)
    }
}
