package com.mentora.backend.quiz

import com.mentora.backend.quiz.repository.QuizRepository
import com.mentora.backend.quiz.service.QuizService
import org.koin.dsl.module

val quizModule = module {
    single { QuizRepository(get()) }
    single { QuizService(get(), get(), get(), get(), get()) }
}
