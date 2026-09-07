"use client";

import { useEffect } from "react";
import { AppShell, type AppShellNavItem } from "./app-shell";
import { useRouter } from "@/i18n/navigation";
import { useCurrentUser } from "@/lib/auth/use-current-user";

const INSTRUCTOR_NAV_ITEMS: AppShellNavItem[] = [
  { key: "dashboard", href: "/instructor", icon: "dashboard" },
  { key: "profile", href: "/instructor/profile", icon: "profile" },
  { key: "settings", href: "/instructor/settings", icon: "settings" },
];

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
