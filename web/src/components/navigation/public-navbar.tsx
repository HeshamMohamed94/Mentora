import { getTranslations } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { Button } from "@/components/ui";

/**
 * design-system/COMPONENTS.md § Navigation, product/INFORMATION_ARCHITECTURE.md § 1's locked
 * link set: Explore, Learning Paths, Search, Login, Get Started — no "Pricing" (approved
 * decision, product/MVP_SCOPE.md § 5). Height 64 (ux/WEB_UX.md § 1).
 */
export async function PublicNavbar({ sticky = false }: { sticky?: boolean }) {
  const t = await getTranslations("nav");
  const tCommon = await getTranslations("common");

  return (
    <header
      className={`h-16 border-b border-border-default bg-surface-default ${sticky ? "sticky top-0 z-40" : ""}`}
    >
      <div className="mx-auto flex h-full max-w-[1440px] items-center justify-between gap-4 px-4 tablet:px-6 desktop:px-8 large-desktop:px-12">
        <Link href="/" className="mtx-text-heading-h4 shrink-0" style={{ color: "var(--color-brand-primary)" }}>
          {tCommon("appName")}
        </Link>
        <nav aria-label={t("explore")} className="hidden items-center gap-1 tablet:flex">
          <Link href="/explore" className="mtx-nav-item">
            {t("explore")}
          </Link>
          <Link href="/paths" className="mtx-nav-item">
            {t("learningPaths")}
          </Link>
        </nav>
        <div className="flex items-center gap-2">
          <Link href="/login" className="mtx-nav-item">
            {t("login")}
          </Link>
          <Link href="/register">
            <Button variant="primary">{t("getStarted")}</Button>
          </Link>
        </div>
      </div>
    </header>
  );
}
