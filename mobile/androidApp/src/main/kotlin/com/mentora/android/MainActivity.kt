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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.locale.LocalizedContent
import com.mentora.android.navigation.MentoraNavHost
import com.mentora.android.session.AppSessionViewModel
import com.mentora.android.theme.MentoraTheme
import com.mentora.android.theme.resolveDarkTheme
import com.mentora.shared.auth.AuthState
import com.mentora.shared.settings.AppLocale

// Task 1 scaffolded a bare MaterialTheme placeholder with no navigation/real screens yet. Task 2
// wired in the real MentoraTheme (design-token-driven ColorScheme/Typography/Shapes). Task 4 wired
// MentoraSdk session-restore + first-run locale/theme bootstrap through, behind a temporary
// smoke-test screen. Task 6 replaces that smoke-test screen with the real navigation shell
// (`MentoraNavHost`) — real screen content (Login/Register/Explore/...) remains later tasks' concern;
// every destination MentoraNavHost renders today is still a placeholder composable.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MentoraApplication
        setContent {
            MentoraRootScreen(app)
        }
    }
}

/**
 * The app's real root composable. Collects [AppSessionViewModel]'s `sessionState` — unchanged from
 * Task 4, still the ONE place this app observes `sdk.auth.observeAuthState()` — plus theme/locale
 * state, then either shows a lightweight loading placeholder (while [AuthState.Unknown], i.e. before
 * `MentoraApplication`'s `restoreSession()` bootstrap has resolved) or hands the resolved
 * [AuthState] down into [MentoraNavHost], which owns the whole navigation shell from that point on.
 *
 * `MentoraNavHost` is only ever created once [AuthState] has left `Unknown` — deliberately: its own
 * guest-mode/auth-gate logic (`navigation/MentoraNavHost.kt`'s kdoc) only needs to reason about the
 * `Authenticated`/`Unauthenticated` distinction, never `Unknown`.
 */
@Composable
private fun MentoraRootScreen(app: MentoraApplication) {
    val sessionViewModel: AppSessionViewModel = viewModel(factory = AppSessionViewModel.Factory(app.sdk))
    val sessionState by sessionViewModel.sessionState.collectAsState()
    val themePreference by app.themeController.theme.collectAsState()
    val currentLocale by app.sdk.user.observeLocale().collectAsState()

    MentoraTheme(
        darkTheme = themePreference.resolveDarkTheme(),
        arabicScript = currentLocale == AppLocale.Arabic,
    ) {
        // T18 — see LocalizedContent's own kdoc for why this wraps every screen (not just Settings)
        // from here down: it's the one place `currentLocale` is already collected, and every
        // `stringResource`/date-formatting call site anywhere below this point needs the same
        // resolved-locale Context to stay consistent with whatever Settings' Language Select last set.
        LocalizedContent(locale = currentLocale) {
            val resolvedState = sessionState
            if (resolvedState is AuthState.Unknown) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.root_loading_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            } else {
                MentoraNavHost(authState = resolvedState, sdk = app.sdk, themeController = app.themeController)
            }
        }
    }
}
