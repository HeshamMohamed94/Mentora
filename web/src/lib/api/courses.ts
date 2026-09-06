import { useQuery } from "@tanstack/react-query";
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

export interface CourseResponse extends CourseSummary {
  status: "draft" | "published";
  sections: SectionResponse[];
}

export interface CourseListFilters {
  category?: string;
  level?: string;
  maxPrice?: number;
  q?: string;
  cursor?: string;
  limit?: number;
}

function buildQuery(filters: CourseListFilters): string {
  const params = new URLSearchParams();
  if (filters.category) params.set("category", filters.category);
  if (filters.level) params.set("level", filters.level);
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

export async function getCourse(id: string): Promise<CourseResponse> {
  return apiFetch<CourseResponse>(`/courses/${id}`);
}

export function useCourses(filters: CourseListFilters = {}) {
  return useQuery({
    queryKey: ["courses", filters],
    queryFn: () => listCourses(filters),
  });
}

export function useCourse(id: string) {
  return useQuery({
    queryKey: ["courses", id],
    queryFn: () => getCourse(id),
    enabled: Boolean(id),
  });
}
