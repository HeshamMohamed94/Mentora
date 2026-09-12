package com.mentora.shared.data.repository.user

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.User

/**
 * The only user-profile network surface `domain/usecase/user` use cases are allowed to depend on —
 * a use case never touches [com.mentora.shared.data.network.ApiClient]/`UserRepositoryImpl`/
 * [com.mentora.shared.auth.SessionManager] directly, mirroring
 * [com.mentora.shared.data.repository.auth.AuthRepository]'s pattern
 * (`execution/PHASE_3_KMP_PLAN.md` Task 6).
 */
interface UserRepository {
    /** `GET /users/me` → the full [User] profile. */
    suspend fun getProfile(): ApiResult<User>

    /**
     * `PATCH /users/me` — the backend accepts ONLY `name`/`preferredLocale`
     * (`UserService.kt:23`'s `UpdateProfileRequest`). Pass only the field(s) that actually
     * changed; a `null` argument here is never sent as a literal wire `null`, it is simply omitted
     * from the request body (see [com.mentora.shared.data.network.dto.UpdateProfileRequestDto]).
     */
    suspend fun updateProfile(name: String? = null, preferredLocale: String? = null): ApiResult<User>
}
