package com.mentora.android.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.MentoraIconButton
import com.mentora.android.ui.components.MentoraIconName

/**
 * T6 — a minimal, generic top bar: a real page title, plus correct back-button-vs-not logic (tab
 * roots show none, pushed destinations show one that pops the back stack). Per-screen custom top
 * bars (Explore's search field, Course Player's close/back + progress chrome, ...) are later tasks'
 * concern per `execution/PHASE_4_ANDROID_PLAN.md` T6's own scope note — this is deliberately the
 * only top bar rendered for every screen in this task.
 */
@Composable
fun MentoraTopBar(
    title: String,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = MentoraDimens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            MentoraIconButton(
                icon = MentoraIconName.ArrowBack,
                contentDescription = "Back",
                onClick = onBackClick,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = if (showBackButton) MentoraDimens.spacing.space2 else MentoraDimens.spacing.space0),
        )
    }
}
