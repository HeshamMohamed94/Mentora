import SwiftUI
import shared

// Phase 5 Task T9 -- placeholder content for every one of `TabShell`'s 5 tab roots and its 3 pushed
// destinations. Every view in this file carries a `// TEMPORARY (T9)` marker comment and is deleted
// piecewise as its real screen lands in T10-T21 -- `tools/ios-checks/navigation-checks.js` Check E1
// greps for exactly that marker so a future task can find what's left to replace. ZERO new localization
// catalog keys are introduced here (`tools/ios-checks/catalog-parity.js` pins the EN key count at 279) --
// every string below resolves an EXISTING key, confirmed by grepping the real
// `Resources/Localizable.xcstrings` catalog before writing this file, never guessed.
//
// Every view takes an explicit `environment: AppEnvironment` -- never a static/singleton (A3), matching
// `TabShell`'s own `let environment: AppEnvironment` convention. Course/lesson ids used to push a
// `Route` are local, non-user-facing placeholder string constants -- not a localization concern.

private let placeholderCourseId = "t9-placeholder-course"

// MARK: - Tab roots

// TEMPORARY (T9) -- replaced by the real Home screen in a later task.
struct HomePlaceholderView: View {
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        VStack(spacing: MentoraSpacing.space4) {
            Text(MentoraStrings.text("nav_home", locale: locale))
                .mentoraFont(.h2)
            MentoraButton(MentoraStrings.text("course_details_nav_title", locale: locale)) {
                environment.router.push(.courseDetails(courseId: placeholderCourseId), onto: .home)
            }
        }
        .padding(MentoraSpacing.space4)
    }
}

// TEMPORARY (T9) -- replaced by the real Explore screen in a later task.
struct ExplorePlaceholderView: View {
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        VStack(spacing: MentoraSpacing.space4) {
            Text(MentoraStrings.text("explore_heading", locale: locale))
                .mentoraFont(.h2)
            MentoraButton(MentoraStrings.text("course_details_nav_title", locale: locale)) {
                environment.router.push(.courseDetails(courseId: placeholderCourseId), onto: .explore)
            }
        }
        .padding(MentoraSpacing.space4)
    }
}

// TEMPORARY (T9) -- replaced by the real My Learning screen in a later task.
struct MyLearningPlaceholderView: View {
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        VStack(spacing: MentoraSpacing.space4) {
            Text(MentoraStrings.text("my_learning_heading", locale: locale))
                .mentoraFont(.h2)
            MentoraButton(MentoraStrings.text("my_learning_resume_action", locale: locale)) {
                environment.router.push(
                    .coursePlayer(courseId: placeholderCourseId, lessonId: nil),
                    onto: .myLearning
                )
            }
        }
        .padding(MentoraSpacing.space4)
    }
}

// TEMPORARY (T9) -- replaced by the real AI Tutor screen in a later task. No pushed screens from this
// tab in T9.
struct AITutorPlaceholderView: View {
    let environment: AppEnvironment

    var body: some View {
        Text(MentoraStrings.text("nav_ai_tutor", locale: environment.localeController.currentLocale))
            .mentoraFont(.h2)
            .padding(MentoraSpacing.space4)
    }
}

// TEMPORARY (T9) -- replaced by the real Profile screen in a later task (Settings is T21's job). No
// pushed screens from this tab in T9.
struct ProfilePlaceholderView: View {
    let environment: AppEnvironment

    var body: some View {
        Text(MentoraStrings.text("nav_profile", locale: environment.localeController.currentLocale))
            .mentoraFont(.h2)
            .padding(MentoraSpacing.space4)
    }
}

// MARK: - Pushed destinations

// TEMPORARY (T9) -- replaced by the real Course Details screen in a later task. Also the real B8
// demonstration: the "Login to enroll" CTA is shown only to a guest and routes through the auth-gate
// helper (`TabRouter.requestGatedRoute`) instead of pushing directly.
struct CourseDetailsPlaceholderView: View {
    let courseId: String
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        VStack(spacing: MentoraSpacing.space4) {
            MentoraButton(MentoraStrings.text("course_details_cta_continue_learning", locale: locale)) {
                environment.router.push(
                    .coursePlayer(courseId: courseId, lessonId: nil),
                    onto: environment.router.selectedTab
                )
            }
            if !environment.sessionController.isAuthenticated {
                MentoraButton(
                    MentoraStrings.text("course_details_cta_login_to_enroll", locale: locale),
                    variant: .secondary
                ) {
                    environment.router.requestGatedRoute(
                        .coursePlayer(courseId: courseId, lessonId: nil),
                        from: environment.router.selectedTab,
                        isAuthenticated: environment.sessionController.isAuthenticated
                    )
                }
            }
        }
        .padding(MentoraSpacing.space4)
        .navigationTitle(MentoraStrings.text("course_details_nav_title", locale: locale))
    }
}

// TEMPORARY (T9) -- replaced by the real Course Player screen in a later task.
struct CoursePlayerPlaceholderView: View {
    let courseId: String
    let lessonId: String?
    let environment: AppEnvironment

    private var locale: AppLocale { environment.localeController.currentLocale }

    var body: some View {
        MentoraButton(MentoraStrings.text("quiz_nav_title", locale: locale)) {
            environment.router.push(.quiz(courseId: courseId), onto: environment.router.selectedTab)
        }
        .padding(MentoraSpacing.space4)
        .navigationTitle(MentoraStrings.text("course_player_nav_title_fallback", locale: locale))
    }
}

// TEMPORARY (T9) -- replaced by the real Quiz screen in a later task. No further pushes.
struct QuizPlaceholderView: View {
    let courseId: String
    let environment: AppEnvironment

    var body: some View {
        Text(MentoraStrings.text("quiz_nav_title", locale: environment.localeController.currentLocale))
            .mentoraFont(.bodyLarge)
            .padding(MentoraSpacing.space4)
            .navigationTitle(MentoraStrings.text("quiz_nav_title", locale: environment.localeController.currentLocale))
    }
}
