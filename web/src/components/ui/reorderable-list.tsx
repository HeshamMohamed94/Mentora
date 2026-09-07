"use client";

import { DragEvent, ReactNode, useState } from "react";
import { Icon } from "./icon";

export interface ReorderableEntry {
  id: string;
  content: ReactNode;
}

function movedIds(entries: ReorderableEntry[], from: number, to: number): string[] {
  const reordered = entries.map((entry) => entry.id);
  const [moved] = reordered.splice(from, 1);
  if (moved) reordered.splice(to, 0, moved);
  return reordered;
}

export function ReorderableList({ entries, onReorder, moveUpLabel, moveDownLabel, dragLabel, disabled }: {
  entries: ReorderableEntry[];
  onReorder: (ids: string[]) => void;
  moveUpLabel: string;
  moveDownLabel: string;
  dragLabel: string;
  disabled?: boolean;
}) {
  const [draggedIndex, setDraggedIndex] = useState<number>();

  function dropAt(event: DragEvent<HTMLLIElement>, targetIndex: number) {
    event.preventDefault();
    if (draggedIndex !== undefined && draggedIndex !== targetIndex) onReorder(movedIds(entries, draggedIndex, targetIndex));
    setDraggedIndex(undefined);
  }

  return (
    <ol className="mtx-reorder-list">
      {entries.map((entry, index) => (
        <li key={entry.id} className="mtx-reorder-row" data-dragging={draggedIndex === index || undefined} onDragOver={(event) => event.preventDefault()} onDrop={(event) => dropAt(event, index)}>
          <button type="button" className="mtx-drag-handle" draggable={!disabled} aria-label={dragLabel} disabled={disabled} onDragStart={() => setDraggedIndex(index)} onDragEnd={() => setDraggedIndex(undefined)}>
            <Icon name="dragHandle" size={20} />
          </button>
          <div className="mtx-reorder-content">{entry.content}</div>
          <div className="mtx-reorder-actions">
            <button type="button" className="mtx-icon-button" aria-label={moveUpLabel} disabled={disabled || index === 0} onClick={() => onReorder(movedIds(entries, index, index - 1))}><Icon name="arrowUpward" size={20} /></button>
            <button type="button" className="mtx-icon-button" aria-label={moveDownLabel} disabled={disabled || index === entries.length - 1} onClick={() => onReorder(movedIds(entries, index, index + 1))}><Icon name="arrowDownward" size={20} /></button>
          </div>
        </li>
      ))}
    </ol>
  );
}
