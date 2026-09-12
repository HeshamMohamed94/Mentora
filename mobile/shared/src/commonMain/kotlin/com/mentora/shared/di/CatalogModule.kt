package com.mentora.shared.di

import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.catalog.CatalogRepositoryImpl
import com.mentora.shared.domain.usecase.catalog.GetCourseCurriculumUseCase
import com.mentora.shared.domain.usecase.catalog.GetCourseDetailsUseCase
import com.mentora.shared.domain.usecase.catalog.ListCategoriesUseCase
import com.mentora.shared.domain.usecase.catalog.SearchCoursesUseCase
import org.koin.dsl.module

/** Task 7's catalog domain (categories/courses/curriculum/search). */
internal val catalogModule = module {
    single<CatalogRepository> { CatalogRepositoryImpl(get(), get()) }

    factory { ListCategoriesUseCase(get()) }
    factory { SearchCoursesUseCase(get()) }
    factory { GetCourseDetailsUseCase(get()) }
    factory { GetCourseCurriculumUseCase(get()) }
}
