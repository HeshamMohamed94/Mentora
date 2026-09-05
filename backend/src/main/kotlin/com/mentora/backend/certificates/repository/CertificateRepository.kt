package com.mentora.backend.certificates.repository

import com.mentora.backend.common.PageRequest
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class CertificateRepository(database: MongoDatabase) {
    private val certificates = database.getCollection<CertificateDocument>("certificates")

    suspend fun insert(session: ClientSession, document: CertificateDocument): CertificateDocument {
        val id = requireNotNull(certificates.insertOne(session, document).insertedId?.asObjectId()?.value)
        return document.copy(id = id)
    }

    suspend fun list(userId: ObjectId, page: PageRequest): List<CertificateDocument> {
        val filters = buildList<Bson> {
            add(eq("userId", userId))
            page.cursor?.let { add(gt("_id", it)) }
        }
        return certificates.find(and(filters)).sort(ascending("_id")).limit(page.limit + 1).toList()
    }

    suspend fun findByIdAndUser(id: ObjectId, userId: ObjectId): CertificateDocument? =
        certificates.find(and(eq("_id", id), eq("userId", userId))).firstOrNull()
}
