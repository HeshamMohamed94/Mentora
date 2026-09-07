"use client";

import { useEffect, useId, useRef } from "react";
import { Button } from "./button";

export function AppDialog({ open, title, description, confirmLabel, cancelLabel, onConfirm, onCancel }: {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={dialogRef} className="mtx-dialog" aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined} onCancel={(event) => { event.preventDefault(); onCancel(); }}>
      <h2 id={titleId} className="mtx-text-heading-h3">{title}</h2>
      {description && <p id={descriptionId} className="mtx-dialog-body">{description}</p>}
      <div className="mtx-dialog-actions">
        <Button type="button" variant="text" onClick={onCancel}>{cancelLabel}</Button>
        <Button type="button" variant="primary" onClick={onConfirm}>{confirmLabel}</Button>
      </div>
    </dialog>
  );
}
