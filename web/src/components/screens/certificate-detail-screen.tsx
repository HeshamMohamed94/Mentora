"use client";

import { useEffect, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Button, EmptyState, ErrorState, Icon } from "@/components/ui";
import { Link, useRouter } from "@/i18n/navigation";
import { useCertificate } from "@/lib/api/certificates";
import { formatDate } from "@/lib/i18n/format";

export function CertificateDetailScreen({ certificateId }: { certificateId: string }) {
  const t = useTranslations("certificates");
  const locale = useLocale();
  const router = useRouter();
  const certificateQuery = useCertificate(certificateId);
  const [shareAcknowledged, setShareAcknowledged] = useState(false);

  useEffect(() => {
    if (!shareAcknowledged) return;
    const timeoutId = window.setTimeout(() => setShareAcknowledged(false), 2000);
    return () => window.clearTimeout(timeoutId);
  }, [shareAcknowledged]);

  if (certificateQuery.isLoading) {
    return <div className="mtx-certificate-detail-page px-4 py-8"><div className="mtx-skeleton mtx-certificate-detail-skeleton" /></div>;
  }

  if (certificateQuery.isError) {
    return (
      <div className="px-4 py-8">
        <ErrorState description={t("detailLoadError")} retryLabel={t("retry")} onRetry={() => certificateQuery.refetch()} />
      </div>
    );
  }

  if (!certificateQuery.data) {
    return (
      <div className="px-4 py-8">
        <EmptyState
          title={t("notFoundTitle")}
          description={t("notFoundDescription")}
          actionLabel={t("backToCertificates")}
          onAction={() => router.push("/app/certificates")}
        />
      </div>
    );
  }

  const certificate = certificateQuery.data;

  return (
    <div className="mtx-certificate-detail-page px-4 py-8 tablet:px-6 desktop:px-8">
      <Link href="/app/certificates" className="mtx-btn mtx-btn-text mtx-certificate-back-link">
        {t("backToCertificates")}
      </Link>

      <article className="mtx-certificate-document">
        <div className="mtx-certificate-document-border" aria-hidden="true" />
        <Icon name="certificates" className="mtx-certificate-document-icon" />
        <p className="mtx-text-label-large mtx-certificate-kicker">{t("certificateOfCompletion")}</p>
        <p className="mtx-text-body-small mtx-certificate-meta">{t("awardedTo")}</p>
        <h1 className="mtx-text-heading-h2 mtx-certificate-student-name">{certificate.studentNameSnapshot}</h1>
        <p className="mtx-text-body-medium mtx-certificate-meta">{t("completionStatement")}</p>
        <h2 className="mtx-text-heading-h3 mtx-certificate-course-name">{certificate.courseTitleSnapshot}</h2>
        <div className="mtx-certificate-detail-meta">
          <p className="mtx-text-caption mtx-certificate-meta">
            {t("instructor", { name: certificate.instructorNameSnapshot })}
          </p>
          <p className="mtx-text-caption mtx-certificate-meta">
            {t("completedOn", { date: formatDate(certificate.completionDateSnapshot, locale) })}
          </p>
          <p className="mtx-text-caption mtx-certificate-meta">
            {t("issuedOn", { date: formatDate(certificate.issuedAt, locale) })}
          </p>
        </div>
        <p className="mtx-text-caption mtx-certificate-id">{t("certificateId", { id: certificate.id })}</p>
      </article>

      <div className="mtx-certificate-actions">
        <Button variant="primary" aria-pressed={shareAcknowledged} onClick={() => setShareAcknowledged(true)}>
          {shareAcknowledged ? t("shareAcknowledged") : t("share")}
        </Button>
      </div>
    </div>
  );
}
