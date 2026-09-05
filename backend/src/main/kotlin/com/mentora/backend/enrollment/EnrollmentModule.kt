package com.mentora.backend.enrollment

import com.mentora.backend.enrollment.repository.EnrollmentRepository
import com.mentora.backend.enrollment.service.EnrollmentService
import org.koin.dsl.module

val enrollmentModule = module {
    single { EnrollmentRepository(get()) }
    single { EnrollmentService(get(), get(), get(), get()) }
}
