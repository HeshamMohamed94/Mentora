import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CURRENT_USER_QUERY_KEY } from "../auth/use-current-user";
import type { AuthUser } from "../auth/types";
import { apiFetch } from "./client";

export interface UpdateCurrentUserInput {
  name?: string;
  preferredLocale?: string;
}

export function useUpdateCurrentUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateCurrentUserInput) =>
      apiFetch<AuthUser>("/users/me", { method: "PATCH", body: JSON.stringify(input) }),
    onSuccess: (user) => {
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, user);
      void queryClient.invalidateQueries({ queryKey: CURRENT_USER_QUERY_KEY });
    },
  });
}
