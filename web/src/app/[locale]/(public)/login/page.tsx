import { getTranslations } from "next-intl/server";
import { PublicNavbar } from "@/components/navigation/public-navbar";
import { LoginForm } from "./login-form";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ redirect?: string }>;
}) {
  const t = await getTranslations("auth");
  const { redirect } = await searchParams;

  return (
    <>
      {/* Not sticky — a short, single-purpose form has nothing to scroll back up for
          (ux/WEB_UX.md § 1). */}
      <PublicNavbar />
      <main id="main-content" className="flex justify-center px-4 py-16">
        <div className="w-full" style={{ maxWidth: "480px" }}>
          <h1 className="mtx-text-heading-h3 mb-6 text-center">{t("loginTitle")}</h1>
          <LoginForm redirectTo={redirect ?? ""} />
        </div>
      </main>
    </>
  );
}
