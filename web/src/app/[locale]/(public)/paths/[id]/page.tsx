import { PublicNavbar } from "@/components/navigation/public-navbar";
import { LearningPathDetailsScreen } from "@/components/screens/learning-path-details-screen";

export default async function PublicLearningPathDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content">
        <LearningPathDetailsScreen pathId={id} />
      </main>
    </>
  );
}
