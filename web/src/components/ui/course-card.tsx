import { Link } from "@/i18n/navigation";
import type { CourseSummary } from "@/lib/api/courses";
import { formatPrice } from "@/lib/i18n/format";
import { CourseThumbnail } from "./course-thumbnail";

/**
 * design-system/COMPONENTS.md § Course Card, artwork/badge placement per the locked visual
 * reference (design-review-locked showcase § 12): the category chip sits at the thumbnail's
 * logical start corner over the artwork's scrim, not below it in the card body. Content
 * hierarchy: thumbnail (with overlaid category chip) → title (2-line clamp) → instructor →
 * rating/level/price meta row. No duration/student-count fields exist on the backend's
 * `CourseSummary` (MVP doesn't track them per course), so the meta row is rating + level + price
 * rather than the full rating/students/duration spec — a content-availability adaptation, not a
 * design change.
 */
export function CourseCard({
  course,
  categoryName,
  levelLabel,
  locale,
  viewLabel,
  basePath = "",
  contentLanguageLabel,
}: {
  course: CourseSummary;
  categoryName?: string;
  levelLabel: string;
  locale: string;
  viewLabel: string;
  /** "/app" when rendered for an authenticated Student so the link stays inside the
   * authenticated shell instead of dropping them onto the Guest-styled public route. */
  basePath?: string;
  /** Pre-translated "Course content: <Language>" string — pass only when
   * `course.contentLanguage !== locale`, so a translated title is never mistaken for translated
   * lesson content (the course's title may be localized while its content stays in the language
   * this badge names — see execution/DECISIONS_LOG.md D57). Omit entirely when they match. */
  contentLanguageLabel?: string;
}) {
  return (
    <Link href={`${basePath}/courses/${course.id}`} className="mtx-card" aria-label={course.title}>
      <CourseThumbnail
        mediaId={course.thumbnailMediaId}
        className="mtx-card-thumbnail"
        seed={course.id}
        categoryId={course.categoryId}
        badge={categoryName && <span className="mtx-thumbnail-badge">{categoryName}</span>}
      />
      <div className="mtx-card-body">
        <h3 className="mtx-text-heading-h4 mtx-card-title">{course.title}</h3>
        <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
          {course.instructorName}
        </p>
        <div className="mtx-card-meta-row mtx-text-caption">
          <span className="mtx-card-meta-rating">★ {course.ratingSeed.toFixed(1)}</span>
          <span>{levelLabel}</span>
        </div>
        {contentLanguageLabel && (
          <p className="mtx-text-caption" style={{ color: "var(--color-text-secondary)" }}>
            {contentLanguageLabel}
          </p>
        )}
        <p className="mtx-text-label-large" style={{ color: "var(--color-text-primary)" }}>
          {formatPrice(course.priceDisplay.amount, course.priceDisplay.currency, locale)}
        </p>
        <span className="mtx-btn mtx-btn-tonal mtx-card-cta" aria-hidden="true">
          {viewLabel}
        </span>
      </div>
    </Link>
  );
}
