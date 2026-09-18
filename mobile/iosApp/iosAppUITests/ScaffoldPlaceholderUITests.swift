import XCTest

// Phase 5 Task T4a — scaffold placeholder for the `iosAppUITests` UI-test target declared in
// `project.yml`. Real UI test flows (portfolio journey + navigation behaviors — see
// `execution/PHASE_5_IOS_SYSTEM_DESIGN.md` § 22) are added by later, Mac-only tasks once the app has
// real screens to drive (blocked on MC-1). This file exists only so the target has a non-empty source
// directory for `xcodegen generate` to point at.
final class ScaffoldPlaceholderUITests: XCTestCase {
    func testScaffoldCompiles() {
        XCTAssertTrue(true)
    }
}
