package com.mentora.shared.di

import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.data.repository.progress.ProgressRepositoryImpl
import com.mentora.shared.domain.usecase.progress.CompleteLessonUseCase
import com.mentora.shared.domain.usecase.progress.GetCourseProgressUseCase
import com.mentora.shared.domain.usecase.progress.ReportPlaybackPositionUseCase
import com.mentora.shared.domain.usecase.progress.ResumeCourseUseCase
import org.koin.dsl.module

/**
 * Task 9's progress domain. [CompleteLessonUseCase]/[ResumeCourseUseCase] additionally depend on
 * [CatalogRepository][com.mentora.shared.data.repository.catalog.CatalogRepository] (Task 7,
 * declared in [catalogModule]) for curriculum-order resolution.
 *
 * [ReportPlaybackPositionUseCase] holds real per-instance throttle state (`lastSentAt`) — it is
 * still declared `factory` here, per Task 15 AC's uniform use-case convention, on the assumption
 * that [com.mentora.shared.facade.ProgressFacade] resolves it exactly ONCE (at façade construction,
 * as a `val`) and every caller reuses that same façade property thereafter, never re-resolving from
 * Koin mid-lifecycle. A caller that instead called `koin.get<ReportPlaybackPositionUseCase>()`
 * repeatedly would get a fresh, un-throttled instance every time — the façade is what prevents that.
 */
internal val progressModule = module {
    single<ProgressRepository> { ProgressRepositoryImpl(get()) }

    factory { GetCourseProgressUseCase(get()) }
    factory { CompleteLessonUseCase(get(), get()) }
    factory { ResumeCourseUseCase(get(), get()) }
    factory { ReportPlaybackPositionUseCase(get()) }
}
