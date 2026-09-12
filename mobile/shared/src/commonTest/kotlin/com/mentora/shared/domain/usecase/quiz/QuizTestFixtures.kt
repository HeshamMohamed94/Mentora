package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizOption
import com.mentora.shared.domain.model.QuizQuestion

/** A realistic 3-question quiz fixture (2-4 options each), per
 * `execution/PHASE_3_KMP_PLAN.md` Task 10's "realistic multi-question fixture" test requirement. */
internal fun sampleQuiz(): Quiz = Quiz(
    questions = listOf(
        QuizQuestion(
            questionId = "q1", prompt = "What is a Kotlin `val`?", order = 0,
            options = listOf(
                QuizOption("o1a", "A mutable variable"),
                QuizOption("o1b", "An immutable variable"),
                QuizOption("o1c", "A function"),
            ),
        ),
        QuizQuestion(
            questionId = "q2", prompt = "Which keyword declares a class?", order = 1,
            options = listOf(
                QuizOption("o2a", "fun"),
                QuizOption("o2b", "class"),
            ),
        ),
        QuizQuestion(
            questionId = "q3", prompt = "What does `suspend` mark?", order = 2,
            options = listOf(
                QuizOption("o3a", "A coroutine-capable function"),
                QuizOption("o3b", "A final class"),
                QuizOption("o3c", "A top-level property"),
                QuizOption("o3d", "An annotation processor"),
            ),
        ),
    ),
)
