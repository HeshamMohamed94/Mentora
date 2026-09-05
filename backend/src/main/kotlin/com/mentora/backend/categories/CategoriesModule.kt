package com.mentora.backend.categories

import com.mentora.backend.categories.repository.CategoryRepository
import com.mentora.backend.categories.service.CategoryService
import org.koin.dsl.module

val categoriesModule = module {
    single { CategoryRepository(get()) }
    single { CategoryService(get()) }
}
