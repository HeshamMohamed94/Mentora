import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 2 -- `Components/MentoraIconButton.swift`'s pure metrics/state resolvers
// (`MentoraIconButtonMetrics`, `MentoraIconButtonRules`). No rendering required. `Color` equality here
// rests on `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice
// 1, already proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraIconButtonTests: XCTestCase {

    // MARK: - Metrics: hit area (44) vs. visual size (40 box, 24 icon) stay independent

    func test_hitAreaMinimumIs44_matchingMentoraTouchTarget() {
        XCTAssertEqual(MentoraIconButtonMetrics.hitAreaMinimum, 44)
        XCTAssertEqual(MentoraIconButtonMetrics.hitAreaMinimum, MentoraTouchTarget.iosPt)
    }

    func test_iconRenderedSizeIs24_matchingMentoraIconSizeDefault() {
        XCTAssertEqual(MentoraIconButtonMetrics.iconSize, 24)
        XCTAssertEqual(MentoraIconButtonMetrics.iconSize, MentoraIconSize.`default`)
    }

    /// The 40x40 VISUAL touch-area box `COMPONENTS.md` states is distinct from BOTH the 24pt icon glyph
    /// and the 44pt REAL hit area (see `MentoraIconButton.swift`'s file header for why 44, not 40, is
    /// the real hit area) -- this asserts all three numbers stay independently correct, not accidentally
    /// collapsed into one.
    func test_visualDiameterIconSizeAndHitAreaAreThreeDistinctNumbers() {
        XCTAssertEqual(MentoraIconButtonMetrics.visualDiameter, 40)
        XCTAssertEqual(MentoraIconButtonMetrics.iconSize, 24)
        XCTAssertEqual(MentoraIconButtonMetrics.hitAreaMinimum, 44)
        XCTAssertGreaterThan(MentoraIconButtonMetrics.hitAreaMinimum, MentoraIconButtonMetrics.visualDiameter)
        XCTAssertGreaterThan(MentoraIconButtonMetrics.visualDiameter, MentoraIconButtonMetrics.iconSize)
    }

    // MARK: - Icon color resolution

    func test_iconColor_defaultInactive_isTextSecondary() {
        XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: false, state: .`default`), .mentoraTextSecondary)
    }

    func test_iconColor_defaultActive_isBrandPrimary() {
        XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: true, state: .`default`), .mentoraBrandPrimary)
    }

    /// `COMPONENTS.md § IconButton`'s Hover/Pressed/Focused rows all read `color.text.primary`
    /// regardless of whether the button represents an active/selected toggle -- the active tint only
    /// shows at rest.
    func test_iconColor_hoverPressedFocused_alwaysTextPrimaryRegardlessOfActive() {
        for isActive in [false, true] {
            XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: isActive, state: .hover), .mentoraTextPrimary)
            XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: isActive, state: .pressed), .mentoraTextPrimary)
            XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: isActive, state: .focused), .mentoraTextPrimary)
        }
    }

    func test_iconColor_disabled_isTextDisabledRegardlessOfActive() {
        XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: false, state: .disabled), .mentoraTextDisabled)
        XCTAssertEqual(MentoraIconButtonRules.iconColor(isActive: true, state: .disabled), .mentoraTextDisabled)
    }

    // MARK: - State layer (hover/pressed background only)

    func test_stateLayer_hoverAndPressed_useTextPrimaryAtTheirOpacity() throws {
        let hover = try XCTUnwrap(MentoraIconButtonRules.stateLayer(for: .hover, colorScheme: .light))
        XCTAssertEqual(hover.color, .mentoraTextPrimary)
        XCTAssertEqual(hover.opacity, MentoraStateOpacityLight.hoverOpacity)

        let pressed = try XCTUnwrap(MentoraIconButtonRules.stateLayer(for: .pressed, colorScheme: .light))
        XCTAssertEqual(pressed.color, .mentoraTextPrimary)
        XCTAssertEqual(pressed.opacity, MentoraStateOpacityLight.pressedOpacity)
    }

    func test_stateLayer_defaultFocusedDisabled_areNil() {
        XCTAssertNil(MentoraIconButtonRules.stateLayer(for: .`default`, colorScheme: .light))
        XCTAssertNil(MentoraIconButtonRules.stateLayer(for: .focused, colorScheme: .light))
        XCTAssertNil(MentoraIconButtonRules.stateLayer(for: .disabled, colorScheme: .light))
    }

    // MARK: - Focus ring (offset, since IconButton has no resting border)

    func test_focusRing_onlyForFocusedState() throws {
        XCTAssertNil(MentoraIconButtonRules.focusRing(for: .`default`))
        XCTAssertNil(MentoraIconButtonRules.focusRing(for: .hover))
        XCTAssertNil(MentoraIconButtonRules.focusRing(for: .pressed))
        XCTAssertNil(MentoraIconButtonRules.focusRing(for: .disabled))

        let ring = try XCTUnwrap(MentoraIconButtonRules.focusRing(for: .focused))
        XCTAssertEqual(ring.color, .mentoraBorderFocus)
        XCTAssertEqual(ring.width, MentoraBorderWidth.focus)
        XCTAssertGreaterThan(ring.offset, 0, "IconButton's focus ring must be OFFSET (no resting border to replace in place).")
    }

    // MARK: - Interaction-state priority resolution (mirrors MentoraButtonInteractionState.resolve)

    func test_resolve_disabledBeatsEverything() {
        XCTAssertEqual(MentoraIconButtonInteractionState.resolve(isEnabled: false, isPressed: true, isFocused: true), .disabled)
    }

    func test_resolve_pressedBeatsFocused() {
        XCTAssertEqual(MentoraIconButtonInteractionState.resolve(isEnabled: true, isPressed: true, isFocused: true), .pressed)
    }

    func test_resolve_focusedBeatsDefault() {
        XCTAssertEqual(MentoraIconButtonInteractionState.resolve(isEnabled: true, isPressed: false, isFocused: true), .focused)
    }

    func test_resolve_defaultWhenNothingElseApplies() {
        XCTAssertEqual(MentoraIconButtonInteractionState.resolve(isEnabled: true, isPressed: false, isFocused: false), .`default`)
    }
}
