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
