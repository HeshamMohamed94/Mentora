"use client";

import { FormEvent, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Avatar, Button, ErrorState, StatCard, TextField } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useCertificates } from "@/lib/api/certificates";
import { ApiError } from "@/lib/api/client";
import { useMyLearning } from "@/lib/api/my-learning";
import { useUpdateCurrentUser } from "@/lib/api/users";
import { logout } from "@/lib/auth/actions";
import { CURRENT_USER_QUERY_KEY, useCurrentUser } from "@/lib/auth/use-current-user";
import { formatCount } from "@/lib/i18n/format";

const MAX_NAME_LENGTH = 120;

export function ProfileScreen({ settingsHref = "/app/settings", showLearningStats = true }: { settingsHref?: string; showLearningStats?: boolean }) {
  const t = useTranslations("profile");
  const locale = useLocale();
  const router = useRouter();
  const queryClient = useQueryClient();
  const userQuery = useCurrentUser();
  const myLearning = useMyLearning(locale);
  const certificatesQuery = useCertificates(showLearningStats);
  const updateUser = useUpdateCurrentUser();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState<string>();

  function startEditing() {
    if (!userQuery.data) return;
    setName(userQuery.data.name);
    setNameError(undefined);
    updateUser.reset();
    setEditing(true);
  }

  function validationMessage(candidate: string): string | undefined {
    if (!candidate.trim()) return t("nameRequired");
    if (candidate.length > MAX_NAME_LENGTH) return t("nameTooLong");
    return undefined;
  }

  function saveName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedName = name.trim();
    const validationError = validationMessage(name);
    if (validationError) {
      setNameError(validationError);
      return;
    }

    setNameError(undefined);
    updateUser.mutate({ name: normalizedName }, {
      onSuccess: () => setEditing(false),
      onError: (error) => {
        if (error instanceof ApiError && error.fields?.name === "REQUIRED") setNameError(t("nameRequired"));
        else if (error instanceof ApiError && error.fields?.name === "TOO_LONG") setNameError(t("nameTooLong"));
      },
    });
  }

  async function signOut() {
    await logout();
    queryClient.setQueryData(CURRENT_USER_QUERY_KEY, undefined);
    router.push("/login");
  }

  function retryProfile() {
    void userQuery.refetch();
    if (showLearningStats) {
      void myLearning.refetch();
      void certificatesQuery.refetch();
    }
  }

  if (userQuery.isError || (showLearningStats && (myLearning.isError || certificatesQuery.isError))) {
    return <div className="mtx-account-page"><ErrorState description={t("loadError")} retryLabel={t("retry")} onRetry={retryProfile} /></div>;
  }

  if (!userQuery.data || (showLearningStats && (myLearning.isLoading || certificatesQuery.isLoading))) {
    return (
      <div className="mtx-account-page" role="status" aria-label={t("loading")}>
        <div className="mtx-skeleton mtx-account-skeleton" aria-hidden="true" />
      </div>
    );
  }

  const inProgress = myLearning.items.filter((learningItem) => learningItem.progress.completionPercent < 100);
  const completedCount = myLearning.items.length - inProgress.length;

  return (
    <div className="mtx-account-page">
      <h1 className="mtx-text-heading-h1">{t("title")}</h1>
      <section className="mtx-account-card" aria-labelledby="profile-name">
        <div className="mtx-profile-identity">
          <Avatar name={userQuery.data.name} size="xlarge" />
          <div className="mtx-profile-copy">
            <h2 id="profile-name" className="mtx-text-heading-h2">{userQuery.data.name}</h2>
            <p className="mtx-text-body-medium mtx-account-secondary">{userQuery.data.email}</p>
          </div>
        </div>

        {showLearningStats && (
          <div className="mtx-profile-stats">
            <StatCard value={formatCount(completedCount, locale)} label={t("coursesCompleted")} />
            <StatCard value={formatCount(certificatesQuery.data?.length ?? 0, locale)} label={t("certificates")} />
          </div>
        )}

        {editing ? (
          <form className="mtx-profile-edit" onSubmit={saveName}>
            <TextField
              label={t("nameLabel")}
              value={name}
              maxLength={MAX_NAME_LENGTH + 1}
              error={nameError}
              disabled={updateUser.isPending}
              autoFocus
              onChange={(event) => {
                setName(event.target.value);
                if (nameError) setNameError(undefined);
              }}
            />
            {updateUser.isError && !nameError && <p className="mtx-field-error" role="alert">{t("saveError")}</p>}
            <div className="mtx-account-actions">
              <Button type="submit" variant="primary" loading={updateUser.isPending}>{t("save")}</Button>
              <Button type="button" variant="text" disabled={updateUser.isPending} onClick={() => setEditing(false)}>{t("cancel")}</Button>
            </div>
          </form>
        ) : (
          <div className="mtx-account-actions">
            <Button type="button" variant="primary" onClick={startEditing}>{t("editProfile")}</Button>
            <Link href={settingsHref} className="mtx-btn mtx-btn-secondary">{t("settings")}</Link>
          </div>
        )}

        <Button type="button" variant="text" onClick={() => void signOut()}>{t("logout")}</Button>
      </section>
    </div>
  );
}
