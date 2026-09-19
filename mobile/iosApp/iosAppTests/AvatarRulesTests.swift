import XCTest
@testable import iosApp

// Phase 5 Task T8 slice 1 -- `Components/Avatar.swift`'s `AvatarRules` pure logic: initials
// derivation, status-dot diameter, and status-dot ring width.
final class AvatarRulesTests: XCTestCase {

    // MARK: - Initials derivation

    func test_multiWordEnglishNameUsesFirstAndLastInitials() {
        XCTAssertEqual(AvatarRules.initials(from: "Sara Ahmed"), "SA")
    }

    func test_threeWordEnglishNameUsesFirstAndLastWordOnly() {
        XCTAssertEqual(AvatarRules.initials(from: "John Michael Smith"), "JS")
    }

    func test_singleWordNameUsesOnlyOneInitial() {
        XCTAssertEqual(AvatarRules.initials(from: "Cher"), "C")
    }

    /// Arabic script has no case distinction -- `.uppercased()` must be a safe no-op on an Arabic
    /// letter (it must not crash, corrupt, or drop the character).
    func test_arabicNameProducesTwoArabicInitials() {
        XCTAssertEqual(AvatarRules.initials(from: "سارة أحمد"), "سأ")
    }

    func test_singleWordArabicNameProducesOneInitial() {
        XCTAssertEqual(AvatarRules.initials(from: "سارة"), "س")
    }

    func test_emptyStringProducesPlaceholder() {
        XCTAssertEqual(AvatarRules.initials(from: ""), "?")
    }

    func test_whitespaceOnlyStringProducesPlaceholder() {
        XCTAssertEqual(AvatarRules.initials(from: "   "), "?")
    }

    /// Extra internal whitespace (multiple spaces, tabs) between words must not break word-splitting.
    func test_extraWhitespaceBetweenWordsIsHandled() {
        XCTAssertEqual(AvatarRules.initials(from: "  Sara   Ahmed  "), "SA")
    }

    // MARK: - Status dot geometry

    func test_statusDotDiameterIsExactlyTwentyFivePercentForAllSizes() {
        XCTAssertEqual(AvatarRules.statusDotDiameter(for: .small), 24 * 0.25, accuracy: 0.0001)
        XCTAssertEqual(AvatarRules.statusDotDiameter(for: .medium), 40 * 0.25, accuracy: 0.0001)
        XCTAssertEqual(AvatarRules.statusDotDiameter(for: .large), 64 * 0.25, accuracy: 0.0001)
        XCTAssertEqual(AvatarRules.statusDotDiameter(for: .xlarge), 96 * 0.25, accuracy: 0.0001)
    }

    func test_statusDotRingWidthMatchesBorderWidthDefault() {
        XCTAssertEqual(AvatarRules.statusDotRingWidth, MentoraBorderWidth.`default`)
    }

    // MARK: - Avatar size diameters (drift guard -- design-tokens.json#/avatar)

    func test_avatarDiametersMatchDesignTokens() {
        XCTAssertEqual(MentoraAvatarSize.small.diameter, 24)
        XCTAssertEqual(MentoraAvatarSize.medium.diameter, 40)
        XCTAssertEqual(MentoraAvatarSize.large.diameter, 64)
        XCTAssertEqual(MentoraAvatarSize.xlarge.diameter, 96)
    }

    // MARK: - Label scale (relative to the .medium=40pt documented baseline)

    func test_labelScaleIsOneAtMediumBaseline() {
        XCTAssertEqual(AvatarRules.labelScale(for: .medium), 1.0, accuracy: 0.0001)
    }

    func test_labelScaleIsProportionalToDiameter() {
        XCTAssertEqual(AvatarRules.labelScale(for: .small), 24.0 / 40.0, accuracy: 0.0001)
        XCTAssertEqual(AvatarRules.labelScale(for: .large), 64.0 / 40.0, accuracy: 0.0001)
        XCTAssertEqual(AvatarRules.labelScale(for: .xlarge), 96.0 / 40.0, accuracy: 0.0001)
    }
}
