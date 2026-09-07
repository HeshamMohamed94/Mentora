"use client";

import { useEffect } from "react";
import { AppShell, type AppShellNavItem } from "./app-shell";
import { useRouter } from "@/i18n/navigation";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { instructorNavItems } from "@/lib/design-to-code.generated";

/** design-to-code/shared/navigation.json#/shells/instructorWeb — source of truth for this list. */
const INSTRUCTOR_NAV_ITEMS: AppShellNavItem[] = instructorNavItems as AppShellNavItem[];

export function InstructorShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const currentUser = useCurrentUser();

  useEffect(() => {
    if (currentUser.data && currentUser.data.role !== "instructor") router.replace("/app");
  }, [currentUser.data, router]);

  if (!currentUser.data || currentUser.data.role !== "instructor") {
    return <div className="mtx-instructor-page"><div className="mtx-skeleton mtx-account-skeleton" /></div>;
  }
  return <AppShell navItems={INSTRUCTOR_NAV_ITEMS}>{children}</AppShell>;
}
