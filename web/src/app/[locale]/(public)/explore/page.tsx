import { Suspense } from "react";
import { PublicNavbar } from "@/components/navigation/public-navbar";
import { ExploreScreen } from "@/components/screens/explore-screen";

export default function PublicExplorePage() {
  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content">
        <Suspense>
          <ExploreScreen />
        </Suspense>
      </main>
    </>
  );
}
