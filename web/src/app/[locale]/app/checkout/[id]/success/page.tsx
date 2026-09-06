import { PurchaseSuccessScreen } from "@/components/screens/purchase-success-screen";

export default async function PurchaseSuccessPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PurchaseSuccessScreen courseId={id} />;
}
