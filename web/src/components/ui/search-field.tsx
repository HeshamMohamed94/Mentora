import { useId } from "react";

export function SearchField({
  value,
  onChange,
  label,
  placeholder,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder?: string;
}) {
  const id = useId();
  return (
    <div className="mtx-search-field">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <span className="mtx-search-field-icon" aria-hidden="true">
        ⌕
      </span>
      <input
        id={id}
        type="search"
        className="mtx-input"
        value={value}
        placeholder={placeholder ?? label}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
