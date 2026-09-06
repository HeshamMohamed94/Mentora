package com.mentora.backend.instructor

import com.mentora.backend.instructor.repository.InstructorRepository
import com.mentora.backend.instructor.service.InstructorService
import org.koin.dsl.module

val instructorModule = module {
    single { InstructorRepository(get()) }
    single { InstructorService(get()) }
}
