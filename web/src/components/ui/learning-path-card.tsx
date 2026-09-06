import { Link } from "@/i18n/navigation";
import type { LearningPathSummary } from "@/lib/api/learning-paths";

export function LearningPathCard({ path, courseCountLabel }: { path: LearningPathSummary; courseCountLabel: string }) {
  return (
    <Link href={`/paths/${path.id}`} className="mtx-path-card" aria-label={`${path.title}, ${courseCountLabel}`}>
      <h3 className="mtx-text-heading-h4">{path.title}</h3>
      <p className="mtx-text-body-small">{path.description}</p>
      <p className="mtx-text-caption">{courseCountLabel}</p>
    </Link>
  );
}
