import { Link } from "@/i18n/navigation";
import type { CourseResponse } from "@/lib/api/courses";
import { ProgressBar } from "./progress-bar";
import { Button } from "./button";

/**
 * design-system/COMPONENTS.md § CourseProgressCard — "variant of CourseCard for My Learning:
 * same shell, always shows ProgressBar + % complete + Resume TonalButton, drops the
 * rating/student-count row."
 */
export function CourseProgressCard({
  course,
  percent,
  percentLabel,
  resumeLabel,
}: {
  course: Pick<CourseResponse, "id" | "title" | "thumbnailMediaId" | "instructorName">;
  percent: number;
  percentLabel: string;
  resumeLabel: string;
}) {
  return (
    <div className="mtx-card">
      {course.thumbnailMediaId ? (
        <img src={`/api/v1/media/${course.thumbnailMediaId}/file`} alt="" className="mtx-card-thumbnail" />
      ) : (
        <div className="mtx-card-thumbnail" aria-hidden="true" />
      )}
      <div className="mtx-card-body">
        <h3 className="mtx-text-heading-h4 mtx-card-title">{course.title}</h3>
        <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
          {course.instructorName}
        </p>
        <div className="mt-2 flex flex-col gap-1">
          <ProgressBar percent={percent} label={percentLabel} />
          <span className="mtx-text-caption" style={{ color: "var(--color-text-secondary)" }}>
            {percentLabel}
          </span>
        </div>
        <Link href={`/app/learn/${course.id}`} className="mt-2">
          <Button variant="tonal" className="w-full">
            {resumeLabel}
          </Button>
        </Link>
      </div>
    </div>
  );
}
