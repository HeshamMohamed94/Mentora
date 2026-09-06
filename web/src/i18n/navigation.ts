import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";

// Locale-aware wrappers around Next.js navigation APIs — always use these instead of
// next/link and next/navigation directly, so every generated href carries the active locale.
export const { Link, redirect, usePathname, useRouter, getPathname } = createNavigation(routing);
