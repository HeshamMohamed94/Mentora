import SwiftUI

// Phase 5 Task T9 slice 1 -- app-level tab-bar SPEC constants + chrome, backing `TabShell.swift`'s
// native `TabView`. This is NOT a custom View replacing native `TabView` chrome (D1/§ 10 lock `TabView`
// itself as the navigation root; a fully custom bar would need to hide the native one via
// `.toolbar(.hidden, for: .tabBar)`, the very API D5's own contingency plan already treats as
// potentially unreliable -- depending on that to hide a from-scratch replacement would make the shell
// depend on the thing it is hedging against). Instead, this file supplies the real
// `design-system/COMPONENTS.md § MobileBottomNavigation` spec numbers as named constants, plus the
// subset of that spec pure SwiftUI `TabView` modifiers CAN express today.
//
// NOTE: `design-system/COMPONENTS.md`'s `MobileBottomNavigation` section (its own property/state table,
// read directly before writing this file) is a DIFFERENT component from `Components/MentoraTabs.swift`
// (T8) -- that file is a horizontal, in-screen content-tab-strip atom (an underlined tab row for e.g. a
// course's Overview/Curriculum/Reviews), not the app-level bottom tab bar. It is not reused here.
//
// DISCLOSED GAP -- NOT FULLY ACHIEVABLE IN PURE SWIFTUI TODAY: `MobileBottomNavigation`'s real spec
// (confirmed by reading `COMPONENTS.md § MobileBottomNavigation` directly) additionally calls for an
// inactive-item color of `color.text.secondary`, item labels set in `typography.caption`, and a
// top hairline border (`border.width.default`, `color.border.default`) on top of the exact height below.
// None of those four are reachable through a pure SwiftUI `TabView` modifier without a `UIColor`-based
// `UITabBarAppearance` customization -- which `tools/ios-checks/theme-checks.js` Check B3 currently bans
// repo-wide (`UIColor` anywhere in this target). This is a PRE-DECIDED fallback, matching
// `PHASE_5_IOS_SYSTEM_DESIGN.md` § 10's own pattern for the `fullScreenCover` D5 contingency: if a later
// MC-3 visual sweep finds the native chrome unacceptably off-spec, the fix is a single new file
// (`Navigation/MentoraTabBarAppearance.swift`) configuring `UITabBarAppearance` exactly once, plus a
// named `SANCTIONED_EXCEPTIONS` entry in `theme-checks.js` scoped to exactly that one file -- NOT built
// now, and taking it later requires its own `execution/DECISIONS_LOG.md` entry, never a silent gate edit.
// D132 review fix: `height`/`iconSize` below are RECORDED spec numbers, not currently CONSUMED by any
// rendering code -- native `TabView` chrome takes neither as a parameter, so nothing in `TabShell.swift`
// reads them. `MentoraTabBarSpecTests.swift`'s passing assertion proves only "these constants equal
// COMPONENTS.md's numbers," not "the rendered tab bar measures 64pt with 24pt icons" -- that remains an
// MC-3 visual-sweep item, same as the 4 properties this enum's own header already discloses as
// unreachable in pure SwiftUI. `items` is the one property here an actual call site does use
// (`MentoraTabBarSpecTests.swift`'s own D1-order assertion).
enum MentoraTabBarSpec {
    /// `COMPONENTS.md § MobileBottomNavigation`: "Height | 64 + safe-area inset".
    static let height: CGFloat = 64

    /// `COMPONENTS.md § MobileBottomNavigation`: "Item | icon `icon.default` (24) + label
    /// `typography.caption`" -- `icon.default` resolves to `MentoraIconSize.default` (24), confirmed by
    /// reading `Theme/MentoraTokens.swift` directly, NOT `icon.medium` (20).
    static let iconSize: CGFloat = MentoraIconSize.`default`

    /// The 5 locked-order tabs this bar renders -- delegates to `Tab.allCases` rather than re-listing
    /// them, so there is exactly one place (`Route.swift`) that can ever define/reorder the tab set.
    static var items: [Tab] { Tab.allCases }
}

extension View {
    /// The active-item tint (`color.brand.primary`). A plain environment value read DOWNWARD by
    /// `TabView` itself, so applying it once at the `TabView` root (`TabShell.body`) is correct SwiftUI
    /// usage -- unlike `mentoraTabBarBackgroundChrome()` below, this one is NOT a preference-propagating
    /// modifier and does not need to sit inside the bar-hosting container.
    func mentoraTabBarTint() -> some View {
        self.tint(Color.mentoraBrandPrimary)
    }

    /// The tab bar's background surface (`color.surface.default`) via the two-call `.toolbarBackground(...)`
    /// pair `TabView` needs to actually render an opaque, themed background instead of its default
    /// translucent material.
    ///
    /// D132 review fix: `.toolbarBackground` is a PREFERENCE-propagating modifier (like `.toolbar`/
    /// `.navigationTitle`) -- it must be applied INSIDE the bar-hosting container so the preference
    /// travels upward to it. The original single `mentoraTabBarChrome()` applied this pair to the
    /// `TabView` itself, from outside every tab's `NavigationStack` -- a silent no-op, caught by mandatory
    /// review before this task's first CI run, not by a source-policy gate (no automated check can prove
    /// a SwiftUI preference actually propagates). Call sites: `TabShell.stack(for:)`, once per tab's own
    /// stack content, never once at the `TabView` root.
    func mentoraTabBarBackgroundChrome() -> some View {
        self
            .toolbarBackground(Color.mentoraSurfaceDefault, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}
