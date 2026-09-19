import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 4 -- `Components/MentoraToggle.swift`'s pure metrics/state resolver
// (`MentoraToggleMetrics`/`MentoraToggleRules`). No rendering required. `Color` equality here rests on
// `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1, already
// proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraToggleTests: XCTestCase {

    // MARK: - Metrics (COMPONENTS.md § Toggle / Switch's property table)

    func test_trackDimsAreSpace10BySpace6() {
        XCTAssertEqual(MentoraToggleMetrics.trackWidth, MentoraSpacing.space10)
        XCTAssertEqual(MentoraToggleMetrics.trackWidth, 40)
        XCTAssertEqual(MentoraToggleMetrics.trackHeight, MentoraSpacing.space6)
        XCTAssertEqual(MentoraToggleMetrics.trackHeight, 24)
    }

    func test_thumbSizeIsSpace5() {
        XCTAssertEqual(MentoraToggleMetrics.thumbSize, MentoraSpacing.space5)
        XCTAssertEqual(MentoraToggleMetrics.thumbSize, 20)
    }

    func test_thumbInsetEqualsFocusBorderWidth() {
        XCTAssertEqual(MentoraToggleMetrics.thumbInset, MentoraBorderWidth.focus)
        XCTAssertEqual(MentoraToggleMetrics.thumbInset, 2)
    }

    func test_thumbTravelIsSpace4() {
        XCTAssertEqual(MentoraToggleMetrics.thumbTravel, MentoraSpacing.space4)
        XCTAssertEqual(MentoraToggleMetrics.thumbTravel, 16)
    }

    func test_labelGapIsSpace2() {
        XCTAssertEqual(MentoraToggleMetrics.labelGap, MentoraSpacing.space2)
    }

    func test_hitAreaMinimumMatchesSystemWideTouchTarget() {
        XCTAssertEqual(MentoraToggleMetrics.hitAreaMinimum, MentoraTouchTarget.iosPt)
    }

    /// Internal consistency guard (see `MentoraToggleMetrics.thumbTravel`'s own doc comment): the stated
    /// 40/24/20/2/16 numbers must actually agree with each other geometrically, not just each
    /// individually match its own named token.
    func test_thumbTravelGeometryIsInternallyConsistentWithTrackAndThumbAndInset() {
        let expectedTravel = MentoraToggleMetrics.trackWidth - MentoraToggleMetrics.thumbSize - (2 * MentoraToggleMetrics.thumbInset)
        XCTAssertEqual(MentoraToggleMetrics.thumbTravel, expectedTravel)
    }

    // MARK: - colorSet: On / Off (enabled, not focused)

    func test_colorSet_on_enabled_notFocused_usesBrandPrimaryTrack() {
        let colors = MentoraToggleRules.colorSet(isOn: true, isEnabled: true, isFocused: false, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBrandPrimary)
        XCTAssertEqual(colors.trackOpacity, 1.0)
        XCTAssertEqual(colors.thumbColor, .mentoraSurfaceDefault)
        XCTAssertTrue(colors.thumbHasElevation)
        XCTAssertNil(colors.focusRingColor)
    }

    func test_colorSet_off_enabled_notFocused_usesBorderStrongTrack() {
        let colors = MentoraToggleRules.colorSet(isOn: false, isEnabled: true, isFocused: false, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBorderStrong)
        XCTAssertEqual(colors.trackOpacity, 1.0)
        XCTAssertEqual(colors.thumbColor, .mentoraSurfaceDefault)
        XCTAssertTrue(colors.thumbHasElevation)
        XCTAssertNil(colors.focusRingColor)
    }

    // MARK: - colorSet: Focused (COMPONENTS.md: "as above + color.border.focus outline")

    func test_colorSet_focused_on_keepsBrandPrimaryTrackAndAddsFocusRing() {
        let colors = MentoraToggleRules.colorSet(isOn: true, isEnabled: true, isFocused: true, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBrandPrimary)
        XCTAssertEqual(colors.focusRingColor, .mentoraBorderFocus)
    }

    func test_colorSet_focused_off_keepsBorderStrongTrackAndAddsFocusRing() {
        let colors = MentoraToggleRules.colorSet(isOn: false, isEnabled: true, isFocused: true, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBorderStrong)
        XCTAssertEqual(colors.focusRingColor, .mentoraBorderFocus)
    }

    // MARK: - colorSet: Disabled beats everything (on or off, even if isFocused is somehow also true)

    func test_colorSet_disabled_on_usesBorderDefaultTrackAtDisabledContainerOpacity_noFocusRingEvenIfFocusedTrue() {
        let colors = MentoraToggleRules.colorSet(isOn: true, isEnabled: false, isFocused: true, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBorderDefault)
        XCTAssertEqual(colors.trackOpacity, MentoraStateOpacityLight.disabledContainerOpacity)
        XCTAssertEqual(colors.thumbColor, .mentoraSurfaceDefault)
        XCTAssertFalse(colors.thumbHasElevation)
        XCTAssertNil(colors.focusRingColor)
    }

    func test_colorSet_disabled_off_usesBorderDefaultTrackAtDisabledContainerOpacity() {
        let colors = MentoraToggleRules.colorSet(isOn: false, isEnabled: false, isFocused: false, colorScheme: .light)
        XCTAssertEqual(colors.trackColor, .mentoraBorderDefault)
        XCTAssertEqual(colors.trackOpacity, MentoraStateOpacityLight.disabledContainerOpacity)
    }

    /// Light vs. dark `disabledContainerOpacity` genuinely differ (`Theme/MentoraTokens.swift`) -- catches
    /// a copy-paste that accidentally always reads the light table.
    func test_colorSet_disabled_disabledContainerOpacityDiffersBetweenLightAndDark() {
        let light = MentoraToggleRules.colorSet(isOn: true, isEnabled: false, isFocused: false, colorScheme: .light)
        let dark = MentoraToggleRules.colorSet(isOn: true, isEnabled: false, isFocused: false, colorScheme: .dark)
        XCTAssertEqual(light.trackOpacity, MentoraStateOpacityLight.disabledContainerOpacity)
        XCTAssertEqual(dark.trackOpacity, MentoraStateOpacityDark.disabledContainerOpacity)
        XCTAssertNotEqual(light.trackOpacity, dark.trackOpacity)
    }

    // MARK: - Copy-paste guard across the 4 live (non-disabled) branches

    /// `disabled` and `off` share the SAME thumb color/elevation-false-vs-true distinction; keying on the
    /// full (track, opacity, thumb, elevation, ring-present) tuple catches a genuine branch collision
    /// without keying on a single field that legitimately repeats across branches, same technique
    /// `MentoraTextFieldRulesTests.swift`'s own `test_priorityBranches_mapToDistinctColorSets` uses.
    func test_liveStateBranches_mapToDistinctColorSets() {
        struct Key: Hashable {
            let track: Color
            let opacity: Double
            let thumb: Color
            let elevation: Bool
            let ringPresent: Bool
        }
        let on = MentoraToggleRules.colorSet(isOn: true, isEnabled: true, isFocused: false, colorScheme: .light)
        let off = MentoraToggleRules.colorSet(isOn: false, isEnabled: true, isFocused: false, colorScheme: .light)
        let focusedOn = MentoraToggleRules.colorSet(isOn: true, isEnabled: true, isFocused: true, colorScheme: .light)
        let focusedOff = MentoraToggleRules.colorSet(isOn: false, isEnabled: true, isFocused: true, colorScheme: .light)

        let keys = [on, off, focusedOn, focusedOff].map {
            Key(track: $0.trackColor, opacity: $0.trackOpacity, thumb: $0.thumbColor, elevation: $0.thumbHasElevation, ringPresent: $0.focusRingColor != nil)
        }
        XCTAssertEqual(Set(keys).count, 4, "Two or more of the 4 live (on/off x focused/not) branches resolved to the same color set.")
    }
}
