import { Suspense } from "react";
import { ExploreScreen } from "@/components/screens/explore-screen";

// Same screen as (public)/explore, enrollment-aware chrome comes from the authenticated
// shell around it (product/INFORMATION_ARCHITECTURE.md § 2) — no duplicate implementation.
// Suspense boundary required by Next.js for ExploreScreen's useSearchParams() (reads an
// optional initial ?q= from the Dashboard search bar).
export default function AppExplorePage() {
  return (
    <Suspense>
      <ExploreScreen />
    </Suspense>
  );
}
