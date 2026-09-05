package com.mentora.backend.enrollment.repository

import com.mentora.backend.common.PageRequest
import com.mentora.backend.courses.service.PriceDisplayDto
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Sorts.ascending
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId

@Serializable
data class DemoPurchaseDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val courseId: ObjectId,
    val priceDisplaySnapshot: PriceDisplayDto,
    val completedAt: Instant,
)

@Serializable
data class EnrollmentDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val courseId: ObjectId,
    val source: String,
    @Contextual val demoPurchaseId: ObjectId,
    val enrolledAt: Instant,
    val status: String,
)

class EnrollmentRepository(database: MongoDatabase) {
    private val enrollments = database.getCollection<EnrollmentDocument>("enrollments")
    private val demoPurchases = database.getCollection<DemoPurchaseDocument>("demoPurchases")

    suspend fun find(session: ClientSession, userId: ObjectId, courseId: ObjectId): EnrollmentDocument? =
        enrollments.find(session, enrollmentFilter(userId, courseId)).firstOrNull()

    suspend fun find(userId: ObjectId, courseId: ObjectId): EnrollmentDocument? =
        enrollments.find(enrollmentFilter(userId, courseId)).firstOrNull()

    suspend fun insertDemoPurchase(session: ClientSession, document: DemoPurchaseDocument): DemoPurchaseDocument {
        val id = requireNotNull(demoPurchases.insertOne(session, document).insertedId?.asObjectId()?.value)
        return document.copy(id = id)
    }

    suspend fun insertEnrollment(session: ClientSession, document: EnrollmentDocument): EnrollmentDocument {
        val id = requireNotNull(enrollments.insertOne(session, document).insertedId?.asObjectId()?.value)
        return document.copy(id = id)
    }

    suspend fun list(userId: ObjectId, page: PageRequest): List<EnrollmentDocument> {
        val filters = buildList<Bson> {
            add(eq("userId", userId))
            page.cursor?.let { add(gt("_id", it)) }
        }
        return enrollments.find(and(filters)).sort(ascending("_id")).limit(page.limit + 1).toList()
    }

    private fun enrollmentFilter(userId: ObjectId, courseId: ObjectId) =
        and(eq("userId", userId), eq("courseId", courseId))
}
