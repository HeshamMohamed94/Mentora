import { Link } from "@/i18n/navigation";
import type { CourseSummary } from "@/lib/api/courses";
import { formatPrice } from "@/lib/i18n/format";
import { CategoryChip } from "./category-chip";
import { CourseThumbnail } from "./course-thumbnail";

/**
 * design-system/COMPONENTS.md § Course Card. Content hierarchy: thumbnail → category chip →
 * title (2-line clamp) → instructor → rating/level/price meta row. No duration/student-count
 * fields exist on the backend's `CourseSummary` (MVP doesn't track them per course), so the
 * meta row is rating + level + price rather than the full rating/students/duration spec —
 * a content-availability adaptation, not a design change.
 */
export function CourseCard({
  course,
  categoryName,
  levelLabel,
  locale,
  viewLabel,
  basePath = "",
}: {
  course: CourseSummary;
  categoryName?: string;
  levelLabel: string;
  locale: string;
  viewLabel: string;
  /** "/app" when rendered for an authenticated Student so the link stays inside the
   * authenticated shell instead of dropping them onto the Guest-styled public route. */
  basePath?: string;
}) {
  return (
    <Link href={`${basePath}/courses/${course.id}`} className="mtx-card" aria-label={course.title}>
      <CourseThumbnail mediaId={course.thumbnailMediaId} className="mtx-card-thumbnail" />
      <div className="mtx-card-body">
        {categoryName && <CategoryChip label={categoryName} />}
        <h3 className="mtx-text-heading-h4 mtx-card-title">{course.title}</h3>
        <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
          {course.instructorName}
        </p>
        <div className="mtx-card-meta-row mtx-text-caption">
          <span>★ {course.ratingSeed.toFixed(1)}</span>
          <span>{levelLabel}</span>
        </div>
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
