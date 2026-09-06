"use client";

import { useTranslations, useLocale } from "next-intl";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { useMyLearning } from "@/lib/api/my-learning";
import { useCourses } from "@/lib/api/courses";
import { useFollowedLearningPaths } from "@/lib/api/learning-paths";
import { CourseProgressCard, CourseCard, LearningPathCard, StatCard, EmptyState } from "@/components/ui";
import { LEVEL_LABEL_KEYS } from "@/lib/i18n/course-labels";
import { formatCount } from "@/lib/i18n/format";
import { useRouter } from "@/i18n/navigation";

/**
 * product/SCREEN_INVENTORY.md § 8 (Dashboard / Home). "Certificates" stat reuses the completed-
 * course count rather than a separate `GET /certificates` call — a course only reaches
 * `courseCompletedAt` at the exact moment its certificate becomes eligible for issuance
 * (backend/CertificateService.checkAndIssueIfComplete), so the two counts are equal by
 * construction, not an approximation.
 */
export function DashboardScreen() {
  const t = useTranslations("dashboard");
  const tExplore = useTranslations("explore");
  const locale = useLocale();
  const router = useRouter();
  const { data: user } = useCurrentUser();
  const myLearning = useMyLearning();
  const followedPaths = useFollowedLearningPaths();

  const enrolledIds = new Set(myLearning.items.map((item) => item.course.id));
  const recommendedQuery = useCourses({ limit: 8 });
  const recommended = (recommendedQuery.data?.items ?? []).filter((c) => !enrolledIds.has(c.id)).slice(0, 4);

  const inProgress = myLearning.items.filter((item) => item.progress.completionPercent < 100);
  const completedCount = myLearning.items.length - inProgress.length;
  const continueItem = inProgress[0];

  return (
    <div className="mx-auto max-w-[1280px] px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-6">
        {user ? t("greeting", { name: user.name }) : t("title")}
      </h1>

      <div className="mb-8 grid grid-cols-1 gap-4 tablet:grid-cols-3">
        <StatCard value={formatCount(inProgress.length, locale)} label={t("statInProgress")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCompleted")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCertificates")} />
      </div>

      {continueItem && (
        <section className="mb-8">
          <h2 className="mtx-text-heading-h2 mb-4">{t("continueLearning")}</h2>
          <div className="mtx-course-grid">
            <CourseProgressCard
              course={continueItem.course}
              percent={continueItem.progress.completionPercent}
              percentLabel={t("percentComplete", { percent: formatCount(continueItem.progress.completionPercent, locale) })}
              resumeLabel={t("resume")}
            />
          </div>
        </section>
      )}

      {!myLearning.isLoading && myLearning.items.length === 0 && (
        <div className="mb-8">
          <EmptyState
            title={t("emptyTitle")}
            description={t("emptyDescription")}
            actionLabel={t("exploreCourses")}
            onAction={() => router.push("/app/explore")}
          />
        </div>
      )}

      {followedPaths.items.length > 0 && (
        <section className="mb-8">
          <h2 className="mtx-text-heading-h2 mb-4">{t("pathsInProgress")}</h2>
          <div className="mtx-course-grid">
            {followedPaths.items.map((path) => (
              <LearningPathCard
                key={path.id}
                path={{ id: path.id, title: path.title, description: path.description, courseCount: path.courses.length }}
                courseCountLabel={t("pathCourseCount", { count: path.courses.length })}
                viewLabel={t("viewPath")}
                basePath="/app"
              />
            ))}
          </div>
        </section>
      )}

      {recommended.length > 0 && (
        <section>
          <h2 className="mtx-text-heading-h2 mb-4">{t("recommended")}</h2>
          <div className="mtx-course-grid">
            {recommended.map((course) => (
              <CourseCard
                key={course.id}
                course={course}
                levelLabel={tExplore(LEVEL_LABEL_KEYS[course.level])}
                locale={locale}
                viewLabel={tExplore("viewCourse")}
                basePath="/app"
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
