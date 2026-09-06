package com.mentora.backend.learningpaths

import com.mentora.backend.learningpaths.repository.LearningPathRepository
import com.mentora.backend.learningpaths.service.LearningPathService
import org.koin.dsl.module

val learningPathsModule = module {
    single { LearningPathRepository(get()) }
    single { LearningPathService(get(), get(), get()) }
}
