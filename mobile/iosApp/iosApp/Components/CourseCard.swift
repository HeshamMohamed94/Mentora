import SwiftUI
import shared

// Phase 5 Task T11 slice 2 (Component Kit B) -- `design-system/COMPONENTS.md § CourseCard` (lines
// 274-303). Content hierarchy top to bottom: thumbnail (16:9, top corners clipped) -> category chip
// overlay -> title (`heading.h4`, 2-line ellipsis) -> instructor name (`body.small`, `text.secondary`)
// -> optional rating/student-count/duration meta row -> optional progress bar (only if enrolled) ->
// full-width `TonalButton` primary action.
//
// **Shell shared with `CourseProgressCard`** (`CourseProgressCard.swift`) via the internal
// `BaseCourseCard` below -- same radius/border/padding, differing only in which optional rows render.
// Mirrors Android's own `CourseCard.kt`/`CourseProgressCard.kt` split exactly (read directly for this
// port, not guessed).
//
// **Meta row icon gap (disclosed, matches Android's own precedent, which itself matches web's).** The
// design spec calls for rating/student-count/duration icons at `icon.small`. This kit's 42-icon set
// (`Theme/MentoraIcon.swift`) has no star or clock/duration glyph -- verified, neither exists under any
// name. Android's `CourseCard.kt` hits the identical gap and resolves it with a literal `"★"` glyph for
// rating (no icon at all) rather than inventing a new asset; `CourseMetaRow` below follows that exact
// precedent (a plain `"★"` text glyph for rating, the already-existing `MentoraIconName.people` for
// student count, plain text with no icon for duration).
//
// **Pressed state -- DISCLOSED, NOT PORTED.** Android's `BaseCourseCard` scales to 0.98 on press via
// `graphicsLayer`/`animateFloatAsState` bound to its own `interactionSource`, an explicit substitute for
// Compose's default ripple. This port wraps a clickable card in a plain SwiftUI `Button` +
// `.buttonStyle(.plain)` (stripping the platform's default blue tint/text-button look, nothing more) --
// it ports the STRUCTURE and every visible row faithfully but does not add a custom pressed-scale
// animation to match Android's own bespoke one (that would need its own hand-rolled `ButtonStyle`, the
// same category of work `MentoraButton.swift`'s custom `ButtonStyle` represents for a different
// component). A purely-visual polish gap, not a missing capability -- deferred rather than silently
// invented.
//
// **No `tonalElevation`/shadow at rest -- matches Android's own explicit note.** `MentoraTheme.swift`
// deliberately leaves `surfaceTint`/no tonal-elevation tint on the base surface color (same reasoning
// Android's own kdoc records for its `Surface(tonalElevation = 0.dp)` choice) -- resting elevation is a
// border alone, same as this kit's sibling cards (`StatCard`/`CertificateCard`).
enum CourseMetaRowContent {

    /// `CourseMetaRow`'s pure "is there anything to show" gate -- kept as a directly-testable static
    /// function rather than only inline in the view, mirroring Android's own implicit early-return.
    static func hasContent(rating: Float?, studentCount: Int?, durationLabel: String?) -> Bool {
        rating != nil || studentCount != nil || durationLabel != nil
    }
}

/// Rating (`"★"`, see file header) + student count (`MentoraIconName.people`) + duration (plain text)
/// -- any subset may be `nil`; only non-`nil` entries render, space-separated.
struct CourseMetaRow: View {
    let rating: Float?
    let studentCount: Int?
    let durationLabel: String?

    var body: some View {
        if CourseMetaRowContent.hasContent(rating: rating, studentCount: studentCount, durationLabel: durationLabel) {
            HStack(spacing: MentoraSpacing.space3) {
                if let rating {
                    HStack(spacing: MentoraSpacing.space1) {
                        // `Text(verbatim:)` -- a real, symbol-only glyph, never natural-language text
                        // needing translation, so it deliberately does not route through
                        // `MentoraStrings`/`Localizable.xcstrings` (matching Android's own identical
                        // literal `"★"` glyph, `CourseCard.kt`, for the same reason). `Text(verbatim:)`
                        // is invisible to `localization-checks.js` Check B1's `Text("literal")` scan by
                        // construction (that check only fires on the plain, non-`verbatim:` overload),
                        // which is the correct outcome here, not an evasion of it.
                        Text(verbatim: "★")
                            .mentoraFont(.caption)
                            .foregroundStyle(Color.mentoraTextSecondary)
                        // Locale-INDEPENDENT `String(format:)` (no `locale:` argument -- Apple's own
                        // documented behavior for this overload) always renders "." + Western digits,
                        // matching Android's `String.format(Locale.US, "%.1f", it)` pin exactly
                        // (`design-system/LOCALIZATION.md § 8`'s "Western numerals everywhere" rule) --
                        // this is not an approximation, it is the same non-localized formatting Android
                        // deliberately forces via an explicit `Locale.US` argument.
                        Text(String(format: "%.1f", rating))
                            .mentoraFont(.caption)
                            .foregroundStyle(Color.mentoraTextSecondary)
                    }
                }
                if let studentCount {
                    HStack(spacing: MentoraSpacing.space1) {
                        MentoraIcon(name: .people, size: MentoraIconSize.small)
                            .foregroundStyle(Color.mentoraTextSecondary)
                        // `Text(String(studentCount))`, not `Text("\(studentCount)")` -- the latter's
                        // Swift string-interpolation escape trips `localization-checks.js` Check B2 (it
                        // makes Xcode silently auto-extract a brand-new, un-ported String Catalog key --
                        // `PHASE_5_IOS_IMPLEMENTATION_PLAN.md` T7). A raw integer's `String(_:)`
                        // conversion is locale-independent Western-digit text by construction (no
                        // decimal/grouping separator to localize), so this is not a fidelity loss.
                        Text(String(studentCount))
                            .mentoraFont(.caption)
                            .foregroundStyle(Color.mentoraTextSecondary)
                    }
                }
                if let durationLabel {
                    Text(durationLabel)
                        .mentoraFont(.caption)
                        .foregroundStyle(Color.mentoraTextSecondary)
                }
            }
        }
    }
}

/// The shell shared by `CourseCard` and `CourseProgressCard` -- see file header.
struct BaseCourseCard: View {
    let title: String
    let instructorName: String
    let seed: String
    let categoryId: String?
    let categoryLabel: String
    let mediaId: String?
    let thumbnailUrl: String?
    let thumbnailAccessibilityLabel: String
    let actionLabel: String
    let onActionClick: () -> Void
    var onClick: (() -> Void)? = nil
    var metaRow: CourseMetaRow? = nil
    var progress: Double? = nil
    var progressLabel: String? = nil

    private var card: some View {
        VStack(spacing: 0) {
            CourseArtworkWithChip(
                seed: seed,
                categoryId: categoryId,
                categoryLabel: categoryLabel,
                accessibilityLabel: thumbnailAccessibilityLabel,
                mediaId: mediaId,
                thumbnailUrl: thumbnailUrl,
                thumbnailShape: AnyShape(CourseThumbnailTopCornersShape)
            )
            VStack(alignment: .leading, spacing: MentoraSpacing.space2) {
                Text(title)
                    .mentoraFont(.h4)
                    .foregroundStyle(Color.mentoraTextPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(instructorName)
                    .mentoraFont(.bodySmall)
                    .foregroundStyle(Color.mentoraTextSecondary)
                    .lineLimit(1) // CONTENT_RESILIENCE.md § 1 -- single-line for grid-rhythm reasons.
                if let metaRow {
                    metaRow
                }
                if let progress {
                    MentoraProgressBar(
                        progress: progress,
                        accessibilityLabel: progressLabel ?? title
                    )
                    if let progressLabel {
                        Text(progressLabel)
                            .mentoraFont(.caption)
                            .foregroundStyle(Color.mentoraTextSecondary)
                    }
                }
                TonalButton(label: actionLabel, action: onActionClick)
                    .frame(maxWidth: .infinity)
            }
            .padding(MentoraSpacing.space4)
        }
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large (16).
        .overlay {
            MentoraShape.large.strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.default)
        }
    }

    var body: some View {
        if let onClick {
            Button(action: onClick) { card }
                .buttonStyle(.plain)
        } else {
            card
        }
    }
}

/// `CourseCard`'s pure "is the progress row shown" gate -- `COMPONENTS.md` line 295: "Progress bar
/// (only if enrolled)". `progress` is only rendered when `isEnrolled` is true, regardless of whether the
/// caller also passed a value while not enrolled -- the conditional is the single source of truth, not
/// the nullability of `progress` alone, so a caller can't accidentally show a stale progress bar on a
/// not-yet-enrolled card. Kept as a directly-testable pure function, matching Android's own
/// `effectiveProgress` local val (`CourseCard.kt`), which this mirrors exactly.
enum CourseCardRules {
    static func effectiveProgress(isEnrolled: Bool, progress: Double?) -> Double? {
        isEnrolled ? progress : nil
    }
}

struct CourseCard: View {
    let title: String
    let instructorName: String
    let seed: String
    let categoryId: String?
    let categoryLabel: String
    let thumbnailAccessibilityLabel: String
    let actionLabel: String
    let onActionClick: () -> Void
    var mediaId: String? = nil
    var thumbnailUrl: String? = nil
    var onClick: (() -> Void)? = nil
    var rating: Float? = nil
    var studentCount: Int? = nil
    var durationLabel: String? = nil
    var isEnrolled: Bool = false
    var progress: Double? = nil
    var progressLabelFormatter: (Double) -> String = { p in "\(Int((p * 100).rounded()))% complete" }

    var body: some View {
        let effectiveProgress = CourseCardRules.effectiveProgress(isEnrolled: isEnrolled, progress: progress)
        BaseCourseCard(
            title: title,
            instructorName: instructorName,
            seed: seed,
            categoryId: categoryId,
            categoryLabel: categoryLabel,
            mediaId: mediaId,
            thumbnailUrl: thumbnailUrl,
            thumbnailAccessibilityLabel: thumbnailAccessibilityLabel,
            actionLabel: actionLabel,
            onActionClick: onActionClick,
            onClick: onClick,
            metaRow: CourseMetaRowContent.hasContent(rating: rating, studentCount: studentCount, durationLabel: durationLabel)
                ? CourseMetaRow(rating: rating, studentCount: studentCount, durationLabel: durationLabel)
                : nil,
            progress: effectiveProgress,
            progressLabel: effectiveProgress.map(progressLabelFormatter)
        )
    }
}
