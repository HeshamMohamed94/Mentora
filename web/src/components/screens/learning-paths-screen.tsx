"use client";

import { useTranslations } from "next-intl";
import { useLearningPaths } from "@/lib/api/learning-paths";
import { LearningPathCard, EmptyState, ErrorState } from "@/components/ui";

export function LearningPathsScreen() {
  const t = useTranslations("learningPaths");
  const query = useLearningPaths();

  return (
    <div className="mx-auto max-w-[1200px] px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-2">{t("title")}</h1>
      <p className="mtx-text-body-medium mb-6" style={{ color: "var(--color-text-secondary)" }}>
        {t("intro")}
      </p>

      {query.isLoading && (
        <div className="mtx-course-grid" aria-hidden="true">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="mtx-skeleton" style={{ height: 160, borderRadius: "var(--radius-large)" }} />
          ))}
        </div>
      )}

      {query.isError && (
        <ErrorState description={t("errorTitle")} retryLabel={t("errorTitle")} onRetry={() => query.refetch()} />
      )}

      {query.data && query.data.length === 0 && <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />}

      {query.data && query.data.length > 0 && (
        <div className="mtx-course-grid">
          {query.data.map((path) => (
            <LearningPathCard key={path.id} path={path} courseCountLabel={t("courseCount", { count: path.courseCount })} />
          ))}
        </div>
      )}
    </div>
  );
}
