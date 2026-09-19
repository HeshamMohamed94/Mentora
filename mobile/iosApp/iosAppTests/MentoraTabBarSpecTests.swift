import XCTest
import shared
@testable import iosApp

// Phase 5 Task T9 slice 1 -- `Tab` (`Navigation/Route.swift`) and `MentoraTabBarSpec`
// (`Components/MentoraTabBar.swift`). Covers D1's locked tab order, the real label-key/icon-name mapping,
// and the `MobileBottomNavigation` spec constants -- mirrors `MentoraThemeTests.swift`'s
// `@MainActor final class ... : XCTestCase` shape (an already-established pattern in this test target,
// not a first).
@MainActor
final class MentoraTabBarSpecTests: XCTestCase {

    // MARK: - 1. D1: locked tab order

    func test_tabAllCasesIsLockedOrder() {
        XCTAssertEqual(Tab.allCases, [.home, .explore, .myLearning, .aiTutor, .profile],
            "D1 requires this exact, locked tab order -- Home, Explore, My Learning, AI Tutor, Profile.")
    }

    // MARK: - 2. Real, non-key-echoing label keys + real icon-name table

    /// `MentoraStrings.text(_:locale:)` falls back to the raw key itself when a key is missing/typo'd
    /// (that file's own documented DEBUG-assert fallback) -- asserting the resolved value differs from
    /// the raw key in BOTH locales is a real, non-vacuous proxy for "this key actually exists in
    /// Localizable.xcstrings," not merely "some string came back."
    func test_labelKeysResolveToRealNonEchoingTextInBothLocales() {
        for tab in Tab.allCases {
            let english = MentoraStrings.text(tab.labelKey, locale: .english)
            let arabic = MentoraStrings.text(tab.labelKey, locale: .arabic)
            XCTAssertNotEqual(english, tab.labelKey,
                "\(tab) labelKey \"\(tab.labelKey)\" did not resolve in English -- missing from the catalog?")
            XCTAssertNotEqual(arabic, tab.labelKey,
                "\(tab) labelKey \"\(tab.labelKey)\" did not resolve in Arabic -- missing from the catalog?")
            XCTAssertNotEqual(english, arabic,
                "\(tab) labelKey \"\(tab.labelKey)\" resolved identically in English and Arabic -- " +
                "suspicious for a real, translated navigation label.")
        }
    }

    /// The real navigation icon table -- `.dashboard` for Home (no `.home` case exists on
    /// `MentoraIconName`, confirmed by reading `Theme/MentoraIcon.swift` directly), and the other four
    /// tabs' identically-named `MentoraIconName` cases.
    func test_iconNameMatchesRealNavigationIconTable() {
        XCTAssertEqual(Tab.home.iconName, .dashboard,
            "MentoraIconName has no .home case -- Android's own MobileBottomNavigation.kt maps the Home " +
            "tab to the .dashboard glyph, and this app mirrors that mapping exactly.")
        XCTAssertEqual(Tab.explore.iconName, .explore)
        XCTAssertEqual(Tab.myLearning.iconName, .myLearning)
        XCTAssertEqual(Tab.aiTutor.iconName, .aiTutor)
        XCTAssertEqual(Tab.profile.iconName, .profile)
    }

    func test_accessibilityIdentifiersAreDistinctAndPrefixed() {
        let identifiers = Tab.allCases.map(\.accessibilityIdentifier)
        XCTAssertEqual(Set(identifiers).count, identifiers.count, "accessibilityIdentifier must be unique per tab.")
        for identifier in identifiers {
            XCTAssertTrue(identifier.hasPrefix("tab_"), "\(identifier) must be prefixed \"tab_\" for T22's XCUITests.")
        }
    }

    // MARK: - MentoraTabBarSpec: real `MobileBottomNavigation` numbers (COMPONENTS.md)

    func test_specConstantsMatchComponentsMdMobileBottomNavigation() {
        XCTAssertEqual(MentoraTabBarSpec.height, 64,
            "COMPONENTS.md § MobileBottomNavigation: \"Height | 64 + safe-area inset\".")
        XCTAssertEqual(MentoraTabBarSpec.iconSize, MentoraIconSize.`default`,
            "COMPONENTS.md § MobileBottomNavigation: \"icon.default (24)\", not icon.medium (20).")
        XCTAssertEqual(MentoraTabBarSpec.items, Tab.allCases,
            "MentoraTabBarSpec.items must delegate to Tab.allCases, never re-list the 5 tabs separately.")
    }
}
