import SwiftUI
import shared

// Phase 5 Task T11 slice 2 (Component Kit B) -- `design-system/COMPONENTS.md § CertificateCard`
// (lines 349-360). No border-color special-casing -- "achievement is signaled by the certificate
// preview image and title, not by border color," same `color.border.default` as every other card in
// this kit.
//
// **No real certificate image asset exists anywhere in this project** (verified -- same disclosed gap
// Android's own `CertificateCard.kt` records for itself, D42/G9). `CertificatePreviewPlaceholder` below
// is a token-driven placeholder (`surface.variant` background + the existing
// `MentoraIconName.certificates` icon, `radius.medium` on the preview itself per the spec), not a
// fabricated image -- ported directly from Android's own identical placeholder.
struct CertificateCard: View {
    let courseTitle: String
    let metaLabel: String // e.g. "Completed Jan 12, 2026 • Mentora" -- caller formats/localizes.
    let onViewClick: () -> Void
    let onShareClick: () -> Void
    /// DISCLOSED DEVIATION from Android's own default-parameter convenience -- see
    /// `CourseProgressCard.swift`'s identical `resumeLabel` doc comment for the full rationale.
    /// Real, pre-existing `certificate_card_view_label` key, confirmed present in
    /// `Resources/Localizable.xcstrings` before this task began.
    var viewLabel: String
    /// Same rationale as `viewLabel` above. Real, pre-existing `certificate_card_share_label` key.
    var shareLabel: String

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            CertificatePreviewPlaceholder()
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                .clipShape(MentoraShape.medium) // radius.medium on the preview itself.

            // `ux/SCREEN_UX_SPECS.md § 13`'s own accessibility rule wants ONE composite accessible name
            // covering title+date -- `.accessibilityElement(children: .combine)` scoped to just this
            // title+meta pair (not the whole card) produces exactly that single announcement, without
            // swallowing the View/Share buttons below into it (they stay outside this combined group,
            // each keeping its own independent accessible name/action). Mirrors Android's own
            // `Modifier.semantics(mergeDescendants = true)` scoping exactly (read directly, T15 review
            // finding round 1, LOW-4).
            VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
                Text(courseTitle)
                    .mentoraFont(.h4)
                    .foregroundStyle(Color.mentoraTextPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(metaLabel)
                    .mentoraFont(.caption)
                    .foregroundStyle(Color.mentoraTextSecondary)
            }
            .accessibilityElement(children: .combine)

            HStack(spacing: MentoraSpacing.space2) {
                TonalButton(label: viewLabel, action: onViewClick)
                TextButton(label: shareLabel, action: onShareClick)
            }
        }
        .padding(MentoraSpacing.space5)
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large (16).
        .overlay {
            MentoraShape.large.strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.default)
        }
    }
}

/// `internal` (not `private`) -- matches Android's own T15 disclosure: a later screen (the iOS
/// equivalent of `CertificateDetailScreen`) may reuse this exact placeholder graphic for its own
/// certificate-document header, the same way Android's does, rather than duplicating a slightly
/// different one.
struct CertificatePreviewPlaceholder: View {
    var body: some View {
        ZStack {
            Color.mentoraSurfaceVariant
            MentoraIcon(name: .certificates, size: MentoraIconSize.large)
                .foregroundStyle(Color.mentoraTextSecondary)
        }
    }
}
