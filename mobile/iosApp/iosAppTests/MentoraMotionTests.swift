import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 1 -- drift guard for `Theme/MentoraMotion.swift`'s hand-authored motion
// tokens, the SAME purpose Android's `MentoraTokensDriftTest.kt` serves for its own generated token
// file (read directly to mirror its assertion STYLE -- transcribed literal expected values, not a
// runtime JSON parse; this repo's own iOS precedent, e.g. `MentoraShapeTests.swift`/
// `MentoraElevationTests.swift`, already established "transcribe the real design-tokens.json values
// directly into the test" as the house style for a drift guard on THIS platform, rather than reading
// the JSON file from disk at test time the way the Android/JVM test does).
//
// Values transcribed directly from `design-system/design-tokens.json#/motion` (confirmed by reading
// that JSON section directly, not merely restated from Android's Kotlin transcription):
//   duration: { fast: 150, normal: 200, slow: 300 }  (milliseconds)
//   easing.standard   = cubic-bezier(0.4, 0.0, 0.2, 1)
//   easing.decelerate = cubic-bezier(0.0, 0.0, 0.2, 1)
//   easing.accelerate = cubic-bezier(0.4, 0.0, 1,   1)
final class MentoraMotionTests: XCTestCase {

    // MARK: - Duration (converted ms -> seconds)

    func test_durationMatchesDesignTokens() {
        XCTAssertEqual(MentoraMotionDuration.fast, 0.15, accuracy: 0.0001)
        XCTAssertEqual(MentoraMotionDuration.normal, 0.2, accuracy: 0.0001)
        XCTAssertEqual(MentoraMotionDuration.slow, 0.3, accuracy: 0.0001)
    }

    // MARK: - Easing curve control points

    func test_standardEasingMatchesDesignTokens() {
        let c = MentoraMotionEasing.standard
        XCTAssertEqual(c, MentoraCubicBezier(x1: 0.4, y1: 0.0, x2: 0.2, y2: 1.0))
    }

    func test_decelerateEasingMatchesDesignTokens() {
        let c = MentoraMotionEasing.decelerate
        XCTAssertEqual(c, MentoraCubicBezier(x1: 0.0, y1: 0.0, x2: 0.2, y2: 1.0))
    }

    func test_accelerateEasingMatchesDesignTokens() {
        let c = MentoraMotionEasing.accelerate
        XCTAssertEqual(c, MentoraCubicBezier(x1: 0.4, y1: 0.0, x2: 1.0, y2: 1.0))
    }

    /// The three curves must be pairwise distinct -- catches a copy-paste error across the three
    /// `static let`s (the same class of regression `MentoraTypographyTests.swift`'s
    /// `test_eachStyleMapsToDistinctMetrics` guards against for the typography table).
    func test_threeCurvesArePairwiseDistinct() {
        let curves = [MentoraMotionEasing.standard, MentoraMotionEasing.decelerate, MentoraMotionEasing.accelerate]
        XCTAssertEqual(Set(curves.map { "\($0.x1),\($0.y1),\($0.x2),\($0.y2)" }).count, 3)
    }

    // MARK: - Factory functions compose without crashing (smoke test)

    /// `Animation` is not a stable, publicly-inspectable API surface (see `MentoraMotion.swift`'s own
    /// header comment), so this only proves the factory functions construct a value at all -- the real
    /// assertions are the raw-number tests above.
    func test_animationFactoriesProduceAValue() {
        _ = MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.fast)
        _ = MentoraMotionEasing.animation(MentoraMotionEasing.decelerate, duration: MentoraMotionDuration.normal)
        _ = MentoraMotionEasing.animation(MentoraMotionEasing.accelerate, duration: MentoraMotionDuration.fast)
        _ = MentoraMotionEasing.linear(duration: MentoraMotionDuration.normal)
    }
}
