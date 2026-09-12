package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.enrollment.CompleteDemoCheckoutUseCase
import com.mentora.shared.domain.usecase.enrollment.GetCheckoutPreviewUseCase
import com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase
import com.mentora.shared.domain.usecase.enrollment.ListEnrollmentsUseCase
import org.koin.core.Koin

/** Task 8's enrollment/demo-checkout domain. */
class EnrollmentFacade internal constructor(koin: Koin) {
    val getCheckoutPreview: GetCheckoutPreviewUseCase = koin.get()
    val completeDemoCheckout: CompleteDemoCheckoutUseCase = koin.get()
    val listEnrollments: ListEnrollmentsUseCase = koin.get()
    val getMyLearning: GetMyLearningUseCase = koin.get()
}
