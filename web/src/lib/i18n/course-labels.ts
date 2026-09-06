/** Shared mapping from the backend's raw `level` enum value to an `explore.*` translation
 * key — used everywhere a course's level is displayed (CourseCard, Course Details, Explore
 * filter chips) so it's never shown as an untranslated raw English string. */
export const LEVEL_LABEL_KEYS = {
  beginner: "levelBeginner",
  intermediate: "levelIntermediate",
  advanced: "levelAdvanced",
} as const;

export type CourseLevel = keyof typeof LEVEL_LABEL_KEYS;
