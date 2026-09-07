import { Link } from "@/i18n/navigation";
import type { CourseResponse } from "@/lib/api/courses";
import { ProgressBar } from "./progress-bar";
import { Icon } from "./icon";
import { CourseThumbnail } from "./course-thumbnail";

/**
 * design-system/COMPONENTS.md § CourseProgressCard — "variant of CourseCard for My Learning:
 * same shell, always shows ProgressBar + % complete + Resume TonalButton, drops the
 * rating/student-count row." Laid out as a horizontal row (small thumbnail, content, Resume
 * button) per the locked visual reference's Dashboard/My Learning "Continue" cards
 * (design-review-locked showcase § 22) — the vertical thumbnail-on-top shell is CourseCard's,
 * this variant is always seen as a compact in-progress row, never a grid tile.
 */
export function CourseProgressCard({
  course,
  percent,
  percentLabel,
  resumeLabel,
}: {
  course: Pick<CourseResponse, "id" | "title" | "thumbnailMediaId" | "instructorName" | "categoryId">;
  percent: number;
  percentLabel: string;
  resumeLabel: string;
}) {
  return (
    <div className="mtx-progress-row">
      <CourseThumbnail
        mediaId={course.thumbnailMediaId}
        className="mtx-progress-row-thumbnail"
        seed={course.id}
        categoryId={course.categoryId}
        iconSize={24}
      />
      <div className="mtx-progress-row-body">
        <h3 className="mtx-text-heading-h4 mtx-card-title">{course.title}</h3>
        <ProgressBar percent={percent} label={percentLabel} />
        <span className="mtx-text-caption" style={{ color: "var(--color-text-secondary)" }}>
          {percentLabel}
        </span>
      </div>
      <Link href={`/app/learn/${course.id}`} className="mtx-btn mtx-btn-tonal mtx-progress-row-cta">
        <Icon name="play" size={20} />
        {resumeLabel}
      </Link>
    </div>
  );
}
