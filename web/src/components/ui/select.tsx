"use client";

import { KeyboardEvent, useEffect, useId, useRef, useState } from "react";
import clsx from "clsx";
import { Icon } from "./icon";

export interface SelectOption<T extends string> {
  value: T;
  label: string;
  disabled?: boolean;
}

export interface SelectProps<T extends string> {
  label: string;
  options: readonly SelectOption<T>[];
  value?: T;
  onChange: (value: T) => void;
  placeholder?: string;
  error?: string;
  disabled?: boolean;
  id?: string;
  className?: string;
}

function enabledOptionIndexes<T extends string>(options: readonly SelectOption<T>[]): number[] {
  return options.flatMap((option, index) => (option.disabled ? [] : [index]));
}

function adjacentIndex(indexes: number[], current: number, direction: 1 | -1): number {
  const position = indexes.indexOf(current);
  if (position === -1) return indexes[0] ?? -1;
  return indexes[(position + direction + indexes.length) % indexes.length] ?? -1;
}

/** design-system/COMPONENTS.md Select / Dropdown (v1.3). */
export function Select<T extends string>({
  label,
  options,
  value,
  onChange,
  placeholder,
  error,
  disabled,
  id,
  className,
}: SelectProps<T>) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const menuId = `${fieldId}-menu`;
  const errorId = `${fieldId}-error`;
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const typeaheadRef = useRef({ query: "", at: 0 });
  const selectedIndex = options.findIndex((option) => option.value === value);
  const [open, setOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(selectedIndex);
  const selectedOption = options[selectedIndex];

  useEffect(() => {
    if (!open) return;
    function dismiss(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", dismiss);
    return () => document.removeEventListener("pointerdown", dismiss);
  }, [open]);

  function openMenu() {
    const enabledIndexes = enabledOptionIndexes(options);
    setHighlightedIndex(selectedIndex >= 0 && !options[selectedIndex]?.disabled ? selectedIndex : (enabledIndexes[0] ?? -1));
    setOpen(true);
  }

  function selectOption(option: SelectOption<T>) {
    if (option.disabled) return;
    onChange(option.value);
    setOpen(false);
    triggerRef.current?.focus();
  }

  function moveHighlight(direction: 1 | -1) {
    const indexes = enabledOptionIndexes(options);
    setHighlightedIndex((current) => adjacentIndex(indexes, current, direction));
  }

  function highlightByTypeahead(key: string) {
    const now = Date.now();
    const previous = typeaheadRef.current;
    const query = `${now - previous.at < 500 ? previous.query : ""}${key}`.toLocaleLowerCase();
    typeaheadRef.current = { query, at: now };
    const match = options.findIndex((option) => !option.disabled && option.label.toLocaleLowerCase().startsWith(query));
    if (match >= 0) setHighlightedIndex(match);
  }

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (!open) openMenu();
      else moveHighlight(event.key === "ArrowDown" ? 1 : -1);
    } else if ((event.key === "Enter" || event.key === " ") && open && highlightedIndex >= 0) {
      event.preventDefault();
      const highlightedOption = options[highlightedIndex];
      if (highlightedOption) selectOption(highlightedOption);
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      openMenu();
    } else if (event.key === "Escape" && open) {
      event.preventDefault();
      setOpen(false);
    } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      if (!open) openMenu();
      highlightByTypeahead(event.key);
    }
  }

  return (
    <div ref={containerRef} className={clsx("mtx-field mtx-select", className)}>
      <label id={`${fieldId}-label`} htmlFor={fieldId} className="mtx-field-label">
        {label}
      </label>
      <button
        ref={triggerRef}
        id={fieldId}
        type="button"
        className="mtx-select-trigger"
        role="combobox"
        aria-labelledby={`${fieldId}-label ${fieldId}`}
        aria-controls={menuId}
        aria-expanded={open}
        aria-haspopup="listbox"
        aria-activedescendant={open && highlightedIndex >= 0 ? `${fieldId}-option-${highlightedIndex}` : undefined}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={error ? errorId : undefined}
        data-open={open || undefined}
        data-placeholder={!selectedOption || undefined}
        disabled={disabled}
        title={selectedOption?.label ?? placeholder}
        onClick={() => (open ? setOpen(false) : openMenu())}
        onKeyDown={onKeyDown}
      >
        <span className="mtx-select-value">{selectedOption?.label ?? placeholder}</span>
        <Icon name={open ? "expandLess" : "expandMore"} size={20} className="mtx-select-indicator" />
      </button>
      {open && (
        <div id={menuId} className="mtx-select-menu" role="listbox" aria-labelledby={`${fieldId}-label`}>
          {options.map((option, index) => (
            <button
              key={option.value}
              id={`${fieldId}-option-${index}`}
              type="button"
              className="mtx-select-option"
              role="option"
              aria-selected={option.value === value}
              data-highlighted={index === highlightedIndex || undefined}
              disabled={option.disabled}
              title={option.label}
              tabIndex={-1}
              onMouseEnter={() => !option.disabled && setHighlightedIndex(index)}
              onClick={() => selectOption(option)}
            >
              {option.label}
            </button>
          ))}
        </div>
      )}
      {error && (
        <p id={errorId} className="mtx-field-error mtx-select-error" role="alert">
          <Icon name="cancel" size={16} />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
