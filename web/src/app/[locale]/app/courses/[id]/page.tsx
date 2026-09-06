import { CourseDetailsScreen } from "@/components/screens/course-details-screen";

export default async function AppCourseDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CourseDetailsScreen courseId={id} />;
}
