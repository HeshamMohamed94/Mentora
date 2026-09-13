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
 * - [ExploreScreen] / [CourseDetailsScreen]: a minimal functional trigger to push
 *   `CourseDetails`/gate `DemoCheckout` through [com.mentora.android.navigation.decideAuthGate], so
 *   the guest-enroll-gate mechanism is reachable and instrumented-testable.
 * - [DemoCheckoutScreen]: a minimal trigger for the Purchase-Success back-stack mechanism.
 * - [LoginScreen] / [RegisterScreen]: react to (not fake) the pending-intent-return mechanism — see
 *   [LoginScreen]'s own kdoc for why there is deliberately no "pretend login succeeded" button here.
 * - [ProfileScreen]: one trigger into `Settings`, since Settings is a real registered destination
 *   this task's manual smoke test needs to be able to reach.
 *
 * Real screen content for every one of these (Login/Register credential forms, Explore's course
 * grid, Course Details' CourseCard, ...) is Tasks 7-18's job — this file's composables are the exact
 * call sites those tasks replace, without touching `MentoraNavHost.kt`.
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
fun LearningPathsScreen(onOpenPathDetails: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Learning Paths (placeholder)", style = MaterialTheme.typography.bodyLarge)
        PrimaryButton(text = "Open a Learning Path", onClick = { onOpenPathDetails("path-1") })
    }
}

@Composable
fun LearningPathDetailsScreen(pathId: String, modifier: Modifier = Modifier) =
    PlaceholderScreen("Learning Path Details (placeholder): $pathId", modifier)

@Composable
fun ExploreScreen(
    onOpenCourseDetails: (String) -> Unit,
    onOpenLearningPaths: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Explore (placeholder)", style = MaterialTheme.typography.bodyLarge)
        // Minimal real navigation trigger (T6 AC) — a real course grid/SearchField is Task 9's job.
        PrimaryButton(text = "Open Course Details", onClick = { onOpenCourseDetails("course-1") })
        PrimaryButton(text = "Learning Paths", onClick = onOpenLearningPaths)
    }
}

@Composable
fun CourseDetailsScreen(
    courseId: String,
    onEnrollRequiringAuth: () -> Unit,
    onContinueLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Course Details (placeholder): $courseId", style = MaterialTheme.typography.bodyLarge)
        // Real T6 mechanism: enrolling is auth-gated (ux/NAVIGATION_SPEC.md § 6) — a real
        // CourseCard/pricing/curriculum-outline display is Task 10's job.
        PrimaryButton(text = "Enroll", onClick = onEnrollRequiringAuth)
        // Real T6 mechanism: the "already enrolled" path straight into Course Player
        // (ux/NAVIGATION_SPEC.md § 3's mobile table) — the trigger this task's nav-hidden-on-Course-
        // Player instrumented test needs to actually reach that route.
        PrimaryButton(text = "Continue Learning", onClick = onContinueLearning)
    }
}

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

/**
 * T6 — the real, testable half of the auth-gate/pending-intent mechanism on the Login side. Neither
 * this screen nor [RegisterScreen] has a credential form yet (Task 7) — deferring the actual
 * credential UI is explicitly allowed by this task's plan. What is NOT allowed, and is NOT done
 * here, is a button that fakes success by calling a "pretend login succeeded" callback directly:
 * this screen has no such callback at all. The only way [MentoraNavHost]'s pending-intent routing
 * fires is a REAL `authState` transition to `Authenticated` (observed by `MentoraNavHost`'s own
 * `LaunchedEffect`, not by this screen) — [hasPendingIntent] is surfaced here purely as on-screen
 * text so an instrumented test (and a human running the smoke test) can see the mechanism recorded
 * the intent while sitting on this screen.
 */
@Composable
fun LoginScreen(hasPendingIntent: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Login (placeholder)", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (hasPendingIntent) "Pending intent recorded" else "No pending intent",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun RegisterScreen(hasPendingIntent: Boolean, onOpenLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Register (placeholder)", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (hasPendingIntent) "Pending intent recorded" else "No pending intent",
            style = MaterialTheme.typography.bodyMedium,
        )
        PrimaryButton(text = "Login", onClick = onOpenLogin)
    }
}

@Composable
private fun PlaceholderScreen(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
