import SwiftUI
import shared

// Phase 5 Task T11 slice 3 (Component Kit B) -- `PHASE_5_IOS_SYSTEM_DESIGN.md § 16` /
// `PHASE_5_IOS_IMPLEMENTATION_PLAN.md`'s T11 file list name this `CheckoutSummary` as one of Component
// Kit B's composites. There is no `design-system/COMPONENTS.md` entry, `design-to-code` screen spec, or
// standalone Android `Components/*.kt` file under this name -- verified, none exist anywhere. Android's
// OWN equivalent content (`ui/checkout/DemoCheckoutScreen.kt`'s private `CheckoutOrderSummaryCard`) is a
// screen-scoped, `private` composable living inside the Checkout screen file itself, never promoted to
// a reusable `ui/components/*.kt` file -- read directly for this port (not guessed), and its own kdoc
// is the closest thing to a spec this component has: "the single Checkout/OrderSummary component
// (`COMPONENTS.md § Checkout/OrderSummary` -- see that screen's own kdoc): line item -> 'ORDER SUMMARY'
// eyebrow -> Course/Total price rows -> demo-payment notice, all inside one `radius.large`/`space.5`/
// bordered surface."
//
// DISCLOSED, DELIBERATE PROMOTION: per this task's authority order, the System Design and Implementation
// Plan (ranks 2-3) outrank Android's own file layout (rank 7, reference-only) -- T11's own file list
// explicitly wants a real, reusable `Components/CheckoutSummary.swift`, so this is built as one here
// (ahead of the later screen task that will actually consume it), sourced from Android's own real
// content/structure rather than re-derived from nothing. Every `demo_checkout_*` localization key this
// view needs was confirmed pre-existing in `Resources/Localizable.xcstrings` before this task began --
// zero new keys added.
//
// Pure presentational, matching this kit's "never resolves `MentoraStrings` itself" convention
// throughout: `priceLabel` is an already-formatted `String` (Android's own `formatDemoPrice` -- a
// screen-level `web/src/lib/i18n/format.ts`-parity concern, not this component's job either) and every
// other row's copy is passed in already-resolved.
struct CheckoutSummary: View {
    let courseTitle: String
    let instructorName: String
    let seed: String
    let thumbnailAccessibilityLabel: String
    var mediaId: String? = nil
    var thumbnailUrl: String? = nil
    let orderSummaryEyebrow: String
    let courseRowLabel: String
    let totalRowLabel: String
    let priceLabel: String
    let noticeTitle: String
    let noticeBody: String

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            HStack(spacing: MentoraSpacing.space3) {
                CourseThumbnail(
                    mediaId: mediaId,
                    thumbnailUrl: thumbnailUrl,
                    seed: seed,
                    categoryId: nil,
                    accessibilityLabel: thumbnailAccessibilityLabel
                )
                .frame(width: 96)

                VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
                    Text(courseTitle)
                        .mentoraFont(.labelLarge)
                        .foregroundStyle(Color.mentoraTextPrimary)
                    Text(instructorName)
                        .mentoraFont(.bodySmall)
                        .foregroundStyle(Color.mentoraTextSecondary)
                }
            }

            Text(orderSummaryEyebrow)
                .mentoraFont(.labelLarge)
                .foregroundStyle(Color.mentoraTextSecondary)

            HStack {
                Text(courseRowLabel)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(Color.mentoraTextSecondary)
                Spacer()
                Text(priceLabel)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(Color.mentoraTextSecondary)
            }

            HStack {
                Text(totalRowLabel)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(Color.mentoraTextSecondary)
                Spacer()
                Text(priceLabel)
                    .mentoraFont(.h3) // heading.h3.
                    .foregroundStyle(Color.mentoraTextPrimary)
            }

            CheckoutDemoPaymentNotice(title: noticeTitle, bodyText: noticeBody)
        }
        .padding(MentoraSpacing.space5)
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large (16).
        .overlay {
            MentoraShape.large.strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.default)
        }
    }
}

/// `color.info.container`/`onInfoContainer`, `radius.medium`, `space.3`/`space.2` padding, `body.small`
/// -- `COMPONENTS.md § Checkout`'s demo-payment notice row exactly. No icon -- matches Android's own
/// `DemoPaymentNotice` disclosed choice (no `Info`/similar glyph exists in this kit's 42-icon set).
private struct CheckoutDemoPaymentNotice: View {
    let title: String
    let bodyText: String

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space1) {
            Text(title)
                .mentoraFont(.labelLarge)
                .foregroundStyle(Color.mentoraInfoOnInfoContainer)
            Text(bodyText)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraInfoOnInfoContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, MentoraSpacing.space3)
        .padding(.vertical, MentoraSpacing.space2)
        .background(Color.mentoraInfoContainer)
        .clipShape(MentoraShape.medium)
    }
}
