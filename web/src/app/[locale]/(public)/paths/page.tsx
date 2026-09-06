import { PublicNavbar } from "@/components/navigation/public-navbar";
import { LearningPathsScreen } from "@/components/screens/learning-paths-screen";

export default function PublicLearningPathsPage() {
  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content">
        <LearningPathsScreen />
      </main>
    </>
  );
}
