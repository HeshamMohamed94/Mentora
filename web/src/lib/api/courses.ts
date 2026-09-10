import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiRequest } from "./client";

// Shapes verified against backend/src/main/kotlin/com/mentora/backend/courses/service/CourseService.kt
// (execution/INTEGRATION_CONTRACT.md's "Courses" section + D37's instructorName addition).

export interface PriceDisplay {
  amount: number;
  currency: string;
}

export interface CourseSummary {
  id: string;
  title: string;
  description: string;
  categoryId: string;
  level: "beginner" | "intermediate" | "advanced";
  contentLanguage: string;
  priceDisplay: PriceDisplay;
  thumbnailMediaId: string | null;
  ratingSeed: number;
  instructorId: string;
  instructorName: string;
}

export interface ResourceLink {
  label: string;
  url: string;
}

export interface LessonResponse {
  lessonId: string;
  title: string;
  description: string;
  order: number;
  videoMediaId: string | null;
  resources: ResourceLink[];
}

export interface SectionResponse {
  sectionId: string;
  title: string;
  order: number;
  lessons: LessonResponse[];
}

export interface CourseTranslation {
  title: string;
  description: string;
}

export interface CourseResponse extends CourseSummary {
  status: "draft" | "published";
  sections: SectionResponse[];
  /** Every locale this course has localized metadata for, keyed by locale ("en"/"ar") — never
   * includes an entry for `contentLanguage` itself (that's already `title`/`description`). Not
   * used for display (the top-level `title`/`description` are already resolved for whatever
   * `language` was requested) — present for a future editing UI. */
  translations: Record<string, CourseTranslation>;
}

export interface CourseListFilters {
  category?: string;
  level?: string;
  /** UI locale to resolve `title`/`description` for. Also narrows the result set: a course is
   * included only when its `contentLanguage` matches or it has a translation for this locale —
   * never omit a course just because its content language differs from a locale it has been
   * translated into (see execution/DECISIONS_LOG.md D57). */
  language?: string;
  maxPrice?: number;
  q?: string;
  cursor?: string;
  limit?: number;
}

function buildQuery(filters: CourseListFilters): string {
  const params = new URLSearchParams();
  if (filters.category) params.set("category", filters.category);
  if (filters.level) params.set("level", filters.level);
  if (filters.language) params.set("language", filters.language);
  if (filters.maxPrice !== undefined) params.set("maxPrice", String(filters.maxPrice));
  if (filters.q) params.set("q", filters.q);
  if (filters.cursor) params.set("cursor", filters.cursor);
  if (filters.limit) params.set("limit", String(filters.limit));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export async function listCourses(filters: CourseListFilters = {}) {
  const { data, meta } = await apiRequest<CourseSummary[]>(`/courses${buildQuery(filters)}`);
  return { items: data, nextCursor: meta.nextCursor };
}

export async function getCourse(id: string, language?: string): Promise<CourseResponse> {
  const qs = language ? `?language=${language}` : "";
  return apiFetch<CourseResponse>(`/courses/${id}${qs}`);
}

export function useCourses(filters: CourseListFilters = {}) {
  return useQuery({
    queryKey: ["courses", filters],
    queryFn: () => listCourses(filters),
  });
}

/** Omit `language` when fetching a course to edit (Course Editor/Lesson Editor) — the base,
 * original-language title/description round-trip unchanged, which is what an instructor editing
 * their own course needs. Pass the current UI locale for any student/guest-facing display. */
export function useCourse(id: string, language?: string) {
  return useQuery({
    queryKey: ["courses", id, language],
    queryFn: () => getCourse(id, language),
    enabled: Boolean(id),
  });
}

export interface CourseWriteRequest {
  title: string;
  description: string;
  categoryId: string;
  level: "beginner" | "intermediate" | "advanced";
  contentLanguage: "en" | "ar";
  priceDisplay: PriceDisplay;
  thumbnailMediaId?: string;
}

export type CourseUpdateRequest = Partial<CourseWriteRequest>;

export function updateCourse(courseId: string, request: CourseUpdateRequest) {
  return apiFetch<CourseResponse>(`/courses/${courseId}`, { method: "PATCH", body: JSON.stringify(request) });
}

export function updateLesson(courseId: string, sectionId: string, lessonId: string, request: Partial<LessonWriteRequest>) {
  return apiFetch<CourseResponse>(`/courses/${courseId}/sections/${sectionId}/lessons/${lessonId}`, {
    method: "PATCH", body: JSON.stringify(request),
  });
}

function useCourseMutation<TVariables, TResponse>(
  mutationFn: (variables: TVariables) => Promise<TResponse>,
  courseId?: string,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["instructor-dashboard"] });
      if (courseId) queryClient.invalidateQueries({ queryKey: ["courses", courseId] });
    },
  });
}

export function useCreateCourse() {
  return useCourseMutation((request: CourseWriteRequest) =>
    apiFetch<CourseResponse>("/courses", { method: "POST", body: JSON.stringify(request) }),
  );
}

export function useUpdateCourse(courseId: string) {
  return useCourseMutation((request: CourseUpdateRequest) => updateCourse(courseId, request), courseId);
}

export function usePublishCourse(courseId: string) {
  return useCourseMutation(() =>
    apiFetch<CourseResponse>(`/courses/${courseId}/publish`, { method: "POST" }), courseId,
  );
}

export function useUnpublishCourse(courseId: string) {
  return useCourseMutation(() =>
    apiFetch<CourseResponse>(`/courses/${courseId}/unpublish`, { method: "POST" }), courseId,
  );
}

export function useAddSection(courseId: string) {
  return useCourseMutation((title: string) =>
    apiFetch<CourseResponse>(`/courses/${courseId}/sections`, {
      method: "POST", body: JSON.stringify({ title }),
    }), courseId,
  );
}

export function useUpdateSection(courseId: string, sectionId: string) {
  return useCourseMutation((title: string) =>
    apiFetch<CourseResponse>(`/courses/${courseId}/sections/${sectionId}`, {
      method: "PATCH", body: JSON.stringify({ title }),
    }), courseId,
  );
}

export function useDeleteSection(courseId: string) {
  return useCourseMutation((sectionId: string) =>
    apiFetch<void>(`/courses/${courseId}/sections/${sectionId}`, { method: "DELETE" }), courseId,
  );
}

export function useReorderSections(courseId: string) {
  return useCourseMutation((sectionIds: string[]) =>
    apiFetch<CourseResponse>(`/courses/${courseId}/sections/reorder`, {
      method: "PATCH", body: JSON.stringify({ sectionIds }),
    }), courseId,
  );
}

export interface LessonWriteRequest {
  title: string;
  description: string;
  videoMediaId?: string;
  resources: ResourceLink[];
}

export function useAddLesson(courseId: string, sectionId: string) {
  return useCourseMutation((request: LessonWriteRequest) =>
    apiFetch<CourseResponse>(`/courses/${courseId}/sections/${sectionId}/lessons`, {
      method: "POST", body: JSON.stringify(request),
    }), courseId,
  );
}

export function useUpdateLesson(courseId: string, sectionId: string, lessonId: string) {
  return useCourseMutation((request: Partial<LessonWriteRequest>) => updateLesson(courseId, sectionId, lessonId, request), courseId);
}

export function useDeleteLesson(courseId: string, sectionId: string) {
  return useCourseMutation((lessonId: string) =>
    apiFetch<void>(`/courses/${courseId}/sections/${sectionId}/lessons/${lessonId}`, { method: "DELETE" }), courseId,
  );
}

export function useReorderLessons(courseId: string, sectionId: string) {
  return useCourseMutation((lessonIds: string[]) =>
    apiFetch<CourseResponse>(`/courses/${courseId}/sections/${sectionId}/lessons/reorder`, {
      method: "PATCH", body: JSON.stringify({ lessonIds }),
    }), courseId,
  );
}
