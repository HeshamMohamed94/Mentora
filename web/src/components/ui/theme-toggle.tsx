"use client";

import { useTranslations } from "next-intl";
import { useTheme } from "@/lib/theme/use-theme";
import { Icon } from "./icon";

/**
 * Design-system `IconButton` (design-system/COMPONENTS.md § IconButton) driving the shared
 * `useTheme()` preference — the icon always names the action available (moon = switch to dark,
 * sun = switch to light), matching the state the button is currently showing rather than the
 * state you'd land in.
 */
export function ThemeToggle() {
  const t = useTranslations("common");
  const { resolvedTheme, setPreference } = useTheme();
  const isDark = resolvedTheme === "dark";

  return (
    <button
      type="button"
      className="mtx-icon-button"
      aria-label={isDark ? t("switchToLightTheme") : t("switchToDarkTheme")}
      onClick={() => setPreference(isDark ? "light" : "dark")}
    >
      <Icon name={isDark ? "lightMode" : "darkMode"} size={24} />
    </button>
  );
}
