import { getTranslations } from "next-intl/server";
import { PublicNavbar } from "@/components/navigation/public-navbar";
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
      <PublicNavbar />
      <main id="main-content" className="flex justify-center px-4 py-16">
        <div className="w-full" style={{ maxWidth: "480px" }}>
          <h1 className="mtx-text-heading-h3 mb-6 text-center">{t("registerTitle")}</h1>
          <RegisterForm redirectTo={redirect ?? ""} />
        </div>
      </main>
    </>
  );
}
