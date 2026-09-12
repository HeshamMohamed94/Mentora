package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.progress.CompleteLessonUseCase
import com.mentora.shared.domain.usecase.progress.GetCourseProgressUseCase
import com.mentora.shared.domain.usecase.progress.ReportPlaybackPositionUseCase
import com.mentora.shared.domain.usecase.progress.ResumeCourseUseCase
import org.koin.core.Koin

/**
 * Task 9's progress domain. [reportPlaybackPosition] is resolved exactly ONCE here (this class is
 * itself resolved once, lazily, by [com.mentora.shared.MentoraSdk]) — its internal throttle state
 * (`lastSentAt`) is therefore correctly shared across every call a caller makes through
 * `sdk.progress.reportPlaybackPosition(...)`, never accidentally reset by a fresh Koin resolution.
 */
class ProgressFacade internal constructor(koin: Koin) {
    val getCourseProgress: GetCourseProgressUseCase = koin.get()
    val completeLesson: CompleteLessonUseCase = koin.get()
    val resumeCourse: ResumeCourseUseCase = koin.get()
    val reportPlaybackPosition: ReportPlaybackPositionUseCase = koin.get()
}
