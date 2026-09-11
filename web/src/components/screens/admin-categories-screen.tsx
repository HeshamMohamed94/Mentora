"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button, DataTable, TextField, type DataTableColumn, type DataTableRowAction } from "@/components/ui";
import { AppDialog } from "@/components/ui/app-dialog";
import { ApiError } from "@/lib/api/client";
import {
  useCategories,
  useCreateCategory,
  useDeleteCategory,
  useUpdateCategory,
  type Category,
} from "@/lib/api/categories";
import { formatCount } from "@/lib/i18n/format";

function CategoryFormDialog({
  open,
  category,
  onClose,
}: {
  open: boolean;
  category: Category | null;
  onClose: () => void;
}) {
  const t = useTranslations("admin.categories");
  const tCommon = useTranslations("common");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const createMutation = useCreateCategory();
  const updateMutation = useUpdateCategory();
  const saving = createMutation.isPending || updateMutation.isPending;

  useEffect(() => {
    if (open) {
      setName(category?.name ?? "");
      setError(null);
    }
  }, [open, category]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError(t("nameRequiredError"));
      return;
    }
    setError(null);
    try {
      if (category) {
        await updateMutation.mutateAsync({ id: category.id, name: trimmed });
      } else {
        await createMutation.mutateAsync(trimmed);
      }
      onClose();
    } catch {
      setError(t("saveError"));
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="mtx-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <h2 id={titleId} className="mtx-text-heading-h3">
        {category ? t("editTitle") : t("createTitle")}
      </h2>
      <form onSubmit={handleSubmit} className="mtx-dialog-form">
        <TextField
          label={t("nameLabel")}
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={error ?? undefined}
          autoFocus
        />
        <div className="mtx-dialog-actions">
          <Button type="button" variant="text" onClick={onClose}>
            {tCommon("cancel")}
          </Button>
          <Button type="submit" variant="primary" loading={saving}>
            {tCommon("save")}
          </Button>
        </div>
      </form>
    </dialog>
  );
}

export function AdminCategoriesScreen() {
  const t = useTranslations("admin");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const categories = useCategories();
  const deleteMutation = useDeleteCategory();
  const [formState, setFormState] = useState<{ open: boolean; category: Category | null }>({ open: false, category: null });
  const [pendingDelete, setPendingDelete] = useState<Category | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setFormState({ open: true, category: null });
  }
  function openEdit(category: Category) {
    setFormState({ open: true, category });
  }
  function closeForm() {
    setFormState({ open: false, category: null });
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    setDeleteError(null);
    try {
      await deleteMutation.mutateAsync(pendingDelete.id);
      setPendingDelete(null);
    } catch (err) {
      if (err instanceof ApiError && err.code === "CATEGORY_IN_USE") {
        setDeleteError(t("categories.deleteInUseError"));
      } else {
        setDeleteError(t("categories.deleteError"));
      }
    }
  }

  const columns: DataTableColumn<Category>[] = [
    { key: "name", header: t("categories.columnName"), render: (category) => category.name },
    { key: "slug", header: t("categories.columnSlug"), render: (category) => category.slug },
    { key: "courses", header: t("categories.columnCourses"), render: (category) => formatCount(category.courseCount, locale) },
  ];

  function rowActions(category: Category): DataTableRowAction<Category>[] {
    return [
      { label: t("categories.edit"), onSelect: () => openEdit(category) },
      { label: t("categories.delete"), onSelect: () => { setDeleteError(null); setPendingDelete(category); } },
    ];
  }

  return (
    <div className="mtx-instructor-page">
      <div className="mtx-instructor-header">
        <h1 className="mtx-text-heading-h1">{t("categories.title")}</h1>
        <Button type="button" onClick={openCreate}>
          {t("categories.addCategory")}
        </Button>
      </div>

      <DataTable
        columns={columns}
        rows={categories.data ?? []}
        rowKey={(category) => category.id}
        rowActions={rowActions}
        rowActionsLabel={t("categories.rowActionsLabel")}
        loading={categories.isLoading}
        error={categories.isError}
        onRetry={() => categories.refetch()}
        loadErrorDescription={t("categories.loadError")}
        retryLabel={tCommon("retry")}
        emptyTitle={t("categories.emptyTitle")}
        emptyDescription={t("categories.emptyDescription")}
      />

      <CategoryFormDialog open={formState.open} category={formState.category} onClose={closeForm} />

      <AppDialog
        open={Boolean(pendingDelete)}
        title={t("categories.deleteConfirmTitle")}
        description={deleteError ?? t("categories.deleteConfirmDescription")}
        confirmLabel={t("categories.delete")}
        cancelLabel={tCommon("cancel")}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
