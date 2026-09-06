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
