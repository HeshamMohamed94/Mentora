import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, apiRequest } from "./client";
import { useCurrentUser } from "../auth/use-current-user";

// Shapes verified against
// backend/src/main/kotlin/com/mentora/backend/enrollment/service/EnrollmentService.kt.

export interface CheckoutPreview {
  course: { id: string; title: string; thumbnailMediaId: string | null };
  instructorName: string;
  priceDisplay: { amount: number; currency: string };
}

export interface EnrollmentResponse {
  id: string;
  courseId: string;
  source: string;
  enrolledAt: string;
  status: string;
}

export interface EnrollmentCompletion {
  enrollment: EnrollmentResponse;
  alreadyEnrolled: boolean;
}

export function useCheckoutPreview(courseId: string, language?: string, enabled = true) {
  return useQuery({
    queryKey: ["checkout", courseId, language],
    queryFn: () =>
      apiFetch<CheckoutPreview>(`/courses/${courseId}/checkout${language ? `?language=${language}` : ""}`),
    enabled: Boolean(courseId) && enabled,
  });
}

export function useCompleteCheckout(courseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<EnrollmentCompletion>(`/courses/${courseId}/checkout/complete`, { method: "POST" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["enrollments"] });
    },
  });
}

async function listEnrollments() {
  const { data } = await apiRequest<EnrollmentResponse[]>("/enrollments?limit=100");
  return data;
}

/** Only meaningful for an authenticated student — callers gate on `useCurrentUser()` first so
 * a Guest never fires a request that will 401. */
export function useEnrollments() {
  const { data: user } = useCurrentUser();
  return useQuery({
    queryKey: ["enrollments"],
    queryFn: listEnrollments,
    enabled: user?.role === "student",
  });
}

export function useIsEnrolled(courseId: string): boolean {
  const { data: enrollments } = useEnrollments();
  return enrollments?.some((e) => e.courseId === courseId) ?? false;
}
