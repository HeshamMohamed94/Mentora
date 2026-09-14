package com.mentora.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.domain.mylearning.GetMyLearningWithProgressUseCase
import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateSummary
import com.mentora.shared.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** `ux/SCREEN_UX_SPECS.md § 16`'s primary content — a real, retryable [ProfileContentState.Error]
 *  on failure, same "this IS the screen's own content" philosophy as every other primary-content
 *  load in this phase (`CourseDetailsViewModel.course`, `MyLearningViewModel.items`). */
sealed interface ProfileContentState {
    data object Loading : ProfileContentState
    data class Loaded(val user: User) : ProfileContentState
    data class Error(val code: ApiErrorCode) : ProfileContentState
}

/** § 16 content item 2, "Basic stats (courses completed, certificates earned)" — best-effort,
 *  secondary, `null` while loading or on failure (same "just stays empty" philosophy
 *  `MyLearningViewModel.followedPaths`/`certificates` already establish for their own secondary
 *  modules) rather than a load state of its own; the Screen simply omits the stats row until this
 *  resolves. A stats-load hiccup must never block the primary profile content above from rendering. */
data class ProfileStats(val coursesCompleted: Int, val certificatesEarned: Int)

/** Review fix (LOW): `fields["name"] == "REQUIRED"`/`"TOO_LONG"` (client-side
 *  [com.mentora.shared.domain.usecase.user.UpdateProfileUseCase] mirroring the backend's own
 *  `UserService` validation) route to their own inline field message — same D51-established pattern
 *  `AuthViewModel`'s `EmailFieldError`/`PasswordFieldError` already use for Register's fields, per
 *  `ux/SCREEN_UX_SPECS.md § 16`'s own "inline field errors preferred where applicable." Any other
 *  failure code (a real network/server error) falls through to [ProfileUiState.saveNameError]'s
 *  generic [ApiErrorCode] slot instead. */
enum class NameFieldError { Required, TooLong }

/** Mirrors `AuthViewModel.mapRegisterFailure`'s exact shape — pure function, no `MentoraSdk`, no
 *  Android runtime, independently testable. */
internal fun mapUpdateProfileFailure(failure: ApiResult.Failure): Pair<NameFieldError?, ApiErrorCode?> {
    val nameError = when (failure.fields?.get("name")) {
        "REQUIRED" -> NameFieldError.Required
        "TOO_LONG" -> NameFieldError.TooLong
        else -> null
    }
    val generalError = if (nameError == null) failure.code else null
    return nameError to generalError
}

data class ProfileUiState(
    val content: ProfileContentState = ProfileContentState.Loading,
    val stats: ProfileStats? = null,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val isSavingName: Boolean = false,
    val saveNameFieldError: NameFieldError? = null,
    val saveNameError: ApiErrorCode? = null,
    val isLoggingOut: Boolean = false,
)

/**
 * T18 — Profile's ViewModel. Same lambda-constructor seam as every prior task's ViewModel.
 *
 * **"Courses completed"** reuses [GetMyLearningWithProgressUseCase] (Task 12's own G3 join) and
 * counts entries whose `progress.courseCompletedAt != null` — the same server-derived definition
 * `LearningPathDetailsViewModel`'s own D91 fix established as authoritative over a lesson-count-only
 * `completionPercent >= 100` reading (a course with every lesson watched but its quiz not yet passed
 * must NOT count as completed here either). **"Certificates earned"** fully pages
 * [listCertificates] (never trusts page 1 alone — the same discipline `CourseDetailsViewModel
 * .isEnrolledIn`/[GetMyLearningWithProgressUseCase] itself already establish) and counts the total.
 * Both loads run in parallel with the primary [ProfileContentState] load, not sequenced after it.
 *
 * **Edit Profile** is name-only — [User.avatarMediaId] is a known dead field nothing on the backend
 * ever writes (`User`'s own kdoc, D44's already-disclosed Web-side precedent), and no password-change
 * endpoint exists at all (same D44 precedent) — so this screen builds no avatar-upload or password
 * control, matching Web's own Task 10 disclosed scope exactly. [onSaveNameTapped] re-derives
 * [ProfileContentState.Loaded] from the fresh [User] [updateProfile] itself returns rather than
 * patching the edited name into the existing one, so any other server-computed field a save
 * incidentally touched (none today, but nothing here assumes that stays true) is never stale.
 */
class ProfileViewModel(
    private val getProfile: suspend () -> ApiResult<User>,
    private val updateProfile: suspend (String) -> ApiResult<User>,
    private val getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>>,
    private val listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>>,
    private val logout: suspend () -> ApiResult<Unit>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadStats()
    }

    /** Review fix (MEDIUM): retries BOTH loads, not just [loadProfile] — a stats-load failure almost
     *  always co-occurs with a primary-content failure (the same network outage causes both), so a
     *  retry that only re-ran [loadProfile] left [ProfileUiState.stats] permanently `null` (the Screen
     *  omits that row forever) even after connectivity returned and the primary content recovered. */
    fun onRetryTapped() {
        loadProfile()
        loadStats()
    }

    private fun loadProfile() {
        _uiState.update { it.copy(content = ProfileContentState.Loading) }
        viewModelScope.launch {
            when (val result = getProfile()) {
                is ApiResult.Success -> _uiState.update { it.copy(content = ProfileContentState.Loaded(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(content = ProfileContentState.Error(result.code)) }
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val learningResult = getMyLearningWithProgress()
            if (learningResult !is ApiResult.Success) return@launch // Best-effort — see this class's own kdoc.
            val coursesCompleted = learningResult.data.count { it.progress.courseCompletedAt != null }

            val certificates = mutableListOf<CertificateSummary>()
            var cursor: String? = null
            do {
                val page = listCertificates(cursor, CertificatesPageLimit)
                if (page !is ApiResult.Success) return@launch // Best-effort — a mid-page failure just drops the stat.
                certificates += page.data.items
                cursor = page.data.nextCursor
            } while (cursor != null)

            _uiState.update { it.copy(stats = ProfileStats(coursesCompleted, certificates.size)) }
        }
    }

    fun onEditProfileTapped() {
        val user = (_uiState.value.content as? ProfileContentState.Loaded)?.user ?: return
        _uiState.update { it.copy(isEditing = true, editedName = user.name, saveNameFieldError = null, saveNameError = null) }
    }

    fun onNameChanged(text: String) {
        _uiState.update { it.copy(editedName = text, saveNameFieldError = null, saveNameError = null) }
    }

    fun onCancelEditTapped() {
        _uiState.update { it.copy(isEditing = false, saveNameFieldError = null, saveNameError = null) }
    }

    fun onSaveNameTapped() {
        if (_uiState.value.isSavingName) return
        val name = _uiState.value.editedName
        _uiState.update { it.copy(isSavingName = true, saveNameFieldError = null, saveNameError = null) }
        viewModelScope.launch {
            when (val result = updateProfile(name)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(content = ProfileContentState.Loaded(result.data), isEditing = false, isSavingName = false)
                }
                is ApiResult.Failure -> {
                    val (fieldError, generalError) = mapUpdateProfileFailure(result)
                    _uiState.update { it.copy(isSavingName = false, saveNameFieldError = fieldError, saveNameError = generalError) }
                }
            }
        }
    }

    /** [com.mentora.android.navigation.MentoraNavHost]'s existing `LaunchedEffect(authState)` (built in Task 6, unchanged since)
     *  already reacts to an `Authenticated -> Unauthenticated` transition by resetting the whole nav
     *  shell back to guest mode — this call is the ONLY thing this screen needs to do; it builds no
     *  navigation logic of its own. [ProfileUiState.isLoggingOut] exists purely so the Logout control
     *  can show its own brief loading state and not be double-tappable, not because anything here
     *  waits on a result to decide where to go next. */
    fun onLogoutTapped() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(isLoggingOut = true) }
        viewModelScope.launch {
            logout()
            // No `isLoggingOut = false` reset on either branch: success tears this whole screen down
            // via the auth-state-driven nav reset above before another frame would show it; a failure
            // (network-only — `LogoutUseCase`'s own repository still clears local session state
            // first per `execution/DECISIONS_LOG.md`'s auth precedents) reaches the identical outcome
            // for this screen's purposes, so there is no failure UI to return this flag for.
        }
    }

    companion object {
        /** Mirrors `MyLearningViewModel.CertificatesPageLimit` — generous per-page size, this loop
         *  still fully pages regardless, so the exact value only affects round-trip count. */
        const val CertificatesPageLimit: Int = 20
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `MyLearningViewModel.Factory`'s exact idiom,
     *  including constructing the shared [GetMyLearningWithProgressUseCase] the same way. */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(
            getProfile = sdk.user.getProfile::invoke,
            updateProfile = sdk.user.updateProfile::invoke,
            getMyLearningWithProgress = GetMyLearningWithProgressUseCase(
                getMyLearning = sdk.enrollment.getMyLearning::invoke,
                getCourseProgress = sdk.progress.getCourseProgress::invoke,
            )::invoke,
            listCertificates = sdk.certificates.listCertificates::invoke,
            logout = sdk.auth.logout::invoke,
        ) as T
    }
}
