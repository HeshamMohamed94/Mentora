import { InstructorShell } from "@/components/navigation/instructor-shell";

export default function InstructorLayout({ children }: { children: React.ReactNode }) {
  return <InstructorShell>{children}</InstructorShell>;
}
