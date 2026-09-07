import { LessonEditorScreen } from "@/components/screens/lesson-editor-screen";

export default async function NewLessonPage({ params }: { params: Promise<{ id: string; sectionId: string }> }) {
  const { id, sectionId } = await params;
  return <LessonEditorScreen courseId={id} sectionId={sectionId} />;
}
