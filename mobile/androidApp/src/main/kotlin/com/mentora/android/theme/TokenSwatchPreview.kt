package com.mentora.android.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Visual inspection surface for [MentoraTheme]'s tokens — a small color-swatch + type-scale sheet,
 * not app UI. Covers both light and dark via the two @Preview annotations below (Android
 * Studio's Compose preview renders both without needing an emulator).
 */
@Composable
private fun TokenSwatchSheet() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            Text("Mentora tokens", style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                ColorSwatch("primary", MaterialTheme.colorScheme.primary)
                ColorSwatch("secondary", MaterialTheme.colorScheme.secondary)
                ColorSwatch("surface", MaterialTheme.colorScheme.surface)
                ColorSwatch("surfaceElevated", MaterialTheme.colorScheme.surfaceContainerHigh)
                ColorSwatch("error", MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                ColorSwatch("success", MaterialTheme.extendedColors.success)
                ColorSwatch("warning", MaterialTheme.extendedColors.warning)
                ColorSwatch("info", MaterialTheme.extendedColors.info)
            }

            Text("Display large", style = MaterialTheme.typography.displayLarge)
            Text("Heading H1", style = MaterialTheme.typography.headlineLarge)
            Text("Heading H3", style = MaterialTheme.typography.headlineSmall)
            Text("Body medium — the quick brown fox", style = MaterialTheme.typography.bodyMedium)
            Text("Label large", style = MaterialTheme.typography.labelLarge)
            Text("Caption", style = MaterialTheme.typography.labelSmall)

            Text(
                "مرحبا بك في منتورا — Arabic sample (letterSpacing=0, body line-height x1.1)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Surface(
            color = color,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(MentoraDimens.touchTargetMin),
        ) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Preview(name = "Light")
@Composable
private fun TokenSwatchPreviewLight() {
    MentoraTheme(darkTheme = false) {
        TokenSwatchSheet()
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TokenSwatchPreviewDark() {
    MentoraTheme(darkTheme = true) {
        TokenSwatchSheet()
    }
}
