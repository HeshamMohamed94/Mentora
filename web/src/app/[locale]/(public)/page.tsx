import { getTranslations } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { PublicNavbar } from "@/components/navigation/public-navbar";
import { Button } from "@/components/ui";

/**
 * Landing (product/SCREEN_INVENTORY.md § 1). Full hero/featured-courses/featured-paths
 * content lands with task 3 (Explore/Course Details/Learning Paths) once course data can be
 * fetched — this is the minimal, real (not stubbed-out) shell proving the App Router + i18n +
 * token pipeline work end-to-end.
 */
export default async function LandingPage() {
  const t = await getTranslations("landing");

  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content" className="mx-auto max-w-[1280px] px-4 py-12 tablet:px-6 desktop:px-8">
        <section className="flex flex-col items-start gap-6 py-12 text-start">
          {/* Responsive sizing comes from the generated media-query blocks on the CSS vars
              themselves (tools/token-pipeline/generate.js), not a Tailwind breakpoint variant —
              custom classes like mtx-text-* aren't Tailwind utilities Tailwind can prefix. */}
          <h1 className="mtx-text-display-large">{t("heroTitle")}</h1>
          <p className="mtx-text-body-large" style={{ color: "var(--color-text-secondary)", maxWidth: "60ch" }}>
            {t("heroSubtitle")}
          </p>
          <div className="flex flex-wrap gap-3">
            <Link href="/explore">
              <Button variant="primary">{t("exploreCourses")}</Button>
            </Link>
            <Link href="/register">
              <Button variant="secondary">{t("getStarted")}</Button>
            </Link>
          </div>
        </section>
      </main>
    </>
  );
}
