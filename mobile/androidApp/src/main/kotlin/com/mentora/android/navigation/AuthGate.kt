package com.mentora.android.navigation

import androidx.compose.runtime.saveable.Saver
import com.mentora.shared.auth.AuthState
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * T6 — the guest enroll/follow gate mechanism (`ux/NAVIGATION_SPEC.md § 6`: "Enroll-gate always
 * returns to intent"). [PendingNavIntent] is the one thing this mechanism remembers: which
 * [Destination] the user was actually trying to reach when the gate intercepted them.
 *
 * **Testability note (plan's disclosed constraint):** [MentoraSdk][com.mentora.shared.MentoraSdk]'s
 * constructor is `internal` to `:shared`, so `:androidApp` cannot construct a fake instance for
 * tests. [decideAuthGate] below sidesteps that entirely — it takes an already-observed [AuthState]
 * value rather than reading `sdk.auth.observeAuthState()` itself, which makes it a pure function:
 * testable as a plain JVM unit test (`AuthGateDecisionTest`, `:androidApp:testDebugUnitTest`) with no
 * Android runtime and no `MentoraSdk` at all. [MentoraNavHost] is the one real call site that
 * supplies the actual observed `AuthState` — via [com.mentora.android.session.AppSessionViewModel]'s
 * `StateFlow`, already collected as Compose state one level up in `MainActivity` — and carries out
 * the resulting [AuthGateDecision]'s navigation side effect. Because [MentoraNavHost] itself takes
 * `authState: AuthState` as a plain parameter (not `sdk` directly), the *end-to-end* mechanism
 * (gate → Login → auth-state flips → lands on the pending intent) is ALSO instrumented-testable
 * (`NavigationShellTest`, `connectedDebugAndroidTest`) by driving a fake `MutableStateFlow<AuthState>`
 * through that parameter — no real `MentoraSdk` needed there either.
 */
@Serializable
data class PendingNavIntent(val destination: Destination)

// T6 fix-up (Finding 2): a plain `remember { mutableStateOf<PendingNavIntent?>(null) }` in
// MentoraNavHost does NOT survive Activity recreation (rotation) or process death, unlike
// `rememberNavController()`'s own state — so a guest gated to Login (pending intent = "return to
// Checkout") who rotates the device before logging in loses the intent and silently lands on Home
// instead. This local `Json` instance (deliberately separate from `:shared`'s `MentoraJson` — that
// one is scoped to the backend wire contract per its own kdoc, not a fit for purely-local
// navigation-state persistence) plus [PendingNavIntentSaver] let `MentoraNavHost` hold
// `pendingNavIntent` in `rememberSaveable` instead, round-tripping it through a JSON string (Bundle-
// safe) using the polymorphic serializer generated for the now-`@Serializable` `Destination` sealed
// interface (see that type's kdoc in `Destinations.kt`).
private val pendingNavIntentJson = Json

/** See the fix-up note above [PendingNavIntent]. `save` returns `null` for "nothing to save" (no
 * pending intent), which `rememberSaveable` treats as "use the initial value on restore" — i.e. a
 * clean null round-trips as null, exactly as it did with plain `remember`. */
val PendingNavIntentSaver: Saver<PendingNavIntent?, String> = Saver(
    save = { intent -> intent?.let { pendingNavIntentJson.encodeToString(it) } },
    restore = { encoded -> pendingNavIntentJson.decodeFromString<PendingNavIntent>(encoded) },
)

/** The two possible outcomes of attempting to reach [destination] with the given [authState]. */
sealed interface AuthGateDecision {
    /** Already authenticated — go straight there, no gate. */
    data class NavigateDirect(val destination: Destination) : AuthGateDecision

    /** Not authenticated (or not yet known) — remember [pendingIntent] and gate through Login. */
    data class GateToLogin(val pendingIntent: PendingNavIntent) : AuthGateDecision
}

/**
 * Pure decision function — no navigation side effect, no `sdk` access, no Android dependency at all.
 * [MentoraNavHost]'s `requireAuth` lambda is the thin composable wrapper that actually acts on this
 * result by calling `navController.navigate(...)`.
 */
fun decideAuthGate(authState: AuthState, destination: Destination): AuthGateDecision =
    if (authState is AuthState.Authenticated) {
        AuthGateDecision.NavigateDirect(destination)
    } else {
        // AuthState.Unknown is treated the same as Unauthenticated here — in practice
        // MentoraNavHost never calls this while still Unknown (MainActivity gates the whole nav
        // shell behind session restore resolving first, per AppSessionViewModel's existing Task 4
        // contract), but the function stays total/safe either way rather than special-casing it.
        AuthGateDecision.GateToLogin(PendingNavIntent(destination))
    }
