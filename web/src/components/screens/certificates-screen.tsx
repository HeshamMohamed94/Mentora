"use client";

import { useLocale, useTranslations } from "next-intl";
import { CertificateCard, CertificateGridSkeleton, EmptyState, ErrorState } from "@/components/ui";
import { useRouter } from "@/i18n/navigation";
import { useCertificates } from "@/lib/api/certificates";
import { formatDate } from "@/lib/i18n/format";

export function CertificatesScreen() {
  const t = useTranslations("certificates");
  const locale = useLocale();
  const router = useRouter();
  const certificatesQuery = useCertificates();

  return (
    <div className="mtx-certificates-page px-4 py-8 tablet:px-6 desktop:px-8">
      <h1 className="mtx-text-heading-h1 mb-6">{t("title")}</h1>

      {certificatesQuery.isLoading && <CertificateGridSkeleton />}

      {certificatesQuery.isError && (
        <ErrorState description={t("loadError")} retryLabel={t("retry")} onRetry={() => certificatesQuery.refetch()} />
      )}

      {!certificatesQuery.isLoading && !certificatesQuery.isError && certificatesQuery.data?.length === 0 && (
        <EmptyState
          title={t("emptyTitle")}
          description={t("emptyDescription")}
          actionLabel={t("exploreCourses")}
          onAction={() => router.push("/app/explore")}
        />
      )}

      {certificatesQuery.data && certificatesQuery.data.length > 0 && (
        <div className="mtx-certificate-grid">
          {certificatesQuery.data.map((certificate) => (
            <CertificateCard
              key={certificate.id}
              certificate={certificate}
              instructorLabel={t("instructor", { name: certificate.instructorNameSnapshot })}
              issuedLabel={t("issuedOn", { date: formatDate(certificate.issuedAt, locale) })}
              viewLabel={t("view")}
            />
          ))}
        </div>
      )}
    </div>
  );
}
