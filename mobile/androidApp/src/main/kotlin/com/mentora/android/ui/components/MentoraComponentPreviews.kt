package com.mentora.android.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraTheme

/**
 * `@Preview` gallery for T5's component kit — visual inspection surface only, not app UI (mirrors
 * `theme/TokenSwatchPreview.kt`'s own convention: light + dark via two `@Preview`s, plus an
 * RTL-forced preview via `CompositionLocalProvider(LocalLayoutDirection provides
 * LayoutDirection.Rtl)` where mirroring actually matters). Covers Button/TextField/Select/Badge/
 * CategoryChip per the task brief; the rest of the kit (IconButton/PasswordField/SearchField/Avatar/
 * ProgressBar/Tabs/Snackbar) is exercised by the Compose UI test suite instead.
 */
@Composable
private fun ComponentKitGallery() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                PrimaryButton(text = "Enroll now", onClick = {})
                SecondaryButton(text = "Preview", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                TonalButton(text = "Continue", onClick = {})
                MentoraTextButton(text = "Skip", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                PrimaryButton(text = "Loading", onClick = {}, loading = true)
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false, disabledReason = "Complete previous step first")
            }

            var textValue by remember { mutableStateOf("") }
            MentoraTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = "Full name",
                placeholder = "Ada Lovelace",
                helperText = "As it will appear on your certificate",
            )
            MentoraTextField(
                value = "not-an-email",
                onValueChange = {},
                label = "Email",
                errorText = "Enter a valid email address",
            )

            var selected by remember { mutableStateOf<String?>(null) }
            MentoraSelect(
                label = "Category",
                options = listOf(
                    MentoraSelectOption("dev", "Development"),
                    MentoraSelectOption("design", "Design"),
                    MentoraSelectOption("business", "Business", enabled = false),
                ),
                selected = selected,
                onSelect = { selected = it },
                placeholder = "Choose a category",
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                MentoraBadge(text = "New", variant = MentoraBadgeVariant.Brand)
                MentoraBadge(text = "Published", variant = MentoraBadgeVariant.Success)
                MentoraBadge(text = "Draft", variant = MentoraBadgeVariant.Warning)
                MentoraBadge(text = "Failed", variant = MentoraBadgeVariant.Error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                CategoryChip(label = "All", selected = true, onClick = {})
                CategoryChip(label = "Programming", selected = false, onClick = {})
                CategoryChip(label = "Design", selected = false, enabled = false, onClick = {})
            }
        }
    }
}

@Preview(name = "Component kit — Light")
@Composable
private fun ComponentKitGalleryLightPreview() {
    MentoraTheme(darkTheme = false) { ComponentKitGallery() }
}

@Preview(name = "Component kit — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComponentKitGalleryDarkPreview() {
    MentoraTheme(darkTheme = true) { ComponentKitGallery() }
}

@Preview(name = "Component kit — RTL")
@Composable
private fun ComponentKitGalleryRtlPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MentoraTheme(darkTheme = false, arabicScript = true) { ComponentKitGallery() }
    }
}
