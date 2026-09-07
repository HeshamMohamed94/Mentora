export interface TabOption<T extends string> {
  id: T;
  label: string;
  disabled?: boolean;
}

export function Tabs<T extends string>({ options, active, onChange, label }: {
  options: readonly TabOption<T>[];
  active: T;
  onChange: (id: T) => void;
  label: string;
}) {
  return (
    <div className="mtx-tabs" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          role="tab"
          className="mtx-tab"
          aria-selected={active === option.id}
          disabled={option.disabled}
          onClick={() => onChange(option.id)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
