import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 5 -- `Components/MentoraSelect.swift`'s pure logic (`MentoraSelectRules`,
// `MentoraSelectOption`). No rendering required. Border/background/label color resolution itself is
// NOT re-tested here -- `MentoraSelect` calls `MentoraTextFieldRules.colorSet(...)` directly with zero
// changes to that function (see `MentoraSelect.swift`'s own header "COLOR RESOLVER REUSE"), and that
// function's full behavior is already covered by `MentoraTextFieldRulesTests.swift`. This file covers
// only the logic genuinely NEW to Select: `effectiveIsFocused`, `fieldEnabled`, `valueTextColor`,
// `trailingIconName`, and `MentoraSelectOption`'s own shape. `Color` equality here rests on
// `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1, already
// proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraSelectTests: XCTestCase {

    // MARK: - effectiveIsFocused (Open is color-identical to Focused, see file header "COLOR RESOLVER REUSE")

    func test_effectiveIsFocused_neitherFocusedNorOpen_isFalse() {
        XCTAssertFalse(MentoraSelectRules.effectiveIsFocused(isFocused: false, isOpen: false))
    }

    func test_effectiveIsFocused_focusedOnly_isTrue() {
        XCTAssertTrue(MentoraSelectRules.effectiveIsFocused(isFocused: true, isOpen: false))
    }

    func test_effectiveIsFocused_openOnly_isTrue() {
        XCTAssertTrue(MentoraSelectRules.effectiveIsFocused(isFocused: false, isOpen: true))
    }

    func test_effectiveIsFocused_bothFocusedAndOpen_isTrue() {
        XCTAssertTrue(MentoraSelectRules.effectiveIsFocused(isFocused: true, isOpen: true))
    }

    // MARK: - fieldEnabled (mirrors MentoraButtonMetrics.isInteractive's loading-implies-disabled split)

    func test_fieldEnabled_enabledAndNotLoading_isTrue() {
        XCTAssertTrue(MentoraSelectRules.fieldEnabled(isEnabled: true, isLoading: false))
    }

    func test_fieldEnabled_loadingImpliesDisabledEvenWhenEnabled() {
        XCTAssertFalse(MentoraSelectRules.fieldEnabled(isEnabled: true, isLoading: true))
    }

    func test_fieldEnabled_disabledIsDisabledRegardlessOfLoading() {
        XCTAssertFalse(MentoraSelectRules.fieldEnabled(isEnabled: false, isLoading: false))
        XCTAssertFalse(MentoraSelectRules.fieldEnabled(isEnabled: false, isLoading: true))
    }

    // MARK: - valueTextColor (COMPONENTS.md: text.primary has-a-value / text.disabled placeholder-or-disabled)

    func test_valueTextColor_hasSelectionAndEnabled_isTextPrimary() {
        XCTAssertEqual(MentoraSelectRules.valueTextColor(hasSelection: true, isFieldEnabled: true), .mentoraTextPrimary)
    }

    func test_valueTextColor_noSelectionAndEnabled_isTextDisabled_placeholderTreatment() {
        XCTAssertEqual(MentoraSelectRules.valueTextColor(hasSelection: false, isFieldEnabled: true), .mentoraTextDisabled)
    }

    /// Disabled/loading always renders `text.disabled` even if a value IS selected -- the disclosed
    /// Android-parity fix this file's header names (`MentoraSelect.kt`'s own F2-class fix).
    func test_valueTextColor_hasSelectionButFieldDisabled_isStillTextDisabled() {
        XCTAssertEqual(MentoraSelectRules.valueTextColor(hasSelection: true, isFieldEnabled: false), .mentoraTextDisabled)
    }

    func test_valueTextColor_noSelectionAndFieldDisabled_isTextDisabled() {
        XCTAssertEqual(MentoraSelectRules.valueTextColor(hasSelection: false, isFieldEnabled: false), .mentoraTextDisabled)
    }

    // MARK: - trailingIconName (COMPONENTS.md: expand_more closed / expand_less open)

    func test_trailingIconName_closed_isExpandMore() {
        XCTAssertEqual(MentoraSelectRules.trailingIconName(isOpen: false), .expandMore)
    }

    func test_trailingIconName_open_isExpandLess() {
        XCTAssertEqual(MentoraSelectRules.trailingIconName(isOpen: true), .expandLess)
    }

    // MARK: - MentoraSelectOption (COMPONENTS.md line 241: an option can be individually non-selectable)

    func test_option_isEnabledDefaultsToTrue() {
        let option = MentoraSelectOption(value: 1, label: "One")
        XCTAssertTrue(option.isEnabled)
    }

    func test_option_isEnabledCanBeOverriddenFalse() {
        let option = MentoraSelectOption(value: 1, label: "One", isEnabled: false)
        XCTAssertFalse(option.isEnabled)
    }

    /// `Identifiable` conformance keys directly off `value` -- two options with the same `value` are the
    /// same identity, matching how `selection`/`ForEach` equality is expected to behave.
    func test_option_idEqualsItsOwnValue() {
        let option = MentoraSelectOption(value: 7, label: "Seven")
        XCTAssertEqual(option.id, 7)
    }
}
