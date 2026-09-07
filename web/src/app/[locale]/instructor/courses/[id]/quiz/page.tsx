import { QuizEditorScreen } from "@/components/screens/quiz-editor-screen";

export default async function QuizEditorPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <QuizEditorScreen courseId={id} />;
}
