/** design-system/COMPONENTS.md § StatCard. */
export function StatCard({ value, label }: { value: string; label: string }) {
  return (
    <div className="mtx-stat-card">
      <p className="mtx-text-heading-h2">{value}</p>
      <p className="mtx-text-body-small" style={{ color: "var(--color-text-secondary)" }}>
        {label}
      </p>
    </div>
  );
}
