import { PublicNavbar } from "@/components/navigation/public-navbar";
import { CourseDetailsScreen } from "@/components/screens/course-details-screen";

export default async function PublicCourseDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <>
      <PublicNavbar sticky />
      <main id="main-content">
        <CourseDetailsScreen courseId={id} />
      </main>
    </>
  );
}
