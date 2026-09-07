import { Link } from "@/i18n/navigation";
import { useTranslations } from "next-intl";
import type { LearningPathSummary } from "@/lib/api/learning-paths";
import { Icon } from "./icon";

/**
 * design-review-locked showcase § 12 "Learning path card": an icon + "LEARNING PATH" eyebrow
 * above the title, and the "View path" action carries a trailing arrow — both were missing here
 * even though the brand-tinted surface itself already matched the governed spec.
 */
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
  const t = useTranslations("learningPaths");
  return (
    <Link href={`${basePath}/paths/${path.id}`} className="mtx-path-card" aria-label={`${path.title}, ${courseCountLabel}`}>
      <span className="mtx-path-card-eyebrow">
        <Icon name="learningPaths" size={20} />
        {t("eyebrow")}
      </span>
      <h3 className="mtx-text-heading-h4">{path.title}</h3>
      <p className="mtx-text-body-small mtx-path-card-description">{path.description}</p>
      <p className="mtx-text-caption">{courseCountLabel}</p>
      <span className="mtx-btn mtx-btn-text mtx-path-card-cta" aria-hidden="true">
        {viewLabel}
        <Icon name="arrowForward" size={20} className="mtx-icon-mirror-rtl" />
      </span>
    </Link>
  );
}
