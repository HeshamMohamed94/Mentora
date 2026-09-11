import { Suspense } from "react";
import { AdminCoursesScreen } from "@/components/screens/admin-courses-screen";

// Suspense boundary required by Next.js for AdminCoursesScreen's useSearchParams() (reads the
// optional ?instructor= filter set by Manage Instructors' "View courses" action).
export default function AdminCoursesPage() {
  return (
    <Suspense>
      <AdminCoursesScreen />
    </Suspense>
  );
}
