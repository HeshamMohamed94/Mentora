package com.mentora.shared.di

import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.data.repository.enrollment.EnrollmentRepositoryImpl
import com.mentora.shared.domain.usecase.enrollment.CompleteDemoCheckoutUseCase
import com.mentora.shared.domain.usecase.enrollment.GetCheckoutPreviewUseCase
import com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase
import com.mentora.shared.domain.usecase.enrollment.ListEnrollmentsUseCase
import org.koin.dsl.module

/**
 * Task 8's enrollment/demo-checkout domain. [GetMyLearningUseCase] additionally depends on
 * [CatalogRepository][com.mentora.shared.data.repository.catalog.CatalogRepository] (Task 7,
 * declared in [catalogModule]) — resolved fine regardless of module declaration order.
 */
internal val enrollmentModule = module {
    single<EnrollmentRepository> { EnrollmentRepositoryImpl(get(), get()) }

    factory { GetCheckoutPreviewUseCase(get()) }
    factory { CompleteDemoCheckoutUseCase(get()) }
    factory { ListEnrollmentsUseCase(get()) }
    factory { GetMyLearningUseCase(get(), get()) }
}
