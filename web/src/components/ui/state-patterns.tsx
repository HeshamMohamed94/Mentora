import { Button } from "./button";

/** design-system/COMPONENTS.md § State Patterns. */
export function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
}: {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div className="mtx-state-container">
      <h2 className="mtx-text-heading-h4">{title}</h2>
      <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
        {description}
      </p>
      {actionLabel && onAction && (
        <Button variant="primary" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
}

export function ErrorState({ description, onRetry, retryLabel }: { description: string; onRetry: () => void; retryLabel: string }) {
  return (
    <div className="mtx-state-container">
      <h2 className="mtx-text-heading-h4" style={{ color: "var(--color-error-default)" }}>
        {description}
      </h2>
      <Button variant="tonal" onClick={onRetry}>
        {retryLabel}
      </Button>
    </div>
  );
}

/** design-system/COMPONENTS.md § SuccessState — celebratory sibling of EmptyState/ErrorState,
 * used by Purchase Success (product/DEMO_PAYMENT_FLOW.md § 3). */
export function SuccessState({
  title,
  description,
  actionLabel,
  onAction,
  secondaryLabel,
  onSecondary,
}: {
  title: string;
  description: string;
  actionLabel: string;
  onAction: () => void;
  secondaryLabel?: string;
  onSecondary?: () => void;
}) {
  return (
    <div className="mtx-success-container">
      {/* COMPONENTS.md § SuccessState: "icon.large (32) or a custom illustration" — sized to
       * avatar.xlarge (96, the design system's own largest circular-illustration precedent,
       * avatar.tsx's AVATAR_DIMENSIONS.xlarge) so the celebratory moment reads as a genuine
       * full-page confirmation rather than an inline message icon. */}
      <svg
        className="mtx-success-icon"
        width="96"
        height="96"
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" fill="currentColor" opacity="0.15" />
        <path d="M7 12.5l3 3 7-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <h2 className="mtx-text-heading-h3 mtx-success-title">{title}</h2>
      <p className="mtx-text-body-small mtx-success-description">{description}</p>
      <div className="mtx-success-actions">
        <Button variant="primary" onClick={onAction} className="w-full">
          {actionLabel}
        </Button>
        {secondaryLabel && onSecondary && (
          <Button variant="text" onClick={onSecondary} className="w-full">
            {secondaryLabel}
          </Button>
        )}
      </div>
    </div>
  );
}

/** Skeleton grid matching CourseCard's footprint — never a bare spinner over a content area
 * (ux/UX_STATES.md § 1). */
export function CourseGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="mtx-course-grid" aria-hidden="true">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="mtx-card">
          <div className="mtx-skeleton mtx-card-thumbnail" />
          <div className="mtx-card-body">
            <div className="mtx-skeleton" style={{ height: 20, width: "70%" }} />
            <div className="mtx-skeleton" style={{ height: 14, width: "40%" }} />
            <div className="mtx-skeleton" style={{ height: 14, width: "50%" }} />
          </div>
        </div>
      ))}
    </div>
  );
}
