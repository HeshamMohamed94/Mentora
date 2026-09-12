package com.mentora.shared.domain.usecase.user

import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.PreferenceStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes [PreferenceStore.locale] to the use-case layer — a platform UI observes the active UI
 * locale through this use case rather than reaching into `settings.PreferenceStore` directly from
 * outside `domain` (`execution/PHASE_3_KMP_PLAN.md` Task 6).
 */
class ObserveLocaleUseCase(private val preferenceStore: PreferenceStore) {
    operator fun invoke(): StateFlow<AppLocale> = preferenceStore.locale
}
