import { CoursePlayerScreen } from "@/components/screens/course-player-screen";

export default async function CoursePlayerPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CoursePlayerScreen courseId={id} />;
}
