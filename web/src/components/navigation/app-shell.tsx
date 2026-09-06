"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { Icon, type IconName } from "@/components/ui";
import { useCurrentUser, CURRENT_USER_QUERY_KEY } from "@/lib/auth/use-current-user";
import { logout } from "@/lib/auth/actions";

const NAV_ITEMS: { key: string; href: string; icon: IconName }[] = [
  { key: "dashboard", href: "/app", icon: "dashboard" },
  { key: "explore", href: "/app/explore", icon: "explore" },
  { key: "myLearning", href: "/app/my-learning", icon: "myLearning" },
  { key: "learningPaths", href: "/app/paths", icon: "learningPaths" },
  { key: "aiTutor", href: "/app/ai-tutor", icon: "aiTutor" },
  { key: "certificates", href: "/app/certificates", icon: "certificates" },
  { key: "profile", href: "/app/profile", icon: "profile" },
  { key: "settings", href: "/app/settings", icon: "settings" },
];

const COLLAPSE_STORAGE_KEY = "mentora:sidebar-collapsed";

function isActive(pathname: string, href: string): boolean {
  return href === "/app" ? pathname === "/app" : pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * design-system/COMPONENTS.md § Sidebar + ux/WEB_UX.md § "Authenticated pages do not show the
 * public Navbar — the Sidebar replaces it." Wraps every `/app/*` page (see
 * `app/[locale]/app/layout.tsx`) — previously those pages rendered with no chrome at all.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const router = useRouter();
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    try {
      setCollapsed(window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === "true");
    } catch {
      // ignore — collapse preference is a convenience, not required for correctness
    }
  }, []);

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(COLLAPSE_STORAGE_KEY, String(next));
      } catch {
        // ignore
      }
      return next;
    });
  }

  async function handleLogout() {
    await logout();
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, undefined);
    router.push("/login");
  }

  const labelClass = `mtx-sidebar-label tablet:hidden ${collapsed ? "desktop:hidden" : "desktop:inline"}`;

  return (
    <div className="flex min-h-screen">
      {mobileOpen && <div className="mtx-sidebar-scrim tablet:hidden" onClick={() => setMobileOpen(false)} />}

      <aside
        className={`mtx-sidebar ${mobileOpen ? "flex" : "hidden"} tablet:flex fixed inset-y-0 start-0 z-50 tablet:static tablet:z-auto w-[264px] tablet:w-[72px] ${collapsed ? "desktop:w-[72px]" : "desktop:w-[264px]"}`}
      >
        <nav className="mtx-sidebar-nav" aria-label={t("primaryNavigation")}>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.key}
              href={item.href}
              className="mtx-sidebar-item"
              data-active={isActive(pathname, item.href) || undefined}
              title={t(item.key)}
              onClick={() => setMobileOpen(false)}
            >
              <Icon name={item.icon} />
              <span className={labelClass}>{t(item.key)}</span>
            </Link>
          ))}
        </nav>

        <div className="mtx-sidebar-nav">
          <button
            type="button"
            className="mtx-sidebar-item hidden desktop:flex"
            onClick={toggleCollapsed}
            title={t("collapseSidebar")}
          >
            <Icon name="menu" />
            <span className={labelClass}>{t("collapseSidebar")}</span>
          </button>
          <button type="button" className="mtx-sidebar-item" onClick={handleLogout} title={t("logout")}>
            <Icon name="logout" />
            <span className={labelClass}>{t("logout")}</span>
          </button>
        </div>
      </aside>

      <div className="flex flex-1 flex-col">
        <header className="mtx-topbar">
          <button
            type="button"
            className="mtx-btn mtx-btn-text tablet:hidden"
            onClick={() => setMobileOpen(true)}
            aria-label={t("openMenu")}
          >
            <Icon name="menu" />
          </button>
          <div className="flex flex-1 items-center justify-end gap-3">
            {user && (
              <span className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
                {user.name}
              </span>
            )}
          </div>
        </header>
        <main id="main-content" className="flex-1">
          {children}
        </main>
      </div>
    </div>
  );
}
