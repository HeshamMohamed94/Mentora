package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.certificate.GetCertificateUseCase
import com.mentora.shared.domain.usecase.certificate.ListCertificatesUseCase
import org.koin.core.Koin

/** Task 11's certificates domain — read-only by design, no issuance action exists. */
class CertificateFacade internal constructor(koin: Koin) {
    val listCertificates: ListCertificatesUseCase = koin.get()
    val getCertificate: GetCertificateUseCase = koin.get()
}
