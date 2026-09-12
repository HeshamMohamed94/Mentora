package com.mentora.shared.di

import android.content.Context
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The Android half of the "platform module" shape [com.mentora.shared.di.initKoin]/
 * [com.mentora.shared.MentoraSdk.create] require, per `execution/PHASE_3_KMP_PLAN.md` Task 15.
 * Phase 4's Android app is expected to call this with its application [Context] (e.g. from its
 * `Application.onCreate`) and pass the result straight through to
 * `MentoraSdk.create(environment, platformModule(applicationContext))`. Nothing in `shared` calls
 * this itself — it exists purely as the Android-specific binding source [networkModule]/[authModule]/
 * etc. depend on but cannot construct themselves (they are pure `commonMain`, with no `Context` of
 * their own — see [AndroidTokenStorage]/[AndroidPreferenceStore]'s kdoc, which already documents
 * this exact wiring point).
 */
fun platformModule(context: Context): Module = module {
    single<TokenStorage> { AndroidTokenStorage(context) }
    single<PreferenceStore> { AndroidPreferenceStore(context) }
    single<HttpClientEngine> { defaultHttpClientEngine() }
}
