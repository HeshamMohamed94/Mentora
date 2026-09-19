import SwiftUI

// Phase 5 Task T9 slice 2 -- the ONE root shell (`PHASE_5_ACCEPTANCE_CRITERIA.md` D1). Per this task's
// own guest-shell override (see `Route.swift`'s header), `TabShell` is used identically for guest and
// authenticated users -- there is no second, guest-specific shell type anywhere in this app.
struct TabShell: View {
    /// Passed explicitly, never a static/singleton (A3).
    let environment: AppEnvironment

    var body: some View {
        TabView(selection: selectionBinding) {
            ForEach(Tab.allCases, id: \.self) { tab in
                stack(for: tab)
                    .tabItem {
                        Label(
                            MentoraStrings.text(tab.labelKey, locale: environment.localeController.currentLocale),
                            image: tab.iconName.rawValue
                        )
                    }
                    .tag(tab)
            }
        }
        .mentoraTabBarTint()
    }

    /// D2's interception point: tapping the tab bar writes through `TabRouter.selectTab(_:)`, which pops
    /// to root when the tapped tab is already selected instead of merely re-selecting it.
    ///
    /// D132 review fix: wraps both closures in `MainActor.assumeIsolated` -- the same treatment
    /// `TabRouter.path(for:)` already applies to its own hand-rolled bindings, now made consistent
    /// across every `Binding(get:set:)` in this codebase that touches `@MainActor`-isolated state,
    /// regardless of which of the two possible compiler outcomes `TabRouter.swift`'s own doc comment
    /// discusses turns out to be true -- `assumeIsolated` is correct and harmless either way.
    private var selectionBinding: Binding<Tab> {
        Binding(
            get: { MainActor.assumeIsolated { environment.router.selectedTab } },
            set: { newValue in MainActor.assumeIsolated { environment.router.selectTab(newValue) } }
        )
    }

    @ViewBuilder
    private func stack(for tab: Tab) -> some View {
        NavigationStack(path: environment.router.path(for: tab)) {
            tabRoot(for: tab)
                .modifier(MentoraRouteDestinations(environment: environment))
                // D132 review fix: `.toolbarBackground` is a preference-propagating modifier, exactly
                // like `.toolbar`/`.navigationTitle` just below it via `MentoraRouteDestinations` -- it
                // must sit INSIDE the bar-hosting container (this stack's own content) so the preference
                // travels upward to the tab bar. The original code applied it to the `TabView` itself
                // from `mentoraTabBarChrome()`, which is exactly the mirror-image mistake this same file's
                // `MentoraRouteDestinations` doc comment already warns against for `.navigationDestination`
                // -- applied from outside the container it targets, it silently does nothing. Applied
                // here, once per tab's own stack content (5 call sites, one per `ForEach` iteration), it
                // reaches the one real tab bar that actually exists.
                .mentoraTabBarBackgroundChrome()
        }
    }

    @ViewBuilder
    private func tabRoot(for tab: Tab) -> some View {
        switch tab {
        case .home:
            HomePlaceholderView(environment: environment)
        case .explore:
            ExplorePlaceholderView(environment: environment)
        case .myLearning:
            MyLearningPlaceholderView(environment: environment)
        case .aiTutor:
            AITutorPlaceholderView(environment: environment)
        case .profile:
            ProfilePlaceholderView(environment: environment)
        }
    }
}

/// The ONE shared destination table (D4) -- applied identically inside every tab's `NavigationStack`
/// (`TabShell.stack(for:)` above) so `tools/ios-checks/navigation-checks.js` Check C1 ("`.navigationDestination(for:`
/// appears exactly once in the whole app target") stays true structurally, not merely by convention.
///
/// Applied to the STACK'S ROOT CONTENT, not chained onto the `NavigationStack` view itself from the
/// outside -- `.navigationDestination(for:)` only associates with the enclosing `NavigationStack` when it
/// sits somewhere inside that stack's own content tree; attaching it after the `NavigationStack(...)`
/// call from outside would associate it with whatever navigation context encloses THIS one instead (none,
/// for a root-level stack embedded directly in a `TabView` tab), silently doing nothing.
///
/// `route.hidesTabBar` (D5) drives `.toolbar(.hidden, for: .tabBar)` through a real `if/else` branch
/// (never a ternary folded into one `.toolbar(...)` call) so that exact literal substring appears in
/// source for `navigation-checks.js` Check C2 to find, and so the tab bar is only ever hidden, never
/// force-shown by this modifier (`.visible` is SwiftUI's own default when nothing states otherwise).
struct MentoraRouteDestinations: ViewModifier {
    let environment: AppEnvironment

    func body(content: Content) -> some View {
        content.navigationDestination(for: Route.self) { route in
            Group {
                if route.hidesTabBar {
                    destination(for: route)
                        .toolbar(.hidden, for: .tabBar)
                } else {
                    destination(for: route)
                }
            }
        }
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case let .courseDetails(courseId):
            CourseDetailsPlaceholderView(courseId: courseId, environment: environment)
        case let .coursePlayer(courseId, lessonId):
            CoursePlayerPlaceholderView(courseId: courseId, lessonId: lessonId, environment: environment)
        case let .quiz(courseId):
            QuizPlaceholderView(courseId: courseId, environment: environment)
        }
    }
}
