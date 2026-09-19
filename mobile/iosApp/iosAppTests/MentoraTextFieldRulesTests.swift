import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 3 -- `Components/MentoraTextField.swift`'s pure state/color resolver
// (`MentoraTextFieldRules`). No rendering required. `Color` equality here rests on
// `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1,
// already proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraTextFieldRulesTests: XCTestCase {

    // MARK: - isLabelFloated (isFocused || !isEmpty)

    func test_isLabelFloated_focusedAndEmpty_isTrue() {
        XCTAssertTrue(MentoraTextFieldRules.isLabelFloated(isFocused: true, isEmpty: true))
    }

    func test_isLabelFloated_notFocusedButHasValue_isTrue() {
        XCTAssertTrue(MentoraTextFieldRules.isLabelFloated(isFocused: false, isEmpty: false))
    }

    func test_isLabelFloated_focusedAndHasValue_isTrue() {
        XCTAssertTrue(MentoraTextFieldRules.isLabelFloated(isFocused: true, isEmpty: false))
    }

    func test_isLabelFloated_restingState_notFocusedAndEmpty_isFalse() {
        XCTAssertFalse(MentoraTextFieldRules.isLabelFloated(isFocused: false, isEmpty: true))
    }

    // MARK: - Disabled short-circuits everything

    func test_colorSet_disabled_beatsFocusedErrorAndSuccess() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: true, isEmpty: false, isEnabled: false, hasError: true, hasSuccess: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderDefault)
        XCTAssertEqual(colors.borderWidth, MentoraBorderWidth.`default`)
        XCTAssertEqual(colors.backgroundColor, .mentoraSurfaceVariant)
        XCTAssertEqual(colors.labelColor, .mentoraTextDisabled)
    }

    func test_colorSet_disabled_plainRestingInputsToo() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: true, isEnabled: false, hasError: false, hasSuccess: false
        )
        XCTAssertEqual(colors.backgroundColor, .mentoraSurfaceVariant)
        XCTAssertEqual(colors.labelColor, .mentoraTextDisabled)
    }

    // MARK: - Error beats focused (COMPONENTS.md line 154: "error state overrides focus color")

    func test_colorSet_focusedAndError_resolvesToErrorBorder_notFocusBorder() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: true, isEmpty: false, isEnabled: true, hasError: true, hasSuccess: false
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderError)
        XCTAssertNotEqual(colors.borderColor, .mentoraBorderFocus)
    }

    func test_colorSet_errorAlone_notFocused_stillErrorBorder() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: false, isEnabled: true, hasError: true, hasSuccess: false
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderError)
    }

    // MARK: - Error beats success

    func test_colorSet_errorAndSuccessBothTrue_resolvesToErrorBorder() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: false, isEnabled: true, hasError: true, hasSuccess: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderError)
        XCTAssertNotEqual(colors.borderColor, .mentoraSuccessDefault)
    }

    // MARK: - Focused+success gap-fill: disclosed ordering borrowed from Android's own
    // `mentoraTextFieldColors` kdoc ("success overrides the border... when the field isn't in an error
    // state" -- error always wins). Neither platform's spec states this combination explicitly; this
    // resolver (and this test) apply Android's own disclosed ordering: error > focused > success.

    func test_colorSet_focusedAndSuccessNoError_resolvesToFocusBorder_notSuccessBorder() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: true, isEmpty: false, isEnabled: true, hasError: false, hasSuccess: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderFocus)
        XCTAssertNotEqual(colors.borderColor, .mentoraSuccessDefault)
    }

    // MARK: - Success (no error, not focused)

    func test_colorSet_successOnly_resolvesToSuccessBorder() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: false, isEnabled: true, hasError: false, hasSuccess: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraSuccessDefault)
        XCTAssertEqual(colors.backgroundColor, .mentoraSurfaceDefault)
    }

    // MARK: - Hover (modeled for testability, never dynamically wired -- see file header)

    func test_colorSet_hover_usesBorderStrong_whenNotFocusedNotErrorNotDisabled() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false,
            isHovered: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderStrong)
    }

    func test_colorSet_hover_beatenByFocusedAndError() {
        let focusedBeatsHover = MentoraTextFieldRules.colorSet(
            isFocused: true, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false,
            isHovered: true
        )
        XCTAssertEqual(focusedBeatsHover.borderColor, .mentoraBorderFocus)

        let errorBeatsHover = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: true, isEnabled: true, hasError: true, hasSuccess: false,
            isHovered: true
        )
        XCTAssertEqual(errorBeatsHover.borderColor, .mentoraBorderError)
    }

    func test_colorSet_hover_beatsSuccess() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: true,
            isHovered: true
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderStrong)
    }

    // MARK: - Plain resting default

    func test_colorSet_default_noFlagsSet() {
        let colors = MentoraTextFieldRules.colorSet(
            isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false
        )
        XCTAssertEqual(colors.borderColor, .mentoraBorderDefault)
        XCTAssertEqual(colors.backgroundColor, .mentoraSurfaceDefault)
        XCTAssertEqual(colors.labelColor, .mentoraTextSecondary)
        XCTAssertEqual(colors.borderWidth, MentoraBorderWidth.`default`)
    }

    // MARK: - Border width driven ONLY by isFocused (not by error/success) -- a deliberate, minimal
    // reading of COMPONENTS.md's per-state table (see MentoraTextFieldRules.colorSet's doc comment).

    func test_borderWidth_focusedIsAlwaysTwo_regardlessOfErrorOrSuccess() {
        for (hasError, hasSuccess) in [(false, false), (true, false), (false, true), (true, true)] {
            let colors = MentoraTextFieldRules.colorSet(
                isFocused: true, isEmpty: false, isEnabled: true, hasError: hasError, hasSuccess: hasSuccess
            )
            XCTAssertEqual(colors.borderWidth, MentoraBorderWidth.focus,
                "focused borderWidth must stay 2 regardless of (hasError: \(hasError), hasSuccess: \(hasSuccess))")
        }
    }

    func test_borderWidth_notFocusedIsAlwaysOne_regardlessOfErrorOrSuccess() {
        for (hasError, hasSuccess) in [(false, false), (true, false), (false, true)] {
            let colors = MentoraTextFieldRules.colorSet(
                isFocused: false, isEmpty: false, isEnabled: true, hasError: hasError, hasSuccess: hasSuccess
            )
            XCTAssertEqual(colors.borderWidth, MentoraBorderWidth.`default`,
                "unfocused borderWidth must stay 1 regardless of (hasError: \(hasError), hasSuccess: \(hasSuccess))")
        }
    }

    // MARK: - Copy-paste guard across the priority chain's distinct branches

    /// `disabled` and `plainDefault` share the SAME `borderColor` by design (`COMPONENTS.md` line 156:
    /// Disabled border is `color.border.default`, identical to the plain resting Default row) -- keying
    /// on `borderColor` ALONE would make this assertion fail for real (5 distinct colors, not 6) despite
    /// nothing being wrong, exactly the kind of vacuous-or-broken guard `BadgeVariantTests.swift`'s
    /// pair-keyed equivalent already avoids. Keying on the FULL (border, background, label) triple
    /// instead correctly distinguishes disabled from default via `backgroundColor`/`labelColor`
    /// (`.mentoraSurfaceVariant`/`.mentoraTextDisabled` vs. `.mentoraSurfaceDefault`/`.mentoraTextSecondary`)
    /// while still catching a genuine copy-paste collision across any of the 6 branches.
    func test_priorityBranches_mapToDistinctColorSets() {
        struct Key: Hashable { let border, background, label: Color }
        let disabled = MentoraTextFieldRules.colorSet(isFocused: false, isEmpty: true, isEnabled: false, hasError: false, hasSuccess: false)
        let error = MentoraTextFieldRules.colorSet(isFocused: false, isEmpty: true, isEnabled: true, hasError: true, hasSuccess: false)
        let focused = MentoraTextFieldRules.colorSet(isFocused: true, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false)
        let hover = MentoraTextFieldRules.colorSet(isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false, isHovered: true)
        let success = MentoraTextFieldRules.colorSet(isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: true)
        let plainDefault = MentoraTextFieldRules.colorSet(isFocused: false, isEmpty: true, isEnabled: true, hasError: false, hasSuccess: false)

        let keys = [disabled, error, focused, hover, success, plainDefault].map {
            Key(border: $0.borderColor, background: $0.backgroundColor, label: $0.labelColor)
        }
        XCTAssertEqual(Set(keys).count, 6, "Two or more priority branches resolved to the same (border, background, label) color set -- check the if-chain for a copy-paste error.")
    }
}
