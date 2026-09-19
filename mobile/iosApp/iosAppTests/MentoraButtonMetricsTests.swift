import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 2 -- `Components/MentoraButton.swift`'s pure metrics
// (`MentoraButtonVariant`'s dims, `MentoraButtonMetrics`'s shared constants). No rendering required.
// `Color` equality assertions elsewhere in this slice's test files rest on `BadgeVariantTests.swift`'s
// `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1, already CI-green on this exact
// toolchain) -- not re-guarded here.
final class MentoraButtonMetricsTests: XCTestCase {

    // MARK: - Per-variant dims (COMPONENTS.md § Buttons)

    func test_heightIs48ForPrimarySecondaryTonalAnd40ForText() {
        XCTAssertEqual(MentoraButtonVariant.primary.height, 48)
        XCTAssertEqual(MentoraButtonVariant.secondary.height, 48)
        XCTAssertEqual(MentoraButtonVariant.tonal.height, 48)
        XCTAssertEqual(MentoraButtonVariant.text.height, 40)
    }

    func test_horizontalPaddingIsSpace6ForPrimarySecondaryTonalAndSpace3ForText() {
        XCTAssertEqual(MentoraButtonVariant.primary.horizontalPadding, MentoraSpacing.space6)
        XCTAssertEqual(MentoraButtonVariant.secondary.horizontalPadding, MentoraSpacing.space6)
        XCTAssertEqual(MentoraButtonVariant.tonal.horizontalPadding, MentoraSpacing.space6)
        XCTAssertEqual(MentoraButtonVariant.text.horizontalPadding, MentoraSpacing.space3)
    }

    func test_minWidthIs88ForThePrimarySecondaryTonalH48Variants() {
        XCTAssertEqual(MentoraButtonVariant.primary.minWidth, 88)
        XCTAssertEqual(MentoraButtonVariant.secondary.minWidth, 88)
        XCTAssertEqual(MentoraButtonVariant.tonal.minWidth, 88)
    }

    /// TextButton states no minimum in `COMPONENTS.md` -- `0` means "no minimum applied", not a token.
    func test_textVariantHasNoMinWidth() {
        XCTAssertEqual(MentoraButtonVariant.text.minWidth, 0)
    }

    // MARK: - Shared constants (MentoraButtonMetrics)

    func test_iconSizeIs20AndGapIsSpace2() {
        XCTAssertEqual(MentoraButtonMetrics.iconSize, 20)
        XCTAssertEqual(MentoraButtonMetrics.iconSize, MentoraIconSize.medium)
        XCTAssertEqual(MentoraButtonMetrics.iconGap, MentoraSpacing.space2)
    }

    /// Every variant's hit area must be at least `MentoraTouchTarget.iosPt` (44) REGARDLESS of visual
    /// height -- `MentoraButtonMetrics.hitAreaMinimum` is one shared, non-variant-specific constant (not
    /// derived from `variant.height`), so this asserts it independently for all 4 variants rather than
    /// assuming the constant can never accidentally be made variant-dependent later.
    func test_hitAreaMinimumIs44ForAllFourVariantsRegardlessOfVisualHeight() {
        for variant in MentoraButtonVariant.allCases {
            XCTAssertEqual(MentoraButtonMetrics.hitAreaMinimum, MentoraTouchTarget.iosPt,
                "Variant \(variant) must resolve the same 44pt hit-area minimum regardless of its \(variant.height)pt visual height.")
        }
    }

    func test_focusRingOffsetEqualsFocusBorderWidth() {
        XCTAssertEqual(MentoraButtonMetrics.focusRingOffset, MentoraBorderWidth.focus)
    }

    // MARK: - State opacities (light vs. dark split)

    /// `Theme/MentoraTokens.swift`'s `MentoraStateOpacityLight`/`Dark`: hover is identical (0.08) in
    /// both themes, but pressed/disabledContainer genuinely differ -- asserting BOTH the equal and the
    /// differing fields catches a copy-paste that accidentally always reads the light table.
    func test_stateOpacitiesDifferBetweenLightAndDarkExceptHover() {
        let light = MentoraButtonMetrics.stateOpacities(for: .light)
        let dark = MentoraButtonMetrics.stateOpacities(for: .dark)

        XCTAssertEqual(light.hoverOpacity, MentoraStateOpacityLight.hoverOpacity)
        XCTAssertEqual(dark.hoverOpacity, MentoraStateOpacityDark.hoverOpacity)
        XCTAssertEqual(light.hoverOpacity, dark.hoverOpacity, "hoverOpacity is 0.08 in both themes.")

        XCTAssertEqual(light.pressedOpacity, MentoraStateOpacityLight.pressedOpacity)
        XCTAssertEqual(dark.pressedOpacity, MentoraStateOpacityDark.pressedOpacity)
        XCTAssertNotEqual(light.pressedOpacity, dark.pressedOpacity, "pressedOpacity must differ (0.12 light vs 0.16 dark).")

        XCTAssertEqual(light.disabledContainerOpacity, MentoraStateOpacityLight.disabledContainerOpacity)
        XCTAssertEqual(dark.disabledContainerOpacity, MentoraStateOpacityDark.disabledContainerOpacity)
        XCTAssertNotEqual(light.disabledContainerOpacity, dark.disabledContainerOpacity, "disabledContainerOpacity must differ (0.12 light vs 0.16 dark).")
    }

    // MARK: - isInteractive (loading-implies-non-interactive)

    /// Mirrors Android's `interactive = enabled && !loading` split (`MentoraButton.kt`) -- loading must
    /// block interaction even though its COLORS equal `.default`'s (a separate, view-composition
    /// concern, not asserted here).
    func test_isInteractive_enabledAndNotLoading_isTrue() {
        XCTAssertTrue(MentoraButtonMetrics.isInteractive(isEnabled: true, isLoading: false))
    }

    func test_isInteractive_loadingImpliesNonInteractiveEvenWhenEnabled() {
        XCTAssertFalse(MentoraButtonMetrics.isInteractive(isEnabled: true, isLoading: true))
    }

    func test_isInteractive_disabledIsNonInteractiveRegardlessOfLoading() {
        XCTAssertFalse(MentoraButtonMetrics.isInteractive(isEnabled: false, isLoading: false))
        XCTAssertFalse(MentoraButtonMetrics.isInteractive(isEnabled: false, isLoading: true))
    }

    // MARK: - Interaction-state priority resolution

    func test_resolve_disabledBeatsEverything() {
        XCTAssertEqual(MentoraButtonInteractionState.resolve(isEnabled: false, isPressed: true, isFocused: true), .disabled)
    }

    func test_resolve_pressedBeatsFocused() {
        XCTAssertEqual(MentoraButtonInteractionState.resolve(isEnabled: true, isPressed: true, isFocused: true), .pressed)
    }

    func test_resolve_focusedBeatsDefault() {
        XCTAssertEqual(MentoraButtonInteractionState.resolve(isEnabled: true, isPressed: false, isFocused: true), .focused)
    }

    func test_resolve_defaultWhenNothingElseApplies() {
        XCTAssertEqual(MentoraButtonInteractionState.resolve(isEnabled: true, isPressed: false, isFocused: false), .`default`)
    }
}
