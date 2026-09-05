package com.mentora.backend.categories.repository

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates.inc
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class CategoryDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val name: String,
    val slug: String,
    val courseCount: Int = 0,
)

class CategoryRepository(database: MongoDatabase) {
    private val categories = database.getCollection<CategoryDocument>("categories")

    suspend fun findAll(): List<CategoryDocument> = categories.find().toList()
    suspend fun findById(id: ObjectId): CategoryDocument? = categories.find(eq("_id", id)).firstOrNull()
    suspend fun slugExists(slug: String): Boolean = categories.find(eq("slug", slug)).firstOrNull() != null
    suspend fun insert(category: CategoryDocument): CategoryDocument {
        val id = requireNotNull(categories.insertOne(category).insertedId?.asObjectId()?.value)
        return category.copy(id = id)
    }
    suspend fun updateName(id: ObjectId, name: String): CategoryDocument? = categories.findOneAndUpdate(
        eq("_id", id), set("name", name), FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
    )
    suspend fun adjustCourseCount(id: ObjectId, delta: Int): Boolean =
        categories.updateOne(eq("_id", id), inc("courseCount", delta)).matchedCount == 1L
    suspend fun delete(id: ObjectId): Boolean = categories.deleteOne(eq("_id", id)).deletedCount == 1L
}
