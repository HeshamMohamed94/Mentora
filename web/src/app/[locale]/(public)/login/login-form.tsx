"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button, PasswordField, TextField } from "@/components/ui";
import { login } from "@/lib/auth/actions";
import { CURRENT_USER_QUERY_KEY } from "@/lib/auth/use-current-user";
import { loginSchema, type LoginInput } from "@/lib/auth/schemas";
import { ApiError } from "@/lib/api/client";
import { Link, useRouter } from "@/i18n/navigation";

/** Enroll-gate always returns to intent (ux/NAVIGATION_SPEC.md § 6) — `redirectTo` comes from
 * `?redirect=` set by middleware.ts (or a same-session "please log in" prompt), and falls
 * back to the role-appropriate landing (Dashboard) only when there was no specific intent. */
export function LoginForm({ redirectTo }: { redirectTo: string }) {
  const t = useTranslations("auth");
  const router = useRouter();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) });

  async function onSubmit(values: LoginInput) {
    setFormError(null);
    try {
      const user = await login(values);
      await queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY });
      router.push(redirectTo || roleLandingPath(user.role));
    } catch (err) {
      if (err instanceof ApiError && err.code === "AUTH_INVALID_CREDENTIALS") {
        setFormError(t("invalidCredentials"));
      } else {
        setFormError(t("genericError"));
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="mtx-auth-form">
      <TextField
        label={t("emailLabel")}
        type="email"
        autoComplete="email"
        error={errors.email ? t("emailInvalid") : undefined}
        {...register("email")}
      />
      <PasswordField
        label={t("passwordLabel")}
        autoComplete="current-password"
        error={errors.password ? t("passwordRequired") : undefined}
        showPasswordLabel={t("showPassword")}
        hidePasswordLabel={t("hidePassword")}
        {...register("password")}
      />
      {formError && (
        <p role="alert" className="mtx-text-body-small mtx-auth-error">
          {formError}
        </p>
      )}
      <Button type="submit" variant="primary" loading={isSubmitting}>
        {t("loginAction")}
      </Button>
      <p className="mtx-text-body-small mtx-auth-footer">
        {t("noAccount")} <Link href="/register" className="mtx-link">{t("registerAction")}</Link>
      </p>
    </form>
  );
}

function roleLandingPath(role: "student" | "instructor" | "admin"): string {
  if (role === "instructor") return "/instructor";
  if (role === "admin") return "/admin";
  return "/app";
}
