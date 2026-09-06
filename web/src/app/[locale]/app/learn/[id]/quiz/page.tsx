import { QuizScreen } from "@/components/screens/quiz-screen";

export default async function QuizPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <QuizScreen courseId={id} />;
}
