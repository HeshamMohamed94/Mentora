package com.mentora.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.Avatar
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.MentoraAvatarSize
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.MentoraTextField
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.User

/**
 * T18 — the real Profile screen, replacing `ui/screens/PlaceholderScreens.kt`'s placeholder. Built
 * from `ux/SCREEN_UX_SPECS.md § 16` directly (no exact-showcase mockup extracted by Task 3 covers
 * Profile/Settings — the 8 extracted screens are home/explore/course-details/my-learning/ai-tutor/
 * course-player/demo-checkout/purchase-success). Content order matches § 16 exactly: avatar+name+
 * email, stats, Edit Profile, Settings entry, Logout. See [ProfileViewModel]'s own kdoc for the two
 * disclosed scope resolutions (name-only editing, Logout's placement) this screen and [SettingsScreen]
 * share.
 */
@Composable
fun ProfileScreen(sdk: MentoraSdk, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory(sdk))
    val uiState by viewModel.uiState.collectAsState()

    ProfileContent(
        uiState = uiState,
        onRetryTapped = viewModel::onRetryTapped,
        onEditProfileTapped = viewModel::onEditProfileTapped,
        onNameChanged = viewModel::onNameChanged,
        onCancelEditTapped = viewModel::onCancelEditTapped,
        onSaveNameTapped = viewModel::onSaveNameTapped,
        onOpenSettings = onOpenSettings,
        onLogoutTapped = viewModel::onLogoutTapped,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileContent(
    uiState: ProfileUiState,
    onRetryTapped: () -> Unit,
    onEditProfileTapped: () -> Unit,
    onNameChanged: (String) -> Unit,
    onCancelEditTapped: () -> Unit,
    onSaveNameTapped: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogoutTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = uiState.content) {
        is ProfileContentState.Loading -> FullScreenLoadingState(
            modifier = modifier.fillMaxSize().testTag(ProfileScreenTestTag),
            contentDescriptionLabel = stringResource(R.string.profile_loading_content_description),
        )

        is ProfileContentState.Error -> ErrorState(
            title = stringResource(R.string.profile_error_title),
            description = apiErrorMessage(content.code),
            onRetryClick = onRetryTapped,
            retryLabel = stringResource(R.string.profile_retry_action),
            modifier = modifier.fillMaxSize().padding(MentoraDimens.spacing.space4).testTag(ProfileScreenTestTag),
        )

        is ProfileContentState.Loaded -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MentoraDimens.spacing.space4)
                .testTag(ProfileScreenTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space6),
        ) {
            ProfileHeader(user = content.user)

            if (uiState.stats != null) {
                ProfileStatsRow(stats = uiState.stats)
            }

            if (uiState.isEditing) {
                EditNameSection(
                    editedName = uiState.editedName,
                    isSaving = uiState.isSavingName,
                    // Review fix (LOW): a real REQUIRED/TOO_LONG field error routes to its own
                    // specific inline message (D51 precedent — `AuthViewModel`'s identical
                    // `fields[...]`-routing pattern for Register); any other failure code falls back
                    // to the generic `apiErrorMessage` copy.
                    errorMessage = when (uiState.saveNameFieldError) {
                        NameFieldError.Required -> stringResource(R.string.profile_name_error_required)
                        NameFieldError.TooLong -> stringResource(R.string.profile_name_error_too_long)
                        null -> uiState.saveNameError?.let { apiErrorMessage(it) }
                    },
                    onNameChanged = onNameChanged,
                    onCancelTapped = onCancelEditTapped,
                    onSaveTapped = onSaveNameTapped,
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.profile_edit_profile_action),
                    onClick = onEditProfileTapped,
                    modifier = Modifier.fillMaxWidth().testTag(ProfileEditButtonTestTag),
                )
            }

            MentoraTextButton(
                text = stringResource(R.string.profile_settings_action),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().testTag(ProfileSettingsButtonTestTag),
            )

            MentoraTextButton(
                text = stringResource(R.string.profile_logout_action),
                onClick = onLogoutTapped,
                loading = uiState.isLoggingOut,
                modifier = Modifier.fillMaxWidth().testTag(ProfileLogoutButtonTestTag),
            )
        }
    }
}

@Composable
private fun ProfileHeader(user: User) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
        // Review fix (MEDIUM): `ux/SCREEN_UX_SPECS.md § 16`'s own accessibility line names this
        // exact requirement ("avatar has an accessible name") — [Avatar] itself applies no semantics
        // at all (a real `Text` initials node, unlabeled otherwise), so TalkBack was announcing raw
        // initials letters instead of a real name. `clearAndSetSemantics` replaces that noise with one
        // real accessible name, at this call site only (not a change to the shared component).
        val avatarContentDescription = stringResource(R.string.profile_avatar_content_description, user.name)
        Avatar(
            name = user.name,
            size = MentoraAvatarSize.XLarge,
            modifier = Modifier
                .clearAndSetSemantics { contentDescription = avatarContentDescription }
                .testTag(ProfileAvatarTestTag),
        )
        Text(text = user.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileStatsRow(stats: ProfileStats) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(ProfileStatsRowTestTag),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ProfileStatItem(
            value = stats.coursesCompleted.toString(),
            label = stringResource(R.string.profile_stat_courses_completed_label),
        )
        ProfileStatItem(
            value = stats.certificatesEarned.toString(),
            label = stringResource(R.string.profile_stat_certificates_earned_label),
        )
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1)) {
        // Locked-numerals rule (`design-system/LOCALIZATION.md § 8`): the count itself is formatted
        // via `Int.toString()` at the call site above, never a raw `%d`-style format string — this
        // Text just renders that already-resolved string verbatim.
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditNameSection(
    editedName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onNameChanged: (String) -> Unit,
    onCancelTapped: () -> Unit,
    onSaveTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3)) {
        MentoraTextField(
            value = editedName,
            onValueChange = onNameChanged,
            label = stringResource(R.string.profile_name_field_label),
            errorText = errorMessage,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().testTag(ProfileNameFieldTestTag),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3)) {
            MentoraTextButton(
                text = stringResource(R.string.profile_cancel_edit_action),
                onClick = onCancelTapped,
                enabled = !isSaving,
                modifier = Modifier.weight(1f).testTag(ProfileCancelEditButtonTestTag),
            )
            PrimaryButton(
                text = stringResource(R.string.profile_save_name_action),
                onClick = onSaveTapped,
                loading = isSaving,
                modifier = Modifier.weight(1f).testTag(ProfileSaveNameButtonTestTag),
            )
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val ProfileScreenTestTag = "profile-screen"
const val ProfileAvatarTestTag = "profile-avatar"
const val ProfileStatsRowTestTag = "profile-stats-row"
const val ProfileEditButtonTestTag = "profile-edit-button"
const val ProfileNameFieldTestTag = "profile-name-field"
const val ProfileCancelEditButtonTestTag = "profile-cancel-edit-button"
const val ProfileSaveNameButtonTestTag = "profile-save-name-button"
const val ProfileSettingsButtonTestTag = "profile-settings-button"
const val ProfileLogoutButtonTestTag = "profile-logout-button"
