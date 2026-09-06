import { Link } from "@/i18n/navigation";
import type { CourseSummary } from "@/lib/api/courses";
import { formatPrice } from "@/lib/i18n/format";
import { CategoryChip } from "./category-chip";

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
}: {
  course: CourseSummary;
  categoryName?: string;
  levelLabel: string;
  locale: string;
}) {
  return (
    <Link href={`/courses/${course.id}`} className="mtx-card" aria-label={course.title}>
      {course.thumbnailMediaId ? (
        <img
          src={`/api/v1/media/${course.thumbnailMediaId}/file`}
          alt=""
          className="mtx-card-thumbnail"
        />
      ) : (
        <div className="mtx-card-thumbnail" aria-hidden="true" />
      )}
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
      </div>
    </Link>
  );
}
