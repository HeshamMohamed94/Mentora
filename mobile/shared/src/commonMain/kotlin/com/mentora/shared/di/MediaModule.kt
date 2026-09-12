package com.mentora.shared.di

import com.mentora.shared.data.repository.media.MediaRepository
import com.mentora.shared.data.repository.media.MediaRepositoryImpl
import com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase
import com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase
import com.mentora.shared.domain.usecase.media.ResolveThumbnailUrlUseCase
import org.koin.dsl.module

/**
 * Task 13's media/playback-contract domain. Deliberately provides no binding for
 * [com.mentora.shared.playback.LessonPlaybackController] — it is an interface-only contract with
 * zero implementation in `shared` (ExoPlayer/AVPlayer are Phase 4/5's job); Koin has nothing to
 * construct for it, and it must never be wired here even as a stub.
 */
internal val mediaModule = module {
    single<MediaRepository> { MediaRepositoryImpl(get()) }

    factory { GetLessonPlaybackSourceUseCase(get(), get()) }
    factory { RefreshPlaybackUrlUseCase(get()) }
    factory { ResolveThumbnailUrlUseCase(get()) }
}
