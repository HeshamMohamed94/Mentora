package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase
import com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase
import com.mentora.shared.domain.usecase.media.ResolveThumbnailUrlUseCase
import org.koin.core.Koin

/**
 * Task 13's media/lesson-playback-contract domain. Deliberately has NO property for
 * [com.mentora.shared.playback.LessonPlaybackController] — it is an interface-only contract with
 * zero implementation in `shared` (see [com.mentora.shared.di.mediaModule]'s kdoc); Phase 4/5 own
 * their own ExoPlayer/AVPlayer-backed implementation of it entirely outside this façade.
 */
class MediaFacade internal constructor(koin: Koin) {
    val getLessonPlaybackSource: GetLessonPlaybackSourceUseCase = koin.get()
    val refreshPlaybackUrl: RefreshPlaybackUrlUseCase = koin.get()
    val resolveThumbnailUrl: ResolveThumbnailUrlUseCase = koin.get()
}
