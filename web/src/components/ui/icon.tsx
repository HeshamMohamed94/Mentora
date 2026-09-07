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
  | "logout"
  | "play"
  | "pause"
  | "volumeOn"
  | "volumeMuted"
  | "fullscreen"
  | "fullscreenExit"
  | "checkCircle"
  | "cancel"
  | "expandMore"
  | "expandLess"
  | "visibility"
  | "visibilityOff"
  | "search"
  | "close"
  | "add"
  | "delete"
  | "arrowUpward"
  | "arrowDownward"
  | "dragHandle"
  | "upload"
  | "courseAnalytics"
  | "courseDesign"
  | "courseCode"
  | "courseGrid"
  | "courseLayers"
  | "arrowForward"
  | "arrowBack";

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
  play: <path d="M8 5.5v13l10-6.5z" />,
  pause: (
    <>
      <path d="M8 5.5v13M16 5.5v13" />
    </>
  ),
  volumeOn: (
    <>
      <path d="M4 10v4h4l5 4V6L8 10z" />
      <path d="M16 9c1.7 1.7 1.7 4.3 0 6M18.5 6.5c3 3 3 8 0 11" />
    </>
  ),
  volumeMuted: (
    <>
      <path d="M4 10v4h4l5 4V6L8 10zM16.5 9.5l4 5M20.5 9.5l-4 5" />
    </>
  ),
  fullscreen: <path d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5" />,
  fullscreenExit: <path d="M4 9h5V4M20 9h-5V4M4 15h5v5M20 15h-5v5" />,
  checkCircle: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M8 12.5l2.5 2.5 5.5-6" />
    </>
  ),
  cancel: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </>
  ),
  expandMore: <path d="M7 9.5l5 5 5-5" />,
  expandLess: <path d="M7 14.5l5-5 5 5" />,
  visibility: (
    <>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="3" />
    </>
  ),
  visibilityOff: (
    <>
      <path d="M4 4l16 16" />
      <path d="M9.9 5.7c.7-.13 1.4-.2 2.1-.2 6 0 9.5 6.5 9.5 6.5a17.5 17.5 0 01-3.2 4.1M6.8 7.3A17.6 17.6 0 002.5 12S6 18.5 12 18.5c1.3 0 2.5-.3 3.6-.8" />
      <path d="M9.9 14.1a3 3 0 004.2-4.2" />
    </>
  ),
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="M19.5 19.5l-4.3-4.3" />
    </>
  ),
  close: <path d="M6 6l12 12M18 6L6 18" />,
  add: <path d="M12 5v14M5 12h14" />,
  delete: <path d="M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" />,
  arrowUpward: <path d="M12 19V5M6.5 10.5L12 5l5.5 5.5" />,
  arrowDownward: <path d="M12 5v14M6.5 13.5L12 19l5.5-5.5" />,
  dragHandle: <path d="M8 7h8M8 12h8M8 17h8" />,
  upload: <path d="M12 16V4M7 9l5-5 5 5M5 20h14" />,
  courseAnalytics: <path d="M6 20V13M12 20V9M18 20V5" />,
  courseDesign: (
    <>
      <circle cx="8" cy="9" r="2.3" />
      <circle cx="16" cy="9" r="2.3" />
      <circle cx="12" cy="16" r="2.3" />
    </>
  ),
  courseCode: <path d="M9 8l-5 4 5 4M15 8l5 4-5 4" />,
  courseGrid: (
    <>
      <rect x="4" y="4" width="7" height="7" rx="1" />
      <rect x="13" y="4" width="7" height="7" rx="1" />
      <rect x="4" y="13" width="7" height="7" rx="1" />
      <rect x="13" y="13" width="7" height="7" rx="1" />
    </>
  ),
  courseLayers: (
    <>
      <path d="M12 4l8 4-8 4-8-4z" />
      <path d="M4 12l8 4 8-4" />
      <path d="M4 16l8 4 8-4" />
    </>
  ),
  arrowForward: <path d="M5 12h14M13 6l6 6-6 6" />,
  arrowBack: <path d="M19 12H5M11 6l-6 6 6 6" />,
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
