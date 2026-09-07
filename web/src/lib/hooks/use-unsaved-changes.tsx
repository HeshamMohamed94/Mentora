"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AppDialog } from "@/components/ui";
import { useRouter } from "@/i18n/navigation";

interface GuardLabels {
  title: string;
  description: string;
  discard: string;
  keepEditing: string;
}

export function useUnsavedChanges(dirty: boolean, labels: GuardLabels) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const pendingAction = useRef<() => void>(() => undefined);

  const requestAction = useCallback((action: () => void) => {
    if (!dirty) { action(); return; }
    pendingAction.current = action;
    setConfirming(true);
  }, [dirty]);

  useEffect(() => {
    function warnBeforeUnload(event: BeforeUnloadEvent) {
      if (!dirty) return;
      event.preventDefault();
    }
    function guardLink(event: MouseEvent) {
      if (!dirty || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = (event.target as Element).closest("a[href]") as HTMLAnchorElement | null;
      if (!anchor || anchor.target === "_blank" || anchor.href === window.location.href) return;
      event.preventDefault();
      event.stopPropagation();
      requestAction(() => window.location.assign(anchor.href));
    }
    window.addEventListener("beforeunload", warnBeforeUnload);
    document.addEventListener("click", guardLink, true);
    return () => {
      window.removeEventListener("beforeunload", warnBeforeUnload);
      document.removeEventListener("click", guardLink, true);
    };
  }, [dirty, requestAction]);

  function navigate(href: string) {
    requestAction(() => router.push(href));
  }

  const dialog = (
    <AppDialog
      open={confirming}
      title={labels.title}
      description={labels.description}
      confirmLabel={labels.discard}
      cancelLabel={labels.keepEditing}
      onCancel={() => setConfirming(false)}
      onConfirm={() => {
        setConfirming(false);
        pendingAction.current();
      }}
    />
  );

  return { navigate, requestAction, dialog };
}
