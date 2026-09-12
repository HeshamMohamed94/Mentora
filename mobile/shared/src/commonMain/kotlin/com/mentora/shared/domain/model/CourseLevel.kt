package com.mentora.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A course's difficulty level, restricted to the backend's fixed validated set
 * (`CourseService.kt`'s private `LEVELS = setOf("beginner", "intermediate", "advanced")`).
 * [wireValue] duplicates the [SerialName] on each entry deliberately: [SerialName] drives
 * JSON (de)serialization when this type is used directly as a DTO field type (see
 * `data/network/dto/CourseDto.kt`), while [wireValue] is what `CatalogRepositoryImpl`'s
 * query-string builder sends back out as the `?level=` filter value — the same string, but two
 * different code paths need it, so it is defined once here rather than re-derived at either call
 * site.
 *
 * Deliberately a separate, unrelated type from [ContentLanguage] and
 * [com.mentora.shared.settings.AppLocale] — never conflate "which fixed string set is this."
 */
@Serializable
enum class CourseLevel(val wireValue: String) {
    @SerialName("beginner") Beginner("beginner"),
    @SerialName("intermediate") Intermediate("intermediate"),
    @SerialName("advanced") Advanced("advanced"),
}
