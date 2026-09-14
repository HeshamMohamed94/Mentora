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
 * tab — see that file's kdoc for why). The per-path detail screen's own placeholder retirement is
 * covered by the T16 note below.
 *
 * T10 note: `CourseDetailsScreen` is no longer a placeholder here — Task 10 replaced it with a real
 * screen in `com.mentora.android.ui.coursedetails` (`CourseDetailsScreen.kt`).
 *
 * T11 note: `DemoCheckoutScreen`/`PurchaseSuccessScreen` are no longer placeholders here — Task 11
 * replaced them with real screens in `com.mentora.android.ui.checkout`
 * (`DemoCheckoutScreen.kt`/`PurchaseSuccessScreen.kt`).
 *
 * T12 note: `HomeScreen`/`MyLearningScreen` are no longer placeholders here — Task 12 replaced them
 * with real screens in `com.mentora.android.ui.home` (`HomeScreen.kt`) and
 * `com.mentora.android.ui.mylearning` (`MyLearningScreen.kt`).
 *
 * T13 note: `CoursePlayerScreen` is no longer a placeholder here — Task 13 replaced it with a real
 * screen in `com.mentora.android.ui.courseplayer` (`CoursePlayerScreen.kt`, plus `PlayerSurface.kt`/
 * `PlayerControls.kt`/`CurriculumBottomSheet.kt`).
 *
 * T14 note: `QuizScreen`/`QuizResultsScreen` are no longer placeholders here — Task 14 replaced them
 * with real screens in `com.mentora.android.ui.quiz` (`QuizScreen.kt`/`QuizResultsScreen.kt`).
 *
 * T15 note: `CertificatesScreen`/`CertificateDetailScreen` are no longer placeholders here — Task 15
 * replaced them with real screens in `com.mentora.android.ui.certificates`
 * (`CertificatesScreen.kt`/`CertificateDetailScreen.kt`).
 *
 * T16 note: `LearningPathDetailsScreen` is no longer a placeholder here — Task 16 replaced it with a
 * real screen in `com.mentora.android.ui.learningpathdetails` (`LearningPathDetailsScreen.kt`).
 *
 * T17 note: `AiTutorScreen` is no longer a placeholder here — Task 17 replaced it with a real screen in
 * `com.mentora.android.ui.aitutor` (`AiTutorScreen.kt`, plus `AiTutorViewModel.kt`).
 */
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
private fun PlaceholderScreen(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
