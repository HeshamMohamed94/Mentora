package com.mentora.backend.enrollment.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureEnrollmentIndexes(database: MongoDatabase) {
    val enrollments = database.getCollection<EnrollmentDocument>("enrollments")
    enrollments.createIndex(
        compoundIndex(ascending("userId"), ascending("courseId")),
        IndexOptions().unique(true),
    )
    enrollments.createIndex(ascending("userId"))
    database.getCollection<DemoPurchaseDocument>("demoPurchases").createIndex(ascending("userId"))
}
