"use client";

import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui";
import { useCurrentUser } from "@/lib/auth/use-current-user";
import { CURRENT_USER_QUERY_KEY } from "@/lib/auth/use-current-user";
import { logout } from "@/lib/auth/actions";
import { useRouter } from "@/i18n/navigation";

/**
 * Minimal authenticated shell proving the end-to-end session loop (cookie set on
 * login/register → middleware admits /app → GET /users/me succeeds → logout clears the
 * session and middleware bounces back to /login). Full Dashboard content (Continue
 * Learning / Recommended / Learning Paths modules per ux/WEB_UX.md § 5) is task 5 —
 * this is the foundation checkpoint's smoke-test shell, not the real screen.
 */
export function DashboardShell() {
  const t = useTranslations("nav");
  const { data: user, isLoading } = useCurrentUser();
  const router = useRouter();
  const queryClient = useQueryClient();

  async function handleLogout() {
    await logout();
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, undefined);
    router.push("/login");
  }

  if (isLoading) {
    return <p className="mtx-text-body-medium p-8">…</p>;
  }

  return (
    <div className="flex flex-col gap-4 p-8">
      <h1 className="mtx-text-heading-h1">{t("dashboard")}</h1>
      {user && (
        <p className="mtx-text-body-medium">
          {user.name} — {user.email} ({user.role})
        </p>
      )}
      <Button variant="secondary" onClick={handleLogout} className="w-fit">
        {t("logout")}
      </Button>
    </div>
  );
}
