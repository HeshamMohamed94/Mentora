"use client";

import { useEffect, useState } from "react";

/**
 * Client-side cursor stack for the backend's opaque forward-only cursor pagination
 * (`common/Pagination.kt`) — the API only ever returns `nextCursor`, never a "previous" cursor, so
 * "Previous" is implemented by remembering the cursor stack this session has already walked
 * through, not a second API capability. `resetKey` (e.g. a search query) clears back to page 1
 * whenever it changes, since a stale cursor from a different filter is meaningless.
 */
export function useCursorPagination(resetKey: unknown) {
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [history, setHistory] = useState<(string | undefined)[]>([]);

  useEffect(() => {
    setCursor(undefined);
    setHistory([]);
  }, [resetKey]);

  function goNext(nextCursor: string | undefined) {
    if (!nextCursor) return;
    setHistory((prev) => [...prev, cursor]);
    setCursor(nextCursor);
  }

  function goPrevious() {
    setHistory((prev) => {
      if (prev.length === 0) return prev;
      const next = [...prev];
      setCursor(next.pop());
      return next;
    });
  }

  return { cursor, hasPreviousPage: history.length > 0, goNext, goPrevious };
}
