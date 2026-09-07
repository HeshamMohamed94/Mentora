"use client";

import { useLocale, useTranslations } from "next-intl";
import { Badge, Button, EmptyState, ErrorState, StatCard } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useInstructorDashboard } from "@/lib/api/instructor";
import { formatCount } from "@/lib/i18n/format";

export function InstructorDashboardScreen() {
  const t = useTranslations("instructor");
  const locale = useLocale();
  const router = useRouter();
  const dashboard = useInstructorDashboard();

  if (dashboard.isError) {
    return <div className="mtx-instructor-page"><ErrorState description={t("dashboard.loadError")} retryLabel={t("common.retry")} onRetry={() => dashboard.refetch()} /></div>;
  }
  if (!dashboard.data) {
    return <div className="mtx-instructor-page" role="status" aria-label={t("common.loading")}><div className="mtx-skeleton mtx-account-skeleton" /></div>;
  }

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <h1 className="mtx-text-heading-h1">{t("dashboard.title")}</h1>
        <Button type="button" onClick={() => router.push("/instructor/courses/new")}>{t("dashboard.createCourse")}</Button>
      </div>
      <div className="mtx-instructor-stats">
        <StatCard value={formatCount(dashboard.data.stats.totalCourses, locale)} label={t("dashboard.totalCourses")} />
        <StatCard value={formatCount(dashboard.data.stats.publishedCount, locale)} label={t("dashboard.publishedCourses")} />
        <StatCard value={formatCount(dashboard.data.stats.totalEnrollments, locale)} label={t("dashboard.totalEnrollments")} />
      </div>
      <section aria-labelledby="instructor-courses-heading">
        <h2 id="instructor-courses-heading" className="mtx-text-heading-h2 mb-4">{t("dashboard.yourCourses")}</h2>
        {dashboard.data.courses.length === 0 ? (
          <EmptyState title={t("dashboard.emptyTitle")} description={t("dashboard.emptyDescription")} actionLabel={t("dashboard.createCourse")} onAction={() => router.push("/instructor/courses/new")} />
        ) : (
          <div className="mtx-instructor-list">
            {dashboard.data.courses.map((course) => (
              <Link key={course.id} href={`/instructor/courses/${course.id}`} className="mtx-management-card">
                <strong>{course.title}</strong>
                <Badge variant={course.status === "published" ? "success" : "warning"}>{t(`status.${course.status}`)}</Badge>
                <span>{t("dashboard.enrollments", { count: formatCount(course.enrollmentCount, locale) })}</span>
                <span>{t("dashboard.completion", { percent: formatCount(course.completionRate, locale) })}</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
