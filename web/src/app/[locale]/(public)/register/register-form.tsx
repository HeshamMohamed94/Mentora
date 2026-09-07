"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button, PasswordField, TextField } from "@/components/ui";
import { register as registerAccount } from "@/lib/auth/actions";
import { CURRENT_USER_QUERY_KEY } from "@/lib/auth/use-current-user";
import { registerSchema, type RegisterInput } from "@/lib/auth/schemas";
import { ApiError } from "@/lib/api/client";
import { Link, useRouter } from "@/i18n/navigation";

/** Registration auto-logs in (product/USER_FLOWS.md § 1); arriving via the enroll-gate lands
 * back in the original flow (e.g. Demo Checkout) via `redirectTo`, otherwise the Dashboard. */
export function RegisterForm({ redirectTo }: { redirectTo: string }) {
  const t = useTranslations("auth");
  const router = useRouter();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register: registerField,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterInput>({ resolver: zodResolver(registerSchema) });

  async function onSubmit(values: RegisterInput) {
    setFormError(null);
    try {
      await registerAccount(values);
      await queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY });
      router.push(redirectTo || "/app");
    } catch (err) {
      if (err instanceof ApiError && err.code === "EMAIL_ALREADY_REGISTERED") {
        // ux/SCREEN_UX_SPECS.md § 7: this error is actionable/specific, shown inline under
        // the email field — unlike Login's deliberately-vague, non-field-specific error.
        setError("email", { type: "server", message: t("emailAlreadyRegistered") });
      } else {
        setFormError(t("genericError"));
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="mtx-auth-form">
      <TextField
        label={t("nameLabel")}
        autoComplete="name"
        error={errors.name ? t("nameRequired") : undefined}
        {...registerField("name")}
      />
      <TextField
        label={t("emailLabel")}
        type="email"
        autoComplete="email"
        error={
          errors.email?.type === "server" ? errors.email.message : errors.email ? t("emailInvalid") : undefined
        }
        {...registerField("email")}
      />
      <PasswordField
        label={t("passwordLabel")}
        autoComplete="new-password"
        error={errors.password ? t("passwordTooShort") : undefined}
        helperText={t("passwordHint")}
        showPasswordLabel={t("showPassword")}
        hidePasswordLabel={t("hidePassword")}
        {...registerField("password")}
      />
      {formError && (
        <p role="alert" className="mtx-text-body-small mtx-auth-error">
          {formError}
        </p>
      )}
      <Button type="submit" variant="primary" loading={isSubmitting}>
        {t("registerAction")}
      </Button>
      <p className="mtx-text-body-small mtx-auth-footer">
        {t("haveAccount")} <Link href="/login" className="mtx-link">{t("loginAction")}</Link>
      </p>
    </form>
  );
}
