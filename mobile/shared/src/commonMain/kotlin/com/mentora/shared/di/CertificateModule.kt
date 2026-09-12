package com.mentora.shared.di

import com.mentora.shared.data.repository.certificate.CertificateRepository
import com.mentora.shared.data.repository.certificate.CertificateRepositoryImpl
import com.mentora.shared.domain.usecase.certificate.GetCertificateUseCase
import com.mentora.shared.domain.usecase.certificate.ListCertificatesUseCase
import org.koin.dsl.module

/** Task 11's certificates domain (read-only — no issuance use case exists, by design). */
internal val certificateModule = module {
    single<CertificateRepository> { CertificateRepositoryImpl(get()) }

    factory { ListCertificatesUseCase(get()) }
    factory { GetCertificateUseCase(get()) }
}
