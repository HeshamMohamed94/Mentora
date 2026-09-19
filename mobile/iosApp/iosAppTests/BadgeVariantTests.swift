import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 1 -- `Components/Badge.swift`'s `BadgeVariant` color-pair resolver.
// `Color` is `Equatable` in SwiftUI, so these are plain, fast, no-rendering-required assertions --
// same pure-function-testing precedent as `MentoraTypographyRules`/`MentoraShape`.
final class BadgeVariantTests: XCTestCase {

    /// GUARD, run first: every assertion below compares two SEPARATELY-CONSTRUCTED `Color` values
    /// (`Color.mentoraSuccessContainer` etc. are computed `static var`s, each returning a fresh
    /// `Color("...")` instance -- `Theme/Color+Mentora.swift`). This repo has no prior CI-green
    /// precedent for `Color == Color` (`MentoraElevationTests.swift` deliberately compares resolved
    /// `UIColor` RGBA components instead) -- so this guard proves the assumption this whole file rests
    /// on: named-color equality is asset-NAME-based (as documented for `Color(_:bundle:)`), not
    /// reference/identity-based. If this guard ever fails, every other assertion in this file is
    /// meaningless and must be rewritten to compare resolved `UIColor` components instead (the
    /// `MentoraElevationTests.swift` pattern).
    func test_colorEqualityGuard_isNameBasedNotIdentityBased() {
        XCTAssertEqual(Color.mentoraSurfaceVariant, Color.mentoraSurfaceVariant,
            "Color(\"mentoraSurfaceVariant\") does not equal a second, separately-constructed instance " +
            "of itself -- Color equality on this toolchain is NOT simple name-based equality. Every " +
            "other assertion in this file is invalid until rewritten against resolved UIColor components.")
        XCTAssertNotEqual(Color.mentoraSurfaceVariant, Color.mentoraTextSecondary,
            "Two DIFFERENT named colors compared equal -- Color equality on this toolchain is not " +
            "discriminating by name either. Every other assertion in this file is invalid.")
    }

    func test_allSixVariantsExist() {
        XCTAssertEqual(BadgeVariant.allCases.count, 6)
    }

    /// Neutral is the one variant with no dedicated `color.*.container` token of its own --
    /// `COMPONENTS.md § Badge`'s explicit Neutral row maps it to `color.surface.variant` /
    /// `color.text.secondary` instead of a `success`/`warning`/`error`/`info`/`brand`-shaped pair.
    func test_neutralMapsToSurfaceVariantAndTextSecondary() {
        XCTAssertEqual(BadgeVariant.neutral.containerColor, .mentoraSurfaceVariant)
        XCTAssertEqual(BadgeVariant.neutral.onContainerColor, .mentoraTextSecondary)
    }

    func test_successMapsToSuccessContainerPair() {
        XCTAssertEqual(BadgeVariant.success.containerColor, .mentoraSuccessContainer)
        XCTAssertEqual(BadgeVariant.success.onContainerColor, .mentoraSuccessOnSuccessContainer)
    }

    func test_warningMapsToWarningContainerPair() {
        XCTAssertEqual(BadgeVariant.warning.containerColor, .mentoraWarningContainer)
        XCTAssertEqual(BadgeVariant.warning.onContainerColor, .mentoraWarningOnWarningContainer)
    }

    func test_errorMapsToErrorContainerPair() {
        XCTAssertEqual(BadgeVariant.error.containerColor, .mentoraErrorContainer)
        XCTAssertEqual(BadgeVariant.error.onContainerColor, .mentoraErrorOnErrorContainer)
    }

    func test_infoMapsToInfoContainerPair() {
        XCTAssertEqual(BadgeVariant.info.containerColor, .mentoraInfoContainer)
        XCTAssertEqual(BadgeVariant.info.onContainerColor, .mentoraInfoOnInfoContainer)
    }

    func test_brandMapsToBrandPrimaryContainerPair() {
        XCTAssertEqual(BadgeVariant.brand.containerColor, .mentoraBrandPrimaryContainer)
        XCTAssertEqual(BadgeVariant.brand.onContainerColor, .mentoraBrandOnPrimaryContainer)
    }

    /// Catches a copy-paste error across the 6-arm switch -- every variant's (container, onContainer)
    /// pair must be pairwise distinct, mirroring `MentoraTypographyTests.swift`'s
    /// `test_eachStyleMapsToDistinctMetrics` precedent. This assertion's power depends entirely on
    /// `Color`'s `Hashable` conformance discriminating by name (the same assumption
    /// `test_colorEqualityGuard_isNameBasedNotIdentityBased` checks for `Equatable`) -- if `Hashable`
    /// were identity-based instead, all 6 keys would already be distinct by construction and this
    /// would pass vacuously without proving anything about the switch statements themselves.
    func test_allSixVariantsMapToDistinctColorPairs() {
        struct Key: Hashable { let container, onContainer: Color }
        let keys = BadgeVariant.allCases.map { Key(container: $0.containerColor, onContainer: $0.onContainerColor) }
        XCTAssertEqual(Set(keys).count, 6, "Two or more variants resolved to identical color pairs -- check the switch statements for a copy-paste error.")
    }
}
