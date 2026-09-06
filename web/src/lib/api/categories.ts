import { useQuery } from "@tanstack/react-query";
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
