package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.mentora.android.theme.MentoraDimens

/**
 * `design-system/COMPONENTS.md` § CertificateCard (lines 349-360). No border-color special-casing —
 * "achievement is signaled by the certificate preview image and title, not by border color," same
 * `color.border.default` as every other card in this kit.
 *
 * **No real certificate image asset exists anywhere in this project** (verified — same disclosed gap
 * as web's D42/G9). [CertificatePreviewPlaceholder] is a token-driven placeholder (`surface.variant`
 * background + the existing [MentoraIconName.Certificates] icon, `radius.medium` on the preview
 * itself per the spec), not a fabricated image.
 *
 * T15: [CertificatePreviewPlaceholder] is `internal` (not `private`) specifically so
 * `com.mentora.android.ui.certificates.CertificateDetailScreen` can reuse the exact same placeholder
 * graphic for its own certificate-document header rather than duplicating a slightly-different one —
 * see that file's own kdoc for how it's reused there (a smaller, circular badge treatment, not this
 * card's own 16:9 banner).
 */
@Composable
fun CertificateCard(
    courseTitle: String,
    metaLabel: String, // e.g. "Completed Jan 12, 2026 • Mentora" — caller formats/localizes.
    onViewClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewLabel: String = "View",
    shareLabel: String = "Share",
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            CertificatePreviewPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small), // radius.medium on the preview itself.
            )
            // T15 review finding (round 1, LOW-4): `ux/SCREEN_UX_SPECS.md § 13`'s own accessibility
            // rule wants ONE composite accessible name covering title+date — scoping
            // `mergeDescendants` to just this title+meta pair (not the whole card) produces exactly
            // that single announcement, without swallowing the View/Share buttons below into it (they
            // stay outside this merged group, so each keeps its own independent accessible name/action).
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
            ) {
                Text(
                    text = courseTitle,
                    style = MaterialTheme.typography.titleLarge, // heading.h4.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLabel,
                    style = MaterialTheme.typography.labelSmall, // typography.caption.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
                TonalButton(text = viewLabel, onClick = onViewClick)
                MentoraTextButton(text = shareLabel, onClick = onShareClick)
            }
        }
    }
}

@Composable
internal fun CertificatePreviewPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = MentoraIconName.Certificates,
            contentDescription = null,
            size = MentoraDimens.iconSize.large,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
