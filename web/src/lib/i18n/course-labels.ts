/** Shared mapping from the backend's raw `level` enum value to an `explore.*` translation
 * key — used everywhere a course's level is displayed (CourseCard, Course Details, Explore
 * filter chips) so it's never shown as an untranslated raw English string. */
export const LEVEL_LABEL_KEYS = {
  beginner: "levelBeginner",
  intermediate: "levelIntermediate",
  advanced: "levelAdvanced",
} as const;

export type CourseLevel = keyof typeof LEVEL_LABEL_KEYS;

/** Shared mapping from a course's raw `contentLanguage` value to an `explore.*` translation key
 * — used wherever a course's actual content language needs to be shown to the reader (as
 * distinct from its localized/translated title — see execution/DECISIONS_LOG.md D57), so a
 * translated-but-still-Arabic-content course is never misrepresented as English content. */
export const CONTENT_LANGUAGE_LABEL_KEYS = {
  en: "contentLanguageEnglish",
  ar: "contentLanguageArabic",
} as const;

export type CourseContentLanguage = keyof typeof CONTENT_LANGUAGE_LABEL_KEYS;
