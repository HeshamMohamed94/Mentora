package com.mentora.android.navigation

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionUser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T6 — plain JVM unit test (`testDebugUnitTest`) for [decideAuthGate], the pure decision function
 * backing the guest enroll/follow auth-gate mechanism (`ux/NAVIGATION_SPEC.md § 6`). This is the
 * disclosed fallback the task plan itself allows for the one part of the mechanism that's genuinely
 * awkward to drive with a real `MentoraSdk` (its constructor is `internal` to `:shared`, so
 * `:androidApp` cannot fake one — see `AuthGate.kt`'s kdoc): the pure decision logic is covered here
 * with no Android runtime and no `MentoraSdk` at all, while the end-to-end routing (gate → Login →
 * auth-state flips → lands on the pending intent) is separately covered by
 * `NavigationShellTest.guestTriggeringAuthGate_recordsPendingIntent_andReturnsToItAfterAuthentication`
 * (`connectedDebugAndroidTest`), which drives a fake `MutableStateFlow<AuthState>` through
 * `MentoraNavHost`'s own `authState` parameter — also without a real `MentoraSdk`.
 */
class AuthGateDecisionTest {

    private val targetDestination = Destination.DemoCheckout(courseId = "course-1")

    @Test
    fun `authenticated user navigates directly, no gate`() {
        val user = SessionUser(id = "u1", email = "ada@example.com", name = "Ada", role = Role.Student, preferredLocale = "en")
        val decision = decideAuthGate(AuthState.Authenticated(user), targetDestination)

        assertEquals(AuthGateDecision.NavigateDirect(targetDestination), decision)
    }

    @Test
    fun `authenticated user with null profile still navigates directly`() {
        val decision = decideAuthGate(AuthState.Authenticated(user = null), targetDestination)

        assertEquals(AuthGateDecision.NavigateDirect(targetDestination), decision)
    }

    @Test
    fun `unauthenticated user is gated to Login with the intent recorded`() {
        val decision = decideAuthGate(AuthState.Unauthenticated, targetDestination)

        assertEquals(
            AuthGateDecision.GateToLogin(PendingNavIntent(targetDestination)),
            decision,
        )
    }

    @Test
    fun `unknown auth state is also gated to Login, treated like unauthenticated`() {
        val decision = decideAuthGate(AuthState.Unknown, targetDestination)

        assertEquals(
            AuthGateDecision.GateToLogin(PendingNavIntent(targetDestination)),
            decision,
        )
    }
}
