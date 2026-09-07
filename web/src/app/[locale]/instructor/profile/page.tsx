import { ProfileScreen } from "@/components/screens/profile-screen";

export default function InstructorProfilePage() {
  return <ProfileScreen settingsHref="/instructor/settings" showLearningStats={false} />;
}
