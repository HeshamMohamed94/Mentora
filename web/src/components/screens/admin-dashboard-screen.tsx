"use client";

import { useLocale, useTranslations } from "next-intl";
import { ErrorState, Icon, StatCard, type IconName } from "@/components/ui";
import { Link } from "@/i18n/navigation";
import { useAdminDashboard } from "@/lib/api/admin";
import { formatCount } from "@/lib/i18n/format";

const QUICK_LINKS: { key: "manageCourses" | "manageUsers" | "manageInstructors" | "manageCategories"; href: string; icon: IconName; descriptionKey: "manageCoursesDescription" | "manageUsersDescription" | "manageInstructorsDescription" | "manageCategoriesDescription" }[] = [
  { key: "manageCourses", href: "/admin/courses", icon: "myLearning", descriptionKey: "manageCoursesDescription" },
  { key: "manageUsers", href: "/admin/users", icon: "people", descriptionKey: "manageUsersDescription" },
  { key: "manageInstructors", href: "/admin/instructors", icon: "school", descriptionKey: "manageInstructorsDescription" },
  { key: "manageCategories", href: "/admin/categories", icon: "courseGrid", descriptionKey: "manageCategoriesDescription" },
];

export function AdminDashboardScreen() {
  const t = useTranslations("admin");
  const locale = useLocale();
  const dashboard = useAdminDashboard();

  if (dashboard.isError) {
    return (
      <div className="mtx-instructor-page">
        <ErrorState description={t("dashboard.loadError")} retryLabel={t("common.retry")} onRetry={() => dashboard.refetch()} />
      </div>
    );
  }

  if (!dashboard.data) {
    return (
      <div className="mtx-instructor-page" role="status" aria-label={t("common.loading")}>
        <div className="mtx-skeleton mtx-account-skeleton" />
      </div>
    );
  }

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <h1 className="mtx-text-heading-h1">{t("dashboard.title")}</h1>
      </div>

      <div className="mtx-admin-stats">
        <StatCard value={formatCount(dashboard.data.totalCourses, locale)} label={t("dashboard.totalCourses")} />
        <StatCard value={formatCount(dashboard.data.publishedCourses, locale)} label={t("dashboard.publishedCourses")} />
        <StatCard value={formatCount(dashboard.data.draftCourses, locale)} label={t("dashboard.draftCourses")} />
        <StatCard value={formatCount(dashboard.data.totalStudents, locale)} label={t("dashboard.totalStudents")} />
        <StatCard value={formatCount(dashboard.data.totalInstructors, locale)} label={t("dashboard.totalInstructors")} />
      </div>

      <section aria-labelledby="admin-manage-heading">
        <h2 id="admin-manage-heading" className="mtx-text-heading-h2 mb-4">{t("dashboard.manageHeading")}</h2>
        <div className="mtx-course-grid">
          {QUICK_LINKS.map((link) => (
            <Link key={link.key} href={link.href} className="mtx-path-card">
              <span className="mtx-path-card-eyebrow">
                <Icon name={link.icon} size={20} />
              </span>
              <h3 className="mtx-text-heading-h4">{t(`dashboard.${link.key}`)}</h3>
              <p className="mtx-text-body-small mtx-path-card-description">{t(`dashboard.${link.descriptionKey}`)}</p>
              <span className="mtx-btn mtx-btn-text mtx-path-card-cta" aria-hidden="true">
                <Icon name="arrowForward" size={20} className="mtx-icon-mirror-rtl" />
              </span>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
