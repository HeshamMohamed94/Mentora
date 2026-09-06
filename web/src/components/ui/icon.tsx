/**
 * design-system/DESIGN_SYSTEM.md § Iconography specifies Material Symbols Rounded (self-hosted
 * font or inline SVG sprite) as the canonical icon language. No font asset/build pipeline for
 * the real Material Symbols set exists in this repo yet, so this ships a small hand-drawn inline
 * SVG set with an equivalent rounded-stroke silhouette for just the icons the authenticated
 * Sidebar needs — a placeholder behind a stable `Icon` API, swappable for the real self-hosted
 * font later without touching call sites.
 */

export type IconName =
  | "dashboard"
  | "explore"
  | "myLearning"
  | "learningPaths"
  | "aiTutor"
  | "certificates"
  | "profile"
  | "settings"
  | "menu"
  | "logout";

const PATHS: Record<IconName, React.ReactNode> = {
  dashboard: (
    <>
      <rect x="3.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="3.5" width="7" height="7" rx="1.5" />
      <rect x="3.5" y="13.5" width="7" height="7" rx="1.5" />
      <rect x="13.5" y="13.5" width="7" height="7" rx="1.5" />
    </>
  ),
  explore: (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M14.8 9.2l-2 4.7-4.7 2 2-4.7z" />
    </>
  ),
  myLearning: (
    <path d="M4 5.5C4 4.7 4.7 4 5.5 4H12v16H5.5c-.8 0-1.5-.7-1.5-1.5v-13zM20 5.5c0-.8-.7-1.5-1.5-1.5H12v16h6.5c.8 0 1.5-.7 1.5-1.5v-13z" />
  ),
  learningPaths: (
    <>
      <circle cx="5" cy="6" r="2" />
      <circle cx="19" cy="18" r="2" />
      <path d="M6.7 7.3C10 11 14 13 17.3 16.7" />
    </>
  ),
  aiTutor: (
    <>
      <path d="M4 6.5C4 5.1 5.1 4 6.5 4h11C18.9 4 20 5.1 20 6.5v7c0 1.4-1.1 2.5-2.5 2.5H9l-4 4v-4H6.5C5.1 16 4 14.9 4 13.5v-7z" />
    </>
  ),
  certificates: (
    <>
      <circle cx="12" cy="8.5" r="4.5" />
      <path d="M9 12.5l-1.5 7 4.5-2.5 4.5 2.5-1.5-7" />
    </>
  ),
  profile: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c0-3.9 3.1-7 7-7s7 3.1 7 7" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3v2.5M12 18.5V21M21 12h-2.5M5.5 12H3M18.4 5.6l-1.8 1.8M7.4 16.6l-1.8 1.8M18.4 18.4l-1.8-1.8M7.4 7.4L5.6 5.6" />
    </>
  ),
  menu: <path d="M4 6.5h16M4 12h16M4 17.5h16" />,
  logout: (
    <>
      <path d="M9 4H6.5C5.1 4 4 5.1 4 6.5v11C4 18.9 5.1 20 6.5 20H9" />
      <path d="M14 15.5l4.5-3.5-4.5-3.5M18.5 12H9" />
    </>
  ),
};

export function Icon({ name, size = 24, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {PATHS[name]}
    </svg>
  );
}
