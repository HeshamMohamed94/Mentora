import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiRequest } from "./client";

// Shapes verified against backend/src/main/kotlin/com/mentora/backend/admin/service/AdminService.kt
// (GET /api/v1/admin/{dashboard,courses,users,instructors} — all Admin-only, JWT-gated).

export interface AdminDashboardResponse {
  totalCourses: number;
  publishedCourses: number;
  draftCourses: number;
  totalStudents: number;
  totalInstructors: number;
}

export interface AdminCourseSummary {
  id: string;
  title: string;
  instructorName: string;
  status: "draft" | "published";
  enrollmentCount: number;
}

export interface AdminUserSummary {
  id: string;
  name: string;
  email: string;
  createdAt: string;
  enrollmentCount: number;
}

export interface AdminInstructorSummary {
  id: string;
  name: string;
  email: string;
  courseCount: number;
  publishedCount: number;
}

export interface AdminListFilters {
  q?: string;
  cursor?: string;
  limit?: number;
}

function buildQuery(filters: AdminListFilters): string {
  const params = new URLSearchParams();
  if (filters.q) params.set("q", filters.q);
  if (filters.cursor) params.set("cursor", filters.cursor);
  if (filters.limit) params.set("limit", String(filters.limit));
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export function useAdminDashboard() {
  return useQuery({
    queryKey: ["admin-dashboard"],
    queryFn: () => apiFetch<AdminDashboardResponse>("/admin/dashboard"),
  });
}

async function listAdminCourses(filters: AdminListFilters = {}) {
  const { data, meta } = await apiRequest<AdminCourseSummary[]>(`/admin/courses${buildQuery(filters)}`);
  return { items: data, nextCursor: meta.nextCursor };
}

export function useAdminCourses(filters: AdminListFilters = {}) {
  return useQuery({
    queryKey: ["admin-courses", filters],
    queryFn: () => listAdminCourses(filters),
  });
}

async function listAdminUsers(filters: AdminListFilters = {}) {
  const { data, meta } = await apiRequest<AdminUserSummary[]>(`/admin/users${buildQuery(filters)}`);
  return { items: data, nextCursor: meta.nextCursor };
}

export function useAdminUsers(filters: AdminListFilters = {}) {
  return useQuery({
    queryKey: ["admin-users", filters],
    queryFn: () => listAdminUsers(filters),
  });
}

async function listAdminInstructors(filters: AdminListFilters = {}) {
  const { data, meta } = await apiRequest<AdminInstructorSummary[]>(`/admin/instructors${buildQuery(filters)}`);
  return { items: data, nextCursor: meta.nextCursor };
}

export function useAdminInstructors(filters: AdminListFilters = {}) {
  return useQuery({
    queryKey: ["admin-instructors", filters],
    queryFn: () => listAdminInstructors(filters),
  });
}

/** Reuses courses.ts's real unpublish endpoint (Admin-capable per CourseRoutes.kt's
 * `Role.instructor, Role.admin` check on POST /courses/{id}/unpublish) — Admin's only real
 * course-moderation action; no delete-course endpoint exists anywhere in the backend. */
export function useAdminUnpublishCourse() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (courseId: string) => apiFetch(`/courses/${courseId}/unpublish`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-courses"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-dashboard"] });
    },
  });
}
