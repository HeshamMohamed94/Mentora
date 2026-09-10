"use client";

import { useTranslations, useLocale } from "next-intl";
import { useCheckoutPreview, useCompleteCheckout } from "@/lib/api/enrollment";
import { useRouter, Link } from "@/i18n/navigation";
import { Button, ErrorState, CourseThumbnail } from "@/components/ui";
import { formatPrice } from "@/lib/i18n/format";

/**
 * product/SCREEN_INVENTORY.md § 18 (Demo Checkout). Reached only through the authenticated
 * `/app/checkout/:courseId` route (backend requires the `student` role — see
 * `EnrollmentRoutes.kt`), so there is no Guest-facing counterpart to build.
 */
export function CheckoutScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("checkout");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const router = useRouter();
  const previewQuery = useCheckoutPreview(courseId, locale);
  const completeMutation = useCompleteCheckout(courseId);

  function handleConfirm() {
    completeMutation.mutate(undefined, {
      onSuccess: () => router.push(`/app/checkout/${courseId}/success`),
    });
  }

  if (previewQuery.isLoading) {
    return (
      <div className="mtx-checkout-page">
        <div className="mtx-skeleton" style={{ height: 280, borderRadius: "var(--radius-large)" }} />
      </div>
    );
  }

  if (previewQuery.isError || !previewQuery.data) {
    return (
      <div className="mtx-checkout-page">
        <ErrorState description={t("errorTitle")} retryLabel={tCommon("retry")} onRetry={() => previewQuery.refetch()} />
      </div>
    );
  }

  const preview = previewQuery.data;
  const price = formatPrice(preview.priceDisplay.amount, preview.priceDisplay.currency, locale);

  return (
    <div className="mtx-checkout-page">
      <h1 className="mtx-text-heading-h3 mtx-checkout-title">{t("title")}</h1>
      <div className="mtx-checkout-card">
        <div className="mtx-checkout-line-item">
          <CourseThumbnail
            mediaId={preview.course.thumbnailMediaId}
            className="mtx-checkout-thumbnail"
            iconSize={20}
            seed={preview.course.id}
          />
          <div>
            <p className="mtx-text-label-large mtx-card-title">{preview.course.title}</p>
            <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
              {preview.instructorName}
            </p>
          </div>
        </div>

        <p className="mtx-checkout-eyebrow mtx-text-label-large">{t("orderSummary")}</p>

        <div className="mtx-checkout-price-row">
          <span className="mtx-text-body-medium mtx-checkout-price-label">{t("courseRow")}</span>
          <span className="mtx-text-body-medium mtx-checkout-price-label">{price}</span>
        </div>

        <div className="mtx-checkout-summary-row">
          <span className="mtx-text-body-medium mtx-checkout-price-label">{t("total")}</span>
          <span className="mtx-text-heading-h3 mtx-checkout-price-total">{price}</span>
        </div>

        <p className="mtx-checkout-notice mtx-text-body-small">{t("demoNotice")}</p>

        {completeMutation.isError && (
          <p className="mtx-text-caption" style={{ color: "var(--color-error-default)" }}>
            {t("errorMessage")}
          </p>
        )}

        <div className="mtx-checkout-actions">
          <Button variant="primary" loading={completeMutation.isPending} onClick={handleConfirm} className="w-full">
            {t("confirmAction")}
          </Button>
          <Link href={`/app/courses/${courseId}`} className="mtx-btn mtx-btn-text w-full text-center">
            {t("cancelAction")}
          </Link>
        </div>
      </div>
    </div>
  );
}
