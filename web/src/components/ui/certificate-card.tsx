import { Link } from "@/i18n/navigation";
import type { CertificateSummaryResponse } from "@/lib/api/certificates";
import { Button } from "./button";
import { Icon } from "./icon";

export function CertificateCard({
  certificate,
  instructorLabel,
  issuedLabel,
  viewLabel,
}: {
  certificate: CertificateSummaryResponse;
  instructorLabel: string;
  issuedLabel: string;
  viewLabel: string;
}) {
  return (
    <article className="mtx-certificate-card">
      <div className="mtx-certificate-preview" aria-hidden="true">
        <Icon name="certificates" className="mtx-certificate-preview-icon" />
        <span className="mtx-text-label-large mtx-certificate-preview-title">
          {certificate.courseTitleSnapshot}
        </span>
      </div>
      <div className="mtx-certificate-card-content">
        <h2 className="mtx-text-heading-h4 mtx-certificate-card-title">
          {certificate.courseTitleSnapshot}
        </h2>
        <p className="mtx-text-caption mtx-certificate-meta">{instructorLabel}</p>
        <p className="mtx-text-caption mtx-certificate-meta">{issuedLabel}</p>
        <Link href={`/app/certificates/${certificate.id}`} className="mtx-certificate-card-action">
          <Button variant="tonal" className="w-full">{viewLabel}</Button>
        </Link>
      </div>
    </article>
  );
}

export function CertificateGridSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="mtx-certificate-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="mtx-certificate-card">
          <div className="mtx-skeleton mtx-certificate-preview" />
          <div className="mtx-certificate-card-content">
            <div className="mtx-skeleton mtx-certificate-skeleton-title" />
            <div className="mtx-skeleton mtx-certificate-skeleton-meta" />
            <div className="mtx-skeleton mtx-certificate-skeleton-action" />
          </div>
        </div>
      ))}
    </div>
  );
}
