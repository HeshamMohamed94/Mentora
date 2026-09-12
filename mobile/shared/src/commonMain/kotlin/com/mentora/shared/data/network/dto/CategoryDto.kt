package com.mentora.shared.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /api/v1/categories` — mirrors
 * `backend/src/main/kotlin/com/mentora/backend/categories/service/CategoryService.kt:9`'s
 * `CategoryResponse` field-for-field.
 *
 * `GET /api/v1/categories` responds with a plain JSON array as the envelope's `data`
 * (`CategoryRoutes.kt:22`'s `respondData`, not `respondPage`) — decoded via
 * `ApiClient.get<List<CategoryDto>>(...)`, never `getPage`.
 */
@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val courseCount: Int,
)
