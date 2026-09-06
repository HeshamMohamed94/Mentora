import clsx from "clsx";

export function CategoryChip({
  label,
  selected = false,
  onClick,
}: {
  label: string;
  selected?: boolean;
  onClick?: () => void;
}) {
  const Element = onClick ? "button" : "span";
  return (
    <Element
      type={onClick ? "button" : undefined}
      className={clsx("mtx-chip")}
      data-selected={selected || undefined}
      aria-pressed={onClick ? selected : undefined}
      onClick={onClick}
    >
      {label}
    </Element>
  );
}
