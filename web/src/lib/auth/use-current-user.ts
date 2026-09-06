"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { getCurrentUser } from "./actions";

export const CURRENT_USER_QUERY_KEY = ["auth", "currentUser"] as const;

/** Session probe for authenticated shells (Sidebar, header account menu). A 401 here means
 * "not logged in" — callers treat `data === undefined` as the guest state, never as an error
 * to surface, since middleware already gates entry to authenticated routes. */
export function useCurrentUser() {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: CURRENT_USER_QUERY_KEY,
    queryFn: getCurrentUser,
    retry: false,
  });

  useEffect(() => {
    function handleForceLogout() {
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, undefined);
    }
    window.addEventListener("mentora:force-logout", handleForceLogout);
    return () => window.removeEventListener("mentora:force-logout", handleForceLogout);
  }, [queryClient]);

  return query;
}
