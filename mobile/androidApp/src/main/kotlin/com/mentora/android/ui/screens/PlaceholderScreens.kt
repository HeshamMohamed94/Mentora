package com.mentora.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mentora.android.ui.components.PrimaryButton

/**
 * T6 — placeholder screens for every route in `navigation/Destinations.kt`. Per
 * `execution/PHASE_4_ANDROID_PLAN.md` T6's own "Do NOT build real screen content" instruction, every
 * screen below is `Text("... (placeholder)")`-level, nothing more, EXCEPT the handful the task
 * explicitly calls out as needing a real, testable mechanism wired through them (still not real
 * content/design — no styling, no real course data, no credential form):
 * - [DemoCheckoutScreen]: a minimal trigger for the Purchase-Success back-stack mechanism.
 * - [ProfileScreen]: one trigger into `Settings`, since Settings is a real registered destination
 *   this task's manual smoke test needs to be able to reach.
 *
 * Real screen content for every one of these (Course Details' CourseCard, ...) is Tasks 10-18's job —
 * this file's composables are the exact call sites those tasks replace, without touching
 * `MentoraNavHost.kt`.
 *
 * T7 note: `LoginScreen`/`RegisterScreen` are no longer placeholders here — Task 7 replaced them with
 * real credential-form screens in `com.mentora.android.ui.auth` (`LoginScreen.kt`/`RegisterScreen.kt`).
 * See that package's kdoc for why neither screen navigates on its own success (the pending-intent-
 * return mechanism this file's kdoc used to describe is unchanged — `MentoraNavHost`'s own
 * `LaunchedEffect(authState)` still owns it).
 *
 * T9 note: `ExploreScreen`/`LearningPathsScreen` are no longer placeholders here — Task 9 replaced
 * them with a real screen in `com.mentora.android.ui.explore` (`ExploreScreen.kt`); the old
 * `LearningPathsScreen` placeholder is gone entirely (folded into that screen's own Learning Paths
 * tab — see that file's kdoc for why). [LearningPathDetailsScreen] (the per-path detail screen) is
 * still a placeholder here; that's Task 16's job.
 *
 * T10 note: `CourseDetailsScreen` is no longer a placeholder here — Task 10 replaced it with a real
 * screen in `com.mentora.android.ui.coursedetails` (`CourseDetailsScreen.kt`).
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Home (placeholder)", modifier)

@Composable
fun MyLearningScreen(modifier: Modifier = Modifier) = PlaceholderScreen("My Learning (placeholder)", modifier)

@Composable
fun AiTutorScreen(modifier: Modifier = Modifier) = PlaceholderScreen("AI Tutor (placeholder)", modifier)

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile (placeholder)", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = "Settings", onClick = onOpenSettings)
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Settings (placeholder)", modifier)

@Composable
fun LearningPathDetailsScreen(pathId: String, modifier: Modifier = Modifier) =
    PlaceholderScreen("Learning Path Details (placeholder): $pathId", modifier)

@Composable
fun DemoCheckoutScreen(courseId: String, onCompletePurchase: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Demo Checkout (placeholder): $courseId", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = "Complete Demo Purchase", onClick = onCompletePurchase)
    }
}

@Composable
fun PurchaseSuccessScreen(courseId: String, modifier: Modifier = Modifier) =
    PlaceholderScreen("Purchase Success (placeholder): $courseId", modifier)

@Composable
fun CoursePlayerScreen(
    courseId: String,
    lessonId: String?,
    onTakeQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Course Player (placeholder): $courseId" + (lessonId?.let { " / lesson $it" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
        )
        // Real T6 mechanism: Course Player's automatic-after-last-lesson push into Quiz
        // (ux/NAVIGATION_SPEC.md § 3) — the trigger this task's nav-hidden-on-Quiz instrumented
        // test needs to actually reach that route.
        PrimaryButton(text = "Take Quiz", onClick = onTakeQuiz)
    }
}

@Composable
fun QuizScreen(courseId: String, modifier: Modifier = Modifier) =
    PlaceholderScreen("Quiz (placeholder): $courseId", modifier)

@Composable
fun QuizResultsScreen(courseId: String, attemptId: String?, modifier: Modifier = Modifier) =
    PlaceholderScreen(
        "Quiz Results (placeholder): $courseId" + (attemptId?.let { " / attempt $it" } ?: ""),
        modifier,
    )

@Composable
fun CertificatesScreen(modifier: Modifier = Modifier) = PlaceholderScreen("Certificates (placeholder)", modifier)

@Composable
fun CertificateDetailScreen(certificateId: String, modifier: Modifier = Modifier) =
    PlaceholderScreen("Certificate Detail (placeholder): $certificateId", modifier)

@Composable
private fun PlaceholderScreen(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
