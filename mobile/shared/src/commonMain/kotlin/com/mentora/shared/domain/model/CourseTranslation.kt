package com.mentora.shared.domain.model

/**
 * One locale's translated title+description for a course's discovery metadata — mirrors the
 * backend's `CourseTranslationDto` (`CourseService.kt:38`) field-for-field.
 *
 * Distinct from [ContentLanguage]: a course's lesson CONTENT is authored in exactly one
 * [ContentLanguage], but its title/description metadata may additionally be translated for the
 * other supported locale so the course is discoverable (and its metadata resolvable) in both — see
 * `CourseRepository.kt`'s `resolvedTitle`/`resolvedDescription`.
 *
 * [Course.translations] keeps its map key as a raw locale `String` ("en"/"ar") rather than
 * [ContentLanguage], matching `execution/PHASE_3_KMP_PLAN.md` Task 7 AC #4's literal
 * `Map<String, CourseTranslation>` typing — the entry for a course's own base `contentLanguage` is
 * deliberately never present in that map (the base `title`/`description` fields already are that
 * language's text), so typing the key as [ContentLanguage] would misleadingly imply every language
 * always maps to something.
 */
data class CourseTranslation(
    val title: String,
    val description: String,
)
