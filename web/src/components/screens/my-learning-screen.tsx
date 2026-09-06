"use client";

import { useState } from "react";
import { useTranslations, useLocale } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { useMyLearning } from "@/lib/api/my-learning";
import { CourseProgressCard, CategoryChip, EmptyState, CourseGridSkeleton, ErrorState } from "@/components/ui";
import { formatCount } from "@/lib/i18n/format";

type StatusFilter = "all" | "inProgress" | "completed";

/** product/SCREEN_INVENTORY.md § 9 (My Learning). */
export function MyLearningScreen() {
  const t = useTranslations("myLearning");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const router = useRouter();
  const myLearning = useMyLearning();
  const [status, setStatus] = useState<StatusFilter>("all");

  const filtered = myLearning.items.filter((item) => {
    if (status === "inProgress") return item.progress.completionPercent < 100;
    if (status === "completed") return item.progress.completionPercent >= 100;
    return true;
  });

  return (
    <div className="mx-auto max-w-[1280px] px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-6">{t("title")}</h1>

      <div className="mb-6 flex flex-wrap gap-2" role="group" aria-label={t("title")}>
        <CategoryChip label={t("filterAll")} selected={status === "all"} onClick={() => setStatus("all")} />
        <CategoryChip label={t("filterInProgress")} selected={status === "inProgress"} onClick={() => setStatus("inProgress")} />
        <CategoryChip label={t("filterCompleted")} selected={status === "completed"} onClick={() => setStatus("completed")} />
      </div>

      {myLearning.isLoading && <CourseGridSkeleton count={4} />}

      {myLearning.isError && (
        <ErrorState description={t("errorTitle")} retryLabel={tCommon("retry")} onRetry={() => myLearning.refetch()} />
      )}

      {!myLearning.isLoading && !myLearning.isError && myLearning.items.length === 0 && (
        <EmptyState
          title={t("emptyTitle")}
          description={t("emptyDescription")}
          actionLabel={t("exploreCourses")}
          onAction={() => router.push("/app/explore")}
        />
      )}

      {!myLearning.isLoading && filtered.length > 0 && (
        <div className="mtx-course-grid">
          {filtered.map((item) => (
            <CourseProgressCard
              key={item.course.id}
              course={item.course}
              percent={item.progress.completionPercent}
              percentLabel={t("percentComplete", { percent: formatCount(item.progress.completionPercent, locale) })}
              resumeLabel={item.progress.completionPercent >= 100 ? t("review") : t("resume")}
            />
          ))}
        </div>
      )}

      {!myLearning.isLoading && myLearning.items.length > 0 && filtered.length === 0 && (
        <EmptyState title={t("filterEmptyTitle")} description={t("filterEmptyDescription")} />
      )}
    </div>
  );
}
