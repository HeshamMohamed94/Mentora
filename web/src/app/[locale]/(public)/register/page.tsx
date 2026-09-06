import { getTranslations } from "next-intl/server";
import { AuthHeader } from "@/components/navigation/auth-header";
import { RegisterForm } from "./register-form";

export default async function RegisterPage({
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
          <h1 className="mtx-text-heading-h3 mtx-auth-title">{t("registerTitle")}</h1>
          <RegisterForm redirectTo={redirect ?? ""} />
        </div>
      </main>
    </>
  );
}
