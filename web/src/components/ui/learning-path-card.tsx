import { Link } from "@/i18n/navigation";
import type { LearningPathSummary } from "@/lib/api/learning-paths";

export function LearningPathCard({
  path,
  courseCountLabel,
  viewLabel,
  basePath = "",
}: {
  path: LearningPathSummary;
  courseCountLabel: string;
  viewLabel: string;
  /** "/app" when rendered for an authenticated Student — see CourseCard's `basePath`. */
  basePath?: string;
}) {
  return (
    <Link href={`${basePath}/paths/${path.id}`} className="mtx-path-card" aria-label={`${path.title}, ${courseCountLabel}`}>
      <h3 className="mtx-text-heading-h4">{path.title}</h3>
      <p className="mtx-text-body-small mtx-path-card-description">{path.description}</p>
      <p className="mtx-text-caption">{courseCountLabel}</p>
      <span className="mtx-btn mtx-btn-text mtx-path-card-cta" aria-hidden="true">
        {viewLabel}
      </span>
    </Link>
  );
}
