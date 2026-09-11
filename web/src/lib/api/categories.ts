import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "./client";

export interface Category {
  id: string;
  name: string;
  slug: string;
  courseCount: number;
}

/** GET /categories — plain array, no pagination (execution/INTEGRATION_CONTRACT.md). */
export async function listCategories(): Promise<Category[]> {
  return apiFetch<Category[]>("/categories");
}

export function useCategories() {
  return useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
    staleTime: 5 * 60_000, // categories change rarely
  });
}

// Mutations below are Admin-only (backend requires Role.admin + CSRF header on all three —
// CategoryRoutes.kt) and back Admin — Manage Categories (Task 12). `name` is the only writable
// field; `slug` is server-generated at creation and never changes on rename
// (execution/INTEGRATION_CONTRACT.md § Categories).

function useCategoryMutation<TVariables, TResponse>(mutationFn: (variables: TVariables) => Promise<TResponse>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["categories"] });
    },
  });
}

export function useCreateCategory() {
  return useCategoryMutation((name: string) =>
    apiFetch<Category>("/categories", { method: "POST", body: JSON.stringify({ name }) }),
  );
}

export function useUpdateCategory() {
  return useCategoryMutation(({ id, name }: { id: string; name: string }) =>
    apiFetch<Category>(`/categories/${id}`, { method: "PATCH", body: JSON.stringify({ name }) }),
  );
}

/** Backend rejects with 409 CATEGORY_IN_USE while `courseCount > 0` — surfaced to the caller as
 * an ApiError the screen maps to a localized message, never silently swallowed. */
export function useDeleteCategory() {
  return useCategoryMutation((categoryId: string) => apiFetch<void>(`/categories/${categoryId}`, { method: "DELETE" }));
}
