package com.mentora.android.ui.checkout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraMotionDuration
import com.mentora.android.theme.MentoraMotionEasing
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.MentoraBadge
import com.mentora.android.ui.components.MentoraBadgeVariant
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.rememberReducedMotionEnabled
import com.mentora.shared.MentoraSdk

/**
 * T11 — the real, fully chromeless Purchase Success screen
 * (`design-to-code/screens/mobile-purchase-success.json`, `ux/SCREEN_UX_SPECS.md § 19`). Replaces
 * the T6-era placeholder in `ui/screens/PlaceholderScreens.kt`.
 *
 * **`shell: "none"` — genuinely zero app chrome, verified at the nav-shell level.**
 * `MentoraNavHost.kt` hides BOTH `MentoraTopBar` (via its `isChromeless` set, alongside Login/
 * Register) AND `MobileBottomNavigation` (via `showBottomNav`) for
 * [com.mentora.android.navigation.Destination.PurchaseSuccess] — this screen's own content is the
 * ENTIRE visible UI, not a body slotted into a shell that merely looks unstyled. This intentionally
 * does NOT repeat Web's own disclosed defect (`mobile-purchase-success.json`'s own `knownGaps`:
 * `web/src/app/[locale]/app/layout.tsx` still wraps that route in the normal `AppShell` despite its
 * spec's `shell: "none"`) — verified, not merely asserted, by `NavigationShellTest`'s
 * `purchaseSuccess_rendersWithNoTopBarAndNoBottomNav` instrumented test.
 *
 * **Not built on top of [com.mentora.android.ui.components.SuccessState] — a thin, screen-specific
 * composition instead, disclosed.** `SuccessState`'s fixed shape is icon (`icon.large`, 32dp) ->
 * title -> description -> ONE `PrimaryButton`, with no slot for this screen's extra subtitle
 * (`brand.primary`-colored "You're now enrolled!"), its personalized description, its "Demo
 * purchase..." disclosure pill, ITS 88dp celebratory icon override (`mobile-purchase-success.json`'s
 * own note: "larger than shared/components.json#/successState's generic icon.large default", the
 * same D52 precedent already recorded for Web), or its SECOND action ("Back to My Learning"
 * `TextButton`, alongside "Start Learning"). Forcing `SuccessState` to grow enough optional
 * parameters to cover all of that would leave it fighting its own generic, reusable shape for a
 * one-off screen's sake — per the task brief's own "don't force a generic component" guidance, this
 * screen instead manually composes the same pieces (`MentoraIcon`, `Text`, `PrimaryButton`,
 * `MentoraTextButton`, `MentoraBadge`) directly, reusing [rememberReducedMotionEnabled] and the exact
 * scale+fade/reduced-motion-crossfade entrance timing `SuccessState` already established (that one
 * function was widened from `private` to `internal` in `SuccessState.kt` specifically so this screen
 * could reuse it verbatim rather than re-deriving the same reduced-motion check a second time).
 */
@Composable
fun PurchaseSuccessScreen(
    courseId: String,
    sdk: MentoraSdk,
    onStartLearning: () -> Unit,
    onBackToMyLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PurchaseSuccessViewModel = viewModel(factory = PurchaseSuccessViewModel.Factory(sdk, courseId))
    val uiState by viewModel.uiState.collectAsState()

    PurchaseSuccessScreenContent(
        courseTitle = uiState.courseTitle,
        onStartLearning = onStartLearning,
        onBackToMyLearning = onBackToMyLearning,
        modifier = modifier,
    )
}

/** The stateless presentation half of [PurchaseSuccessScreen] — same split rationale as every other
 *  T9-11 screen's `*Content` composable. [courseTitle] `null` renders the generic fallback
 *  description (see [PurchaseSuccessViewModel]'s own kdoc). */
@Composable
internal fun PurchaseSuccessScreenContent(
    courseTitle: String?,
    onStartLearning: () -> Unit,
    onBackToMyLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val durationMillis = if (reducedMotion) MentoraMotionDuration.fast else MentoraMotionDuration.slow
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = MentoraMotionEasing.decelerate),
        label = "purchase-success-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (reducedMotion) 1f else if (visible) 1f else 0.9f,
        animationSpec = tween(durationMillis = durationMillis, easing = MentoraMotionEasing.decelerate),
        label = "purchase-success-scale",
    )

    Box(
        // `colorScheme.background`, not `.surface` — this is a `shell: "none"` full-bleed screen (see
        // this file's own kdoc), and `MentoraNavHost`'s `Scaffold` paints its own container in
        // `colorScheme.background` around the inset-padded content area; painting anything else here
        // would show as a visibly different-toned band behind the system status/nav bars.
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(PurchaseSuccessContentTestTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MentoraDimens.spacing.space6)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(PurchaseSuccessIconContainerSize) // 88dp celebratory override — see this file's kdoc.
                    .clip(CircleShape)
                    .background(MaterialTheme.extendedColors.successContainer),
                contentAlignment = Alignment.Center,
            ) {
                MentoraIcon(
                    name = MentoraIconName.CheckCircle,
                    contentDescription = null,
                    size = PurchaseSuccessIconSize, // 40dp celebratory override, see this file's kdoc.
                    tint = MaterialTheme.extendedColors.success,
                )
            }

            Text(
                text = stringResource(R.string.purchase_success_title),
                style = MaterialTheme.typography.headlineSmall, // heading.h3.
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                // Accessibility § 14 / UX_STATES.md § 6: announced via a live region on appearance,
                // the same confirmation a sighted user gets from the entrance animation.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                text = stringResource(R.string.purchase_success_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, // brand.primary.
                textAlign = TextAlign.Center,
            )
            Text(
                text = courseTitle
                    ?.let { stringResource(R.string.purchase_success_description, it) }
                    ?: stringResource(R.string.purchase_success_description_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            MentoraBadge(
                text = stringResource(R.string.purchase_success_disclosure),
                variant = MentoraBadgeVariant.Neutral,
            )

            // Showcase lines 2320-2323: both actions are full-width, stacked in a pinned footer
            // (`web/src/components/ui/state-patterns.tsx`'s own `SuccessState` renders both
            // `className="w-full"` too) — `fillMaxWidth()` here matches that even though this screen
            // composes its own layout rather than reusing `SuccessState` (see this file's own kdoc).
            PrimaryButton(
                text = stringResource(R.string.purchase_success_start_learning),
                onClick = onStartLearning,
                leadingIcon = MentoraIconName.Play,
                modifier = Modifier.fillMaxWidth().testTag(PurchaseSuccessStartLearningButtonTestTag),
            )
            MentoraTextButton(
                text = stringResource(R.string.purchase_success_back_to_my_learning),
                onClick = onBackToMyLearning,
                modifier = Modifier.fillMaxWidth().testTag(PurchaseSuccessBackToMyLearningButtonTestTag),
            )
        }
    }
}

/** `mobile-purchase-success.json` § icon — 88dp container, "larger than ... icon.large (32px)
 *  default" — a disclosed, celebratory-only literal (same modeling as `PrimaryButton`'s 88dp
 *  min-width literal), no matching `spacing.scale`/`icon.sizes` token exists for this one-off value. */
private val PurchaseSuccessIconContainerSize = 88.dp

/** The glyph itself, per the showcase's own literal `font-size:40px` on the `check_circle` span
 *  (`design-review-locked/Mentora Showcase.dc.html:2314`) — also celebratory-only, distinct from
 *  [PurchaseSuccessIconContainerSize] (the circular background behind it). */
private val PurchaseSuccessIconSize = 40.dp

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val PurchaseSuccessContentTestTag = "purchase-success-content"
const val PurchaseSuccessStartLearningButtonTestTag = "purchase-success-start-learning-button"
const val PurchaseSuccessBackToMyLearningButtonTestTag = "purchase-success-back-to-my-learning-button"
