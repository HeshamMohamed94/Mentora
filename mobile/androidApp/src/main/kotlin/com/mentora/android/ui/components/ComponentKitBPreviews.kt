package com.mentora.android.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraTheme

/**
 * `@Preview` gallery for T8's component kit B — visual inspection surface only, not app UI (same
 * convention as `MentoraComponentPreviews.kt`, Task 5's own gallery for kit A).
 *
 * [motifCoverageSeeds] is a small helper (not shipped as production API — file-private) that scans
 * deterministic sample seeds at preview-render time until it has found one seed per [CourseMotif],
 * so the [CourseCard] row below visibly renders all 5 motifs without hand-computing 5 magic hash-
 * colliding strings by hand.
 *
 * **Disclosed limitation — [AppDialog]/[MentoraBottomSheet] previews.** Android Studio's static
 * `@Preview` renderer does not render the contents of `Dialog`/`Popup`-based composables (both
 * [AppDialog] and Material3's `ModalBottomSheet`, which [MentoraBottomSheet] builds on, use one) —
 * a known Compose tooling limitation, not specific to this kit. The two preview functions below are
 * still provided (real, launchable via Studio's "Interactive Mode" / by running the app) so the call
 * site itself is verified to compile and wire up correctly; the static thumbnail may show only the
 * scrim.
 */
private fun motifCoverageSeeds(): List<String> {
    val found = linkedMapOf<CourseMotif, String>()
    var i = 0
    while (found.size < CourseMotif.entries.size && i < 10_000) {
        val seed = "preview-course-$i"
        found.putIfAbsent(motifFor(seed), seed)
        i++
    }
    return CourseMotif.entries.map { found[it] ?: "preview-course-0" }
}

@Composable
private fun ComponentKitBGallery() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space6),
        ) {
            // CourseCard — all 5 motifs, to visually confirm artwork variety.
            Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4)) {
                motifCoverageSeeds().forEachIndexed { index, seed ->
                    CourseCard(
                        title = "Course sample #${index + 1} — a title long enough to test the 2-line clamp",
                        instructorName = "Ada Lovelace",
                        seed = seed,
                        categoryId = null,
                        categoryLabel = "Development",
                        thumbnailContentDescription = "Course thumbnail",
                        actionLabel = if (index % 2 == 0) "Enroll" else "Continue",
                        onActionClick = {},
                        rating = 4.7f,
                        studentCount = 128,
                        durationLabel = "6h 20m",
                        isEnrolled = index % 2 == 1,
                        progress = 0.42f,
                    )
                }
            }

            CourseProgressCard(
                title = "Kotlin Coroutines in Practice",
                instructorName = "Grace Hopper",
                seed = "progress-card-preview",
                categoryId = null,
                categoryLabel = "Development",
                thumbnailContentDescription = "Course thumbnail",
                progress = 0.65f,
                onResumeClick = {},
            )

            LearningPathCard(
                title = "Become a Backend Engineer",
                description = "A guided sequence of courses from fundamentals to production APIs.",
                metaLabel = "6 courses • 12h total",
                actionLabel = "View path",
                onActionClick = {},
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3)) {
                StatCard(value = "87%", label = "Average score", trendDirection = StatTrendDirection.Up, trendLabel = "+4%")
                StatCard(value = "12", label = "Certificates", trendDirection = StatTrendDirection.Down, trendLabel = "-1")
            }

            CertificateCard(
                courseTitle = "User Experience Design Fundamentals",
                metaLabel = "Completed Jan 12, 2026 • Mentora",
                onViewClick = {},
                onShareClick = {},
            )

            Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                AITutorBubble(text = "Here's a summary of the lesson you just watched.", sender = AiTutorSender.Ai)
                AITutorBubble(text = "Can you give me an example?", sender = AiTutorSender.User)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                AITutorQuickAction(label = "Explain this lesson", onClick = {})
                AITutorQuickAction(label = "Quiz me", onClick = {})
            }

            QuestionCard(questionNumber = 3, totalQuestions = 10, questionText = "Which HTTP method is idempotent?")

            // AnswerOption — all 5 states.
            Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                AnswerOption(text = "GET", isSelected = false, isSubmitted = false, isCorrectAnswer = false, onClick = {})
                AnswerOption(text = "POST", isSelected = true, isSubmitted = false, isCorrectAnswer = false, onClick = {})
                AnswerOption(text = "PUT", isSelected = true, isSubmitted = true, isCorrectAnswer = true, onClick = {})
                AnswerOption(text = "DELETE", isSelected = true, isSubmitted = true, isCorrectAnswer = false, onClick = {})
                AnswerOption(text = "PATCH", isSelected = false, isSubmitted = true, isCorrectAnswer = false, onClick = {})
            }

            EmptyState(
                icon = MentoraIconName.Explore,
                title = "No courses yet",
                description = "Courses you enroll in will show up here.",
                actionLabel = "Explore courses",
                onActionClick = {},
            )

            ErrorState(
                title = "Something went wrong",
                description = "We couldn't load this page. Check your connection and try again.",
                onRetryClick = {},
            )

            SuccessState(
                title = "Purchase complete",
                description = "You're enrolled — start learning right away.",
                actionLabel = "Go to course",
                onActionClick = {},
            )

            CourseCardSkeleton(modifier = Modifier)
        }
    }
}

@Composable
private fun AppDialogPreviewContent() {
    var showDialog by remember { mutableStateOf(true) }
    if (showDialog) {
        AppDialog(
            title = "Discard changes?",
            body = "You have unsaved changes. Are you sure you want to leave without saving?",
            confirmLabel = "Discard",
            onConfirm = { showDialog = false },
            onDismissRequest = { showDialog = false },
            dismissLabel = "Cancel",
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MentoraBottomSheetPreviewContent() {
    MentoraBottomSheet(onDismissRequest = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
            AnswerOption(text = "Curriculum item", isSelected = false, isSubmitted = false, isCorrectAnswer = false, onClick = {})
        }
    }
}

@Preview(name = "Component kit B — Light", showBackground = true, heightDp = 2400)
@Composable
private fun ComponentKitBGalleryLightPreview() {
    MentoraTheme(darkTheme = false) { ComponentKitBGallery() }
}

@Preview(name = "Component kit B — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 2400)
@Composable
private fun ComponentKitBGalleryDarkPreview() {
    MentoraTheme(darkTheme = true) { ComponentKitBGallery() }
}

@Preview(name = "AppDialog — Light")
@Composable
private fun AppDialogLightPreview() {
    MentoraTheme(darkTheme = false) { AppDialogPreviewContent() }
}

@Preview(name = "AppDialog — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppDialogDarkPreview() {
    MentoraTheme(darkTheme = true) { AppDialogPreviewContent() }
}

@Preview(name = "MentoraBottomSheet — Light")
@Composable
private fun MentoraBottomSheetLightPreview() {
    MentoraTheme(darkTheme = false) { MentoraBottomSheetPreviewContent() }
}

@Preview(name = "MentoraBottomSheet — Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MentoraBottomSheetDarkPreview() {
    MentoraTheme(darkTheme = true) { MentoraBottomSheetPreviewContent() }
}
