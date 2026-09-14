package com.mentora.android.ui.certificates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.CertificatePreviewPlaceholder
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.CertificateDetail
import java.util.Locale

/**
 * T15 — the real Certificate Detail screen (`ux/SCREEN_UX_SPECS.md § 14`; `certificate-detail.json` is
 * `"referenceType": "ux-only"`, no exact-showcase mockup exists). Replaces the T6-era placeholder in
 * `ui/screens/PlaceholderScreens.kt`. Reuses the outer `MentoraNavHost` `Scaffold`'s own standard
 * `MentoraTopBar` (back button + title) for the spec's "minimal chrome, back affordance" header — no
 * bespoke inner `Scaffold` needed here (this route is NOT chromeless, unlike Quiz/Course Player, so
 * there is no double-inset hazard those screens' own kdocs warn about).
 *
 * **Certificate content rendering: reuses [CertificatePreviewPlaceholder] (widened to `internal` in
 * `CertificateCard.kt` for exactly this), not a second, slightly-different placeholder graphic** — a
 * smaller circular badge treatment here (this screen's own "document" header) rather than
 * `CertificateCard`'s 16:9 banner, since Detail's own content (name/course/instructor/date/id) is real
 * text laid out below it, not a title+caption summary.
 *
 * **RTL — the certificate's own content, disclosed resolution.** `ux/SCREEN_UX_SPECS.md § 14`: "the
 * certificate's own content... follows the certificate's authored language direction (which may be LTR
 * regardless of the app's current UI direction)... a deliberate, narrow exception similar in spirit to
 * the VideoPlayer scrubber rule." Unlike `PlayerControls.kt`'s scrubber (a FIXED-direction timeline
 * widget, unconditionally forced LTR via a `CompositionLocalProvider(LocalLayoutDirection provides
 * LayoutDirection.Ltr)` override, since a timeline has no "content direction" of its own to preserve),
 * this certificate's fields are inherently VARIABLE-direction data — a course taught in Arabic would
 * have an Arabic [CertificateDetail.courseTitleSnapshot], a course taught in English would not, and
 * neither is knowable in advance. A single hardcoded LTR override would be WRONG for the Arabic case
 * (silently mis-rendering an Arabic-authored certificate as if it were English). Instead, every field
 * below that carries real frozen certificate content ([CertificateDetail.studentNameSnapshot],
 * `.courseTitleSnapshot`, `.instructorNameSnapshot`, the completion-date/issued-date lines, and the
 * certificate id) sets `textDirection = TextDirection.Content` on its own `Text` — the Unicode-standard
 * "auto" resolution (same mechanism as HTML's `dir="auto"`) that reads EACH string's own first STRONG
 * directional character to decide ITS OWN paragraph direction. **Round-1 review correction**: this is
 * NOT fully independent of `LocalLayoutDirection` as an earlier draft of this comment claimed —
 * `TextDirection.Content` only overrides the ambient direction when the string actually CONTAINS a
 * strong directional character; a string with none (pure digits/punctuation, e.g. the certificate id)
 * still falls back to the ambient `LocalLayoutDirection` as its tiebreak. Functionally irrelevant for
 * real names/titles (which always contain strong directional characters), but worth stating precisely
 * rather than as an absolute. This satisfies the spec's "own authored direction, independent of the
 * viewer's current locale" requirement for whichever direction real content actually is — not just the
 * LTR case a hardcoded override would have covered. The certificate document block is
 * horizontally centered ([Alignment.CenterHorizontally]), so this narrow exception needs no companion
 * alignment override either way. Purely app-authored chrome around it (the kicker "Certificate of
 * Completion," "Awarded to," the Share button, the back button/title in the outer top bar) is
 * completely untouched and continues to mirror normally via the ambient `LocalLayoutDirection`, exactly
 * like every other screen in this app.
 *
 * **Accessibility.** No image-based rendering exists here at all — every certificate field is a real
 * Compose `Text`, so `ux/SCREEN_UX_SPECS.md § 14`'s "available to screen readers as real text... even
 * if the visual rendering is image-based" requirement is satisfied trivially (there is no image to need
 * an equivalent for).
 *
 * **Share — UI-only, per `product/PRODUCT_SPEC.md § 13`.** See [shareCertificate]'s own kdoc — a real
 * native Android share sheet (`Intent.ACTION_SEND`), never a fabricated LinkedIn API call or a
 * generated shareable URL (no public/unauthenticated "verify a certificate" endpoint exists anywhere
 * in the backend). No "Download"/"View full size" secondary action — the spec marks it optional
 * ("if included") and no real downloadable certificate asset exists to download.
 */
@Composable
fun CertificateDetailScreen(certificateId: String, sdk: MentoraSdk, modifier: Modifier = Modifier) {
    val viewModel: CertificateDetailViewModel = viewModel(
        factory = CertificateDetailViewModel.Factory(sdk, certificateId),
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()

    Box(modifier = modifier.fillMaxSize().testTag(CertificateDetailScreenTestTag)) {
        when (val state = uiState) {
            is CertificateDetailUiState.Loading -> FullScreenLoadingState(
                modifier = Modifier.fillMaxSize(),
                contentDescriptionLabel = stringResource(R.string.certificate_detail_loading_content_description),
            )

            is CertificateDetailUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(MentoraDimens.spacing.space4),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    title = stringResource(R.string.certificate_detail_error_title),
                    description = apiErrorMessage(state.code),
                    onRetryClick = viewModel::onRetry,
                    // Round-1 review, MEDIUM-1: see `CertificatesScreen.kt`'s identical fix/comment.
                    retryLabel = stringResource(R.string.certificate_detail_retry_action),
                )
            }

            is CertificateDetailUiState.Loaded -> CertificateDetailContent(
                certificate = state.certificate,
                locale = locale,
                onShare = {
                    shareCertificate(
                        context = context,
                        shareText = context.getString(
                            R.string.certificates_share_text,
                            state.certificate.courseTitleSnapshot,
                            state.certificate.id,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun CertificateDetailContent(certificate: CertificateDetail, locale: Locale, onShare: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space5),
    ) {
        CertificateDocument(certificate = certificate, locale = locale)

        PrimaryButton(
            text = stringResource(R.string.certificates_share_action),
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().testTag(CertificateDetailShareButtonTestTag),
        )
    }
}

@Composable
private fun CertificateDocument(certificate: CertificateDetail, locale: Locale) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(CertificateDetailDocumentTestTag),
        shape = MaterialTheme.shapes.medium, // radius.large — same shell shape as CertificateCard.
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            CertificatePreviewPlaceholder(
                modifier = Modifier.size(MentoraDimens.iconSize.large * 2).clip(CircleShape),
            )

            // App-authored chrome — normal ambient text direction, no TextDirection.Content override.
            Text(
                text = stringResource(R.string.certificate_detail_kicker),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.certificate_detail_awarded_to),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // Real frozen certificate content from here down — TextDirection.Content on each, per this
            // file's own kdoc.
            Text(
                text = certificate.studentNameSnapshot,
                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content), // heading.h2/h3.
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.certificate_detail_completion_statement),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = certificate.courseTitleSnapshot,
                style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content), // heading.h4.
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
            ) {
                Text(
                    text = stringResource(R.string.certificate_detail_instructor, certificate.instructorNameSnapshot),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content), // typography.caption.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        R.string.certificate_detail_completed_on,
                        formatCertificateDate(certificate.completionDateSnapshot, locale),
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        R.string.certificate_detail_issued_on,
                        formatCertificateDate(certificate.issuedAt, locale),
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                // `id` is `CertificateDetail.id`'s opaque public MTR-... string — rendered verbatim,
                // never parsed/reformatted/reconstructed (that model's own kdoc).
                text = stringResource(R.string.certificate_detail_certificate_id, certificate.id),
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val CertificateDetailScreenTestTag = "certificate-detail-screen"
const val CertificateDetailDocumentTestTag = "certificate-detail-document"
const val CertificateDetailShareButtonTestTag = "certificate-detail-share-button"
