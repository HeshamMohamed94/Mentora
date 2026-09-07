import { LessonEditorScreen } from "@/components/screens/lesson-editor-screen";

export default async function EditLessonPage({ params }: { params: Promise<{ id: string; sectionId: string; lessonId: string }> }) {
  const { id, sectionId, lessonId } = await params;
  return <LessonEditorScreen courseId={id} sectionId={sectionId} lessonId={lessonId} />;
}
