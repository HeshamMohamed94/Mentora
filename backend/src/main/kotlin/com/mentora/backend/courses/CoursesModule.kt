package com.mentora.backend.courses

import com.mentora.backend.courses.repository.CourseRepository
import com.mentora.backend.courses.service.CourseService
import org.koin.dsl.module

val coursesModule = module {
    single { CourseRepository(get()) }
    single { CourseService(get(), get(), get(), get()) }
}
