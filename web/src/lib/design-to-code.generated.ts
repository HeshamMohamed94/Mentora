// GENERATED — DO NOT EDIT.
// Source: design-to-code/shared/*.json, design-to-code/screens/*.json
// Regenerate with: node tools/design-to-code/generate.js (or npm run generate:design-to-code from web/)
//
// This is the layer ABOVE raw design tokens — raw CSS custom properties are generated
// separately by tools/token-pipeline/generate.js into web/styles/tokens.css and
// web/src/lib/design-tokens.generated.ts. This file never redefines a token value.

export interface NavItem {
  key: string;
  href: string;
  icon: string;
}

/** Authenticated Student Sidebar items — design-to-code/shared/navigation.json#/shells/authenticatedStudent */
export const studentNavItems: readonly NavItem[] = [
  { key: "dashboard", href: "/app", icon: "dashboard" },
  { key: "explore", href: "/app/explore", icon: "explore" },
  { key: "myLearning", href: "/app/my-learning", icon: "myLearning" },
  { key: "learningPaths", href: "/app/paths", icon: "learningPaths" },
  { key: "aiTutor", href: "/app/ai-tutor", icon: "aiTutor" },
  { key: "certificates", href: "/app/certificates", icon: "certificates" },
  { key: "profile", href: "/app/profile", icon: "profile" },
  { key: "settings", href: "/app/settings", icon: "settings" },
] as const;

/** Instructor Sidebar items — design-to-code/shared/navigation.json#/shells/instructorWeb */
export const instructorNavItems: readonly NavItem[] = [
  { key: "dashboard", href: "/instructor", icon: "dashboard" },
  { key: "profile", href: "/instructor/profile", icon: "profile" },
  { key: "settings", href: "/instructor/settings", icon: "settings" },
] as const;

/** Admin Sidebar items — design-to-code/shared/navigation.json#/shells/adminWeb */
export const adminNavItems: readonly NavItem[] = [
  { key: "dashboard", href: "/admin", icon: "dashboard" },
  { key: "courses", href: "/admin/courses", icon: "myLearning" },
  { key: "users", href: "/admin/users", icon: "people" },
  { key: "instructors", href: "/admin/instructors", icon: "school" },
  { key: "categories", href: "/admin/categories", icon: "courseGrid" },
] as const;

export interface ArtworkMotif {
  id: string;
  icon: string;
  gradient: string;
}

/** The governed 5-motif course-artwork system — design-to-code/shared/artwork.json#/motifSystem */
export const artworkMotifs: readonly ArtworkMotif[] = [
  { id: "analytics", icon: "courseAnalytics", gradient: "radial-gradient(circle at 84% 16%, rgba(255,255,255,0.22), transparent 48%), repeating-linear-gradient(90deg, transparent 0 6%, rgba(255,255,255,0.15) 6% 10%), linear-gradient(135deg, #241C5C 0%, #4A3EB0 58%, #4A62F0 100%)" },
  { id: "design", icon: "courseDesign", gradient: "repeating-linear-gradient(45deg, rgba(255,255,255,0.14) 0 1px, transparent 1px 9%), radial-gradient(circle at 76% 78%, rgba(255,255,255,0.20), transparent 42%), linear-gradient(135deg, #35257F 0%, #6558D3 60%, #7C4DFF 100%)" },
  { id: "code", icon: "courseCode", gradient: "repeating-radial-gradient(circle at 72% 50%, rgba(255,255,255,0.16) 0 1.5px, transparent 1.5px 13px), radial-gradient(circle at 72% 50%, rgba(255,255,255,0.22), transparent 30%), linear-gradient(135deg, #1C2470 0%, #3B4278 50%, #4A62F0 100%)" },
  { id: "grid", icon: "courseGrid", gradient: "radial-gradient(circle at 20% 20%, rgba(255,255,255,0.20), transparent 46%), repeating-linear-gradient(0deg, rgba(255,255,255,0.13) 0 1px, transparent 1px 18%), repeating-linear-gradient(90deg, rgba(255,255,255,0.13) 0 1px, transparent 1px 14%), linear-gradient(135deg, #3B2A7A 0%, #6558D3 55%, #7C4DFF 100%)" },
  { id: "layers", icon: "courseLayers", gradient: "repeating-linear-gradient(180deg, rgba(255,255,255,0.14) 0 2px, transparent 2px 22%), radial-gradient(circle at 18% 82%, rgba(255,255,255,0.18), transparent 44%), linear-gradient(135deg, #191A20 0%, #2B2170 55%, #3B4278 100%)" },
] as const;

/** Deterministic motif assignment — design-to-code/shared/artwork.json#/motifSystem/assignmentRule */
export function artworkMotifFor(seedOrCategoryId: string): ArtworkMotif {
  let hash = 0;
  for (let i = 0; i < seedOrCategoryId.length; i++) {
    hash = (hash * 31 + seedOrCategoryId.charCodeAt(i)) >>> 0;
  }
  return artworkMotifs[hash % artworkMotifs.length] ?? artworkMotifs[0]!;
}

export type ReferenceType = "exact-showcase" | "approved-pattern" | "ux-only";

export interface ScreenRoute {
  routeIntent: string;
  referenceType: ReferenceType;
  screenNumber: number;
}

/** screenId -> route/referenceType, one entry per design-to-code/screens/*.json (24 screens, Tasks 1-11 scope) */
export const screenRoutes: Readonly<Record<string, ScreenRoute>> = {
  "landing": { routeIntent: "/{locale} (public root)", referenceType: "exact-showcase", screenNumber: 1 },
  "explore": { routeIntent: "/{locale}/explore (public) and /{locale}/app/explore (authenticated)", referenceType: "exact-showcase", screenNumber: 2 },
  "course-details": { routeIntent: "/{locale}/courses/{id} (public) and /{locale}/app/courses/{id} (authenticated)", referenceType: "approved-pattern", screenNumber: 3 },
  "learning-paths": { routeIntent: "/{locale}/paths (public) and /{locale}/app/paths (authenticated)", referenceType: "ux-only", screenNumber: 4 },
  "learning-path-details": { routeIntent: "/{locale}/paths/{id} (public) and /{locale}/app/paths/{id} (authenticated)", referenceType: "ux-only", screenNumber: 5 },
  "login": { routeIntent: "/{locale}/login", referenceType: "approved-pattern", screenNumber: 6 },
  "register": { routeIntent: "/{locale}/register", referenceType: "approved-pattern", screenNumber: 7 },
  "dashboard": { routeIntent: "/{locale}/app", referenceType: "exact-showcase", screenNumber: 8 },
  "my-learning": { routeIntent: "/{locale}/app/my-learning", referenceType: "approved-pattern", screenNumber: 9 },
  "course-player": { routeIntent: "/{locale}/app/learn/{courseId}", referenceType: "exact-showcase", screenNumber: 10 },
  "quiz": { routeIntent: "/{locale}/app/learn/{courseId}/quiz", referenceType: "ux-only", screenNumber: 11 },
  "quiz-results": { routeIntent: "/{locale}/app/learn/{courseId}/quiz/results", referenceType: "ux-only", screenNumber: 12 },
  "certificates": { routeIntent: "/{locale}/app/certificates", referenceType: "ux-only", screenNumber: 13 },
  "certificate-detail": { routeIntent: "/{locale}/app/certificates/{id}", referenceType: "ux-only", screenNumber: 14 },
  "ai-tutor": { routeIntent: "/{locale}/app/ai-tutor", referenceType: "approved-pattern", screenNumber: 15 },
  "profile": { routeIntent: "/{locale}/app/profile", referenceType: "ux-only", screenNumber: 16 },
  "settings": { routeIntent: "/{locale}/app/settings", referenceType: "ux-only", screenNumber: 17 },
  "demo-checkout": { routeIntent: "/{locale}/app/checkout/{courseId}", referenceType: "approved-pattern", screenNumber: 18 },
  "purchase-success": { routeIntent: "/{locale}/app/checkout/{courseId}/success", referenceType: "approved-pattern", screenNumber: 19 },
  "instructor-dashboard": { routeIntent: "/{locale}/instructor", referenceType: "exact-showcase", screenNumber: 20 },
  "course-editor-overview": { routeIntent: "/{locale}/instructor/courses/{courseId} (Overview tab active)", referenceType: "exact-showcase", screenNumber: 21 },
  "course-editor-curriculum": { routeIntent: "/{locale}/instructor/courses/{courseId} (Curriculum tab active)", referenceType: "exact-showcase", screenNumber: 22 },
  "lesson-editor": { routeIntent: "/{locale}/instructor/courses/{courseId}/sections/{sectionId}/lessons/{lessonId} (or /new)", referenceType: "ux-only", screenNumber: 23 },
  "quiz-editor": { routeIntent: "/{locale}/instructor/courses/{courseId}/quiz", referenceType: "ux-only", screenNumber: 24 },
  "admin-dashboard": { routeIntent: "/{locale}/admin", referenceType: "ux-only", screenNumber: 25 },
  "admin-courses": { routeIntent: "/{locale}/admin/courses", referenceType: "exact-showcase", screenNumber: 26 },
  "admin-users": { routeIntent: "/{locale}/admin/users", referenceType: "ux-only", screenNumber: 27 },
  "admin-instructors": { routeIntent: "/{locale}/admin/instructors", referenceType: "ux-only", screenNumber: 28 },
  "admin-categories": { routeIntent: "/{locale}/admin/categories", referenceType: "ux-only", screenNumber: 29 },
} as const;
