package com.mentora.android.ui.certificates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.CertificateCard
import com.mentora.android.ui.components.CourseCardSkeleton
import com.mentora.android.ui.components.EmptyState
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.CertificateSummary
import java.util.Locale

/**
 * T15 — the real Certificates List screen (`ux/SCREEN_UX_SPECS.md § 13`; `certificates.json` is
 * `"referenceType": "ux-only"`, no exact-showcase mockup exists — same design-derived footing as
 * Course Details/Quiz). Heading -> a plain scrolling list of [CertificateCard]s, no page-level CTA.
 *
 * **"Grid" collapses to a single column on this phone-only app.** `design-to-code/shared/
 * responsive.json`'s `courseGrid.mobile` is already `1` (its own disclosed note: "certificate cards
 * may cap at 2-3 columns even at Large Desktop... a layout judgment, not a token change" — this app
 * never renders above the mobile breakpoint), and every other list screen in this phase (Explore, My
 * Learning) already renders its own card grid as one scrolling column for the same reason — a real
 * `LazyVerticalGrid` would be introducing a NEW pattern this app doesn't otherwise use, not "reusing
 * the grid," so this screen follows the same established single-column `LazyColumn` convention instead.
 *
 * **No top bar back button — deliberately suppressed for this destination.** Per the task brief,
 * this screen renders with no back affordance even though it's technically a pushed (non-tab-root)
 * destination — see `MentoraNavHost.kt`'s own `isCertificatesList` flag for where that's implemented;
 * system back still works regardless (same precedent as every other chromeless-affordance case in that
 * file).
 */
@Composable
fun CertificatesScreen(
    sdk: MentoraSdk,
    onOpenCertificateDetail: (String) -> Unit,
    onOpenMyLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CertificatesViewModel = viewModel(factory = CertificatesViewModel.Factory(sdk))
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

    CertificatesScreenContent(
        uiState = uiState,
        locale = locale,
        onRetry = viewModel::onRetry,
        onLoadMore = viewModel::onLoadMore,
        onOpenCertificateDetail = onOpenCertificateDetail,
        onOpenMyLearning = onOpenMyLearning,
        onShare = { certificate ->
            shareCertificate(
                context = context,
                shareText = context.getString(
                    R.string.certificates_share_text,
                    certificate.courseTitleSnapshot,
                    certificate.id,
                ),
            )
        },
        modifier = modifier,
    )
}

/** The stateless presentation half of [CertificatesScreen] — same split rationale as every other
 *  T7-T14 screen. */
@Composable
internal fun CertificatesScreenContent(
    uiState: CertificatesUiState,
    locale: Locale,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenCertificateDetail: (String) -> Unit,
    onOpenMyLearning: () -> Unit,
    onShare: (CertificateSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Same cursor-based "load more" trigger as `ExploreScreen`'s own `shouldLoadMore` — fires once the
    // last visible item is within 3 of the end of the CURRENT list.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag(CertificatesScreenTestTag),
        contentPadding = PaddingValues(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
    ) {
        item {
            Text(
                text = stringResource(R.string.certificates_heading),
                style = MaterialTheme.typography.headlineLarge, // heading.h1.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        certificatesItems(
            uiState = uiState,
            locale = locale,
            onRetry = onRetry,
            onOpenCertificateDetail = onOpenCertificateDetail,
            onOpenMyLearning = onOpenMyLearning,
            onShare = onShare,
        )
    }
}

private fun LazyListScope.certificatesItems(
    uiState: CertificatesUiState,
    locale: Locale,
    onRetry: () -> Unit,
    onOpenCertificateDetail: (String) -> Unit,
    onOpenMyLearning: () -> Unit,
    onShare: (CertificateSummary) -> Unit,
) {
    when (uiState) {
        is CertificatesUiState.Loading -> items(3) {
            CourseCardSkeleton(modifier = Modifier.fillMaxWidth())
        }

        is CertificatesUiState.Empty -> item {
            EmptyState(
                icon = MentoraIconName.Certificates,
                title = stringResource(R.string.certificates_empty_title),
                description = stringResource(R.string.certificates_empty_description),
                actionLabel = stringResource(R.string.certificates_go_to_my_learning),
                onActionClick = onOpenMyLearning,
            )
        }

        is CertificatesUiState.Error -> item {
            ErrorState(
                title = stringResource(R.string.certificates_error_title),
                description = apiErrorMessage(uiState.code),
                onRetryClick = onRetry,
                // Round-1 review, MEDIUM-1: `ErrorState`'s own `retryLabel` default is a raw,
                // unlocalized English literal — always pass a real localized value explicitly, same
                // gotcha this codebase already hit with `QuestionCard`/`AnswerOption` (Task 14).
                retryLabel = stringResource(R.string.certificates_retry_action),
            )
        }

        is CertificatesUiState.Loaded -> {
            items(uiState.items, key = { it.id }) { certificate ->
                CertificateListItem(
                    certificate = certificate,
                    locale = locale,
                    onView = { onOpenCertificateDetail(certificate.id) },
                    onShare = { onShare(certificate) },
                )
            }
            if (uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = MentoraDimens.spacing.space4)
                            .testTag(CertificatesLoadMoreSpinnerTestTag),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * `ux/SCREEN_UX_SPECS.md § 13`'s own accessibility rule: "each card's accessible name includes course
 * title and completion date." **Disclosed resolution**: [CertificateCard] already renders both as two
 * separate, real (never icon/color-only) `Text` nodes read in sequence by a screen reader —
 * [CertificateSummary.courseTitleSnapshot] then [metaLabel] — so that information genuinely reaches
 * accessibility tech without any extra wiring. A single merged `Modifier.semantics(mergeDescendants =
 * true)` wrapper around the whole card, which would be the literal way to produce ONE composite
 * "accessible name" string, was deliberately NOT added: this card contains two independently
 * actionable buttons (View/Share, both real per-certificate actions) — merging descendants would
 * swallow both buttons into one opaque, unlabeled-for-those-two-actions node (a real accessibility
 * regression), and layering a THIRD, non-merged `contentDescription` on top of the card would instead
 * make a screen reader announce the title+date twice (once via that description, once again via the
 * two `Text` nodes underneath, since a non-merged parent doesn't suppress its own children). Two
 * separate, real, unduplicated announcements in the correct reading order is the better outcome here,
 * not a worse one. `[metaLabel]` is built from `issuedAt` (the backend writes the identical instant to
 * both `issuedAt`/`completionDateSnapshot` at issuance — `CertificateDetail`'s own kdoc, verified from
 * `CertificateService.kt`), so "Issued {date}" already IS the completion date in substance, mirroring
 * `web/src/components/screens/certificates-screen.tsx`'s identical `issuedOn` copy choice for this
 * same List card (cross-platform-consistency precedent, same as Task 12/14's own copy reuse).
 */
@Composable
private fun CertificateListItem(
    certificate: CertificateSummary,
    locale: Locale,
    onView: () -> Unit,
    onShare: () -> Unit,
) {
    val metaLabel = stringResource(
        R.string.certificates_issued_on,
        formatCertificateDate(certificate.issuedAt, locale),
    )
    CertificateCard(
        courseTitle = certificate.courseTitleSnapshot,
        metaLabel = metaLabel,
        onViewClick = onView,
        onShareClick = onShare,
        viewLabel = stringResource(R.string.certificates_view_action),
        shareLabel = stringResource(R.string.certificates_share_action),
        modifier = Modifier.fillMaxWidth().testTag(certificatesCardTestTag(certificate.id)),
    )
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val CertificatesScreenTestTag = "certificates-screen"
const val CertificatesLoadMoreSpinnerTestTag = "certificates-load-more-spinner"

fun certificatesCardTestTag(certificateId: String): String = "certificates-card-$certificateId"
