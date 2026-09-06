import { CertificateDetailScreen } from "@/components/screens/certificate-detail-screen";

export default async function CertificateDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CertificateDetailScreen certificateId={id} />;
}
