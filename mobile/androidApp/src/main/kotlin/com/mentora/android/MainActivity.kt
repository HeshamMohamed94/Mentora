package com.mentora.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.session.AppSessionViewModel
import com.mentora.android.theme.MentoraTheme
import com.mentora.android.theme.resolveDarkTheme
import com.mentora.shared.auth.AuthState
import com.mentora.shared.settings.AppLocale

// Task 1 scaffolded a bare MaterialTheme placeholder with no navigation/real screens yet. Task 2
// wired in the real MentoraTheme (design-token-driven ColorScheme/Typography/Shapes). Task 4 wires
// MentoraSdk session-restore + first-run locale/theme bootstrap through — navigation and real
// screens (Login/Register/Home, ...) remain later tasks' concern; the composable below is still a
// temporary smoke-test view, not a real screen.
// Android 15+ (targetSdk 36) enforces edge-to-edge for every app regardless of whether
// enableEdgeToEdge() is called, so a bare Surface(fillMaxSize()) draws under the status/nav bars.
// windowInsetsPadding(WindowInsets.safeDrawing) is the one-line fix to keep this placeholder's text
// clear of system bars — real inset-aware layout (Scaffold, top bars, etc.) is a later task's concern
// once a real navigation shell exists.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MentoraApplication
        setContent {
            MentoraSessionSmokeTestScreen(app)
        }
    }
}

/**
 * T4's smoke-test composable — proves `MentoraSdk`/session-restore/locale-and-theme bootstrap wire
 * up end to end. Renders one of "Loading…" / "Signed in as {email/name}" / "Signed out" as plain
 * `Text`; a real screen (Login/Register/Home) is a later task's job.
 *
 * Session restore and locale seeding are no longer triggered from here — `MentoraApplication`
 * already ran that single, ordered, application-scoped bootstrap sequence in `onCreate()` (F3
 * fix) before this Activity/composable ever exists. This composable purely observes state.
 */
@Composable
private fun MentoraSessionSmokeTestScreen(app: MentoraApplication) {
    val sessionViewModel: AppSessionViewModel = viewModel(factory = AppSessionViewModel.Factory(app.sdk))
    val sessionState by sessionViewModel.sessionState.collectAsState()
    val themePreference by app.themeController.theme.collectAsState()
    val currentLocale by app.sdk.user.observeLocale().collectAsState()

    MentoraTheme(
        darkTheme = themePreference.resolveDarkTheme(),
        arabicScript = currentLocale == AppLocale.Arabic,
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                text = sessionStatusText(sessionState),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

private fun sessionStatusText(state: AuthState): String = when (state) {
    is AuthState.Unknown -> "Loading…"
    is AuthState.Authenticated -> {
        val user = state.user
        if (user != null) "Signed in as ${user.name} (${user.email})" else "Loading…"
    }
    is AuthState.Unauthenticated -> "Signed out"
}
