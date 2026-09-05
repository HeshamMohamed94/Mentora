package com.mentora.backend.courses.repository

import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.client.model.Indexes.text
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureCoursesIndexes(database: MongoDatabase) {
    val courses = database.getCollection<CourseDocument>("courses")
    courses.createIndex(ascending("instructorId"))
    courses.createIndex(compoundIndex(ascending("status"), ascending("categoryId")))
    courses.createIndex(compoundIndex(text("title"), text("description")))
    courses.createIndex(ascending("status"))
}
