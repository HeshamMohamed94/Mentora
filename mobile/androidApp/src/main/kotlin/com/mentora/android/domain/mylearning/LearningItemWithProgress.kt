package com.mentora.android.domain.mylearning

import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.Enrollment

/**
 * T12 — the G3 join's result shape (`execution/PHASE_4_ANDROID_PLAN.md` § 6 G3). Extends `shared`'s
 * own [com.mentora.shared.domain.model.MyLearningItem] (`enrollment` + `course`, already composed
 * inside `shared` by `GetMyLearningUseCase`) with [progress] — the one piece neither `shared`'s
 * `MyLearningItem`/`GetMyLearningUseCase` carries (that use case's own kdoc: Task 8 AC #4 never asked
 * for per-course progress, only enrollment+course).
 *
 * **Deliberately layered in `androidApp`, not `shared`.** G3's decision (already made, not
 * relitigated here): a real aggregate "my learning + progress" `shared` use case was explicitly
 * rejected for Phase 4 (flagged for a possible Phase 5 promotion decision instead) — see
 * [GetMyLearningWithProgressUseCase]'s own kdoc for the exact extra round trips this adds on top of
 * `sdk.enrollment.getMyLearning()`.
 */
data class LearningItemWithProgress(
    val enrollment: Enrollment,
    val course: Course,
    val progress: CourseProgress,
)
