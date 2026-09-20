import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 3 -- `Components/AITutorBubble.swift`'s pure per-sender resolution logic.
final class AITutorBubbleTests: XCTestCase {

    func test_aiAndUserResolveToDistinctBackgroundColors() {
        XCTAssertNotEqual(AITutorBubbleRules.background(for: .ai), AITutorBubbleRules.background(for: .user))
    }

    func test_aiAndUserResolveToDistinctContentColors() {
        XCTAssertNotEqual(AITutorBubbleRules.contentColor(for: .ai), AITutorBubbleRules.contentColor(for: .user))
    }

    func test_maxWidthFractionIsEightyPercentPerSpec() {
        // `design-system/COMPONENTS.md § AITutorBubble` / `design-to-code/components.json`: 80%,
        // matching Web and every other platform -- see this file's own header for why the mobile
        // screen spec's own 85% reading was rejected.
        XCTAssertEqual(AITutorBubbleRules.maxWidthFraction, 0.8, accuracy: 0.0001)
    }

    // MARK: - Tail-corner shape -- AI's tail is bottom-leading, User's is bottom-trailing

    /// DISCLOSED, DELIBERATELY CONSERVATIVE SCOPE: `UnevenRoundedRectangle`'s four radius arguments are
    /// passed as literal `MentoraRadius.large`/`.small` values at the two real call sites in
    /// `AITutorBubbleRules.shape(for:)` -- correct by direct code inspection -- but this project's own
    /// standing rule (D108/D109/D115: never assert an unconfirmed Apple API surface rather than verify
    /// it against a real artifact) means this test does not introspect `UnevenRoundedRectangle`'s own
    /// stored properties (whether they are even part of its public API was not independently confirmed
    /// against a real toolchain artifact before writing this file). `.path(in:)` IS a guaranteed,
    /// protocol-required `Shape` member, so rendering a path and asserting it is non-empty for both
    /// senders is a zero-guess smoke check that both shapes actually construct and draw something,
    /// without depending on an unverified property surface. The four-radius PLACEMENT itself (which
    /// corner is visually smaller for which sender) is a real, disclosed MC-2/MC-3 live-verification
    /// item, not silently assumed correct.
    func test_bothSenderShapesRenderANonEmptyPath() {
        let rect = CGRect(x: 0, y: 0, width: 200, height: 60)
        XCTAssertFalse(AITutorBubbleRules.shape(for: .ai).path(in: rect).isEmpty)
        XCTAssertFalse(AITutorBubbleRules.shape(for: .user).path(in: rect).isEmpty)
    }
}
