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
import androidx.compose.ui.Modifier

// Task 1: bare scaffold only — no custom Mentora theme (Task 2), no navigation, no real screens.
// Android 15+ (targetSdk 36) enforces edge-to-edge for every app regardless of whether
// enableEdgeToEdge() is called, so a bare Surface(fillMaxSize()) draws under the status/nav bars.
// windowInsetsPadding(WindowInsets.safeDrawing) is the one-line fix to keep this placeholder's text
// clear of system bars — real inset-aware layout (Scaffold, top bars, etc.) is a later task's concern
// once a real theme/navigation shell exists.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MentoraPlaceholderScreen()
        }
    }
}

@Composable
private fun MentoraPlaceholderScreen() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Mentora",
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}
