import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T8 slice 2 -- `Components/MentoraButton.swift`'s `MentoraButtonVariant.colorSet(for:
// colorScheme:)` state-table resolver, per `design-system/COMPONENTS.md § Buttons`'s per-variant state
// tables (lines 63-111). `Color` equality here rests on `BadgeVariantTests.swift`'s
// `test_colorEqualityGuard_isNameBasedNotIdentityBased` (T8 slice 1, already proven real on this exact
// CI toolchain) -- not re-guarded in this file.
final class MentoraButtonVariantTests: XCTestCase {

    // MARK: - Primary

    func test_primary_default_isBrandPrimaryOnPrimaryNoBorder() {
        let colors = MentoraButtonVariant.primary.colorSet(for: .`default`, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimary)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimary)
        XCTAssertNil(colors.border)
        XCTAssertNil(colors.stateLayer)
    }

    func test_primary_hover_usesDedicatedHoverToken() {
        let colors = MentoraButtonVariant.primary.colorSet(for: .hover, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimaryHover)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimary)
    }

    func test_primary_pressed_usesDedicatedPressedToken() {
        let colors = MentoraButtonVariant.primary.colorSet(for: .pressed, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimaryPressed)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimary)
    }

    /// Focused keeps Default's background/text and ADDS a `border.width.focus`/`color.border.focus`
    /// ring, offset (not inline) since Primary has no resting border to swap in place.
    func test_primary_focused_sameColorsAsDefaultPlusOffsetFocusRing() throws {
        let colors = MentoraButtonVariant.primary.colorSet(for: .focused, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimary)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimary)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBorderFocus)
        XCTAssertEqual(border.width, MentoraBorderWidth.focus)
        XCTAssertGreaterThan(border.offset, 0, "Primary's focus ring must be OFFSET, not inline.")
    }

    /// "`color.brand.primary` @ `state.disabledContainerOpacity` over `surface.default`" -- modeled as
    /// a `surface.default` base with a translucent `brand.primary` state layer on top (see
    /// `MentoraButton.swift`'s header "STATE-LAYER RENDERING" for why this is the real composite).
    func test_primary_disabled_isBrandPrimaryOverSurfaceDefaultWithDisabledText() throws {
        let colors = MentoraButtonVariant.primary.colorSet(for: .disabled, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraSurfaceDefault)
        let layer = try XCTUnwrap(colors.stateLayer)
        XCTAssertEqual(layer.color, .mentoraBrandPrimary)
        XCTAssertEqual(layer.opacity, MentoraStateOpacityLight.disabledContainerOpacity)
        XCTAssertEqual(colors.content, .mentoraTextDisabled)
        XCTAssertNil(colors.border)
    }

    func test_primary_disabled_usesDarkOpacityUnderDarkColorScheme() throws {
        let colors = MentoraButtonVariant.primary.colorSet(for: .disabled, colorScheme: .dark)
        let layer = try XCTUnwrap(colors.stateLayer)
        XCTAssertEqual(layer.opacity, MentoraStateOpacityDark.disabledContainerOpacity)
    }

    // MARK: - Secondary (outlined)

    func test_secondary_default_isTransparentWithBrandPrimaryBorderAndText() throws {
        let colors = MentoraButtonVariant.secondary.colorSet(for: .`default`, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraBrandPrimary)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBrandPrimary)
        XCTAssertEqual(border.width, MentoraBorderWidth.`default`)
        XCTAssertEqual(border.offset, 0, "Secondary's resting border is INLINE.")
    }

    func test_secondary_hover_addsBrandPrimaryStateLayerAtHoverOpacity() throws {
        let colors = MentoraButtonVariant.secondary.colorSet(for: .hover, colorScheme: .light)
        let layer = try XCTUnwrap(colors.stateLayer)
        XCTAssertEqual(layer.color, .mentoraBrandPrimary)
        XCTAssertEqual(layer.opacity, MentoraStateOpacityLight.hoverOpacity)
        XCTAssertNotNil(colors.border, "Border stays visible while hovering.")
    }

    func test_secondary_pressed_addsBrandPrimaryStateLayerAtPressedOpacity() throws {
        let colors = MentoraButtonVariant.secondary.colorSet(for: .pressed, colorScheme: .light)
        let layer = try XCTUnwrap(colors.stateLayer)
        XCTAssertEqual(layer.color, .mentoraBrandPrimary)
        XCTAssertEqual(layer.opacity, MentoraStateOpacityLight.pressedOpacity)
    }

    /// Focused REPLACES the resting inline border with a thicker, focus-colored one IN PLACE (offset
    /// 0) -- unlike Primary/Tonal/Text, Secondary never gets a second, offset ring.
    func test_secondary_focused_replacesBorderInPlaceWithFocusColorAndWidth() throws {
        let colors = MentoraButtonVariant.secondary.colorSet(for: .focused, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraBrandPrimary)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBorderFocus)
        XCTAssertEqual(border.width, MentoraBorderWidth.focus)
        XCTAssertEqual(border.offset, 0, "Secondary's focus border replaces the resting border INLINE, never an offset ring.")
    }

    func test_secondary_disabled_borderDrawsAsBorderDefaultWithDisabledText() throws {
        let colors = MentoraButtonVariant.secondary.colorSet(for: .disabled, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraTextDisabled)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBorderDefault)
    }

    // MARK: - Tonal (filled container)

    func test_tonal_default_isPrimaryContainerOnPrimaryContainer() {
        let colors = MentoraButtonVariant.tonal.colorSet(for: .`default`, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimaryContainer)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimaryContainer)
        XCTAssertNil(colors.border)
    }

    func test_tonal_hoverAndPressed_addOnPrimaryContainerStateLayer() throws {
        let hover = MentoraButtonVariant.tonal.colorSet(for: .hover, colorScheme: .light)
        let hoverLayer = try XCTUnwrap(hover.stateLayer)
        XCTAssertEqual(hoverLayer.color, .mentoraBrandOnPrimaryContainer)
        XCTAssertEqual(hoverLayer.opacity, MentoraStateOpacityLight.hoverOpacity)

        let pressed = MentoraButtonVariant.tonal.colorSet(for: .pressed, colorScheme: .light)
        let pressedLayer = try XCTUnwrap(pressed.stateLayer)
        XCTAssertEqual(pressedLayer.color, .mentoraBrandOnPrimaryContainer)
        XCTAssertEqual(pressedLayer.opacity, MentoraStateOpacityLight.pressedOpacity)
    }

    func test_tonal_focused_sameColorsAsDefaultPlusOffsetFocusRing() throws {
        let colors = MentoraButtonVariant.tonal.colorSet(for: .focused, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraBrandPrimaryContainer)
        XCTAssertEqual(colors.content, .mentoraBrandOnPrimaryContainer)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBorderFocus)
        XCTAssertGreaterThan(border.offset, 0)
    }

    func test_tonal_disabled_isSurfaceVariantWithDisabledText() {
        let colors = MentoraButtonVariant.tonal.colorSet(for: .disabled, colorScheme: .light)
        XCTAssertEqual(colors.base, .mentoraSurfaceVariant)
        XCTAssertEqual(colors.content, .mentoraTextDisabled)
        XCTAssertNil(colors.border)
    }

    // MARK: - Text (no fill)

    func test_text_default_isTransparentWithBrandPrimaryTextNoBorder() {
        let colors = MentoraButtonVariant.text.colorSet(for: .`default`, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraBrandPrimary)
        XCTAssertNil(colors.border)
    }

    func test_text_hoverAndPressed_addBrandPrimaryStateLayer() throws {
        let hover = MentoraButtonVariant.text.colorSet(for: .hover, colorScheme: .light)
        XCTAssertEqual(try XCTUnwrap(hover.stateLayer).opacity, MentoraStateOpacityLight.hoverOpacity)

        let pressed = MentoraButtonVariant.text.colorSet(for: .pressed, colorScheme: .light)
        XCTAssertEqual(try XCTUnwrap(pressed.stateLayer).opacity, MentoraStateOpacityLight.pressedOpacity)
    }

    func test_text_focused_addsOffsetFocusRing() throws {
        let colors = MentoraButtonVariant.text.colorSet(for: .focused, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraBrandPrimary)
        let border = try XCTUnwrap(colors.border)
        XCTAssertEqual(border.color, .mentoraBorderFocus)
        XCTAssertGreaterThan(border.offset, 0)
    }

    func test_text_disabled_isTransparentWithDisabledText() {
        let colors = MentoraButtonVariant.text.colorSet(for: .disabled, colorScheme: .light)
        XCTAssertEqual(colors.base, .clear)
        XCTAssertEqual(colors.content, .mentoraTextDisabled)
        XCTAssertNil(colors.border)
    }
}
