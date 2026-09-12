package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.learningpath.FollowLearningPathUseCase
import com.mentora.shared.domain.usecase.learningpath.GetLearningPathDetailUseCase
import com.mentora.shared.domain.usecase.learningpath.ListLearningPathsUseCase
import com.mentora.shared.domain.usecase.learningpath.UnfollowLearningPathUseCase
import org.koin.core.Koin

/** Task 12's Learning Paths domain. */
class LearningPathFacade internal constructor(koin: Koin) {
    val listLearningPaths: ListLearningPathsUseCase = koin.get()
    val getLearningPathDetail: GetLearningPathDetailUseCase = koin.get()
    val followLearningPath: FollowLearningPathUseCase = koin.get()
    val unfollowLearningPath: UnfollowLearningPathUseCase = koin.get()
}
