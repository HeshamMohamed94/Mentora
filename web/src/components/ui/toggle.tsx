import { useId } from "react";

export interface ToggleProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> {
  label: string;
}

export function Toggle({ label, id, ...props }: ToggleProps) {
  const generatedId = useId();
  const toggleId = id ?? generatedId;
  return (
    <label className="mtx-toggle" htmlFor={toggleId}>
      <input id={toggleId} type="checkbox" role="switch" {...props} />
      <span className="mtx-toggle-track" aria-hidden="true"><span className="mtx-toggle-thumb" /></span>
      <span className="mtx-toggle-label">{label}</span>
    </label>
  );
}
