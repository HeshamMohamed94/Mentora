import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "./client";

export interface InstructorCourseSummary {
  id: string;
  title: string;
  status: "draft" | "published";
  enrollmentCount: number;
  completionRate: number;
}

export interface InstructorDashboardResponse {
  stats: {
    totalCourses: number;
    publishedCount: number;
    totalEnrollments: number;
  };
  courses: InstructorCourseSummary[];
}

export function useInstructorDashboard() {
  return useQuery({
    queryKey: ["instructor-dashboard"],
    queryFn: () => apiFetch<InstructorDashboardResponse>("/instructor/dashboard"),
  });
}
