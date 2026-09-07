"use client";

import { useTranslations, useLocale } from "next-intl";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { useMyLearning } from "@/lib/api/my-learning";
import { useCourses } from "@/lib/api/courses";
import { useFollowedLearningPaths } from "@/lib/api/learning-paths";
import { CourseProgressCard, CourseCard, LearningPathCard, StatCard, EmptyState, Icon } from "@/components/ui";
import { LEVEL_LABEL_KEYS } from "@/lib/i18n/course-labels";
import { formatCount } from "@/lib/i18n/format";
import { Link, useRouter } from "@/i18n/navigation";

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
  const continueItems = inProgress.slice(0, 3);
  const avgProgress = myLearning.items.length
    ? Math.round(
        myLearning.items.reduce((sum, item) => sum + item.progress.completionPercent, 0) / myLearning.items.length,
      )
    : 0;

  return (
    <div className="mx-auto max-w-[1280px] px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-6">
        {user ? t("greeting", { name: user.name }) : t("title")}
      </h1>

      <div className="mb-8 grid grid-cols-2 gap-4 tablet:grid-cols-4">
        <StatCard value={formatCount(inProgress.length, locale)} label={t("statInProgress")} />
        <StatCard value={t("percentComplete", { percent: formatCount(avgProgress, locale) })} label={t("statAvgProgress")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCompleted")} />
        <StatCard value={formatCount(completedCount, locale)} label={t("statCertificates")} />
      </div>

      <div className="grid grid-cols-1 gap-8 desktop:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] desktop:items-start">
        <div className="flex flex-col gap-8">
          {continueItems.length > 0 && (
            <section>
              <h2 className="mtx-text-heading-h2 mb-4">{t("continueLearning")}</h2>
              <div className="mtx-progress-list">
                {continueItems.map((item) => (
                  <CourseProgressCard
                    key={item.course.id}
                    course={item.course}
                    percent={item.progress.completionPercent}
                    percentLabel={t("percentComplete", { percent: formatCount(item.progress.completionPercent, locale) })}
                    resumeLabel={t("resume")}
                  />
                ))}
              </div>
            </section>
          )}

          {!myLearning.isLoading && myLearning.items.length === 0 && (
            <EmptyState
              title={t("emptyTitle")}
              description={t("emptyDescription")}
              actionLabel={t("exploreCourses")}
              onAction={() => router.push("/app/explore")}
            />
          )}

          {followedPaths.items.length > 0 && (
            <section>
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

        <aside className="mtx-dashboard-ai-nudge">
          <span className="mtx-dashboard-ai-nudge-eyebrow">
            <Icon name="aiTutor" size={20} />
            {t("aiTutorEyebrow")}
          </span>
          <h2 className="mtx-text-heading-h4">{t("aiTutorNudgeTitle")}</h2>
          <p className="mtx-text-body-small">{t("aiTutorNudgeDescription")}</p>
          <Link href="/app/ai-tutor" className="mtx-btn mtx-btn-primary">
            {t("aiTutorNudgeCta")}
          </Link>
        </aside>
      </div>
    </div>
  );
}
