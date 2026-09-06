"use client";

import { forwardRef, useId, useState } from "react";
import clsx from "clsx";
import { Icon } from "./icon";

export interface PasswordFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "id" | "type"> {
  label: string;
  error?: string;
  id?: string;
  showPasswordLabel: string;
  hidePasswordLabel: string;
}

/**
 * design-system/COMPONENTS.md § PasswordField — extends TextField with a trailing visibility
 * toggle IconButton ("visibility" / "visibility_off"). "Must always ship the toggle — never a
 * password field with no reveal option."
 */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(function PasswordField(
  { label, error, id, className, placeholder, showPasswordLabel, hidePasswordLabel, ...props },
  ref
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const errorId = `${fieldId}-error`;
  const [visible, setVisible] = useState(false);

  return (
    <div className="mtx-field mtx-text-field mtx-field-with-toggle">
      <label htmlFor={fieldId} className="mtx-field-label">
        {label}
      </label>
      <input
        ref={ref}
        id={fieldId}
        type={visible ? "text" : "password"}
        className={clsx("mtx-input", className)}
        placeholder={placeholder ?? " "}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? errorId : undefined}
        {...props}
      />
      <button
        type="button"
        className="mtx-field-toggle"
        aria-label={visible ? hidePasswordLabel : showPasswordLabel}
        aria-pressed={visible}
        onClick={() => setVisible((current) => !current)}
      >
        <Icon name={visible ? "visibilityOff" : "visibility"} size={20} />
      </button>
      {error && (
        <p id={errorId} className="mtx-field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
});
