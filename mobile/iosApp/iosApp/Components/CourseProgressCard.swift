import SwiftUI
import shared

// Phase 5 Task T11 slice 2 (Component Kit B) -- `design-system/COMPONENTS.md § CourseProgressCard`
// (lines 305-307): "same shell as CourseCard -- always shows ProgressBar + '% complete' + 'Resume'
// TonalButton, and drops the rating/student-count row." Delegates to the same `BaseCourseCard` as
// `CourseCard.swift` (shared shell, per that file's own header), always passing `progress`/
// `progressLabel`, never a `metaRow`. Ported directly from Android's own `CourseProgressCard.kt`.
struct CourseProgressCard: View {
    let title: String
    let instructorName: String
    let seed: String
    let categoryId: String?
    let categoryLabel: String
    let thumbnailAccessibilityLabel: String
    let progress: Double
    let onResumeClick: () -> Void
    var mediaId: String? = nil
    var thumbnailUrl: String? = nil
    var onClick: (() -> Void)? = nil
    /// DISCLOSED DEVIATION from Android's own default-parameter convenience: Android's
    /// `resumeLabel: String = stringResource(R.string.course_progress_card_resume_label)` can resolve a
    /// default inline because a `@Composable` always has an ambient `Context`/locale to call
    /// `stringResource` with. `MentoraStrings.text(_:locale:)` (this key: the real, pre-existing
    /// `course_progress_card_resume_label`, confirmed present in `Resources/Localizable.xcstrings`
    /// before this task began -- zero new keys added) instead requires an explicit `AppLocale`, and this
    /// file (a plain `Components/` composite, matching every atom/composite in this kit's own "never
    /// resolves `MentoraStrings` itself" convention) has no `AppEnvironment` of its own to source one
    /// from -- so `resumeLabel` is REQUIRED here, always resolved and passed in by the caller.
    var resumeLabel: String
    var progressLabelFormatter: (Double) -> String = { p in "\(Int((p * 100).rounded()))% complete" }

    var body: some View {
        BaseCourseCard(
            title: title,
            instructorName: instructorName,
            seed: seed,
            categoryId: categoryId,
            categoryLabel: categoryLabel,
            mediaId: mediaId,
            thumbnailUrl: thumbnailUrl,
            thumbnailAccessibilityLabel: thumbnailAccessibilityLabel,
            actionLabel: resumeLabel,
            onActionClick: onResumeClick,
            onClick: onClick,
            metaRow: nil, // dropped per spec.
            progress: progress,
            progressLabel: progressLabelFormatter(progress)
        )
    }
}
