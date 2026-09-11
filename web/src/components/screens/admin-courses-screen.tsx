"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { Badge, DataTable, Select, SearchField, type DataTableColumn, type DataTableRowAction } from "@/components/ui";
import { AppDialog } from "@/components/ui/app-dialog";
import { Link } from "@/i18n/navigation";
import { useAdminCourses, useAdminDashboard, useAdminUnpublishCourse, type AdminCourseSummary } from "@/lib/api/admin";
import { useCursorPagination } from "@/lib/hooks/use-cursor-pagination";
import { useDebouncedValue } from "@/lib/hooks/use-debounced-value";

const INSTRUCTOR_FILTER_LIMIT = 100;
type StatusFilter = "all" | "published" | "draft";

export function AdminCoursesScreen() {
  const t = useTranslations("admin");
  const tCommon = useTranslations("common");
  const searchParams = useSearchParams();
  const instructorFilter = searchParams.get("instructor");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const debouncedSearch = useDebouncedValue(search);
  const { cursor, hasPreviousPage, goNext, goPrevious } = useCursorPagination(debouncedSearch);
  const [pendingUnpublish, setPendingUnpublish] = useState<AdminCourseSummary | null>(null);
  const [unpublishError, setUnpublishError] = useState(false);

  const hasClientSideFilter = Boolean(instructorFilter) || statusFilter !== "all";
  const dashboard = useAdminDashboard();
  const courses = useAdminCourses({
    q: debouncedSearch || undefined,
    cursor: hasClientSideFilter ? undefined : cursor,
    limit: hasClientSideFilter ? INSTRUCTOR_FILTER_LIMIT : undefined,
  });
  const unpublishMutation = useAdminUnpublishCourse();

  const rows = (instructorFilter
    ? (courses.data?.items ?? []).filter((course) => course.instructorName === instructorFilter)
    : (courses.data?.items ?? [])
  ).filter((course) => statusFilter === "all" || course.status === statusFilter);

  const columns: DataTableColumn<AdminCourseSummary>[] = [
    {
      key: "title",
      header: t("courses.columnCourse"),
      render: (course) => (
        <Link href={`/app/courses/${course.id}`} className="mtx-link" title={t("courses.open")}>
          {course.title}
        </Link>
      ),
    },
    { key: "instructor", header: t("courses.columnInstructor"), render: (course) => course.instructorName },
    {
      key: "status",
      header: t("courses.columnStatus"),
      render: (course) => (
        <Badge variant={course.status === "published" ? "success" : "warning"}>{t(`status.${course.status}`)}</Badge>
      ),
    },
    { key: "enrollments", header: t("courses.columnEnrollments"), render: (course) => course.enrollmentCount },
  ];

  function rowActions(course: AdminCourseSummary): DataTableRowAction<AdminCourseSummary>[] {
    if (course.status !== "published") return [];
    return [{ label: t("courses.unpublish"), onSelect: () => setPendingUnpublish(course) }];
  }

  async function confirmUnpublish() {
    if (!pendingUnpublish) return;
    setUnpublishError(false);
    try {
      await unpublishMutation.mutateAsync(pendingUnpublish.id);
      setPendingUnpublish(null);
    } catch {
      setUnpublishError(true);
    }
  }

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <div>
          <h1 className="mtx-text-heading-h1">{t("courses.title")}</h1>
          {dashboard.data && (
            <p className="mtx-text-body-small mtx-admin-subtitle">
              {t("courses.subtitle", { total: dashboard.data.totalCourses, published: dashboard.data.publishedCourses })}
            </p>
          )}
        </div>
      </div>

      <p className="mtx-info-banner">{t("courses.infoBanner")}</p>

      <div className="mtx-admin-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          label={t("courses.searchLabel")}
          placeholder={t("courses.searchPlaceholder")}
          clearLabel={tCommon("clearSearch")}
        />
        <Select
          label={t("courses.statusFilterLabel")}
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: "all", label: t("courses.statusFilterAll") },
            { value: "published", label: t("status.published") },
            { value: "draft", label: t("status.draft") },
          ]}
        />
      </div>

      {instructorFilter && (
        <div className="mtx-admin-filter-bar">
          <Badge variant="brand">{t("courses.filteredByInstructor", { name: instructorFilter })}</Badge>
          <Link href="/admin/courses" className="mtx-btn mtx-btn-text">
            {t("courses.clearFilter")}
          </Link>
        </div>
      )}

      {unpublishError && <p className="mtx-editor-error" role="alert">{t("courses.unpublishError")}</p>}

      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(course) => course.id}
        rowActions={rowActions}
        rowActionsLabel={t("courses.rowActionsLabel")}
        loading={courses.isLoading}
        error={courses.isError}
        onRetry={() => courses.refetch()}
        loadErrorDescription={t("courses.loadError")}
        retryLabel={t("common.retry")}
        emptyTitle={t("courses.emptyTitle")}
        emptyDescription={t("courses.emptyDescription")}
        hasPreviousPage={!hasClientSideFilter && hasPreviousPage}
        hasNextPage={!hasClientSideFilter && Boolean(courses.data?.nextCursor)}
        onPreviousPage={goPrevious}
        onNextPage={() => goNext(courses.data?.nextCursor)}
        previousLabel={t("courses.previous")}
        nextLabel={t("courses.next")}
      />

      <AppDialog
        open={Boolean(pendingUnpublish)}
        title={t("courses.unpublishConfirmTitle")}
        description={t("courses.unpublishConfirmDescription")}
        confirmLabel={t("courses.unpublish")}
        cancelLabel={tCommon("cancel")}
        onConfirm={confirmUnpublish}
        onCancel={() => setPendingUnpublish(null)}
      />
    </div>
  );
}
