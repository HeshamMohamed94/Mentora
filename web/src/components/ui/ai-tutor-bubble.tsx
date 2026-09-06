import clsx from "clsx";
import { Button } from "./button";

export interface AiTutorBubbleProps {
  variant: "ai" | "user";
  content: string;
  label: string;
  streaming?: boolean;
  error?: boolean;
  thinkingLabel?: string;
  retryLabel?: string;
  retryDisabled?: boolean;
  onRetry?: () => void;
}

export function AiTutorBubble({
  variant,
  content,
  label,
  streaming = false,
  error = false,
  thinkingLabel,
  retryLabel,
  retryDisabled = false,
  onRetry,
}: AiTutorBubbleProps) {
  return (
    <article
      className={clsx("mtx-ai-tutor-message", `mtx-ai-tutor-message-${variant}`)}
      aria-label={label}
      aria-busy={streaming || undefined}
    >
      <div
        className={clsx(
          "mtx-ai-tutor-bubble",
          `mtx-ai-tutor-bubble-${variant}`,
          error && "mtx-ai-tutor-bubble-error"
        )}
      >
        {streaming && !content ? (
          <span className="mtx-ai-tutor-thinking" role="status">
            <span className="sr-only">{thinkingLabel}</span>
            <span aria-hidden="true" />
            <span aria-hidden="true" />
            <span aria-hidden="true" />
          </span>
        ) : (
          <p>
            {content}
            {streaming && <span className="mtx-ai-tutor-cursor" aria-hidden="true" />}
          </p>
        )}
        {error && retryLabel && onRetry && (
          <Button variant="text" className="mtx-ai-tutor-retry" disabled={retryDisabled} onClick={onRetry}>
            {retryLabel}
          </Button>
        )}
      </div>
    </article>
  );
}
