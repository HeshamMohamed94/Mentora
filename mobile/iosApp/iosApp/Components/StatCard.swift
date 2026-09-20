import SwiftUI
import shared

/// `success.default` up / `error.default` down -- `design-system/COMPONENTS.md § StatCard` trend row.
enum StatTrendDirection {
    case up
    case down
}

// Phase 5 Task T11 slice 2 (Component Kit B) -- `design-system/COMPONENTS.md § StatCard` (lines
// 337-347) -- also `QuestionCard`'s shell per that section's own "same shell as a StatCard" note (a
// later T11 slice; not built here).
//
// Trend indicator pairs `MentoraIconName.arrowUpward`/`.arrowDownward` (`icon.small`) with
// `trendLabel` text in the matching semantic color -- never a bare colored arrow, so the signal still
// reads correctly for a color-blind user (`ACCESSIBILITY.md § 8`). Ported directly from Android's own
// `StatCard.kt`.
struct StatCard: View {
    let value: String
    let label: String
    var trendDirection: StatTrendDirection? = nil
    var trendLabel: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
            Text(value)
                .mentoraFont(.h2) // heading.h2.
                .foregroundStyle(Color.mentoraTextPrimary)
            Text(label)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextSecondary)
                .lineLimit(1) // CONTENT_RESILIENCE.md § 1 -- a long label must not grow the card taller.

            if let trendDirection, let trendLabel {
                let trendColor: Color = trendDirection == .up ? .mentoraSuccessDefault : .mentoraErrorDefault
                HStack(spacing: MentoraSpacing.space1) {
                    MentoraIcon(
                        name: trendDirection == .up ? .arrowUpward : .arrowDownward,
                        size: MentoraIconSize.small
                    )
                    .foregroundStyle(trendColor)
                    Text(trendLabel)
                        .mentoraFont(.caption)
                        .foregroundStyle(trendColor)
                }
            }
        }
        .padding(MentoraSpacing.space4)
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large (16).
        .overlay {
            MentoraShape.large.strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.default)
        }
    }
}
