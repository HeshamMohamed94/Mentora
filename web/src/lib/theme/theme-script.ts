/**
 * Runs before paint (inlined into <head>) so there's no flash of the wrong theme.
 * Reads an explicit user override from localStorage; falls back to system preference.
 * The "system" value means "no explicit override" — the CSS `@media (prefers-color-scheme)`
 * rule in styles/tokens.css handles that case on its own, so this script only ever sets
 * data-theme when the user has explicitly chosen light or dark (WEB_ARCHITECTURE.md § 4).
 */
export const THEME_STORAGE_KEY = "mentora-theme";

export function themeInitScript(): string {
  return `(function(){try{var t=localStorage.getItem('${THEME_STORAGE_KEY}');if(t==='light'||t==='dark'){document.documentElement.setAttribute('data-theme',t);}}catch(e){}})();`;
}
