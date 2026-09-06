export function ProgressBar({ percent, label }: { percent: number; label: string }) {
  const clamped = Math.max(0, Math.min(100, percent));
  return (
    <div
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
      className="mtx-progress-track"
    >
      <div className="mtx-progress-fill" data-complete={clamped >= 100 || undefined} style={{ width: `${clamped}%` }} />
    </div>
  );
}
