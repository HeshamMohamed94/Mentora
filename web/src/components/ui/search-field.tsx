import { useId } from "react";
import { Icon } from "./icon";

export function SearchField({
  value,
  onChange,
  label,
  placeholder,
  clearLabel,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder?: string;
  clearLabel: string;
}) {
  const id = useId();
  return (
    <div className="mtx-search-field">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <Icon name="search" size={20} className="mtx-search-field-icon" />
      <input
        id={id}
        type="search"
        className="mtx-input"
        value={value}
        placeholder={placeholder ?? label}
        onChange={(event) => onChange(event.target.value)}
      />
      {value && (
        <button
          type="button"
          className="mtx-search-field-clear"
          aria-label={clearLabel}
          onClick={() => onChange("")}
        >
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  );
}
