import { LearningPathDetailsScreen } from "@/components/screens/learning-path-details-screen";

export default async function AppLearningPathDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main id="main-content">
      <LearningPathDetailsScreen pathId={id} />
    </main>
  );
}
