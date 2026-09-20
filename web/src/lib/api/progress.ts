import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "./client";

// Shapes verified against backend/src/main/kotlin/com/mentora/backend/progress/service/ProgressService.kt.
// Nullable Kotlin fields are optional here per D38 (explicitNulls = false — omitted, not `null`).
export interface ProgressResponse {
  courseId: string;
  completedLessonIds: string[];
  currentLessonId?: string;
  currentPositionSeconds?: number;
  quizPassed?: boolean;
  completionPercent: number;
  courseCompletedAt?: string;
}

export function getProgress(courseId: string) {
  return apiFetch<ProgressResponse>(`/courses/${courseId}/progress`);
}

export function useProgress(courseId: string) {
  return useQuery({
    queryKey: ["progress", courseId],
    queryFn: () => getProgress(courseId),
    enabled: Boolean(courseId),
  });
}

export function useCompleteLesson(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (lessonId: string) =>
      apiFetch<ProgressResponse>(`/courses/${courseId}/lessons/${lessonId}/complete`, { method: "POST" }),
    onSuccess: (progress) => {
      queryClient.setQueryData(["progress", courseId], progress);
      queryClient.invalidateQueries({ queryKey: ["progress", courseId] });
    },
  });
}

export function useUpdatePosition(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ lessonId, positionSeconds }: { lessonId: string; positionSeconds: number }) =>
      // keepalive: true lets this request survive a same-tab navigation immediately after — the
      // real-world case this exists for (pause, then immediately leave), not just a test artifact.
      apiFetch<ProgressResponse>(`/courses/${courseId}/lessons/${lessonId}/position`, {
        method: "POST",
        body: JSON.stringify({ positionSeconds }),
        keepalive: true,
      }),
    onSuccess: (progress) => {
      queryClient.setQueryData(["progress", courseId], progress);
      queryClient.invalidateQueries({ queryKey: ["progress", courseId] });
    },
  });
}
