package com.mentora.backend.media

import com.mentora.backend.media.repository.MediaRepository
import com.mentora.backend.media.service.MediaService
import com.mentora.backend.media.storage.LocalFileSystemMediaStorage
import com.mentora.backend.media.storage.MediaStorage
import org.koin.dsl.module

val mediaModule = module {
    single { MediaRepository(get()) }
    single { LocalFileSystemMediaStorage(get()) as MediaStorage }
    single { MediaService(get(), get(), get(), get(), get()) }
}
