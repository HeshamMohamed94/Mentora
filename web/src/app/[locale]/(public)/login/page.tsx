import { getTranslations } from "next-intl/server";
import { AuthHeader } from "@/components/navigation/auth-header";
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
      <AuthHeader />
      <main id="main-content" className="mtx-auth-page">
        <div className="mtx-auth-card">
          <h1 className="mtx-text-heading-h3 mtx-auth-title">{t("loginTitle")}</h1>
          <LoginForm redirectTo={redirect ?? ""} />
        </div>
      </main>
    </>
  );
}
