package com.mentora.backend.quiz.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureQuizIndexes(database: MongoDatabase) {
    database.getCollection<QuizDocument>("quizzes")
        .createIndex(ascending("courseId"), IndexOptions().unique(true))
    database.getCollection<QuizAttemptDocument>("quizAttempts")
        .createIndex(compoundIndex(ascending("userId"), ascending("quizId")))
}
