package com.mentora.shared.di

import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.data.repository.quiz.QuizRepositoryImpl
import com.mentora.shared.domain.usecase.quiz.GetLatestAttemptUseCase
import com.mentora.shared.domain.usecase.quiz.GetQuizUseCase
import com.mentora.shared.domain.usecase.quiz.SubmitQuizUseCase
import org.koin.dsl.module

/** Task 10's quiz domain. */
internal val quizModule = module {
    single<QuizRepository> { QuizRepositoryImpl(get()) }

    factory { GetQuizUseCase(get()) }
    factory { SubmitQuizUseCase(get()) }
    factory { GetLatestAttemptUseCase(get()) }
}
