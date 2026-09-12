package com.mentora.shared.domain.usecase.catalog

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.domain.model.Category

/** `GET /api/v1/categories` → a plain `List<Category>` (never `CursorPage`, see [Category]'s kdoc). */
class ListCategoriesUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(): ApiResult<List<Category>> = repository.listCategories()
}
