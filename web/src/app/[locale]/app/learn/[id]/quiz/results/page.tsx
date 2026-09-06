import { QuizResultsScreen } from "@/components/screens/quiz-results-screen";

export default async function QuizResultsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <QuizResultsScreen courseId={id} />;
}
