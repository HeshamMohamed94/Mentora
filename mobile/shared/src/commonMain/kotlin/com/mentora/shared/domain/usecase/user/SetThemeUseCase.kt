package com.mentora.shared.domain.usecase.user

import com.mentora.shared.settings.PreferenceStore
import com.mentora.shared.settings.ThemePreference

/**
 * Local-only theme switch — no backend theme field exists at all
 * (`execution/PHASE_3_KMP_PLAN.md` Task 6), so this use case has zero network/repository
 * dependency of any kind, by construction rather than merely by convention: there is nothing here
 * that could accidentally grow a network call later.
 */
class SetThemeUseCase(private val preferenceStore: PreferenceStore) {
    operator fun invoke(theme: ThemePreference) {
        preferenceStore.setTheme(theme)
    }
}
