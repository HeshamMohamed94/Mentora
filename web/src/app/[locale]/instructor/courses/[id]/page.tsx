import { CourseEditorScreen } from "@/components/screens/course-editor-screen";

export default async function CourseEditorPage({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ tab?: string }> }) {
  const { id } = await params;
  const { tab } = await searchParams;
  return <CourseEditorScreen courseId={id} initialTab={tab === "curriculum" ? "curriculum" : "overview"} />;
}
