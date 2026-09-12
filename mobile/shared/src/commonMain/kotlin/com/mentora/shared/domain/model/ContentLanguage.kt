package com.mentora.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A course's authored metadata/content language — restricted to the backend's fixed validated set
 * (`CourseService.kt`'s private `LANGUAGES = setOf("en", "ar")`).
 *
 * This is a course-METADATA concept, completely unrelated to
 * [com.mentora.shared.settings.AppLocale] (the UI's active display language), even though the two
 * currently share the same two wire values ("en"/"ar") — `execution/PHASE_3_KMP_PLAN.md` Task 7
 * AC #6 requires zero shared code path between them. There is deliberately no conversion function
 * between [ContentLanguage] and `AppLocale` anywhere in `shared`; a caller that genuinely needs to
 * compare the two must do so explicitly, by wire-value string, at its own call site — never by
 * adding a function here or on `AppLocale` that accepts "either kind of language."
 */
@Serializable
enum class ContentLanguage(val wireValue: String) {
    @SerialName("en") English("en"),
    @SerialName("ar") Arabic("ar"),
}
