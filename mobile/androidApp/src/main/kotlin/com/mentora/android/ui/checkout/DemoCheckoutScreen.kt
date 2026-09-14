package com.mentora.android.ui.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.CourseThumbnail
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.CheckoutPreview

/**
 * T11 — the real Demo Checkout screen (`design-to-code/screens/mobile-demo-checkout.json`,
 * `ux/SCREEN_UX_SPECS.md § 18`). Replaces the T6-era placeholder in `ui/screens/PlaceholderScreens.kt`.
 *
 * **No header rendered here.** `mobile-demo-checkout.json`'s `header` section ("arrow_back + 'Demo
 * checkout' title") is already exactly what `MentoraNavHost`'s shared `MentoraTopBar` renders for
 * every pushed destination, DemoCheckout included (`MentoraNavHost.kt`'s `hasRoute<Destination.DemoCheckout>()`
 * keeps it visible — only the bottom nav is hidden here) — same "don't duplicate a second back
 * control" reasoning `CourseDetailsScreen.kt`'s own kdoc already established for its header.
 *
 * **One card, not two — disclosed resolution of the mobile spec's own wording.** The mobile screen
 * spec's `layout.structure` describes "lineItem card -> order summary card" as if these were two
 * separate card instances. The LOCKED, higher-authority component spec
 * (`design-system/COMPONENTS.md § Checkout/OrderSummary`) defines exactly ONE component — one
 * `radius.large`/`space.5`/bordered surface containing the line item, price rows, AND the
 * demo-payment notice together — and Web's already-shipped `checkout-screen.tsx` builds it that
 * exact way (one `.mtx-checkout-card` div). This screen follows the locked component spec + existing
 * Web precedent: ONE [Surface] holds the line item, order summary, and demo notice; the confirm/cancel
 * actions render as their own sticky footer below it, mirroring `CourseDetailsScreen`'s own
 * already-established sticky-footer treatment for this same reason (pinned CTA, never part of the
 * scrollable body).
 *
 * **Error "Try Again" — a button-label swap, not a second button.** `ux/UX_STATES.md § 11`/
 * `COMPONENTS.md § Checkout`'s Error state calls for inline error copy plus a `TextButton` "Try
 * Again". Rendering that as a SECOND, separate button next to the still-present "Complete Demo
 * Purchase" primary action would put two functionally identical controls on screen at once (both call
 * the exact same [CheckoutViewModel.completePurchase]). Instead, the confirm button's own label swaps
 * to "Try Again" whenever [CheckoutUiState.completionFailed] is true (same button, same callback,
 * `MentoraButtonVariant.Primary` unchanged) — the inline error text above it still satisfies "inline
 * ... + Try Again" without a redundant control.
 *
 * **No info icon — disclosed, matches G8 + Web's own precedent.** The demo-payment notice's spec
 * calls for an "info icon." No `Info`/similar glyph exists anywhere in the ported 42-icon set
 * (`MentoraIcons.kt`'s G8 disclosure) — inventing a new icon glyph is exactly what G8's own resolution
 * forbids, and Web's real `checkout-screen.tsx` already renders this notice as text-only, no icon at
 * all. This mirrors that shipped precedent (and `CourseDetailsScreen`'s own lesson-lock-icon omission,
 * same reasoning) rather than fabricating a new glyph.
 */
@Composable
fun DemoCheckoutScreen(
    courseId: String,
    sdk: MentoraSdk,
    onCompletePurchase: () -> Unit,
    onBackToCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CheckoutViewModel = viewModel(factory = CheckoutViewModel.Factory(sdk, courseId))
    val uiState by viewModel.uiState.collectAsState()

    DemoCheckoutScreenContent(
        uiState = uiState,
        onRetryPreview = viewModel::onRetryPreview,
        onConfirm = { viewModel.completePurchase(onSuccess = { onCompletePurchase() }) },
        onBackToCourse = onBackToCourse,
        modifier = modifier,
    )
}

/** The stateless presentation half of [DemoCheckoutScreen] — same split rationale as
 *  `CourseDetailsScreenContent`/`ExploreScreenContent`. */
@Composable
internal fun DemoCheckoutScreenContent(
    uiState: CheckoutUiState,
    onRetryPreview: () -> Unit,
    onConfirm: () -> Unit,
    onBackToCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val previewState = uiState.preview) {
        is CheckoutLoadState.Loading -> FullScreenLoadingState(
            modifier = modifier.fillMaxSize().testTag(DemoCheckoutLoadingTestTag),
            contentDescriptionLabel = stringResource(R.string.demo_checkout_loading_content_description),
        )

        is CheckoutLoadState.Error -> Box(
            modifier = modifier.fillMaxSize().testTag(DemoCheckoutErrorTestTag),
            contentAlignment = Alignment.Center,
        ) {
            ErrorState(
                title = stringResource(R.string.demo_checkout_preview_error_title),
                description = apiErrorMessage(previewState.code),
                onRetryClick = onRetryPreview,
                retryLabel = stringResource(R.string.demo_checkout_try_again_action),
                modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
            )
        }

        is CheckoutLoadState.Success -> DemoCheckoutSuccessContent(
            preview = previewState.preview,
            thumbnailUrl = previewState.thumbnailUrl,
            isProcessing = uiState.isProcessing,
            completionFailed = uiState.completionFailed,
            onConfirm = onConfirm,
            onBackToCourse = onBackToCourse,
            modifier = modifier,
        )
    }
}

@Composable
private fun DemoCheckoutSuccessContent(
    preview: CheckoutPreview,
    thumbnailUrl: String?,
    isProcessing: Boolean,
    completionFailed: Boolean,
    onConfirm: () -> Unit,
    onBackToCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same "scrolling body (weighted) + un-weighted sticky footer" `Column` shape as
    // `CourseDetailsSuccessContent` — see that composable's kdoc for why.
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(MentoraDimens.spacing.space4)
                .testTag(DemoCheckoutContentTestTag),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            CheckoutOrderSummaryCard(preview = preview, thumbnailUrl = thumbnailUrl)

            if (completionFailed) {
                Text(
                    text = stringResource(R.string.demo_checkout_error_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(DemoCheckoutErrorMessageTestTag),
                )
            }
        }

        DemoCheckoutActionsFooter(
            isProcessing = isProcessing,
            completionFailed = completionFailed,
            onConfirm = onConfirm,
            onBackToCourse = onBackToCourse,
        )
    }
}

/**
 * The single Checkout/OrderSummary component (`COMPONENTS.md § Checkout` — see this file's own kdoc
 * on why this is ONE card, not two): line item -> "ORDER SUMMARY" eyebrow -> Course/Total price rows
 * -> demo-payment notice, all inside one `radius.large`/`space.5`/bordered [Surface].
 */
@Composable
private fun CheckoutOrderSummaryCard(preview: CheckoutPreview, thumbnailUrl: String?) {
    val price = formatDemoPrice(preview.priceDisplay.amount, preview.priceDisplay.currency)

    Surface(
        shape = MaterialTheme.shapes.large, // radius.large.
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(DemoCheckoutOrderSummaryCardTestTag),
    ) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            // Line item: thumbnail + title + instructor. No lesson count — see `CheckoutViewModel`'s
            // own kdoc for the disclosed omission rationale.
            Row(
                horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CourseThumbnail(
                    mediaId = preview.course.thumbnailMediaId,
                    thumbnailUrl = thumbnailUrl,
                    seed = preview.course.id,
                    categoryId = null,
                    contentDescription = stringResource(
                        R.string.explore_course_thumbnail_content_description,
                        preview.course.title,
                    ),
                    modifier = Modifier.width(96.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1)) {
                    Text(
                        text = preview.course.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = preview.instructorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(R.string.demo_checkout_order_summary_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.demo_checkout_course_row_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = price, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.demo_checkout_total_row_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = price, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface) // heading.h3.
            }

            DemoPaymentNotice()
        }
    }
}

/** `color.info.container`/`onInfoContainer`, `radius.medium`, `space.3`/`space.2` padding,
 *  `body.small` — `COMPONENTS.md § Checkout`'s demo-payment notice row exactly. No icon — see this
 *  file's own kdoc. */
@Composable
private fun DemoPaymentNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.infoContainer, MaterialTheme.shapes.medium)
            .padding(horizontal = MentoraDimens.spacing.space3, vertical = MentoraDimens.spacing.space2)
            .testTag(DemoCheckoutNoticeTestTag),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
    ) {
        Text(
            text = stringResource(R.string.demo_checkout_notice_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.extendedColors.onInfoContainer,
        )
        Text(
            text = stringResource(R.string.demo_checkout_notice_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.onInfoContainer,
        )
    }
}

/** The sticky confirm/cancel footer — a sibling of (not inside) the scrolling body above, same
 *  pinned-footer treatment as `CourseDetailsStickyFooter`. */
@Composable
private fun DemoCheckoutActionsFooter(
    isProcessing: Boolean,
    completionFailed: Boolean,
    onConfirm: () -> Unit,
    onBackToCourse: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(DemoCheckoutActionsFooterTestTag),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            PrimaryButton(
                text = stringResource(
                    if (completionFailed) R.string.demo_checkout_try_again_action else R.string.demo_checkout_confirm_action,
                ),
                onClick = onConfirm,
                loading = isProcessing,
                modifier = Modifier.fillMaxWidth().testTag(DemoCheckoutConfirmButtonTestTag),
            )
            MentoraTextButton(
                text = stringResource(R.string.demo_checkout_cancel_action),
                onClick = onBackToCourse,
                modifier = Modifier.fillMaxWidth().testTag(DemoCheckoutBackToCourseButtonTestTag),
            )
        }
    }
}

/** `web/src/lib/i18n/format.ts` parity — see `CourseDetailsScreen.kt`'s identical
 *  `formatDemoPrice`'s kdoc for the full rationale (same disclosed "{CODE} {amount}" format, not
 *  duplicated here beyond the one-line function itself since neither package exports the other's
 *  private helper). */
private fun formatDemoPrice(amount: Int, currency: String): String = "$currency $amount"

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val DemoCheckoutLoadingTestTag = "demo-checkout-loading"
const val DemoCheckoutErrorTestTag = "demo-checkout-error"
const val DemoCheckoutContentTestTag = "demo-checkout-content"
const val DemoCheckoutOrderSummaryCardTestTag = "demo-checkout-order-summary-card"
const val DemoCheckoutNoticeTestTag = "demo-checkout-notice"
const val DemoCheckoutErrorMessageTestTag = "demo-checkout-error-message"
const val DemoCheckoutActionsFooterTestTag = "demo-checkout-actions-footer"
const val DemoCheckoutConfirmButtonTestTag = "demo-checkout-confirm-button"
const val DemoCheckoutBackToCourseButtonTestTag = "demo-checkout-back-to-course-button"
