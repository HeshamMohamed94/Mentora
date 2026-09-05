package com.mentora.backend.certificates

import com.mentora.backend.certificates.repository.CertificateRepository
import com.mentora.backend.certificates.service.CertificateService
import org.koin.dsl.module

val certificatesModule = module {
    single { CertificateRepository(get()) }
    single { CertificateService(get(), get(), get(), get(), get(), get()) }
}
