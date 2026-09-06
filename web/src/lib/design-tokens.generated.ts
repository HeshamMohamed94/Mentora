// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json
// Regenerate with: npm run generate (from tools/token-pipeline/)

export const breakpoints = {
  "mobile": {
    "min": 0,
    "max": 599
  },
  "tablet": {
    "min": 600,
    "max": 1023
  },
  "desktop": {
    "min": 1024,
    "max": 1439
  },
  "largeDesktop": {
    "min": 1440,
    "max": null
  }
} as const;

export const grid = {
  "columns": {
    "mobile": 4,
    "tablet": 8,
    "desktop": 12,
    "largeDesktop": 12
  },
  "gutter": {
    "mobile": 16,
    "tablet": 24,
    "desktop": 24,
    "largeDesktop": 24
  },
  "maxContentWidth": 1440,
  "preferredMarketingWidth": "1200-1280"
} as const;

export const courseGrid = {
  "mobile": 1,
  "tablet": 2,
  "desktop": 3,
  "largeDesktop": 4
} as const;

export const spacingScale = {
  "space.0": 0,
  "space.1": 4,
  "space.2": 8,
  "space.3": 12,
  "space.4": 16,
  "space.5": 20,
  "space.6": 24,
  "space.8": 32,
  "space.10": 40,
  "space.12": 48,
  "space.16": 64
} as const;

export const iconDirectional = {
  "note": "Icons that encode a left/right direction MUST be authored as logical (start/end) and mirrored automatically in RTL locales (see LOCALIZATION.md). Icons with no inherent direction never mirror.",
  "mirrorInRtl": [
    "chevron_left",
    "chevron_right",
    "arrow_back",
    "arrow_forward",
    "arrow_back_ios",
    "arrow_forward_ios",
    "trending_flat",
    "redo",
    "undo",
    "reply",
    "forward",
    "last_page",
    "first_page",
    "double_arrow",
    "menu_open"
  ],
  "neverMirror": [
    "play_arrow",
    "pause",
    "check",
    "check_circle",
    "cancel",
    "close",
    "search",
    "add",
    "remove",
    "star",
    "favorite",
    "download",
    "upload",
    "expand_more",
    "expand_less",
    "refresh",
    "visibility",
    "visibility_off",
    "volume_up",
    "volume_off",
    "fullscreen",
    "fullscreen_exit",
    "speed",
    "cloud_upload",
    "insert_drive_file",
    "drag_indicator",
    "arrow_upward",
    "arrow_downward",
    "more_vert"
  ]
} as const;

export const touchTarget = {
  "android_dp": 48,
  "ios_pt": 44,
  "web_px": 44
} as const;
