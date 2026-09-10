"use client";

import { useQueries } from "@tanstack/react-query";
import { useEnrollments, type EnrollmentResponse } from "./enrollment";
import { getCourse, type CourseResponse } from "./courses";
import { getProgress, type ProgressResponse } from "./progress";

export interface MyLearningItem {
  enrollment: EnrollmentResponse;
  course: CourseResponse;
  progress: ProgressResponse;
}

/**
 * Composes `GET /enrollments` with a per-course `GET /courses/:id` and `GET
 * /courses/:id/progress` — there is no dedicated aggregate "My Learning" backend endpoint.
 * At MVP demo scale (a handful of enrollments per student) N+1 client-side composition is the
 * simpler choice over proposing another backend addition (see D37 for when that bar is worth
 * crossing).
 */
export function useMyLearning(language?: string) {
  const enrollmentsQuery = useEnrollments();
  const enrollments = enrollmentsQuery.data ?? [];

  const courseQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ["courses", e.courseId, language],
      queryFn: () => getCourse(e.courseId, language),
    })),
  });

  const progressQueries = useQueries({
    queries: enrollments.map((e) => ({
      queryKey: ["progress", e.courseId],
      queryFn: () => getProgress(e.courseId),
    })),
  });

  const isLoading =
    enrollmentsQuery.isLoading || courseQueries.some((q) => q.isLoading) || progressQueries.some((q) => q.isLoading);
  const isError =
    enrollmentsQuery.isError || courseQueries.some((q) => q.isError) || progressQueries.some((q) => q.isError);

  const items: MyLearningItem[] = enrollments
    .map((enrollment, index) => {
      const course = courseQueries[index]?.data;
      const progress = progressQueries[index]?.data;
      return course && progress ? { enrollment, course, progress } : null;
    })
    .filter((item): item is MyLearningItem => item !== null);

  return { items, isLoading, isError, refetch: enrollmentsQuery.refetch };
}
