import { AppShell } from "@/components/navigation/app-shell";

// ux/WEB_UX.md § "Authenticated pages do not show the public Navbar — the Sidebar replaces
// it." Wraps every /app/* route; middleware.ts already gates entry to authenticated requests
// only, so AppShell can assume a logged-in session.
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
