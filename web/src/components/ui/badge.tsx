import clsx from "clsx";

export type BadgeVariant = "neutral" | "success" | "warning" | "error" | "info" | "brand";

export function Badge({ variant = "neutral", children }: { variant?: BadgeVariant; children: React.ReactNode }) {
  return <span className={clsx("mtx-badge", `mtx-badge-${variant}`)}>{children}</span>;
}
