package com.mentora.backend.certificates.repository

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes.ascending
import com.mongodb.client.model.Indexes.compoundIndex
import com.mongodb.kotlin.client.coroutine.MongoDatabase

suspend fun ensureCertificateIndexes(database: MongoDatabase) {
    val certificates = database.getCollection<CertificateDocument>("certificates")
    certificates.createIndex(ascending("userId"))
    certificates.createIndex(
        compoundIndex(ascending("userId"), ascending("courseId")),
        IndexOptions().unique(true),
    )
}
