import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T6 slice 3a -- MentoraShape (design-tokens.json#/shape/radius). No `import shared`
// needed -- nothing here touches KMP. Every geometry assertion below is deterministic (a fixed
// CGRect, `Path.contains(_:)` at fixed points) -- no font metrics, no OS-version dependence.
final class MentoraShapeTests: XCTestCase {

    // MARK: - 1. Drift test

    /// Transcribed directly from `design-system/design-tokens.json#/shape/radius`. This IS the
    /// drift test -- if the generated `MentoraTokens.swift#MentoraRadius` ever changes, this fails.
    /// `.sheetTop` deliberately shares `.xlarge`'s value (24) -- asserted explicitly here, not
    /// papered over by a "distinct values" test.
    func test_cornerRadiusMatchesDesignTokens() {
        XCTAssertEqual(MentoraShape.none.cornerRadius, 0)
        XCTAssertEqual(MentoraShape.small.cornerRadius, 8)
        XCTAssertEqual(MentoraShape.medium.cornerRadius, 12)
        XCTAssertEqual(MentoraShape.large.cornerRadius, 16)
        XCTAssertEqual(MentoraShape.xlarge.cornerRadius, 24)
        XCTAssertEqual(MentoraShape.full.cornerRadius, 999)
        XCTAssertEqual(MentoraShape.sheetTop.cornerRadius, MentoraShape.xlarge.cornerRadius,
            ".sheetTop deliberately shares .xlarge's radius (24) -- design-tokens.json's componentUsage.bottomSheet is 'radius.xlarge (24) top corners only', not a distinct value")
    }

    func test_sevenStepsExist() {
        XCTAssertEqual(MentoraShape.Step.allCases.count, 7)
    }

    // MARK: - 2. Top-corners-only geometry (the headline requirement)

    /// `.sheetTop` rounds only the top two corners; the bottom two remain square. Deterministic
    /// point-in-path probes on a fixed 200x200 rect -- no rendering, no font metrics.
    func test_sheetTopRoundsTopCornersOnly() {
        let p = MentoraShape.sheetTop.path(in: CGRect(x: 0, y: 0, width: 200, height: 200))
        XCTAssertFalse(p.contains(CGPoint(x: 1, y: 1)), "top-left should be rounded away")
        XCTAssertFalse(p.contains(CGPoint(x: 199, y: 1)), "top-right should be rounded away")
        XCTAssertTrue(p.contains(CGPoint(x: 1, y: 199)), "bottom-left should be square")
        XCTAssertTrue(p.contains(CGPoint(x: 199, y: 199)), "bottom-right should be square")
    }

    /// `.xlarge` (a plain `RoundedRectangle`) rounds ALL four corners -- proof the dialog-vs-sheet
    /// shape distinction from the test above is real, not an artifact of the probe points chosen.
    func test_xlargeRoundsAllFourCorners() {
        let p = MentoraShape.xlarge.path(in: CGRect(x: 0, y: 0, width: 200, height: 200))
        XCTAssertFalse(p.contains(CGPoint(x: 1, y: 1)), "top-left should be rounded away")
        XCTAssertFalse(p.contains(CGPoint(x: 199, y: 1)), "top-right should be rounded away")
        XCTAssertFalse(p.contains(CGPoint(x: 1, y: 199)), "bottom-left should be rounded away")
        XCTAssertFalse(p.contains(CGPoint(x: 199, y: 199)), "bottom-right should be rounded away")
    }

    /// `.full` on a square rect is a circle/capsule: all four corners excluded, but a center-edge
    /// point (well inside the inscribed circle) is included.
    func test_fullIsACircleOnASquareRect() {
        let p = MentoraShape.full.path(in: CGRect(x: 0, y: 0, width: 200, height: 200))
        XCTAssertFalse(p.contains(CGPoint(x: 1, y: 1)))
        XCTAssertFalse(p.contains(CGPoint(x: 199, y: 1)))
        XCTAssertFalse(p.contains(CGPoint(x: 1, y: 199)))
        XCTAssertFalse(p.contains(CGPoint(x: 199, y: 199)))
        XCTAssertTrue(p.contains(CGPoint(x: 100, y: 1)), "a center-edge point should be inside the inscribed circle")
    }

    /// `.none` is a plain rectangle -- all four corners included.
    func test_noneIsAPlainRectangle() {
        let p = MentoraShape.none.path(in: CGRect(x: 0, y: 0, width: 200, height: 200))
        XCTAssertTrue(p.contains(CGPoint(x: 1, y: 1)))
        XCTAssertTrue(p.contains(CGPoint(x: 199, y: 1)))
        XCTAssertTrue(p.contains(CGPoint(x: 1, y: 199)))
        XCTAssertTrue(p.contains(CGPoint(x: 199, y: 199)))
    }

    // MARK: - 3. inset(by:)

    func test_insetShrinksBoundingRect() {
        let rect = CGRect(x: 0, y: 0, width: 200, height: 100)
        let inset = MentoraShape.large.inset(by: 8)
        let bounding = inset.path(in: rect).boundingRect
        let expected = rect.insetBy(dx: 8, dy: 8)
        XCTAssertEqual(bounding.minX, expected.minX, accuracy: 0.5)
        XCTAssertEqual(bounding.minY, expected.minY, accuracy: 0.5)
        XCTAssertEqual(bounding.width, expected.width, accuracy: 0.5)
        XCTAssertEqual(bounding.height, expected.height, accuracy: 0.5)
    }

    /// An inset larger than the shape's own radius must clamp the effective radius at 0 rather than
    /// crash or produce a degenerate/non-finite path.
    func test_overInsetClampsRadiusWithoutCrashing() {
        let rect = CGRect(x: 0, y: 0, width: 200, height: 100)
        let path = MentoraShape.small.inset(by: 20).path(in: rect)
        let bounding = path.boundingRect
        XCTAssertTrue(bounding.width.isFinite && bounding.height.isFinite)
        XCTAssertGreaterThan(bounding.width, 0)
        XCTAssertGreaterThan(bounding.height, 0)
    }
}
