import { forwardRef, useId } from "react";
import clsx from "clsx";

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "id"> {
  label: string;
  error?: string;
  id?: string;
}

/**
 * design-system/COMPONENTS.md § Inputs. Label is programmatically associated (htmlFor/id),
 * error text is linked via aria-describedby (ACCESSIBILITY.md § 7).
 *
 * Known gap (tracked, not blocking): the error state currently pairs border + text only, not
 * the third "icon" signal ACCESSIBILITY.md § 7 calls for — the Material Symbols icon system
 * (DESIGN_SYSTEM.md § 8) isn't wired up yet. Add the icon once that lands.
 */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, id, className, ...props },
  ref
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const errorId = `${fieldId}-error`;

  return (
    <div className="mtx-field">
      <label htmlFor={fieldId} className="mtx-field-label">
        {label}
      </label>
      <input
        ref={ref}
        id={fieldId}
        className={clsx("mtx-input", className)}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? errorId : undefined}
        {...props}
      />
      {error && (
        <p id={errorId} className="mtx-field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
});
