import { useQuery } from "@tanstack/react-query";
import { ApiError, apiFetch, apiRequest } from "./client";

export interface CertificateSummaryResponse {
  id: string;
  courseTitleSnapshot: string;
  instructorNameSnapshot: string;
  issuedAt: string;
}

export interface CertificateDetailResponse extends CertificateSummaryResponse {
  studentNameSnapshot: string;
  completionDateSnapshot: string;
}

async function listCertificates(): Promise<CertificateSummaryResponse[]> {
  const { data } = await apiRequest<CertificateSummaryResponse[]>("/certificates?limit=100");
  return data;
}

async function getCertificate(id: string): Promise<CertificateDetailResponse | null> {
  try {
    return await apiFetch<CertificateDetailResponse>(`/certificates/${id}`);
  } catch (error) {
    if (error instanceof ApiError && error.code === "CERTIFICATE_NOT_FOUND") return null;
    throw error;
  }
}

export function useCertificates() {
  return useQuery({
    queryKey: ["certificates"],
    queryFn: listCertificates,
  });
}

export function useCertificate(id: string) {
  return useQuery({
    queryKey: ["certificates", id],
    queryFn: () => getCertificate(id),
    enabled: Boolean(id),
  });
}
