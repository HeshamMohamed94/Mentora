"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "@/i18n/navigation";
import { SuccessState } from "@/components/ui";

/**
 * product/SCREEN_INVENTORY.md § 19 (Purchase Success). Per ux/NAVIGATION_SPEC.md § "Post-success
 * screens don't allow back into the action that produced them" — this screen is a route dead-end,
 * not deep-linkable back into a completed checkout; both actions push forward/sideways only.
 */
export function PurchaseSuccessScreen({ courseId }: { courseId: string }) {
  const t = useTranslations("checkout");
  const router = useRouter();

  return (
    <div className="mtx-success-page">
      <SuccessState
        title={t("successTitle")}
        description={t("successDescription")}
        actionLabel={t("startLearning")}
        onAction={() => router.push(`/app/learn/${courseId}`)}
        secondaryLabel={t("backToMyLearning")}
        onSecondary={() => router.push("/app")}
      />
    </div>
  );
}
