"use client";

import { useEffect } from "react";
import { AppShell, type AppShellNavItem } from "./app-shell";
import { useRouter } from "@/i18n/navigation";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { adminNavItems } from "@/lib/design-to-code.generated";

/** design-to-code/shared/navigation.json#/shells/adminWeb — source of truth for this list. */
const ADMIN_NAV_ITEMS: AppShellNavItem[] = adminNavItems as AppShellNavItem[];

export function AdminShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const currentUser = useCurrentUser();

  useEffect(() => {
    if (currentUser.data && currentUser.data.role !== "admin") router.replace("/app");
  }, [currentUser.data, router]);

  if (!currentUser.data || currentUser.data.role !== "admin") {
    return <div className="mtx-instructor-page"><div className="mtx-skeleton mtx-account-skeleton" /></div>;
  }
  return <AppShell navItems={ADMIN_NAV_ITEMS}>{children}</AppShell>;
}
