import { getTranslations } from "next-intl/server";
import { Link } from "@/i18n/navigation";

/**
 * ux/SCREEN_UX_SPECS.md §§ 6-7 (Login/Register): "minimal — logo/wordmark only, no Navbar
 * link row (nothing to navigate to from a focused auth form)". Distinct from PublicNavbar,
 * which carries the full link set for every other public page.
 */
export async function AuthHeader() {
  const tCommon = await getTranslations("common");

  return (
    <header className="mtx-auth-header">
      <Link href="/" className="mtx-text-heading-h4 mtx-auth-wordmark">
        {tCommon("appName")}
      </Link>
    </header>
  );
}
