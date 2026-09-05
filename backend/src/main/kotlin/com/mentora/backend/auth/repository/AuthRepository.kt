package com.mentora.backend.auth.repository

import com.mentora.backend.users.repository.UserDocument
import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class RefreshTokenDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    val tokenHash: String,
    val familyId: String,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val deviceLabel: String? = null,
)

class AuthRepository(database: MongoDatabase) {
    private val users = database.getCollection<UserDocument>("users")
    private val refreshTokens = database.getCollection<RefreshTokenDocument>("refreshTokens")

    suspend fun insertUser(user: UserDocument): ObjectId =
        requireNotNull(users.insertOne(user).insertedId?.asObjectId()?.value)
    suspend fun findUserByEmail(email: String): UserDocument? = users.find(eq("email", email)).firstOrNull()
    suspend fun findUserById(id: ObjectId): UserDocument? = users.find(eq("_id", id)).firstOrNull()
    suspend fun insertRefreshToken(token: RefreshTokenDocument) { refreshTokens.insertOne(token) }
    suspend fun findRefreshToken(tokenHash: String): RefreshTokenDocument? =
        refreshTokens.find(eq("tokenHash", tokenHash)).firstOrNull()
    suspend fun revokeIfActive(id: ObjectId, now: Instant): Boolean =
        refreshTokens.updateOne(and(eq("_id", id), eq("revokedAt", null)), set("revokedAt", now)).modifiedCount == 1L
    suspend fun revokeFamily(familyId: String, now: Instant) {
        refreshTokens.updateMany(eq("familyId", familyId), set("revokedAt", now))
    }
}
