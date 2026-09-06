import { CourseDetailsScreen } from "@/components/screens/course-details-screen";

export default async function AppCourseDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <main id="main-content">
      <CourseDetailsScreen courseId={id} />
    </main>
  );
}
