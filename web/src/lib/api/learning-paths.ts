import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "./client";

// Shapes verified against
// backend/src/main/kotlin/com/mentora/backend/learningpaths/service/LearningPathService.kt.
// Route is /api/v1/learning-paths (kebab-case) — distinct from the Web URL /paths.

export interface LearningPathSummary {
  id: string;
  title: string;
  description: string;
  courseCount: number;
}

export interface LearningPathCourse {
  id: string;
  title: string;
  thumbnailMediaId: string | null;
}

export interface LearningPathResponse {
  id: string;
  title: string;
  description: string;
  courses: LearningPathCourse[];
  /** Absent (not `null`) when there's no principal — backend `explicitNulls = false`, D20/D36. */
  progressPercent?: number;
  isFollowing: boolean;
}

export async function listLearningPaths(): Promise<LearningPathSummary[]> {
  return apiFetch<LearningPathSummary[]>("/learning-paths");
}

export async function getLearningPath(id: string): Promise<LearningPathResponse> {
  return apiFetch<LearningPathResponse>(`/learning-paths/${id}`);
}

export function useLearningPaths() {
  return useQuery({ queryKey: ["learning-paths"], queryFn: listLearningPaths });
}

export function useLearningPath(id: string) {
  return useQuery({
    queryKey: ["learning-paths", id],
    queryFn: () => getLearningPath(id),
    enabled: Boolean(id),
  });
}

export function useFollowLearningPath(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<{ isFollowing: boolean }>(`/learning-paths/${id}/follow`, { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["learning-paths", id] }),
  });
}

/**
 * `GET /learning-paths` (the list) carries no `isFollowing`/`progressPercent` — only the detail
 * endpoint does. There's no dedicated "my followed paths" backend endpoint either, so this
 * fetches every path's detail (fine at MVP demo scale — a handful of paths total, per the seed
 * data) and filters client-side, the same N+1-at-small-scale tradeoff as `useMyLearning`.
 */
export function useFollowedLearningPaths() {
  const listQuery = useLearningPaths();
  const paths = listQuery.data ?? [];

  const detailQueries = useQueries({
    queries: paths.map((p) => ({
      queryKey: ["learning-paths", p.id],
      queryFn: () => getLearningPath(p.id),
    })),
  });

  const isLoading = listQuery.isLoading || detailQueries.some((q) => q.isLoading);
  const items = detailQueries
    .map((q) => q.data)
    .filter((path): path is LearningPathResponse => Boolean(path?.isFollowing));

  return { items, isLoading };
}

export function useUnfollowLearningPath(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<{ isFollowing: boolean }>(`/learning-paths/${id}/follow`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["learning-paths", id] }),
  });
}
