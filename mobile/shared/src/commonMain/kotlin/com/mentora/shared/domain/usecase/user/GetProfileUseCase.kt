package com.mentora.shared.domain.usecase.user

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User

/**
 * `GET /users/me`. On success, [UserRepository]'s real implementation also updates
 * [com.mentora.shared.auth.SessionManager]'s current
 * [com.mentora.shared.auth.AuthState.Authenticated] user as a side effect — most importantly
 * closing the `user = null` gap
 * [com.mentora.shared.data.repository.auth.AuthRepositoryImpl.restoreSession] deliberately leaves
 * after a cold-start restore (Task 5). This use case itself stays repository-interface-only, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 6's "each depending only on repository interfaces" rule.
 */
class GetProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(): ApiResult<User> = repository.getProfile()
}
