import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 1 -- `Components/CategoryChip.swift`'s `CategoryChipState` color-pair
// resolver. Every assertion below rests on `Color` equality being asset-NAME-based, not
// identity-based -- see `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased`
// for the guard proving that assumption (not re-duplicated here; both files run in the same target).
final class CategoryChipTests: XCTestCase {

    func test_allFourStatesExist() {
        XCTAssertEqual(CategoryChipState.allCases.count, 4)
    }

    func test_defaultMapsToSurfaceVariantAndTextSecondary() {
        XCTAssertEqual(CategoryChipState.`default`.backgroundColor, .mentoraSurfaceVariant)
        XCTAssertEqual(CategoryChipState.`default`.foregroundColor, .mentoraTextSecondary)
    }

    func test_selectedMapsToBrandPrimaryContainerPair() {
        XCTAssertEqual(CategoryChipState.selected.backgroundColor, .mentoraBrandPrimaryContainer)
        XCTAssertEqual(CategoryChipState.selected.foregroundColor, .mentoraBrandOnPrimaryContainer)
    }

    func test_onImageOverlayMapsToChipScrimAndTextInverse() {
        XCTAssertEqual(CategoryChipState.onImageOverlay.backgroundColor, .mentoraOverlayChipScrim)
        XCTAssertEqual(CategoryChipState.onImageOverlay.foregroundColor, .mentoraTextInverse)
    }

    /// `COMPONENTS.md § CategoryChip`'s disabled row is explicit: "`color.surface.variant` (no
    /// opacity modifier -- background already reads as inactive against `text.disabled`)". This
    /// asserts the disabled background is LITERALLY the plain `.mentoraSurfaceVariant` value -- the
    /// same value `.default` uses -- never some derived/dimmed variant (e.g. `.opacity(0.5)`), which
    /// would be indistinguishable from a correct implementation by inspection alone if this test only
    /// checked "some inactive-looking color".
    func test_disabledUsesPlainSurfaceVariantWithNoOpacityDerivation() {
        XCTAssertEqual(CategoryChipState.disabled.backgroundColor, .mentoraSurfaceVariant)
        XCTAssertEqual(CategoryChipState.disabled.backgroundColor, CategoryChipState.`default`.backgroundColor,
            "disabled and default must resolve to the literal SAME background color -- no opacity/dimming derivation")
        XCTAssertEqual(CategoryChipState.disabled.foregroundColor, .mentoraTextDisabled)
    }

    /// Catches a copy-paste error across the 4-arm switches. NOTE: `.default` and `.disabled`
    /// deliberately SHARE the same background (see the test above) -- so this only asserts the FULL
    /// (background, foreground) pairs are pairwise distinct, not the backgrounds alone. Like
    /// `BadgeVariantTests`'s equivalent, this depends on `Color`'s `Hashable` discriminating by name --
    /// see that file's guard test.
    func test_allFourStatesMapToDistinctColorPairs() {
        struct Key: Hashable { let background, foreground: Color }
        let keys = CategoryChipState.allCases.map { Key(background: $0.backgroundColor, foreground: $0.foregroundColor) }
        XCTAssertEqual(Set(keys).count, 4, "Two or more states resolved to identical (background, foreground) pairs -- check the switch statements for a copy-paste error.")
    }
}
