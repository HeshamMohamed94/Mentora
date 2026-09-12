package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.catalog.GetCourseCurriculumUseCase
import com.mentora.shared.domain.usecase.catalog.GetCourseDetailsUseCase
import com.mentora.shared.domain.usecase.catalog.ListCategoriesUseCase
import com.mentora.shared.domain.usecase.catalog.SearchCoursesUseCase
import org.koin.core.Koin

/** Task 7's catalog domain — e.g. `sdk.catalog.searchCourses(...)`. */
class CatalogFacade internal constructor(koin: Koin) {
    val listCategories: ListCategoriesUseCase = koin.get()
    val searchCourses: SearchCoursesUseCase = koin.get()
    val getCourseDetails: GetCourseDetailsUseCase = koin.get()
    val getCourseCurriculum: GetCourseCurriculumUseCase = koin.get()
}
