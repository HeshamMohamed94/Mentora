import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 6 -- `Components/MentoraTabs.swift`'s pure metrics/state resolver
// (`MentoraTabsMetrics`/`MentoraTabsRules`). No rendering required. `Color` equality here rests on
// `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1, already
// proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraTabsTests: XCTestCase {

    // MARK: - Metrics (COMPONENTS.md § Tabs's property table)

    func test_heightIs44() {
        XCTAssertEqual(MentoraTabsMetrics.height, 44)
    }

    func test_indicatorHeightIs2px() {
        XCTAssertEqual(MentoraTabsMetrics.indicatorHeight, 2)
    }

    func test_horizontalPaddingIsSpace4() {
        XCTAssertEqual(MentoraTabsMetrics.horizontalPadding, MentoraSpacing.space4)
        XCTAssertEqual(MentoraTabsMetrics.horizontalPadding, 16)
    }

    // MARK: - textColor (COMPONENTS.md's 3-row state table)

    func test_textColor_default_usesTextSecondary() {
        XCTAssertEqual(MentoraTabsRules.textColor(isSelected: false, isEnabled: true), .mentoraTextSecondary)
    }

    func test_textColor_active_usesTextPrimary() {
        XCTAssertEqual(MentoraTabsRules.textColor(isSelected: true, isEnabled: true), .mentoraTextPrimary)
    }

    func test_textColor_disabled_usesTextDisabled_regardlessOfSelection() {
        XCTAssertEqual(MentoraTabsRules.textColor(isSelected: false, isEnabled: false), .mentoraTextDisabled)
        XCTAssertEqual(MentoraTabsRules.textColor(isSelected: true, isEnabled: false), .mentoraTextDisabled)
    }

    /// The 3 real states (default/active/disabled) must map to distinct colors -- catches a copy-paste
    /// branch collision, same technique `MentoraToggleTests.swift`'s own
    /// `test_liveStateBranches_mapToDistinctColorSets` uses.
    func test_defaultActiveDisabled_mapToDistinctColors() {
        let colors = [
            MentoraTabsRules.textColor(isSelected: false, isEnabled: true),
            MentoraTabsRules.textColor(isSelected: true, isEnabled: true),
            MentoraTabsRules.textColor(isSelected: false, isEnabled: false),
        ]
        XCTAssertEqual(Set(colors).count, 3)
    }

    // MARK: - MentoraTabItem (mirrors MentoraSelectOption's shape)

    func test_tabItem_idEqualsValue() {
        let item = MentoraTabItem(value: "overview", label: "Overview")
        XCTAssertEqual(item.id, "overview")
    }

    func test_tabItem_isEnabledDefaultsToTrue() {
        let item = MentoraTabItem(value: 1, label: "Notes")
        XCTAssertTrue(item.isEnabled)
    }
}
