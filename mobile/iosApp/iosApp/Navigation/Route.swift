import Foundation

// Phase 5 Task T9 slice 1 -- the navigation shell's pure model (`execution/PHASE_5_IOS_SYSTEM_DESIGN.md`
// § 10, `execution/PHASE_5_ACCEPTANCE_CRITERIA.md` D1-D8). Holds NO SwiftUI rendering and NO
// `AppEnvironment`/SDK dependency -- `Tab`/`Route`/`PendingIntent` are plain value types, fully
// unit-testable with literal values (`TabRouterTests.swift`/`AuthGateLogicTests.swift`).
//
// GUEST-SHELL OVERRIDE (read this before touching anything else in `Navigation/`): the architect's plan
// proposed a guest-specific single-stack "GuestShell" separate from the 5-tab `TabShell`. That was
// overridden, project-owner-level, in favor of ONE root shell (the 5-tab `TabView`) used identically for
// both guest and authenticated users. See D132 (`execution/DECISIONS_LOG.md`) for the full,
// review-corrected rationale and its disclosed conflict with `ux/NAVIGATION_SPEC.md:82` -- the primary
// justification is NOT "D1's literal text is silent about guests" (mandatory review found D1's own cited
// source, `design-to-code/shared/navigation.json#/shells/mobileStudentShell`, is explicitly scoped to
// authenticated Students, so that reading does not hold up as stated). The real, stronger justification is
// engineering: keeping `.unauthenticated`/`.authenticated` in the SAME `RootView` branch (never swapping
// shell TYPES at login) means the tab whose `NavigationStack` originated a gated action survives login
// completely untouched, which is exactly what B8's "replay onto the original intent" requires -- a
// GuestShell/TabShell swap would tear down and rebuild that stack at the worst possible moment. This
// override is fully reversible without touching `Route`/`TabRouter`/`MentoraRouteDestinations` (all
// shell-agnostic) -- a future task can add a real guest shell as a one-case addition to
// `RootView.content(for:)` if the deferred product gap (a guest currently sees My Learning/AI
// Tutor/Profile tabs with placeholder-only content) needs closing before then.
// `Route`/`Tab`/`PendingIntent` below are therefore the ONLY navigation model this app has -- there is no
// second, guest-specific model anywhere.

/// The 5 locked-order root tabs (`RootView`/`TabShell`, D1). `CaseIterable`'s synthesized order follows
/// declaration order below -- this order IS the acceptance criterion, not an incidental detail, so
/// reordering these cases is itself a behavior change, not a refactor.
enum Tab: Hashable, CaseIterable {
    case home
    case explore
    case myLearning
    case aiTutor
    case profile

    /// Real, already-ported `Localizable.xcstrings` keys (confirmed by grepping the actual catalog file
    /// before writing this, not guessed) -- `nav_home`/`nav_explore`/`nav_my_learning`/`nav_ai_tutor`/
    /// `nav_profile`. Resolved through `MentoraStrings.text(_:locale:)` at every call site, never here
    /// (this type carries no locale of its own).
    var labelKey: String {
        switch self {
        case .home: return "nav_home"
        case .explore: return "nav_explore"
        case .myLearning: return "nav_my_learning"
        case .aiTutor: return "nav_ai_tutor"
        case .profile: return "nav_profile"
        }
    }

    /// `MentoraIconName` (`Theme/MentoraIcon.swift`) has NO `.home` case -- the ported 42-icon set maps
    /// the "home" tab to the `.dashboard` glyph, confirmed directly against Android's own
    /// `MobileBottomNavigation.kt` reference and by reading `MentoraIconName`'s real case list before
    /// writing this switch. The other four cases (`.explore`/`.myLearning`/`.aiTutor`/`.profile`) are
    /// likewise confirmed real, existing `MentoraIconName` cases, not guessed spellings.
    var iconName: MentoraIconName {
        switch self {
        case .home: return .dashboard
        case .explore: return .explore
        case .myLearning: return .myLearning
        case .aiTutor: return .aiTutor
        case .profile: return .profile
        }
    }

    /// For T22's future XCUITests -- not consumed by anything in T9 itself.
    var accessibilityIdentifier: String {
        switch self {
        case .home: return "tab_home"
        case .explore: return "tab_explore"
        case .myLearning: return "tab_my_learning"
        case .aiTutor: return "tab_ai_tutor"
        case .profile: return "tab_profile"
        }
    }
}

/// A pushed destination inside one of `Tab`'s 5 `NavigationStack`s. Carries IDS ONLY, never a model
/// object (D7) -- `courseId`/`lessonId` are always the string identifiers a later screen re-fetches by,
/// never a cached domain value, so no stale/duplicated model instance can ever live inside navigation
/// state.
///
/// DELIBERATELY EXACTLY THESE 3 CASES, not a full forward-looking 13-case set covering every screen
/// `SCREEN_INVENTORY.md` eventually needs. Grounds:
///   (a) `PHASE_5_IOS_SYSTEM_DESIGN.md § 10` (lines ~591-592) names exactly `courseDetails`/
///       `coursePlayer`/`quiz` as T9's own pushed-destination set.
///   (b) `QuizRepository.getQuiz(courseId: String)` (`mobile/shared`, confirmed by reading the real
///       Kotlin interface directly) is keyed by `courseId` ONLY -- there is no `quizId` anywhere in
///       `shared` to guess a shape for.
///   (c) `Route` is not persisted, not `Codable`-encoded, not deep-linked, and crosses no process
///       boundary anywhere in this app -- its only consumer is `MentoraRouteDestinations`'s ONE
///       exhaustive `switch` (`TabShell.swift`). Adding a case later is therefore a two-line,
///       single-file, purely additive change (the switch will not even compile until the new case is
///       handled, since it carries no `default:`), whereas guessing wrong parameter shapes now for 10
///       screens that don't exist yet (T10-T21) would cost strictly more than deferring costs.
enum Route: Hashable {
    case courseDetails(courseId: String)
    case coursePlayer(courseId: String, lessonId: String?)
    case quiz(courseId: String)

    /// D5: which pushed destinations hide the app-level tab bar. `.coursePlayer`/`.quiz` are full-bleed,
    /// immersive screens (video playback / focused assessment); `.courseDetails` keeps the tab bar
    /// visible (it is still a "browsing" screen, one level under a tab root).
    var hidesTabBar: Bool {
        switch self {
        case .coursePlayer, .quiz:
            return true
        case .courseDetails:
            return false
        }
    }
}

/// A gated route recorded while the login sheet is up (B8: "guest taps a gated action, logs in, lands
/// back on the original intent"). Recorded on `TabRouter` (see that file's header for why this deviates
/// from `PHASE_5_IOS_SYSTEM_DESIGN.md § 10`'s literal "stores the pending Route on AppEnvironment"
/// wording), never on `AppEnvironment` itself.
struct PendingIntent: Hashable {
    let route: Route
    let tab: Tab
}
