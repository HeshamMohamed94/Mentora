package com.mentora.shared.domain.model

/**
 * Mirrors the backend's `CategoryResponse` (`backend/src/main/kotlin/com/mentora/backend/categories
 * /service/CategoryService.kt:9`) field-for-field: `id, name, slug, courseCount`.
 *
 * `GET /api/v1/categories` returns a plain JSON array as the envelope's `data` — never
 * `CursorPage<Category>` — see `CategoryRoutes.kt:22`'s `respondData` (not `respondPage`).
 */
data class Category(
    val id: String,
    val name: String,
    val slug: String,
    val courseCount: Int,
)
