"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Avatar, DataTable, SearchField, type DataTableColumn, type DataTableRowAction } from "@/components/ui";
import { useRouter } from "@/i18n/navigation";
import { useAdminInstructors, type AdminInstructorSummary } from "@/lib/api/admin";
import { useCursorPagination } from "@/lib/hooks/use-cursor-pagination";
import { useDebouncedValue } from "@/lib/hooks/use-debounced-value";
import { formatCount } from "@/lib/i18n/format";

export function AdminInstructorsScreen() {
  const t = useTranslations("admin");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const router = useRouter();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const { cursor, hasPreviousPage, goNext, goPrevious } = useCursorPagination(debouncedSearch);

  const instructors = useAdminInstructors({ q: debouncedSearch || undefined, cursor });

  const columns: DataTableColumn<AdminInstructorSummary>[] = [
    {
      key: "name",
      header: t("instructors.columnName"),
      render: (instructor) => (
        <span className="mtx-admin-identity">
          <Avatar name={instructor.name} size="small" />
          {instructor.name}
        </span>
      ),
    },
    { key: "email", header: t("instructors.columnEmail"), render: (instructor) => instructor.email },
    { key: "courses", header: t("instructors.columnCourses"), render: (instructor) => formatCount(instructor.courseCount, locale) },
    { key: "published", header: t("instructors.columnPublished"), render: (instructor) => formatCount(instructor.publishedCount, locale) },
  ];

  function rowActions(instructor: AdminInstructorSummary): DataTableRowAction<AdminInstructorSummary>[] {
    return [
      {
        label: t("instructors.viewCourses"),
        onSelect: () => router.push(`/admin/courses?instructor=${encodeURIComponent(instructor.name)}`),
      },
    ];
  }

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <h1 className="mtx-text-heading-h1">{t("instructors.title")}</h1>
      </div>

      <div className="mtx-admin-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          label={t("instructors.searchLabel")}
          placeholder={t("instructors.searchPlaceholder")}
          clearLabel={tCommon("clearSearch")}
        />
      </div>

      <DataTable
        columns={columns}
        rows={instructors.data?.items ?? []}
        rowKey={(instructor) => instructor.id}
        rowActions={rowActions}
        rowActionsLabel={t("instructors.rowActionsLabel")}
        loading={instructors.isLoading}
        error={instructors.isError}
        onRetry={() => instructors.refetch()}
        loadErrorDescription={t("instructors.loadError")}
        retryLabel={tCommon("retry")}
        emptyTitle={t("instructors.emptyTitle")}
        emptyDescription={t("instructors.emptyDescription")}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={Boolean(instructors.data?.nextCursor)}
        onPreviousPage={goPrevious}
        onNextPage={() => goNext(instructors.data?.nextCursor)}
        previousLabel={t("instructors.previous")}
        nextLabel={t("instructors.next")}
      />
    </div>
  );
}
