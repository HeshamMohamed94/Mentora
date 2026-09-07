import { useMutation, useQuery } from "@tanstack/react-query";
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

export interface UploadMediaRequest {
  kind: "courseThumbnail" | "lessonVideo";
  ownerRefId: string;
  contentType: string;
  courseId?: string;
  file: File;
}

export interface UploadMediaResponse {
  mediaId: string;
  storageKey: string;
}

export function uploadMedia(request: UploadMediaRequest) {
  const form = new FormData();
  form.append("kind", request.kind);
  form.append("ownerRefId", request.ownerRefId);
  form.append("contentType", request.contentType);
  if (request.courseId) form.append("courseId", request.courseId);
  form.append("file", request.file);
  return apiFetch<UploadMediaResponse>("/media/uploads", { method: "POST", body: form });
}

export function useUploadMedia() {
  return useMutation({ mutationFn: uploadMedia });
}
