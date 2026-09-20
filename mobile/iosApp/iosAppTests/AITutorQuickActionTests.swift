import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 3 -- `Components/AITutorQuickAction.swift`'s pure per-state color resolution.
final class AITutorQuickActionTests: XCTestCase {

    func test_pressedAndEnabledUsesTheAccentBackground() {
        XCTAssertEqual(AITutorQuickActionRules.background(isPressed: true, isEnabled: true), .mentoraBrandPrimaryContainer)
    }

    func test_notPressedUsesTheRestingBackgroundRegardlessOfEnabled() {
        XCTAssertEqual(AITutorQuickActionRules.background(isPressed: false, isEnabled: true), .mentoraSurfaceDefault)
        XCTAssertEqual(AITutorQuickActionRules.background(isPressed: false, isEnabled: false), .mentoraSurfaceDefault)
    }

    func test_pressedButDisabledDoesNotUseTheAccentBackground() {
        // Android's own `pressed && enabled` guard -- a disabled control cannot show the pressed
        // treatment even if `isPressed` were somehow still true.
        XCTAssertEqual(AITutorQuickActionRules.background(isPressed: true, isEnabled: false), .mentoraSurfaceDefault)
    }

    func test_pressedAndEnabledUsesTheAccentBorder() {
        XCTAssertEqual(AITutorQuickActionRules.borderColor(isPressed: true, isEnabled: true), .mentoraBrandPrimary)
    }

    func test_pressedButDisabledDoesNotUseTheAccentBorder() {
        XCTAssertEqual(AITutorQuickActionRules.borderColor(isPressed: true, isEnabled: false), .mentoraBorderDefault)
    }

    func test_enabledTextColorHasNoOpacityReduction() {
        XCTAssertEqual(AITutorQuickActionRules.textColor(isEnabled: true, disabledContentOpacity: 0.38), .mentoraBrandPrimary)
    }

    func test_disabledTextColorIsDistinctFromEnabled() {
        let disabled = AITutorQuickActionRules.textColor(isEnabled: false, disabledContentOpacity: 0.38)
        let enabled = AITutorQuickActionRules.textColor(isEnabled: true, disabledContentOpacity: 0.38)
        XCTAssertNotEqual(disabled, enabled)
    }
}
