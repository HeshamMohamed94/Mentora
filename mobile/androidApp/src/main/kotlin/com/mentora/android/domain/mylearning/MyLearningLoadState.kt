package com.mentora.android.domain.mylearning

import com.mentora.shared.data.network.ApiErrorCode

/**
 * T12 — the one shared load-state shape for [GetMyLearningWithProgressUseCase]'s result, used by both
 * `HomeViewModel` and `MyLearningViewModel` (the same join, per G3 — see that use case's own kdoc for
 * why this lives once here rather than as two near-duplicate per-screen sealed types).
 */
sealed interface MyLearningLoadState {
    data object Loading : MyLearningLoadState
    data class Loaded(val items: List<LearningItemWithProgress>) : MyLearningLoadState
    data class Error(val code: ApiErrorCode) : MyLearningLoadState
}
