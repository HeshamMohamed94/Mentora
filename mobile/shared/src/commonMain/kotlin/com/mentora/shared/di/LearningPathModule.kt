package com.mentora.shared.di

import com.mentora.shared.data.repository.learningpath.LearningPathRepository
import com.mentora.shared.data.repository.learningpath.LearningPathRepositoryImpl
import com.mentora.shared.domain.usecase.learningpath.FollowLearningPathUseCase
import com.mentora.shared.domain.usecase.learningpath.GetLearningPathDetailUseCase
import com.mentora.shared.domain.usecase.learningpath.ListLearningPathsUseCase
import com.mentora.shared.domain.usecase.learningpath.UnfollowLearningPathUseCase
import org.koin.dsl.module

/** Task 12's Learning Paths domain. */
internal val learningPathModule = module {
    single<LearningPathRepository> { LearningPathRepositoryImpl(get(), get()) }

    factory { ListLearningPathsUseCase(get()) }
    factory { GetLearningPathDetailUseCase(get()) }
    factory { FollowLearningPathUseCase(get()) }
    factory { UnfollowLearningPathUseCase(get()) }
}
