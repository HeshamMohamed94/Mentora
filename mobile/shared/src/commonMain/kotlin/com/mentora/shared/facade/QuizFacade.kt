package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.quiz.GetLatestAttemptUseCase
import com.mentora.shared.domain.usecase.quiz.GetQuizUseCase
import com.mentora.shared.domain.usecase.quiz.SubmitQuizUseCase
import org.koin.core.Koin

/** Task 10's quiz domain. */
class QuizFacade internal constructor(koin: Koin) {
    val getQuiz: GetQuizUseCase = koin.get()
    val submitQuiz: SubmitQuizUseCase = koin.get()
    val getLatestAttempt: GetLatestAttemptUseCase = koin.get()
}
