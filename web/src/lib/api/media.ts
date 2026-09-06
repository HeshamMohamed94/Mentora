import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "./client";

export interface PlaybackUrlResponse {
  url: string;
  expiresAt: string;
}

export function getPlaybackUrl(mediaId: string) {
  return apiFetch<PlaybackUrlResponse>(`/media/${mediaId}/playback-url`);
}

export function usePlaybackUrl(mediaId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ["playback-url", mediaId],
    queryFn: () => getPlaybackUrl(mediaId as string),
    enabled: Boolean(mediaId) && enabled,
    staleTime: 4 * 60 * 1000,
  });
}
