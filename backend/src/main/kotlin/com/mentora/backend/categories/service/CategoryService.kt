package com.mentora.backend.categories.service

import com.mentora.backend.categories.repository.CategoryDocument
import com.mentora.backend.categories.repository.CategoryRepository
import com.mentora.backend.common.ApiException
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable data class CategoryResponse(val id: String, val name: String, val slug: String, val courseCount: Int)
@Serializable data class CategoryNameRequest(val name: String)

class CategoryService(private val repository: CategoryRepository) {
    suspend fun list(): List<CategoryResponse> = repository.findAll().map { it.toResponse() }

    suspend fun create(request: CategoryNameRequest): CategoryResponse {
        val name = validatedName(request.name)
        val base = slugify(name).ifBlank { "category" }
        var slug = base
        var suffix = 2
        while (repository.slugExists(slug)) slug = "$base-${suffix++}"
        return repository.insert(CategoryDocument(name = name, slug = slug)).toResponse()
    }

    suspend fun update(id: String, request: CategoryNameRequest): CategoryResponse =
        repository.updateName(objectId(id, "id"), validatedName(request.name))?.toResponse()
            ?: throw categoryNotFound()

    suspend fun delete(id: String) {
        val objectId = objectId(id, "id")
        val category = repository.findById(objectId) ?: throw categoryNotFound()
        if (category.courseCount > 0) throw ApiException.Conflict(
            "CATEGORY_IN_USE", "The category is referenced by one or more courses."
        )
        if (!repository.delete(objectId)) throw categoryNotFound()
    }

    suspend fun requireExists(id: ObjectId) {
        if (repository.findById(id) == null) throw categoryNotFound()
    }

    suspend fun adjustCourseCount(id: ObjectId, delta: Int) {
        if (!repository.adjustCourseCount(id, delta)) throw categoryNotFound()
    }

    private fun validatedName(raw: String): String = raw.trim().also {
        if (it.isBlank()) throw ApiException.Validation(fields = mapOf("name" to "REQUIRED"))
        if (it.length > 120) throw ApiException.Validation(fields = mapOf("name" to "TOO_LONG"))
    }

    private fun slugify(name: String) = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun CategoryDocument.toResponse() = CategoryResponse(
        requireNotNull(id).toHexString(), name, slug, courseCount,
    )

    private fun objectId(value: String, field: String) = try { ObjectId(value) } catch (_: IllegalArgumentException) {
        throw ApiException.Validation(fields = mapOf(field to "INVALID"))
    }

    private fun categoryNotFound() = ApiException.NotFound("CATEGORY_NOT_FOUND", "The category was not found.")
}
