"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "./button";
import { EmptyState, ErrorState } from "./state-patterns";
import { Icon } from "./icon";

export interface DataTableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => React.ReactNode;
}

export interface DataTableRowAction<T> {
  label: string;
  onSelect: (row: T) => void;
}

export interface DataTableProps<T> {
  columns: DataTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  /** Per-row actions, opened from the row's `more_vert` trigger (design-system/COMPONENTS.md §
   * DataTable). Return an empty array to hide the trigger for that specific row. */
  rowActions?: (row: T) => DataTableRowAction<T>[];
  rowActionsLabel?: string;
  loading?: boolean;
  error?: boolean;
  onRetry?: () => void;
  loadErrorDescription?: string;
  retryLabel?: string;
  emptyTitle: string;
  emptyDescription: string;
  hasNextPage?: boolean;
  hasPreviousPage?: boolean;
  onNextPage?: () => void;
  onPreviousPage?: () => void;
  previousLabel?: string;
  nextLabel?: string;
}

function RowActionMenu<T>({ row, actions, label }: { row: T; actions: DataTableRowAction<T>[]; label: string }) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function dismiss(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", dismiss);
    return () => document.removeEventListener("pointerdown", dismiss);
  }, [open]);

  if (actions.length === 0) return null;

  return (
    <div ref={containerRef} className="mtx-data-table-row-menu">
      <button
        type="button"
        className="mtx-icon-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        onClick={(event) => {
          event.stopPropagation();
          setOpen((prev) => !prev);
        }}
      >
        <Icon name="moreVert" size={20} />
      </button>
      {open && (
        <div role="menu" className="mtx-data-table-row-menu-popover">
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              role="menuitem"
              className="mtx-data-table-row-menu-item"
              onClick={(event) => {
                event.stopPropagation();
                setOpen(false);
                action.onSelect(row);
              }}
            >
              {action.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * design-system/COMPONENTS.md § DataTable (v1.2) — a true `<table>` at `desktop`/`largeDesktop`,
 * collapsing to stacked label:value cards below that (COMPONENTS.md's explicit prose; note
 * design-tokens.json's terser one-line summary says the collapse happens below `tablet` instead —
 * a narrow wording discrepancy between two design-system-tier files, resolved in favor of the
 * fuller, component-specific prose spec; see execution/DECISIONS_LOG.md D62). Both layouts render
 * from the same `columns`/`rows` data and are switched with `hidden`/breakpoint utility classes
 * (no JS viewport detection), matching this app's existing responsive convention.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  rowActions,
  rowActionsLabel = "",
  loading,
  error,
  onRetry,
  loadErrorDescription,
  retryLabel,
  emptyTitle,
  emptyDescription,
  hasNextPage,
  hasPreviousPage,
  onNextPage,
  onPreviousPage,
  previousLabel,
  nextLabel,
}: DataTableProps<T>) {
  if (error) {
    return <ErrorState description={loadErrorDescription ?? ""} retryLabel={retryLabel ?? ""} onRetry={onRetry ?? (() => undefined)} />;
  }

  if (loading) {
    return (
      <div className="mtx-data-table-skeleton" role="status" aria-hidden="true">
        {Array.from({ length: 5 }).map((_, index) => (
          <div key={index} className="mtx-skeleton mtx-data-table-skeleton-row" />
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  const showPagination = Boolean(onNextPage || onPreviousPage);

  return (
    <div className="mtx-data-table-wrap">
      <table className="mtx-data-table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
            {rowActions && <th scope="col" className="mtx-data-table-actions-header"><span className="sr-only">{rowActionsLabel}</span></th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const actions = rowActions?.(row) ?? [];
            return (
              <tr key={rowKey(row)}>
                {columns.map((column) => (
                  <td key={column.key} data-label={column.header}>
                    {column.render(row)}
                  </td>
                ))}
                {rowActions && (
                  <td className="mtx-data-table-actions-cell">
                    <RowActionMenu row={row} actions={actions} label={rowActionsLabel} />
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>

      <ul className="mtx-data-table-cards">
        {rows.map((row) => {
          const actions = rowActions?.(row) ?? [];
          return (
            <li key={rowKey(row)} className="mtx-data-table-card">
              {rowActions && (
                <div className="mtx-data-table-card-menu">
                  <RowActionMenu row={row} actions={actions} label={rowActionsLabel} />
                </div>
              )}
              {columns.map((column) => (
                <div key={column.key} className="mtx-data-table-card-field">
                  <span className="mtx-data-table-card-label">{column.header}</span>
                  <span className="mtx-data-table-card-value">{column.render(row)}</span>
                </div>
              ))}
            </li>
          );
        })}
      </ul>

      {showPagination && (
        <div className="mtx-data-table-pagination">
          <Button type="button" variant="text" onClick={onPreviousPage} disabled={!hasPreviousPage}>
            {previousLabel}
          </Button>
          <Button type="button" variant="text" onClick={onNextPage} disabled={!hasNextPage}>
            {nextLabel}
          </Button>
        </div>
      )}
    </div>
  );
}
