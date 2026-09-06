package com.mentora.backend.users.repository

import com.mentora.backend.common.Role
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates.combine
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val email: String,
    val passwordHash: String,
    val role: Role,
    val name: String,
    @Contextual val avatarMediaId: ObjectId? = null,
    val preferredLocale: String? = null,
    val createdAt: Instant,
)

class UserRepository(database: MongoDatabase) {
    private val users = database.getCollection<UserDocument>("users")

    suspend fun findById(id: ObjectId): UserDocument? = users.find(eq("_id", id)).firstOrNull()

    /** Bulk lookup for cross-module name resolution (e.g. `courses` denormalizing
     * `instructorName` onto its responses) — mirrors `AdminRepository.usersByIds`'s pattern. */
    suspend fun findByIds(ids: List<ObjectId>): List<UserDocument> =
        if (ids.isEmpty()) emptyList() else users.find(`in`("_id", ids)).toList()

    suspend fun updateProfile(id: ObjectId, name: String?, preferredLocale: String?): UserDocument? {
        val updates = buildList {
            if (name != null) add(set("name", name))
            if (preferredLocale != null) add(set("preferredLocale", preferredLocale))
        }
        if (updates.isEmpty()) return findById(id)
        return users.findOneAndUpdate(
            eq("_id", id), combine(updates),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
        )
    }
}
