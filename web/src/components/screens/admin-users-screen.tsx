"use client";

import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Avatar, DataTable, SearchField, type DataTableColumn } from "@/components/ui";
import { useAdminUsers, type AdminUserSummary } from "@/lib/api/admin";
import { useCursorPagination } from "@/lib/hooks/use-cursor-pagination";
import { useDebouncedValue } from "@/lib/hooks/use-debounced-value";
import { formatCount, formatDate } from "@/lib/i18n/format";

export function AdminUsersScreen() {
  const t = useTranslations("admin");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const [search, setSearch] = useState("");
  const debouncedSearch = useDebouncedValue(search);
  const { cursor, hasPreviousPage, goNext, goPrevious } = useCursorPagination(debouncedSearch);

  const users = useAdminUsers({ q: debouncedSearch || undefined, cursor });

  const columns: DataTableColumn<AdminUserSummary>[] = [
    {
      key: "name",
      header: t("users.columnName"),
      render: (user) => (
        <span className="mtx-admin-identity">
          <Avatar name={user.name} size="small" />
          {user.name}
        </span>
      ),
    },
    { key: "email", header: t("users.columnEmail"), render: (user) => user.email },
    { key: "joined", header: t("users.columnJoined"), render: (user) => formatDate(user.createdAt, locale) },
    { key: "enrollments", header: t("users.columnEnrollments"), render: (user) => formatCount(user.enrollmentCount, locale) },
  ];

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <h1 className="mtx-text-heading-h1">{t("users.title")}</h1>
      </div>

      <div className="mtx-admin-filter-bar">
        <SearchField
          value={search}
          onChange={setSearch}
          label={t("users.searchLabel")}
          placeholder={t("users.searchPlaceholder")}
          clearLabel={tCommon("clearSearch")}
        />
      </div>

      <DataTable
        columns={columns}
        rows={users.data?.items ?? []}
        rowKey={(user) => user.id}
        loading={users.isLoading}
        error={users.isError}
        onRetry={() => users.refetch()}
        loadErrorDescription={t("users.loadError")}
        retryLabel={tCommon("retry")}
        emptyTitle={t("users.emptyTitle")}
        emptyDescription={t("users.emptyDescription")}
        hasPreviousPage={hasPreviousPage}
        hasNextPage={Boolean(users.data?.nextCursor)}
        onPreviousPage={goPrevious}
        onNextPage={() => goNext(users.data?.nextCursor)}
        previousLabel={t("users.previous")}
        nextLabel={t("users.next")}
      />
    </div>
  );
}
