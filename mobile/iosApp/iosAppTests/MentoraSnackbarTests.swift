import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 7 -- `Components/MentoraSnackbar.swift`. DISCLOSED: this component is almost
// entirely presentation/timing (an `.overlay` + `.transition` + a `Task.sleep`-driven auto-dismiss
// timer inside a private `ViewModifier`), with no separate pure color/state resolver enum the way
// `MentoraProgressBar`/`MentoraTabs`/every other atom in this kit has one -- `COMPONENTS.md § Snackbar`
// states exactly one fixed visual treatment (no per-state color table to resolve), so there is
// genuinely very little pure, non-rendering logic to unit-test here. This file asserts the one real,
// pure, directly-testable unit that DOES exist: `MentoraSnackbar`'s plain stored-property initializer
// shape (message/actionLabel/action wiring), rather than manufacturing a weak test around rendering or
// timing that would need a live view hierarchy / real elapsed time to exercise meaningfully.
final class MentoraSnackbarTests: XCTestCase {

    func test_init_messageOnly_hasNilActionLabelAndAction() {
        let snackbar = MentoraSnackbar(message: "Couldn't enroll -- try again")
        XCTAssertEqual(snackbar.message, "Couldn't enroll -- try again")
        XCTAssertNil(snackbar.actionLabel)
        XCTAssertNil(snackbar.action)
    }

    func test_init_withAction_carriesActionLabelAndInvokableAction() {
        var didInvoke = false
        let snackbar = MentoraSnackbar(message: "Progress saved", actionLabel: "Undo", action: { didInvoke = true })
        XCTAssertEqual(snackbar.actionLabel, "Undo")
        snackbar.action?()
        XCTAssertTrue(didInvoke)
    }
}
