import { CheckoutScreen } from "@/components/screens/checkout-screen";

// Auth-only by design (product/INFORMATION_ARCHITECTURE.md § "/app/checkout/:courseId") — no
// (public) counterpart, since the backend's checkout routes require the student role.
export default async function CheckoutPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main id="main-content">
      <CheckoutScreen courseId={id} />
    </main>
  );
}
