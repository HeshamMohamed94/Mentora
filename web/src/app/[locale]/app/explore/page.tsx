import { ExploreScreen } from "@/components/screens/explore-screen";

// Same screen as (public)/explore, enrollment-aware chrome comes from the authenticated
// shell around it (product/INFORMATION_ARCHITECTURE.md § 2) — no duplicate implementation.
export default function AppExplorePage() {
  return (
    <main id="main-content">
      <ExploreScreen />
    </main>
  );
}
