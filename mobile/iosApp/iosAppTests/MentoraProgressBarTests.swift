import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 6 -- `Components/MentoraProgressBar.swift`'s pure metrics/state/clamp resolver
// (`MentoraProgressBarMetrics`/`MentoraProgressBarRules`). No rendering required. `Color` equality here
// rests on `BadgeVariantTests.swift`'s `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1,
// already proven real on this exact CI toolchain) -- not re-guarded in this file.
final class MentoraProgressBarTests: XCTestCase {

    // MARK: - Metrics (COMPONENTS.md § ProgressBar's property table)

    func test_heightIs8() {
        XCTAssertEqual(MentoraProgressBarMetrics.height, 8)
    }

    func test_shapeIsFull() {
        XCTAssertEqual(MentoraProgressBarMetrics.shape, MentoraShape.full)
    }

    func test_indeterminateSegmentFractionIsWithinZeroToOne() {
        XCTAssertGreaterThan(MentoraProgressBarMetrics.indeterminateSegmentWidthFraction, 0)
        XCTAssertLessThan(MentoraProgressBarMetrics.indeterminateSegmentWidthFraction, 1)
    }

    func test_indeterminateSweepDurationIsPositive() {
        XCTAssertGreaterThan(MentoraProgressBarMetrics.indeterminateSweepDuration, 0)
    }

    // MARK: - colorSet (COMPONENTS.md's per-state Fill color column)

    func test_colorSet_active_usesBrandPrimary() {
        XCTAssertEqual(MentoraProgressBarRules.colorSet(for: .active).fillColor, .mentoraBrandPrimary)
    }

    func test_colorSet_complete_usesSuccessDefault() {
        XCTAssertEqual(MentoraProgressBarRules.colorSet(for: .complete).fillColor, .mentoraSuccessDefault)
    }

    func test_colorSet_paused_usesTextDisabled() {
        XCTAssertEqual(MentoraProgressBarRules.colorSet(for: .paused).fillColor, .mentoraTextDisabled)
    }

    /// The 3 states must map to distinct fill colors -- catches a copy-paste branch collision.
    func test_allStates_mapToDistinctFillColors() {
        let colors = MentoraProgressBarState.allCases.map { MentoraProgressBarRules.colorSet(for: $0).fillColor }
        XCTAssertEqual(Set(colors).count, MentoraProgressBarState.allCases.count)
    }

    // MARK: - clampedProgress

    func test_clampedProgress_withinRange_isUnchanged() {
        XCTAssertEqual(MentoraProgressBarRules.clampedProgress(0.0), 0.0)
        XCTAssertEqual(MentoraProgressBarRules.clampedProgress(0.5), 0.5)
        XCTAssertEqual(MentoraProgressBarRules.clampedProgress(1.0), 1.0)
    }

    func test_clampedProgress_belowZero_clampsToZero() {
        XCTAssertEqual(MentoraProgressBarRules.clampedProgress(-0.3), 0.0)
    }

    func test_clampedProgress_aboveOne_clampsToOne() {
        XCTAssertEqual(MentoraProgressBarRules.clampedProgress(1.7), 1.0)
    }
}
