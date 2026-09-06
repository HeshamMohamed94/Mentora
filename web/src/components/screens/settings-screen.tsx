"use client";

import { useLocale, useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button, ErrorState, Select, type SelectOption } from "@/components/ui";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useUpdateCurrentUser } from "@/lib/api/users";
import { logout } from "@/lib/auth/actions";
import { CURRENT_USER_QUERY_KEY, useCurrentUser } from "@/lib/auth/use-current-user";
import type { AppLocale } from "@/i18n/routing";
import { useTheme, type ThemePreference } from "@/lib/theme/use-theme";

export function SettingsScreen() {
  const t = useTranslations("settings");
  const locale = useLocale() as AppLocale;
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const userQuery = useCurrentUser();
  const updateUser = useUpdateCurrentUser();
  const { preference, setPreference } = useTheme();

  const themeOptions: readonly SelectOption<ThemePreference>[] = [
    { value: "light", label: t("themeLight") },
    { value: "dark", label: t("themeDark") },
    { value: "system", label: t("themeSystem") },
  ];
  const languageOptions: readonly SelectOption<AppLocale>[] = [
    { value: "en", label: "English" },
    { value: "ar", label: "العربية" },
  ];

  function changeLanguage(nextLocale: AppLocale) {
    updateUser.mutate({ preferredLocale: nextLocale });
    router.replace(pathname, { locale: nextLocale });
  }

  async function signOut() {
    await logout();
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, undefined);
    router.push("/login");
  }

  if (userQuery.isError) {
    return <div className="mtx-account-page"><ErrorState description={t("loadError")} retryLabel={t("retry")} onRetry={() => userQuery.refetch()} /></div>;
  }

  if (!userQuery.data) {
    return (
      <div className="mtx-account-page" role="status" aria-label={t("loading")}>
        <div className="mtx-skeleton mtx-account-skeleton" aria-hidden="true" />
      </div>
    );
  }

  return (
    <div className="mtx-account-page">
      <h1 className="mtx-text-heading-h1">{t("title")}</h1>
      <section className="mtx-account-card" aria-label={t("preferences")}>
        <div className="mtx-settings-fields">
          <Select
            label={t("themeLabel")}
            value={preference}
            options={themeOptions}
            onChange={setPreference}
          />
          <Select
            label={t("languageLabel")}
            value={locale}
            options={languageOptions}
            disabled={updateUser.isPending}
            error={updateUser.isError ? t("languageSaveError") : undefined}
            onChange={changeLanguage}
          />
        </div>
        <Button type="button" variant="text" onClick={() => void signOut()}>{t("logout")}</Button>
      </section>
    </div>
  );
}
