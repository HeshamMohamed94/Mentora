import clsx from "clsx";
import { Icon } from "./icon";
import { ProgressBar } from "./progress-bar";

export type AnswerOptionState = "default" | "selected" | "correct" | "incorrect" | "disabled";

export function AnswerOption({
  text,
  state,
  stateLabel,
  onSelect,
}: {
  text: string;
  state: AnswerOptionState;
  stateLabel?: string;
  onSelect?: () => void;
}) {
  const icon = state === "correct" ? "checkCircle" : state === "incorrect" ? "cancel" : state === "selected" ? "checkCircle" : undefined;
  return (
    <button
      type="button"
      className={clsx("mtx-answer-option", `mtx-answer-option-${state}`)}
      disabled={!onSelect}
      aria-pressed={state === "selected"}
      onClick={onSelect}
    >
      {icon && <Icon name={icon} />}
      <span className="mtx-answer-option-text">{text}</span>
      {stateLabel && <span className="mtx-text-label-medium">{stateLabel}</span>}
    </button>
  );
}

export function QuestionCard({
  progressText,
  progressPercent,
  progressLabel,
  prompt,
  children,
}: {
  progressText: string;
  progressPercent: number;
  progressLabel: string;
  prompt: string;
  children: React.ReactNode;
}) {
  return (
    <section className="mtx-question-card">
      <div className="flex flex-col gap-2">
        <p className="mtx-text-caption" style={{ color: "var(--color-text-secondary)" }}>{progressText}</p>
        <ProgressBar percent={progressPercent} label={progressLabel} />
      </div>
      <h2 className="mtx-text-heading-h4">{prompt}</h2>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  );
}
