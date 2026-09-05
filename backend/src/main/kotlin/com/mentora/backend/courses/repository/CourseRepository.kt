package com.mentora.backend.courses.repository

import com.mongodb.client.model.Filters.and
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.gt
import com.mongodb.client.model.Filters.lte
import com.mongodb.client.model.Filters.text
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts.ascending
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
data class PriceDisplay(val amount: Int, val currency: String)

@Serializable
data class CourseResource(val label: String, val url: String)

@Serializable
data class Lesson(
    val lessonId: String,
    val title: String,
    val description: String,
    val order: Int,
    @Contextual val videoMediaId: ObjectId? = null,
    val resources: List<CourseResource> = emptyList(),
)

@Serializable
data class Section(
    val sectionId: String,
    val title: String,
    val order: Int,
    val lessons: List<Lesson> = emptyList(),
)

@Serializable
data class CourseDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val instructorId: ObjectId,
    val title: String,
    val description: String,
    @Contextual val categoryId: ObjectId,
    val level: String,
    val contentLanguage: String,
    val priceDisplay: PriceDisplay,
    @Contextual val thumbnailMediaId: ObjectId? = null,
    val status: String,
    val ratingSeed: Double,
    val sections: List<Section> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

class CourseRepository(database: MongoDatabase) {
    private val courses = database.getCollection<CourseDocument>("courses")

    suspend fun insert(course: CourseDocument): CourseDocument {
        val id = requireNotNull(courses.insertOne(course).insertedId?.asObjectId()?.value)
        return course.copy(id = id)
    }

    suspend fun findById(id: ObjectId): CourseDocument? = courses.find(eq("_id", id)).firstOrNull()

    suspend fun listPublished(filter: PublishedCourseFilter): List<CourseDocument> {
        val filters = buildList<Bson> {
            add(eq("status", "published"))
            filter.categoryId?.let { add(eq("categoryId", it)) }
            filter.level?.let { add(eq("level", it)) }
            filter.maxPrice?.let { add(lte("priceDisplay.amount", it)) }
            filter.query?.takeIf { it.isNotBlank() }?.let { add(text(it)) }
            filter.cursor?.let { add(gt("_id", it)) }
        }
        return courses.find(and(filters)).sort(ascending("_id")).limit(filter.limit + 1).toList()
    }

    suspend fun replace(course: CourseDocument): CourseDocument? {
        val id = requireNotNull(course.id)
        val result = courses.replaceOne(eq("_id", id), course, ReplaceOptions().upsert(false))
        return if (result.matchedCount == 1L) course else null
    }
}

data class PublishedCourseFilter(
    val categoryId: ObjectId?,
    val level: String?,
    val maxPrice: Int?,
    val query: String?,
    val cursor: ObjectId?,
    val limit: Int,
)
